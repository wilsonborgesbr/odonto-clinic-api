# Design: telas de login, recuperação de senha e confirmação de e-mail

> Autor: Designer · Entrega da tarefa DS2 (`docs/plano-execucao-fase-1-2.md`, seção 4.2)
> Público: Frontend (tarefas F1, F2, F3). Só UI pura: nenhuma regra de negócio aqui.
> Base: `src/pages/Login.tsx`, `src/components/ui/*`, `PendenciasBanner.tsx`, tokens de `src/index.css`.

## 0. Regras gerais

**Tokens.** Só os `bokka-*` já existentes (primary `#2A6BF2`, ink/ink-2/ink-3, surface/surface-2/surface-3, border, success/warning/danger/info com `-soft` e `-ink`). Nenhuma cor nova, nenhum CSS novo. Fonte DM Sans herdada do `html`.

**Casca compartilhada (igual ao Login atual).** As quatro telas públicas repetem o mesmo layout. Sugestão: extrair um `AuthShell({ title, subtitle, children })` em `src/components/AuthShell.tsx`.

```
<div className="min-h-screen bg-bokka-surface-2 flex items-center justify-center px-4 py-8">
  <div className="w-full max-w-[400px]">
    <div className="flex flex-col items-center mb-8 text-center">
      <BokkaMark size={44} />
      <h1 className="text-2xl font-bold tracking-tight text-bokka-ink mt-4">{title}</h1>
      <p className="text-sm text-bokka-ink-3 mt-1">{subtitle}</p>
    </div>
    <div className="bg-bokka-surface border border-bokka-border rounded-2xl p-6 shadow-sm space-y-5">{children}</div>
    <p className="text-xs text-bokka-ink-3 text-center mt-6">Bokka · sistema de gestão para clínicas odontológicas</p>
  </div>
</div>
```

**Padrões reaproveitados**
- Campos: `Input` (com `leadingIcon`, `hint`, `error`). Ícones lucide `w-4 h-4 strokeWidth={1.75}`: `Mail`, `Lock`, `Building2`.
- Botão principal: `<Button size="lg" fullWidth loading={...}>`. Secundário: `variant="secondary"`.
- Erro geral do formulário (copiar do Login): `text-sm text-bokka-danger-ink bg-bokka-danger-soft border border-bokka-danger/20 rounded-md px-3 py-2`, com `role="alert"`.
- Link: `text-bokka-primary font-semibold hover:text-bokka-primary-hover`. Link de voltar: `inline-flex items-center gap-1.5 text-sm font-semibold` com `ArrowLeft w-4 h-4`, centralizado no fim do cartão.
- **Painel de estado** (sucesso, erro, carregando), usado em várias telas:
  ```
  <div className="text-center space-y-4" role="status">
    <div className="mx-auto w-14 h-14 rounded-full flex items-center justify-center {chip}"><Icon className="w-7 h-7" strokeWidth={1.75} aria-hidden="true" /></div>
    <p className="text-sm text-bokka-ink-2 leading-relaxed">{texto}</p>
    {botões}
  </div>
  ```
  Chips: sucesso `bg-bokka-success-soft text-bokka-success-ink`; aviso `bg-bokka-warning-soft text-bokka-warning-ink`; informativo `bg-bokka-primary-soft text-bokka-primary`.
- **A11y comum:** ao trocar de estado (enviado, válido, inválido, confirmado), mover o foco para o `h1` (`tabIndex={-1}`) e deixar o painel com `role="status"` (anúncio educado). Erros de validação usam o `error` do `Input`. Ícones decorativos com `aria-hidden`.
- **Mobile 375px:** a coluna de 400px vira `100% - 32px` = 343px. Botões `lg` têm 48px de altura. Nada de grid de 2 colunas nestas telas, então não há scroll horizontal. Textos longos (e-mail, nome de clínica) com `break-words`/`truncate` conforme indicado.

---

## 1. Login (ajustes de F3)

- Campo **Código da clínica**: tirar `required`. Hint novo: *"Opcional. Se você trabalha em mais de uma clínica, informe o código para entrar direto."* Mantém o valor salvo em `localStorage` e o `?c=` da URL.
- Link **Esqueci minha senha** logo abaixo do campo Senha, alinhado à direita: `<div className="flex justify-end -mt-2"><Link to={`/recuperar-senha${codigo ? `?c=${codigo}` : ''}`} className="text-sm text-bokka-primary font-semibold hover:text-bokka-primary-hover">Esqueci minha senha</Link></div>`.
- Não há link "Esqueci o código" (o lembrete de códigos saiu do escopo).
- Erro de credencial continua genérico (mesma caixa vermelha), sem dizer se o e-mail existe.

---

## 2. `/recuperar-senha`

Casca: título **"Recuperar senha"**, subtítulo *"Informe seu e-mail e enviaremos um link para criar uma nova senha."*

**Estado inicial (formulário)**
1. `Input` **Código da clínica** (`Building2`, `autoComplete="organization"`, pré-preenchido por `?c=` ou `localStorage`). Sem asterisco. Hint: *"Opcional. Ajuda a encontrar o seu cadastro se você usa mais de uma clínica."*
2. `Input` **E-mail** (`type="email"`, `Mail`, `required`, `autoComplete="email"`, `autoFocus`, placeholder `voce@clinica.com`).
3. `Button` lg full: **"Enviar link de recuperação"** (loading durante o envio).
4. Link de voltar: *"Voltar para o login"*.

**Estado enviado** (substitui o formulário dentro do mesmo cartão; sempre a mesma resposta, exista ou não a conta, para não vazar cadastro)
- Chip primário com `MailCheck`.
- Título do `h1` troca para **"Confira seu e-mail"**; subtítulo some.
- Texto: *"Se os dados estiverem corretos, enviamos as instruções de recuperação para o seu e-mail. O link vale por 30 minutos."*
- Dica menor (`text-xs text-bokka-ink-3`): *"Não chegou? Olhe a caixa de spam ou a aba de promoções."*
- Botão `secondary` **"Enviar novamente"**, desabilitado por 60 s com texto *"Enviar novamente em 45 s"* (contagem regressiva, sem `aria-live` para não falar a cada segundo).
- Link *"Voltar para o login"*.

**Erros**
- Formato de e-mail inválido: erro no campo (*"Informe um e-mail válido."*), validado no envio.
- Limite de tentativas (429): caixa de erro geral, *"Muitas tentativas. Aguarde alguns minutos e tente de novo."*
- Falha de rede/servidor: *"Não foi possível enviar agora. Tente novamente em instantes."*
- Nunca mostrar "e-mail não encontrado".

---

## 3. `/redefinir-senha?token=...&c=...`

Ao abrir chama a validação do token. Três estados.

**3.1 Validando.** Casca com título *"Criar nova senha"*. Cartão com `Skeleton` (`h-10 w-full` x2 e `h-12 w-full`), `aria-busy="true"`.

**3.2 Link inválido ou expirado** (também após erro de token no envio)
- Chip de aviso com `AlertTriangle`. `h1`: **"Link inválido ou expirado"**.
- Texto: *"Por segurança, o link de recuperação vale por 30 minutos e só pode ser usado uma vez. Peça um novo para continuar."*
- Botão primário lg full **"Pedir novo link"** (`/recuperar-senha?c=...`) e link *"Voltar para o login"*.

**3.3 Link válido (formulário)**
- Acima dos campos, pílula com a clínica: `inline-flex items-center gap-1.5 rounded-full bg-bokka-surface-3 px-3 py-1 text-xs font-medium text-bokka-ink-2 max-w-full` com `Building2 w-3.5 h-3.5` e `<span className="truncate">{nomeClinica}</span>`. Centralizada.
- Subtítulo: *"Escolha uma senha que você não usa em outros lugares."*
- `Input` **Nova senha** (`type=password`, `autoComplete="new-password"`, `Lock`, `required`) com botão de mostrar/ocultar no `trailingIcon`: `<button type="button" aria-label="Mostrar senha" aria-pressed={visivel} className="-m-2 p-2 text-bokka-ink-3 hover:text-bokka-ink rounded-md">` com `Eye`/`EyeOff` `w-4 h-4`. Dá uma área de toque de 32px; é o máximo que o `Input` atual permite sem alterá-lo.
- **Medidor de força** logo abaixo do campo (`mt-2 space-y-2`, ligado ao input por `aria-describedby`):
  - Barra: `grid grid-cols-3 gap-1.5` com 3 segmentos `h-1.5 rounded-full`. Vazio `bg-bokka-surface-3`; preenchido: Fraca 1 segmento `bg-bokka-danger`; Média 2 `bg-bokka-warning`; Forte 3 `bg-bokka-success`.
  - Rótulo ao lado, `text-xs font-semibold`: **Fraca** `text-bokka-danger-ink`, **Média** `text-bokka-warning-ink`, **Forte** `text-bokka-success-ink`. Texto sempre presente, a cor não é o único sinal. Campo vazio: sem rótulo, só a barra cinza.
  - Região `aria-live="polite"` contendo apenas o rótulo; como o texto só muda quando o nível muda, o leitor de tela não repete a cada tecla.
  - Regra de nível (calculada no front, só para orientar): extras = [8+ caracteres] + [letras e números] + [maiúscula e minúscula] + [símbolo]. **Fraca**: menos de 6 caracteres ou até 1 extra. **Média**: 2 extras. **Forte**: 3 ou mais.
  - **Requisitos** (`ul`, `text-xs text-bokka-ink-2 space-y-1`), cada item com ícone: atendido `Check w-3.5 h-3.5 text-bokka-success-ink`, pendente `Circle w-3.5 h-3.5 text-bokka-ink-3`, mais `<span className="sr-only">atendido/pendente</span>`:
    1. **Mínimo de 6 caracteres** (obrigatório, em `font-semibold`)
    2. 8 ou mais caracteres
    3. Letras e números
    4. Maiúscula e minúscula
    5. Um símbolo (ex.: `! ? @ #`)
  - Só o item 1 bloqueia o envio. Senha Fraca com 6+ caracteres é aceita, mas mostra a dica *"Dica: misture letras, números e símbolos para proteger melhor a conta."*
- `Input` **Confirmar nova senha** (`new-password`, `Lock`). Erro *"As senhas não coincidem."* mostrado ao sair do campo ou no envio, não a cada tecla.
- `Button` lg full **"Salvar nova senha"**. Erro geral: *"Não foi possível salvar a nova senha. Tente novamente."*
- Link *"Voltar para o login"*.

**3.4 Senha alterada (sucesso)**
- Chip de sucesso com `CheckCircle2`; `h1` **"Senha alterada"**; texto *"Pronto! Já pode entrar no Bokka com a nova senha. Enviamos um aviso de segurança para o seu e-mail."*
- Botão primário lg full **"Ir para o login"** (`/login?c=...`).

---

## 4. `/confirmar-email?token=...`

Tela pública; funciona logado ou não. Consome o token ao abrir.

| Estado | Visual | Título (`h1`) | Texto | Ação |
| :-- | :-- | :-- | :-- | :-- |
| Confirmando | `Loader2 w-7 h-7 animate-spin` em chip primário | Confirmando seu e-mail | *"Só um instante..."* | nenhuma |
| Confirmado | Chip de sucesso `w-16 h-16` com `Check w-8 h-8` e entrada `motion-safe:animate-[bokka-eye-pop_300ms_ease-out]` (keyframe que já existe em `index.css`, sem CSS novo) | **E-mail confirmado!** | *"Obrigado! Sua conta está verificada e a recuperação de senha por e-mail está ativa."* | Botão primário lg full **"Acessar meu painel"** (`/` se logado, `/login` se não) |
| Já confirmado | igual ao anterior, sem animação | E-mail já confirmado | *"Este e-mail já tinha sido confirmado. Está tudo certo."* | **"Acessar meu painel"** |
| Inválido/expirado | Chip de aviso com `AlertTriangle` | Link inválido ou expirado | *"Entre no Bokka e use o aviso no topo do painel para pedir um novo link de confirmação."* | Botão `secondary` lg full **"Ir para o login"** |

Com `prefers-reduced-motion`, o `motion-safe:` desliga a animação e o ícone aparece direto. Foco vai para o `h1` ao resolver.

---

## 5. `SeletorClinicaModal`

Aparece só depois que o backend validou a senha e respondeu `SELECAO_CLINICA`. Nunca listar clínicas antes disso.

- Base: `Modal` de `components/ui/Modal.tsx`, `size="sm"` (centralizado, também no mobile; é curto), `title="Escolha a clínica"`, `subtitle="Seu e-mail está ligado a mais de uma clínica. Selecione onde quer entrar."`.
- Corpo: `<ul className="space-y-2">`, cada clínica um `<li>` com `<button type="button">`:
  ```
  className="w-full flex items-center gap-3 rounded-xl border border-bokka-border bg-bokka-surface p-3 text-left transition-colors
             hover:border-bokka-primary hover:bg-bokka-primary-soft/40
             focus-visible:outline-2 focus-visible:outline-bokka-primary-ring focus-visible:outline-offset-2
             disabled:opacity-50 disabled:pointer-events-none"
  ```
  - À esquerda: `<span className="w-10 h-10 rounded-lg bg-bokka-primary-soft text-bokka-primary flex items-center justify-center shrink-0"><Building2 className="w-5 h-5" strokeWidth={1.75} aria-hidden="true" /></span>`.
  - Centro (`min-w-0 flex-1`): nome `text-sm font-semibold text-bokka-ink truncate`; abaixo `text-xs text-bokka-ink-3 truncate` com *"Código: tainah"*.
  - À direita: `ChevronRight w-4 h-4 text-bokka-ink-3`.
- Ordem: a clínica do último acesso (código em `bokka:lastClinicaCodigo`) primeiro, com `<Badge tone="neutral">Último acesso</Badge>` no lugar do chevron; o resto por ordem alfabética.
- **Carregando** (depois do clique): o item escolhido troca o chevron por `Loader2 animate-spin`; os outros ficam `disabled`; a lista ganha `aria-busy="true"`.
- **Erro**: caixa de erro geral no topo do corpo (`role="alert"`): *"Não foi possível entrar nessa clínica. Tente novamente."* A lista continua clicável. Se a seleção expirou (ticket vencido), fechar o modal e mostrar no Login: *"A seleção expirou. Digite sua senha novamente."*
- Rodapé (slot `footer` do `Modal`): `Button variant="ghost"` **"Cancelar"** (fecha e volta ao formulário de login).
- Teclado e leitor de tela: o `Dialog` do Headless UI já prende o foco e fecha com Esc; os itens são botões nativos, então Tab/Enter/Espaço funcionam. Nomes longos de clínica truncam com `title` completo.

---

## 6. Banner "e-mail não confirmado"

Componente irmão do `PendenciasBanner`, renderizado no `AppLayout` **acima** dele (se os dois aparecerem, empilham com `mb-4` cada um). Só para `PROPRIETARIO` com `emailConfirmado === false`; não aparece em `/confirmar-email`.

```
<div role="status" className="mb-4 flex items-start gap-3 rounded-lg border border-bokka-info/30 bg-bokka-info-soft px-3 py-2 text-sm text-bokka-info-ink">
  <Mail className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={1.75} aria-hidden="true" />
  <p className="min-w-0 flex-1">
    <span className="font-semibold">Seu e-mail ainda não foi confirmado.</span>{' '}
    Enviamos um link para <span className="font-semibold break-all">{email}</span>.{' '}
    <button type="button" onClick={reenviar} className="font-semibold underline underline-offset-2 whitespace-nowrap">Reenviar confirmação</button>
  </p>
  <button type="button" onClick={dispensar} aria-label="Dispensar aviso" className="-mr-1 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md hover:bg-bokka-info/10">
    <X className="h-4 w-4" strokeWidth={1.75} />
  </button>
</div>
```

Usa o tom `info` (azul, informativo) e não `warning`, para não competir com o aviso amarelo de pendências. Contraste `info-ink` sobre `info-soft`: cerca de 6:1 (AA).

**Estados**
- Reenviando: o botão vira *"Enviando..."* com `Loader2 w-3.5 h-3.5 animate-spin` inline e fica `disabled`.
- Reenviado: o texto inteiro troca para **"Enviamos o link. Confira sua caixa de entrada e o spam."** (`role="status"` já anuncia) e o botão some por 60 s, depois volta como "Reenviar confirmação".
- Erro: *"Não foi possível reenviar agora. Tente de novo em instantes."* em `text-bokka-danger-ink font-semibold`, com o botão de volta.
- Dispensar: guarda na `sessionStorage` (mesmo padrão do `PendenciasBanner`, com `try/catch`); volta na próxima sessão enquanto o e-mail não for confirmado.
- Mobile 375px: o texto quebra em várias linhas, o e-mail usa `break-all`, e o botão de ação fica inline no fim do parágrafo. O X mantém 28px de largura à direita.

---

## 7. Resumo de componentes

| Novo componente | Onde | Reaproveita |
| :-- | :-- | :-- |
| `AuthShell` (opcional) | Login, RecuperarSenha, RedefinirSenha, ConfirmarEmail | layout do Login atual, `BokkaMark` |
| `MedidorForcaSenha` | RedefinirSenha | tokens success/warning/danger, `Check`/`Circle` |
| `SeletorClinicaModal` | Login | `Modal`, `Badge`, `Button` |
| `EmailNaoConfirmadoBanner` | `AppLayout` | padrão do `PendenciasBanner` |

Telas novas usam apenas `Input`, `Button`, `Badge`, `Modal` e `Skeleton` de `src/components/ui/`.
