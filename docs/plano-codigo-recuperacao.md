# Plano de Arquitetura: Código de 6 Dígitos na Recuperação de Senha do Bokka

> **Status:** Proposta de Arquitetura e Especificação Técnica (Fase de Desenho)  
> **Autor:** Arquiteto de Software do Bokka  
> **Data:** 26 de Outubro de 2026  
> **Versão:** 1.0.0  
> **Arquivo de Destino:** `docs/plano-codigo-recuperacao.md`  

---

## 1. Resumo Executivo e Contexto de Produto

Hoje, a recuperação de senha do **Bokka** (`POST /auth/recuperar-senha`) envia ao usuário um e-mail com botão e link seguro contendo um token criptográfico de 32 bytes (256 bits) gravado apenas como hash SHA-256 no MongoDB (`AuthToken`), com TTL de 30 minutos e consumo atômico de uso único.

### 1.1 O Desafio Real do Usuário (Mobile vs. Desktop)
Em clínicas e consultórios odontológicos, o cenário mais comum de recuperação de senha é:
1. O profissional (dentista, secretária ou proprietário) está sentado em frente ao computador do consultório ou da recepção tentando acessar o Bokka.
2. Ele clica em "Esqueci minha senha" no computador da clínica.
3. Ele pega o **smartphone pessoal**, abre o aplicativo de e-mail e recebe a mensagem de recuperação.
4. **O problema:** Clicar no botão do link no celular abre a redefinição de senha **no navegador do celular**. Depois de redefinir, ele precisa voltar ao computador e digitar a nova senha. Se o e-mail não estiver configurado no computador da clínica (ou for uma conta pessoal protegida por 2FA), ele não consegue clicar no link no computador sem antes copiar ou reescrever uma URL com mais de 80 caracteres.

### 1.2 A Solução
O e-mail de recuperação de senha do Bokka passa a ser **híbrido**:
- **Mantém o link e o botão de redefinição de senha** (30 minutos, 32 bytes) para quem está lendo o e-mail no próprio computador.
- **Passa a trazer TAMBÉM um código numérico de 6 dígitos** (15 minutos) destacado no corpo do e-mail.
- Na tela de redefinição e na tela "Confira seu e-mail", o usuário pode simplesmente **digitar o código de 6 dígitos no computador**, sem precisar abrir o link na máquina.

---

## 2. Comparativo de Alternativas de Contrato de API

Para suportar o fluxo via código numérico, analisamos duas arquiteturas de interação com o Backend:

| Critério | **Alternativa 1: Validação com Ticket Temporário (2 Passos - Recomendada)** | **Alternativa 2: Redefinição Direta em 1 Passo** |
| :--- | :--- | :--- |
| **Fluxo** | 1. `POST /auth/validar-codigo-recuperacao` (devolve `ticket` de 5 min) <br> 2. `POST /auth/redefinir-senha` (usa o `ticket` como token) | `POST /auth/redefinir-senha-com-codigo` (envia email, clinica, codigo e nova senha de uma vez) |
| **Experiência do Usuário (UX)** | **Superior:** Valida o código no 6º dígito instantaneamente. Se o código expirou ou está errado, avisa antes de o usuário gastar tempo inventando e digitando a nova senha. | **Inferior:** O usuário preenche a nova senha, confirmação, regras de força e só descobre se o código é válido após submeter tudo. |
| **Confirmação de Contexto** | Mostra o nome do usuário e da clínica na tela de senha (*"Olá, Dra. Tainah · Consultório Tainah"*), transmitindo segurança. | Não tem como confirmar para quem a senha está sendo trocada antes do envio final. |
| **Reuso de Código** | **100% de reuso da tela `RedefinirSenha.tsx`** e do endpoint atual `POST /auth/redefinir-senha`. | Exige novo endpoint de alteração de senha e duplicação da lógica de validação de senha. |
| **Proteção contra Brute Force** | O código morre no passo 1 e é trocado por um ticket de alta entropia. O passo 2 é imune a ataques de dicionário no código. | Se a senha for inválida (ex.: fraca), consome uma tentativa do código ou força tratamento de estado complexo. |

### Decisão Arquitetural Recomendada
**Adotar a Alternativa 1 (Validação com Ticket Temporário).**  
Ela unifica o fluxo: tanto quem entra pelo link do e-mail quanto quem digita o código de 6 dígitos converge para a **mesma tela de nova senha**, reutilizando o mesmo componente, o mesmo medidor de força de senha e o mesmo endpoint de gravação final `POST /auth/redefinir-senha`.

---

## 3. Modelo de Dados e Segurança Criptográfica

### 3.1 Unificação no Documento `AuthToken` (Atomicidade Nativa)
Em vez de criar uma nova collection (`recuperacao_codigos`), o código numérico será modelado como atributos adicionais no documento **`AuthToken`** já existente (`auth_tokens`).

**Por que no mesmo documento?**
- **Invalidação mútua atômica sem transação distribuída:** Quando o usuário consome o link via `tokenHash`, o documento recebe `usadoEm = Instant.now()`. Automaticamente, o código de 6 dígitos contido no mesmo documento se torna inutilizável.
- Se o usuário consome o código de 6 dígitos via `validar-codigo-recuperacao`, o mesmo documento gera o ticket e marca o código como consumido (ou consome o `AuthToken`), invalidando o link de imediato.
- A exclusão por reenvio (`deleteByUserIdAndTipoAndUsadoEmIsNull`) invalida link e código de uma só vez.

### 3.2 Por que NUNCA usar SHA-256 puro no código de 6 dígitos?
- Um código de 6 dígitos tem apenas $10^6$ (1 milhão) de combinações possíveis (`000000` a `999999`).
- Um SHA-256 não salgado de 1 milhão de números pode ser pré-calculado em **menos de 0,5 segundo** em uma máquina doméstica (tabela rainbow trivial). Em caso de vazamento do banco de dados, o código seria quebrado instantaneamente.

### 3.3 Esquema de Criptografia: HMAC-SHA256 com Sal Individual de 16 Bytes
Para cada emissão de código:
1. Gera-se o código de 6 dígitos via `SecureRandom`:
   ```java
   int numero = RANDOM.nextInt(1_000_000);
   String codigo = String.format("%06d", numero);
   ```
2. Gera-se um **sal aleatório de 16 bytes** exclusivo daquele token (`codigoSalt`).
3. O hash gravado no banco é gerado por **HMAC-SHA256**:
   $$\text{codigoHash} = \text{HMAC-SHA256}(\text{chave}=\text{jwt.secret}, \text{dado}=\text{codigoSalt} \parallel \text{codigo})$$
4. **Benefício de Segurança:** Mesmo que o invasor obtenha uma cópia integral do MongoDB, sem a chave de ambiente da VPS (`jwt.secret`), ele é matematicamente incapaz de validar se um palpite de código está correto.
5. **Comparação em Tempo Constante:** Toda conferência do código fornecido contra o hash do banco utiliza:
   ```java
   MessageDigest.isEqual(hashEsperadoBytes, hashFornecidoBytes);
   ```
   mitigando qualquer ataque de canal lateral por temporização (*timing attack*).

### 3.4 Novos Campos em `AuthToken.java`
```java
@Document(collection = "auth_tokens")
public class AuthToken {
    // ... campos existentes (id, userId, clinicaId, tokenHash, tipo, criadoEm, expiraEm, usadoEm, ipSolicitacao)

    /** Hash HMAC-SHA256 do código de 6 dígitos com o salt. Nulo se o token não possuir código numérico. */
    private String codigoHash;

    /** Sal criptográfico de 16 bytes em HexFormat. */
    private String codigoSalt;

    /** Expiração específica do código de 6 dígitos (15 minutos a partir da emissão). */
    private Instant codigoExpiraEm;

    /** Contador de tentativas incorretas de digitação (máximo 5). */
    private int codigoTentativas;

    /** Indicador de bloqueio por excesso de tentativas incorretas. */
    private Boolean codigoBloqueado;
}
```

---

## 4. Regras de Negócio e Resistência a Força Bruta

### 4.1 Validade Temporal
- **Link Longo (32 bytes):** 30 minutos (inalterado).
- **Código de 6 Dígitos:** **15 minutos**.
  - O código expira antes do link para reduzir a janela de exposição de um segredo de menor entropia.

### 4.2 Resistência a Força Bruta Online (5 Tentativas e Bloqueio)
- **Espaço Amostral:** 1.000.000 de códigos.
- **Limite por Código:** Máximo de **5 tentativas erradas**.
  - Cada palpite incorreto incrementa `codigoTentativas` de forma atômica no MongoDB.
  - Ao atingir a 5ª falha (`codigoTentativas >= 5`), o registro recebe `codigoBloqueado = true`.
- **Probabilidade de Acerto por Força Bruta:**
  $$P(\text{acerto}) = \frac{5}{1.000.000} = 0,0005\% \quad (1 \text{ em } 200.000)$$
  Um invasor tem 99,9995% de probabilidade de ser bloqueado sem sucesso.
- **Se o código for bloqueado:**
  - O código de 6 dígitos morre.
  - O link de 32 bytes recebido no e-mail **continua válido** pelo tempo restante dos 30 minutos (pois o link não sofreu tentativas de força bruta e requer acesso à caixa de entrada).
  - O usuário também pode clicar em "Enviar novamente" para gerar um novo par link + código.

### 4.3 Rate Limiting por IP e por E-mail
Além do bloqueio por código, o endpoint `POST /auth/validar-codigo-recuperacao` é protegido pelo `RateLimitService`:
- **Por IP:** Máximo de **10 requisições por IP a cada 15 minutos** (bloqueia scanners automatizados).
- **Por E-mail:** Máximo de **5 requisições por e-mail a cada 15 minutos**.
- Se o limite for excedido: resposta `HTTP 429 Too Many Requests` com mensagem neutra.

### 4.4 Invalidação em Cascata (Novo Pedido de Recuperação)
Se o usuário solicitar um novo e-mail de recuperação antes dos 15 minutos:
- O método `authTokenService.emitir(...)` já executa `deleteByUserIdAndTipoAndUsadoEmIsNull(userId, RESET_SENHA)`.
- Isso **invalida imediatamente todos os links e códigos anteriores**. Apenas o e-mail mais recente é aceito.

### 4.5 Blindagem Anti-Enumeração
1. O endpoint de solicitação `POST /auth/recuperar-senha` continua retornando `HTTP 200` neutro (`MSG_NEUTRA`).
2. No endpoint `POST /auth/validar-codigo-recuperacao`:
   - Se o e-mail não existir;
   - Se a clínica não for encontrada ou estiver inativa;
   - Se o código estiver errado;
   - Se o código estiver expirado (> 15 min);
   - Se o código estiver bloqueado (> 5 erros);
   - **Em todos os casos acima**, a resposta do servidor deve ser rigorosamente idêntica:
     ```json
     {
       "status": 400,
       "message": "Código inválido ou expirado. Verifique os dados ou solicite um novo."
     }
     ```
   - **Simulação de Tempo Constante:** Se a conta não existir no banco, o serviço executa um cálculo dummy de HMAC contra um salt randômico antes de responder, garantindo que o tempo de resposta seja uniforme (evitando detecção por side-channel).

---

## 5. Especificação dos Contratos de API

### 5.1 Endpoint Novo: `POST /auth/validar-codigo-recuperacao`
Permite validar o código de 6 dígitos e trocá-lo por um ticket temporário de curta duração.

#### Requisição
```http
POST /auth/validar-codigo-recuperacao HTTP/1.1
Content-Type: application/json

{
  "email": "tainah@consultorio.com.br",
  "clinicaCodigo": "tainah",
  "codigo": "849201"
}
```

*Validações dos campos no DTO:*
- `email`: `@NotBlank`, `@Email`
- `clinicaCodigo`: opcional (se omitido, busca única conta ativa pelo e-mail)
- `codigo`: `@NotBlank`, `@Pattern(regexp = "^\\d{6}$", message = "O código deve conter exatamente 6 dígitos numéricos")`

#### Resposta de Sucesso (`HTTP 200 OK`)
```json
{
  "valido": true,
  "ticket": "ticket_reset_9f82a1b4c3d2e1f0...",
  "nomeUsuario": "Dra. Tainah",
  "clinicaNome": "Consultório Dra. Tainah",
  "clinicaCodigo": "tainah"
}
```
*Detalhes do Ticket:*
- O `ticket` é emitido como um token de curta duração (validade de **5 minutos**), do tipo `RESET_SENHA`, registrado no `AuthToken` e atrelado ao mesmo `userId` e `clinicaId`.
- O código de 6 dígitos original é marcado como consumido (`codigoBloqueado = true` ou `usadoEm = Instant.now()`).

#### Respostas de Erro
- **Código Inválido / Expirado / Bloqueado / Conta Inexistente:** `HTTP 400 Bad Request`
  ```json
  {
    "message": "Código inválido ou expirado. Verifique os dados ou solicite um novo."
  }
  ```
- **Tentativas Restantes (Opcional seguro):** Se a conta existir e o código estiver errado com menos de 5 tentativas:
  ```json
  {
    "message": "Código incorreto. Você tem mais 3 tentativas antes do bloqueio."
  }
  ```
  *(Nota de segurança: para preservar 100% da anti-enumeração, a contagem de tentativas restantes só é retornada se a política do produto permitir; caso contrário, usa-se a mensagem neutra `HTTP 400`).*
- **Rate Limit Excedido:** `HTTP 429 Too Many Requests`
  ```json
  {
    "message": "Muitas tentativas. Aguarde alguns minutos e tente novamente."
  }
  ```

### 5.2 Endpoint Existente: `POST /auth/redefinir-senha` (Reutilizado)
O endpoint já existente recebe o `token` (que agora pode ser o token do link OU o `ticket` retornado pelo passo anterior):
```http
POST /auth/redefinir-senha HTTP/1.1
Content-Type: application/json

{
  "token": "ticket_reset_9f82a1b4c3d2e1f0...",
  "novaSenha": "MinhaNovaSenha@2026"
}
```
- Consome o token atomicamente e atualiza o hash BCrypt da senha do usuário.
- Envia o e-mail de alerta de segurança (`senha-alterada-alerta`).

---

## 6. Design dos Templates de E-mail (HTML e TXT)

Para garantir alta taxa de entrega no Outlook/Hotmail/Gmail (sem pontuação de spam no filtro SCL) e clareza visual:
- O código de 6 dígitos é exibido em formato de dois blocos com espaço central (`123 456`) para facilitar a leitura rápida no celular e digitação no computador.
- O botão azul principal é mantido como primeira opção de clique.
- O bloco do código ganha destaque limpo com borda suave e fundo neutro.

### 6.1 Template HTML (`src/main/resources/templates/email/reset-senha.html`)
```html
<!-- Bloco de ação principal -->
<p style="margin:0 0 16px 0;">Recebemos uma solicitação para redefinir a senha do seu usuário na clínica <strong style="color:#0B1220;">${nomeClinica}</strong>.</p>

<!-- Opção 1: Botão direto -->
<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 16px 0;">
  <tr>
    <td align="center" bgcolor="#2A6BF2" style="background-color:#2A6BF2;border-radius:8px;">
      <a class="btn-link" href="${link}" target="_blank" style="display:inline-block;padding:14px 32px;font-family:'DM Sans','Segoe UI',-apple-system,Roboto,Arial,sans-serif;font-size:16px;line-height:20px;font-weight:700;color:#FFFFFF;text-decoration:none;">Redefinir minha senha</a>
    </td>
  </tr>
</table>

<!-- Opção 2: Código de 6 dígitos -->
<div style="margin:0 0 24px 0;padding:16px;background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:10px;text-align:center;">
  <p style="margin:0 0 6px 0;font-size:13px;line-height:18px;color:#64748B;font-weight:500;">Ou use este código de verificação no computador:</p>
  <p style="margin:0;font-size:28px;line-height:34px;font-weight:700;letter-spacing:4px;color:#0B1220;font-family:'DM Sans',Consolas,monospace;">${codigoFormatado}</p>
  <p style="margin:6px 0 0 0;font-size:12px;line-height:16px;color:#64748B;">Código válido por ${validadeCodigo} · Link válido por ${validade}</p>
</div>

<!-- Contingência URL -->
<p style="margin:0 0 4px 0;font-size:13px;line-height:18px;color:#64748B;">Se preferir, copie e cole este endereço no navegador:</p>
<p style="margin:0 0 24px 0;font-size:13px;line-height:20px;word-break:break-all;"><a href="${link}" target="_blank" style="color:#1E4FBE;">${link}</a></p>
```

### 6.2 Template Texto Simples (`src/main/resources/templates/email/reset-senha.txt`)
```text
Redefinir sua senha no Bokka

Olá, ${nome}!

Recebemos uma solicitação para redefinir a senha do seu usuário na clínica ${nomeClinica}.

Para criar uma nova senha, abra este link:

${link}

Ou digite este código de verificação de 6 dígitos no sistema:

${codigoFormatado}

O código de verificação vale por ${validadeCodigo}. O link vale por ${validade}. Ambos só podem ser usados uma vez.

Não pediu essa alteração? Pode ignorar este e-mail: sua senha continua a mesma.

--
Você recebeu este e-mail porque alguém solicitou a redefinição de senha da sua conta no Bokka.
Bokka · Sistema de Gestão Odontológica · Suporte: suporte@bokka.com.br
```

---

## 7. Fluxo de Telas e Experiência do Usuário (Frontend)

```
[Tela 1: RecuperarSenha]
   Informar E-mail e Clínica
              │
              ▼
[Tela 2: "Confira seu e-mail"]
   Mostra aviso de e-mail enviado
   ├── Opção A: Usuário clica no Link no celular/PC ──► [Tela 4: RedefinirSenha com ?token=...]
   └── Opção B: Usuário clica em "Digitar código" ───► [Tela 3: Digitar Código de 6 dígitos]
                                                                │
                                                     (Valida código e emite ticket)
                                                                ▼
                                                       [Tela 4: RedefinirSenha]
                                                          Informa nova senha
                                                                │
                                                                ▼
                                                       [Tela 5: Senha Alterada / Login]
```

### 7.1 Tela "Confira seu e-mail" (`RecuperarSenha.tsx`)
Abaixo da mensagem atual de orientações, inclui-se a chamada de ação direta:
```tsx
<div className="mt-4 pt-4 border-t border-bokka-border text-center">
  <p className="text-xs text-bokka-ink-3 mb-2">Já está com o código em mãos?</p>
  <Button
    type="button"
    variant="primary"
    fullWidth
    onClick={() => navigate(`/redefinir-senha?c=${codigoClinica}&email=${encodeURIComponent(email)}`)}
  >
    Digitar código de 6 dígitos
  </Button>
</div>
```

### 7.2 Tela `RedefinirSenha.tsx` (Comportamento Dual Elegante)
1. **Modo Link Direto (`?token=...` presente na URL):**
   - Comportamento idêntico ao atual: valida o token via `useValidarTokenReset` e renderiza os campos de "Nova senha" e "Confirmar senha".
2. **Modo Digitação de Código (sem `token` na URL):**
   - Apresenta os campos de identificação (E-mail e Clínica pré-preenchidos se vieram na navegação) e o campo numérico de 6 dígitos.
   - **Componente Input OTP (6 caixas de dígitos):**
     - Suporte a `inputMode="numeric"`, `autoFocus` na primeira caixa.
     - Suporte a colar (*paste*): se o usuário copiar `849 201` ou `849201`, o componente higieniza e distribui os dígitos automaticamente.
     - Avanço automático de foco entre as caixas ao digitar.
     - Ao completar o 6º dígito, dispara automaticamente a chamada para `validarCodigoRecuperacao`.
   - **Transição de Sucesso:**
     - Armazena o `ticket` retornado no estado.
     - O formulário transiciona suavemente para o passo de digitação da nova senha, exibindo o cabeçalho de boas-vindas (*"Redefinir senha de Dra. Tainah"*).

---

## 8. Mapeamento de Arquivos por Agente

### 8.1 Backend (`demo/`)
| Arquivo | Responsabilidade |
| :--- | :--- |
| `model/AuthToken.java` | Novos campos: `codigoHash`, `codigoSalt`, `codigoExpiraEm`, `codigoTentativas`, `codigoBloqueado`. |
| `service/AuthTokenService.java` | Geração do código via `SecureRandom`, cálculo de HMAC com salt, verificação em tempo constante (`MessageDigest.isEqual`), incremento atômico de tentativas e queima mútua. |
| `service/RecuperacaoSenhaService.java` | Inclusão do código de 6 dígitos nas variáveis do e-mail, e novo método `validarCodigo(ValidarCodigoRecuperacaoDTO)` com proteção anti-enumeração. |
| `dto/ValidarCodigoRecuperacaoDTO.java` | Novo DTO contendo `email`, `clinicaCodigo` e `codigo`. |
| `dto/ValidarCodigoResponseDTO.java` | Novo DTO contendo `valido`, `ticket`, `nomeUsuario`, `clinicaNome`, `clinicaCodigo`. |
| `controller/AuthController.java` | Novo endpoint `@PostMapping("/validar-codigo-recuperacao")`. |
| `templates/email/reset-senha.html` | Atualização do layout HTML com o quadro de destaque do código. |
| `templates/email/reset-senha.txt` | Atualização do texto simples com a exibição do código formatado. |
| `scripts/indices-auth-tokens.js` | Atualização do script de índices do MongoDB se necessário. |

### 8.2 Frontend (`odonto-clinic-web/`)
| Arquivo | Responsabilidade |
| :--- | :--- |
| `pages/RecuperarSenha.tsx` | Adicionar botão e transição "Digitar código de 6 dígitos" na tela de confirmação de envio. |
| `pages/RedefinirSenha.tsx` | Implementar o fluxo dual (Link com token vs. Digitação de código numérico). |
| `components/ui/InputOtp.tsx` | Componente reutilizável e acessível de entrada para 6 dígitos numéricos com suporte a colar e teclado numérico. |
| `services/authService.ts` | Adicionar função `validarCodigoRecuperacao(dados)` e hook React Query `useValidarCodigoRecuperacao()`. |
| `types/index.ts` | Definição das interfaces `ValidarCodigoRecuperacaoDTO` e `ValidarCodigoResponseDTO`. |

---

## 9. Plano de Testes e Cenários de Verificação (Para o QA)

### 9.1 Testes Unitários e de Integração no Backend
1. **Geração e Formato:**
   - Garantir que o código gerado tenha sempre exatamente 6 dígitos numéricos (inclusive com zeros à esquerda, ex.: `004812`).
   - Garantir que o hash gravado no banco não seja SHA-256 puro e dependa do salt e da chave secreta.
2. **Invalidação Mútua:**
   - Emitir par link + código. Consumir pelo link: testar se o código de 6 dígitos é rejeitado com erro.
   - Emitir par link + código. Validar pelo código e redefinir senha com o ticket: testar se o link original de 32 bytes é rejeitado com erro.
3. **Expiração Temporal Independente:**
   - Simular passagem de 16 minutos: código de 6 dígitos deve retornar expirado (`HTTP 400`), mas o link de 32 bytes ainda deve ser aceito até completar 30 minutos.
4. **Proteção contra Força Bruta:**
   - Enviar 4 palpites errados: tentativas incrementadas para 4.
   - Enviar o 5º palpite errado: código marcado como bloqueado.
   - Enviar o 6º palpite com o código correto: **deve ser recusado**, pois o código foi bloqueado na 5ª falha.
5. **Anti-Enumeração e Timing:**
   - Submeter código para e-mail inexistente: deve retornar `HTTP 400` com a mesma mensagem de código inválido e tempo de resposta comparável ao de uma conta real.
6. **Reenvio de E-mail:**
   - Solicitar novo e-mail: o código e o link anteriores são apagados do banco; tentar validar o código antigo resulta em `HTTP 400`.

### 9.2 Testes End-to-End e de Usabilidade no Frontend
1. **Fluxo Mobile-to-Desktop:**
   - Solicitar recuperação na tela do desktop -> ler código no e-mail -> clicar em "Digitar código" -> inserir os 6 dígitos -> definir nova senha -> login automático ou redirecionamento com sucesso.
2. **Colar Código com Espaços e Traços:**
   - Testar copiar do e-mail `"849 201"` ou `"849-201"` e colar na primeira caixa do OTP: as 6 caixas devem ser preenchidas corretamente e o formulário submetido.
3. **Feedback Acessível:**
   - Testar leitura via leitor de tela (NVDA/VoiceOver) das caixas do OTP e anúncio de mensagens de erro (`role="alert"`).
4. **Navegação de Teclado:**
   - Digitar dígito avança foco para o próximo campo; tecla `Backspace` em campo vazio retrocede o foco para o dígito anterior.
