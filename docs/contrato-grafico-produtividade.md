# Contrato de Arquitetura: Gráfico de Produtividade do Painel e Exportações

> **Status:** Aprovado para Implementação (Rodada 2 — Consolidado com Protótipo)  
> **Versão:** 2.2 (v1 Completa — 9 Métricas, Ajustes de Pontos Futuros, Campo `semExpediente` e Fuso)  
> **Data:** 23/10/2026  
> **Autor:** Arquiteto de Software Bokka  
> **Destinatários:** Wilson, Maestro, Backend, Designer, Frontend, QA  
> **Artefatos Integrados:**  
> - `docs/prototipo-grafico-produtividade.html` (Protótipo funcional homologado do Designer)  
> - `docs/design-grafico-produtividade.md` (Especificação visual e de interação do Designer — 12 seções)  
> - `src/main/java/com/example/demo/service/AgregacaoDiariaService.java` (Serviço de agregação diária no Mongo)  
> - `src/main/java/com/example/demo/util/FusoGravacao.java` (Estratégia de fuso horário da JVM/VPS)  
> - `scripts/indices-dashboard.js` (Índices MongoDB para performance de agregação)  
> - `src/main/java/com/example/demo/controller/UsuarioController.java` (`PATCH /api/usuarios/me/preferences`)  
> - `odonto-clinic-web/src/context/FinancialVisibilityContext.tsx` (Contexto do olho de privacidade)  
> - `odonto-clinic-web/src/pages/financeiro/AuditoriaPage.tsx` (Card Fluxo de Caixa)

---

## 1. Visão Geral e Decisões Homologadas do Wilson

Este contrato consolida os requisitos operacionais, de segurança, de banco de dados e de design do painel do Bokka, integrando todas as diretrizes do Wilson, do Designer e do Backend sem cortes de escopo.

### 1.1 Decisões do Wilson
1. **Todas as 9 Métricas na v1:** Sem faseamento ou cortes por limitação de abas. O seletor de métrica migra para o próprio título do card (dropdown agrupado por Agenda, Pacientes e Financeiro).
2. **Consultas Realizadas:** Apenas agendamentos com `status == REALIZADO` contam como consulta realizada.
3. **Ciclo Semanal:** Recorte de segunda-feira a domingo (alinhado ao mini-calendário). Exemplo: para a data de referência `2026-10-23` (sexta-feira), o período semanal compreende **19 a 25 de outubro de 2026** (segunda a domingo).
4. **Agregação no Servidor:** Processamento 100% no backend (`GET /api/dashboard/produtividade`) via pipelines do MongoDB integrados ao `AgregacaoDiariaService`.
5. **Exportação no Navegador:** Geração de relatórios nos formatos **Excel (.xlsx)**, **PDF (.pdf)** e **CSV (.csv)** no cliente, utilizando os dados já entregues pelo JSON agregado.
6. **Card Fluxo de Caixa da Auditoria (`AuditoriaPage.tsx`):** O botão "Exportar" (atualmente inerte, sem `onClick`) é ativado com os mesmos 3 formatos e as mesmas regras de privacidade.
7. **Regras Anti-Poluição:** Gráfico de área limpo com Recharts; uma única métrica por vez; um único número grande; selo de variação discreto; comparação com o período anterior desligada por padrão (switch opcional com linha tracejada na mesma escala, sem eixo Y duplo); detalhes secundários reservados exclusivamente ao tooltip.

---

## 2. Catálogo Oficial das 9 Métricas da v1

O sistema disponibiliza 9 métricas organizadas em 3 categorias temáticas no menu do título do card.

| Categoria | Identificador (Enum) | Rótulo na UI | Unidade | Permissão Exigida | Regra de Cálculo e Origem do Dado | Comportamento do Selo | Linha Extra no Tooltip |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Agenda** | `CONSULTAS_REALIZADAS` *(Padrão)* | Consultas realizadas | `CONTAGEM` | `AGENDAMENTOS` | Contagem de agendamentos com `status == REALIZADO` por `dataHoraInicio`. | Maior é melhor | `de X agendamentos` |
| **Agenda** | `AGENDAMENTOS` | Agendamentos | `CONTAGEM` | `AGENDAMENTOS` | Total geral de agendamentos por `dataHoraInicio`, **contando TODOS os status** (ver Seção 2.2). **Possui valores futuros** (ver Seção 2.5). | Maior é melhor | `X realizadas • Y faltas • Z canceladas • W agendadas` |
| **Agenda** | `FALTAS` | Faltas (no-show) | `CONTAGEM` | `AGENDAMENTOS` | Contagem de agendamentos com `status == FALTOU` por `dataHoraInicio`. | **Menor é melhor** *(inverte cor do selo)* | `de X agendamentos` |
| **Agenda** | `COMPARECIMENTO` | Taxa de comparecimento | `PERCENTUAL` | `AGENDAMENTOS` | `REALIZADO / (REALIZADO + FALTOU) * 100` **apenas de horários já passados** (ver Seção 2.1). | Maior é melhor | `X de Y atendimentos previstos` |
| **Agenda** | `CANCELAMENTOS` | Cancelamentos | `CONTAGEM` | `AGENDAMENTOS` | Contagem de agendamentos com `status == CANCELADO` por `dataHoraInicio`. | **Menor é melhor** *(inverte cor do selo)* | `de X agendamentos` |
| **Pacientes**| `NOVOS_PACIENTES` | Novos pacientes | `CONTAGEM` | `PACIENTES` | Cadastros por `createdAt`, **incluindo ativos e inativos** (ver Seção 2.3). | Maior é melhor | *(sem linha extra)* |
| **Financeiro**| `FATURAMENTO` | Faturamento | `MOEDA` (R$) | `AUDITORIA_FINANCEIRA` | Contas a receber quitadas (`status == PAGO`), somando `valorPago ?? valorTotal` por `dataPagamento` (regime de caixa). | Maior é melhor | `X recebimentos` |
| **Financeiro**| `TICKET_MEDIO` | Ticket médio | `MOEDA` (R$) | `AUDITORIA_FINANCEIRA` | `Faturamento / Pacientes distintos que pagaram no período` (ver Seção 3). Se não houver pagamentos no dia: `null`. | Maior é melhor | `X pacientes pagantes` |
| **Financeiro**| `A_RECEBER` | A receber | `MOEDA` (R$) | `AUDITORIA_FINANCEIRA` | Saldo em aberto (`valorTotal - valorPago`) de contas `PENDENTE` e `PARCIAL` por `dataVencimento` do período. **Possui valores futuros** (ver Seção 2.4 e 2.5). | **Neutro** *(selo cinza)* | `R$ X em atraso` *(se houver títulos vencidos)* |

---

### 2.1 Definição de `COMPARECIMENTO`
$$\text{Taxa de Comparecimento (\%)} = \frac{\text{REALIZADO}}{\text{REALIZADO} + \text{FALTOU}} \times 100$$
- **Regra Temporal Rígida:** Avalia estritamente horários cujo início já ocorreu (`dataHoraInicio <= agora`). Agendamentos futuros (que estão com status `AGENDADO`) ainda não tiveram a oportunidade de comparecimento; incluí-los derrubaria a taxa artificialmente no dia e no mês corrente.
- **Cancelamentos:** Status `CANCELADO` fica **fora do denominador**, pois a desmarcação prévia libera a cadeira/horário e não configura no-show.
- **Pontos Sem Atendimento:** Se $(\text{REALIZADO} + \text{FALTOU}) == 0$ (ex: domingo ou feriado sem agendamentos passados), o ponto diário retorna `valor: null` (não zero), garantindo que a linha gráfica não despenque.
- **Total Consolidado do Período:** O campo `total` do período é calculado agregando todos os atendimentos do período:
  $$\text{Total} = \frac{\sum \text{REALIZADO}_{\text{período}}}{\sum \text{REALIZADO}_{\text{período}} + \sum \text{FALTOU}_{\text{período}}} \times 100$$
  **Jamais calcular como média aritmética das porcentagens diárias.**

---

### 2.2 Definição de `AGENDAMENTOS`
- **Decisão:** A métrica `AGENDAMENTOS` **conta TODOS os status** (`REALIZADO`, `FALTOU`, `AGENDADO` e `CANCELADO`).
- **Justificativa de Negócio:** Mede a **demanda bruta total** absorvida pela clínica e o esforço de agendamento da recepção. Um agendamento posteriormente cancelado consumiu tempo de atendimento e ocupou a grade.
- **Consistência do Tooltip:** Na especificação do Designer, a linha extra do tooltip apresenta: `"9 realizadas • 2 faltas • 1 cancelada • 2 agendadas"`. A contagem total do ponto deve fechar matematicamente com a soma desses componentes ($9 + 2 + 1 + 2 = 14$).

---

### 2.3 Definição de `NOVOS_PACIENTES` e Inativos
- **Decisão:** A métrica `NOVOS_PACIENTES` **conta TODOS os pacientes cadastrados por `createdAt`, incluindo ativos e inativos (`ativo: true` e `ativo: false`)**.
- **Justificativa de Negócio:** Mede o poder de atração, expansão e captação de clientes da clínica na data em que o cadastro ocorreu. Se um paciente cadastrado em março for inativado em outubro por arquivamento de prontuário, a conquista daquele novo paciente em março continua sendo um fato histórico imutável. Excluir inativos adulteraria retroativamente as curvas históricas de crescimento.

---

### 2.4 Definição de `A_RECEBER` e Inadimplência Histórica
- **Na Curva Gráfica e no Total Principal:**  
  Plota e totaliza estritamente os títulos com `dataVencimento` **dentro do intervalo do período selecionado** (`de <= dataVencimento <= ate`) que possuam saldo em aberto (`status in [PENDENTE, PARCIAL]`).
  - *Integridade da Curva:* Os títulos vencidos de meses passados **NÃO** são somados no dia 1º do mês corrente, evitando picos falsos na curva e garantindo que a soma dos pontos feche 1:1 com o total do período.
- **Tratamento de Vencidos de Antes do Período (Inadimplência Histórica):**  
  Títulos com `dataVencimento < de` que continuam pendentes compõem a inadimplência prévia. O backend entrega no JSON o objeto auxiliar:
  ```json
  "inadimplenciaHistorica": {
    "totalAtrasado": 12500.00,
    "quantidadeTitulos": 8
  }
  ```
  A interface exibe, abaixo do controle ou no tooltip de contexto, a informação auditável:  
  *"+ R$ 12.500,00 em atraso de períodos anteriores"*.
- **Títulos Vencidos Internos ao Período:** Para pontos cujo vencimento já expirou (`dataVencimento < hoje`), o tooltip discrimina na linha extra: `"R$ X em atraso"`.

---

### 2.5 Regra de Pontos Futuros: Exceção para `AGENDAMENTOS` e `A_RECEBER`

As 9 métricas se dividem em duas naturezas temporais distintas:

1. **Métricas de Fato Passado (7 métricas):**  
   `CONSULTAS_REALIZADAS`, `FALTAS`, `COMPARECIMENTO`, `CANCELAMENTOS`, `NOVOS_PACIENTES`, `FATURAMENTO`, `TICKET_MEDIO`.
   - No período corrente em andamento, pontos após a data atual (`hoje`) têm **`valor: null`** e `futuro: true`. A curva gráfica encerra no dia corrente.
   - Navegação: não permite avançar para períodos futuros vazios.
   - Variação: `totalAnterior` aplica estritamente a **regra do mesmo intervalo** (ex.: dias 1 a 23).
2. **Métricas de Previsão Futura (2 métricas):**  
   `AGENDAMENTOS` (compromissos marcados na grade) e `A_RECEBER` (vencimentos futuros emitidos).
   - **Pontos Futuros Têm Valor Real:** Nos pontos após `hoje` dentro do período corrente, o campo `valor` é **preenchido numericamente** (não `null`) com o total agendado ou valor a vencer, e `futuro: true`.
   - **Períodos Futuros Navegáveis:** O seletor de período permite avançar para meses futuros ou anos futuros que possuam dados (ex.: novembro/dezembro de 2026, ano 2027), e esses anos constam no array `anosDisponiveis`.
   - **Regra de Comparação:** Para `AGENDAMENTOS` e `A_RECEBER`, `totalAnterior` compara com o **período anterior INTEIRO** (sem a regra do mesmo intervalo), pois uma agenda ou carteira futura já é planejada e comparada contra a capacidade total do ciclo anterior.

---

### 2.6 Novo Campo `semExpediente` e Continuidade Gráfica

Para evitar que a curva despenque artificialmente para zero em domingos e feriados em que a clínica não opera, o backend fornece o indicador de atividade diária:

- **Definição Exata do Campo `semExpediente`:**  
  Em cada elemento do array `pontos[]`, é retornado o campo booleano `semExpediente: boolean`.
  - `semExpediente: true`: Quando naquele dia de calendário a clínica **não teve nenhuma movimentação de qualquer tipo** (zero agendamentos de qualquer status, zero recebimentos quitados, zero pacientes cadastrados e zero contas com vencimento naquele dia).
  - Quando `semExpediente: true`, o backend devolve **`valor: null`** (mesmo em dias passados).
- **Comportamento no Frontend (Recharts):**  
  O componente gráfico no frontend configura a área/linha com a propriedade `connectNulls={true}` (ou no SVG conecta as coordenadas vizinhas úteis). Com isso, a linha gráfica conecta diretamente a sexta-feira/sábado à segunda-feira útil seguinte, sem traçar uma queda abrupta para zero.
- **Aplicabilidade:**
  - Aplica-se às **9 métricas** nos agrupamentos diários (períodos `SEMANA` e `MES`).
  - Em períodos agrupados por mês (`ANO`) ou agrupados por ano (`TODOS`), o campo `semExpediente` é sempre `false`.

---

## 3. Definição Oficial de Ticket Médio: Decisão e Justificativa

Confirmada a **Opção B** alinhada entre Designer, Arquiteto e Backend:
$$\text{Ticket Médio} = \frac{\sum \text{Faturamento Quitado no Período}}{\text{Quantidade de Pacientes Distintos que Pagaram no Período}}$$

### Justificativa Técnica:
1. **Descolamento entre Consulta e Liquidação:** Em clínicas odontológicas, procedimentos de ortodontia, implantes e próteses são pagos via parcelamentos que frequentemente ocorrem em dias sem atendimento clínico presencial.
2. **Estabilidade Numérica (Anti-Distorção):** Se a clínica recebe pagamentos em um sábado ou dia sem consultas, a divisão por consultas forçaria divisão por zero ou geraria `null`. A divisão por paciente pagante garante que todo dia com recebimento tenha ticket médio representativo.
3. **Padrão de Mercado SaaS Odontológico:** É o indicador utilizado por Clinicorp e Simples Dental para medir a despesa média real por paciente ativo.
4. **Cálculo Agregado do Período:** O campo `total` do período é o faturamento consolidado do período dividido pelo total de pacientes distintos que pagaram durante o período (e **não** a média dos tickets diários).

---

## 4. Estratégia de Fuso Horário Alinhada (Backend x Mongo)

O contrato formaliza a divisão de fusos aprovada pelo Backend:

1. **Horários de Parede e Datas de Negócio (`dataHoraInicio`, `dataVencimento`, `dataPagamento`):**
   - Agrupam no **fuso da JVM** (`FusoGravacao.idMongo()`). Na VPS da produção, a JVM roda em `UTC` e o Spring Data grava em instantes UTC relativos; agrupar pelo fuso da JVM garante que o dia civil visto pelo usuário na tela não seja deslocado para o dia anterior por subtração indevida de horas no Mongo.
2. **Carimbos Técnicos de Auditoria do Sistema (`createdAt` de `pacientes`):**
   - Agrupam explicitamente convertendo para **`America/Sao_Paulo`** (`DateOperators.Timezone.valueOf("America/Sao_Paulo")`). Como `createdAt` é gerado automaticamente pelo servidor em UTC real, a conversão para São Paulo garante que um cadastro realizado às 22h no Brasil caia no dia civil brasileiro correto, e não no dia seguinte.
3. **Contrato com o Frontend:** As chaves de data dos pontos são sempre entregues em string ISO `YYYY-MM-DD`. O frontend renderiza o texto puro sem aplicar transformações de timezone locais do navegador.

---

## 5. Filtro por Dentista (`dentistaId`) e Índices do MongoDB

### 5.1 Decisão sobre o Filtro por Dentista
- **Decisão:** **SIM, o filtro opcional `dentistaId` entra na v1** para as 5 métricas de agenda (`CONSULTAS_REALIZADAS`, `AGENDAMENTOS`, `FALTAS`, `COMPARECIMENTO`, `CANCELAMENTOS`).
- **Casos de Uso Suportados:**
  1. O proprietário da clínica pode alternar a visualização da agenda entre a clínica toda e um profissional específico.
  2. Dentistas logados que acessam a agenda podem visualizar sua própria produtividade individual com performance indexada.

### 5.2 Consolidação dos 5 Índices em `scripts/indices-dashboard.js`
O script `scripts/indices-dashboard.js` consolida os 5 índices como padrão obrigatório (`BASE`):

```javascript
const INDICES_PAINEL = [
  // 1. Agendamentos Geral (CONSULTAS_REALIZADAS, AGENDAMENTOS, FALTAS, COMPARECIMENTO, CANCELAMENTOS)
  {
    colecao: 'agendamentos',
    nome: 'agendamentos_clinica_data_status',
    key: { clinicaId: 1, dataHoraInicio: 1, status: 1 },
    opcoes: {}
  },
  // 2. Agendamentos Filtrado por Dentista (v1)
  {
    colecao: 'agendamentos',
    nome: 'agendamentos_clinica_dentista_data_status',
    key: { clinicaId: 1, dentistaId: 1, dataHoraInicio: 1, status: 1 },
    opcoes: {}
  },
  // 3. Faturamento e Ticket Médio por Paciente Pagante
  {
    colecao: 'contas_receber',
    nome: 'contas_receber_clinica_status_pagamento_paciente',
    key: { clinicaId: 1, status: 1, dataPagamento: 1, pacienteId: 1, valorPago: 1 },
    opcoes: {}
  },
  // 4. A Receber por Vencimento
  {
    colecao: 'contas_receber',
    nome: 'contas_receber_clinica_status_vencimento',
    key: { clinicaId: 1, status: 1, dataVencimento: 1, valorTotal: 1, valorPago: 1 },
    opcoes: {}
  },
  // 5. Novos Pacientes por Data de Cadastro
  {
    colecao: 'pacientes',
    nome: 'pacientes_clinica_created_at',
    key: { clinicaId: 1, createdAt: 1 },
    opcoes: {}
  }
];
```

---

## 6. Persistência do Olho de Privacidade e Sinal `pronto`

### 6.1 Persistência por Usuário no Servidor
- **Modelo:** Embutido em `User.preferences` (`UserPreferences.java`, commit `5e2400e`).
- **Gravação:** `PATCH /api/usuarios/me/preferences` com `{ "hideFinancialInfo": boolean }`.
- **Leitura:** `GET /api/usuarios/me` retorna as preferências do usuário conectado.

### 6.2 Prevenção de Flash de Conteúdo via Sinal `pronto`
O contexto `FinancialVisibilityContext.tsx` expõe:
```typescript
interface FinancialVisibilityContextType {
  visible: boolean;
  toggleVisible: () => void;
  pronto: boolean; // TRUE somente após a confirmação do /api/usuarios/me
}
```
- **Regra de Blindagem:** Enquanto `pronto === false`, métricas financeiras (`FATURAMENTO`, `TICKET_MEDIO`, `A_RECEBER`) são exibidas como **estritamente mascaradas** (`R$ ••••`) e o menu de exportação financeira permanece **bloqueado**, impedindo que valores reais pisquem na tela em novas máquinas ou abas privadas.

---

## 7. Regras de Privacidade na Exportação (Produtividade e Fluxo de Caixa)

### 7.1 Matriz de Exportação

| Categoria da Métrica | Estado da Visibilidade Financeira | Ação no Menu Exportar | Conteúdo do Arquivo |
| :--- | :--- | :--- | :--- |
| **Agenda e Pacientes** (`CONSULTAS_REALIZADAS`, `AGENDAMENTOS`, `FALTAS`, `COMPARECIMENTO`, `CANCELAMENTOS`, `NOVOS_PACIENTES`) | Oculto ou Visível | **Sempre Liberado** (Excel, PDF, CSV) | Dados reais da métrica. **Sem valores em R$.** Seguro para apresentar à equipe. |
| **Financeiras** (`FATURAMENTO`, `TICKET_MEDIO`, `A_RECEBER`) | **Visível** (`visible: true` e `pronto: true`) | **Liberado** (Excel, PDF, CSV) | Valores monetários formatados em pt-BR. |
| **Financeiras** (`FATURAMENTO`, `TICKET_MEDIO`, `A_RECEBER`) | **Oculto** (`visible: false` ou `pronto: false`) | **BLOQUEADO** | **Nenhum arquivo gerado.** |
| **Fluxo de Caixa** (`AuditoriaPage.tsx`) | **Oculto** | **BLOQUEADO** | **Nenhum arquivo gerado.** |

### 7.2 Proibição de Exportação Mascarada
> [!CAUTION]
> É expressamente proibido gerar relatórios contendo dados mascarados como `R$ ••••` ou zeros artificiais. Com valores ocultos, as opções de download permanecem desabilitadas no menu e é exibido o banner com o botão *"Mostrar valores"*.

---

## 8. Correção do Botão "Exportar" do Fluxo de Caixa (`AuditoriaPage.tsx`)

O botão inerte em [AuditoriaPage.tsx](file:///C:/Users/Wilson/Desktop/CODE/sistema-tainah/odonto-clinic-web/src/pages/financeiro/AuditoriaPage.tsx) (linhas 497-504) passa a disparar o menu de exportação:
- Reutiliza a variável `fluxo` já presente no estado do componente.
- Formatos: **Excel (.xlsx)**, **PDF (.pdf)** e **CSV (.csv)**.
- Colunas: `Mês` | `Receitas (R$)` | `Despesas (R$)` | `Lucro Líquido (R$)`.
- Linhas: 6 meses + linha final de `Total`.
- Bloqueio imediato se a visibilidade financeira estiver oculta.

---

## 9. Contrato da API: Endpoint `GET /api/dashboard/produtividade`

### 9.1 Parâmetros de Requisição
- `metrica`: enum obrigatório (os 9 valores).
- `periodo`: `SEMANA`, `MES`, `ANO`, `TODOS` (padrão: `MES`).
- `referencia`: data `YYYY-MM-DD` (padrão: hoje no fuso brasileiro).
- `dentistaId`: string opcional (filtro por profissional).

### 9.2 Matriz de Autorização RBAC (HTTP 403)
- `CONSULTAS_REALIZADAS`, `AGENDAMENTOS`, `FALTAS`, `COMPARECIMENTO`, `CANCELAMENTOS`: requer `AGENDAMENTOS` (ou `PROPRIETARIO`/`SOCIO`).
- `NOVOS_PACIENTES`: requer `PACIENTES` (ou `PROPRIETARIO`/`SOCIO`).
- `FATURAMENTO`, `TICKET_MEDIO`, `A_RECEBER`: requer `AUDITORIA_FINANCEIRA` (ou `PROPRIETARIO`/`SOCIO`).

*Usuários não autorizados recebem imediatamente `HTTP 403 Forbidden`.*

---

### 9.3 Contrato de Resposta (HTTP 200 OK)

#### Exemplo 1: `COMPARECIMENTO` no Período `SEMANA` (com `semExpediente`)
*Data de referência: `2026-10-23` (sexta-feira). Semana: segunda-feira 19/10 a domingo 25/10.*
```json
{
  "metrica": "COMPARECIMENTO",
  "periodo": "SEMANA",
  "referencia": "2026-10-23",
  "rotuloPeriodo": "19 a 25 de outubro de 2026",
  "unidade": "PERCENTUAL",
  "total": 87.5,
  "totalAnterior": 83.3,
  "variacaoPercentual": 4.2,
  "textoVariacao": "vs semana anterior (mesmo intervalo: seg a sex)",
  "pontos": [
    {
      "chave": "2026-10-19",
      "rotulo": "Seg",
      "rotuloSecundario": "19",
      "valor": 88.9,
      "anterior": 80.0,
      "semExpediente": false,
      "futuro": false,
      "detalhes": { "realizadas": 8, "faltas": 1, "previstos": 9 }
    },
    {
      "chave": "2026-10-23",
      "rotulo": "Sex",
      "rotuloSecundario": "23",
      "valor": 85.7,
      "anterior": 80.0,
      "semExpediente": false,
      "futuro": false,
      "detalhes": { "realizadas": 6, "faltas": 1, "previstos": 7 }
    },
    {
      "chave": "2026-10-24",
      "rotulo": "Sáb",
      "rotuloSecundario": "24",
      "valor": null,
      "anterior": null,
      "semExpediente": true,
      "futuro": true,
      "detalhes": null
    },
    {
      "chave": "2026-10-25",
      "rotulo": "Dom",
      "rotuloSecundario": "25",
      "valor": null,
      "anterior": null,
      "semExpediente": true,
      "futuro": true,
      "detalhes": null
    }
  ],
  "anosDisponiveis": [2024, 2025, 2026],
  "metricasPermitidas": [
    "CONSULTAS_REALIZADAS",
    "AGENDAMENTOS",
    "FALTAS",
    "COMPARECIMENTO",
    "CANCELAMENTOS",
    "NOVOS_PACIENTES"
  ]
}
```

#### Exemplo 2: `A_RECEBER` no Período `MES` (Pontos Futuros Preenchidos + Inadimplência Histórica)
```json
{
  "metrica": "A_RECEBER",
  "periodo": "MES",
  "referencia": "2026-10-23",
  "rotuloPeriodo": "Outubro de 2026",
  "unidade": "MOEDA",
  "total": 34800.00,
  "totalAnterior": 31200.00,
  "variacaoPercentual": 11.5,
  "textoVariacao": "vs mês anterior (período inteiro)",
  "inadimplenciaHistorica": {
    "totalAtrasado": 12500.00,
    "quantidadeTitulos": 8
  },
  "pontos": [
    {
      "chave": "2026-10-20",
      "rotulo": "20",
      "rotuloSecundario": "ter",
      "valor": 1200.00,
      "anterior": 1000.00,
      "semExpediente": false,
      "futuro": false,
      "detalhes": { "atrasado": 1200.00 }
    },
    {
      "chave": "2026-10-25",
      "rotulo": "25",
      "rotuloSecundario": "dom",
      "valor": null,
      "anterior": null,
      "semExpediente": true,
      "futuro": true,
      "detalhes": null
    },
    {
      "chave": "2026-10-28",
      "rotulo": "28",
      "rotuloSecundario": "qua",
      "valor": 2500.00,
      "anterior": 2000.00,
      "semExpediente": false,
      "futuro": true,
      "detalhes": { "atrasado": 0.00 }
    }
  ],
  "anosDisponiveis": [2024, 2025, 2026, 2027],
  "metricasPermitidas": [
    "CONSULTAS_REALIZADAS",
    "AGENDAMENTOS",
    "FALTAS",
    "COMPARECIMENTO",
    "CANCELAMENTOS",
    "NOVOS_PACIENTES",
    "FATURAMENTO",
    "TICKET_MEDIO",
    "A_RECEBER"
  ]
}
```

---

## 10. Ordem de Implementação por Agente e Matriz QA

```mermaid
flowchart TD
    subgraph 1. Banco e Backend
        D1["Atualizar scripts/indices-dashboard.js com os 5 índices"] --> B1
        B1["AgregacaoDiariaService: Novos pacientes, Vencimentos, Pacientes distintos pagantes"] --> B2
        B2["ProdutividadeService: regras das 9 métricas, campo semExpediente, pontos futuros com valor para AGENDAMENTOS e A_RECEBER"] --> B3
        B3["ProdutividadeController com RBAC 403 e testes automatizados"]
    end

    subgraph 2. Designer e Frontend
        B3 -.-> F1["FinancialVisibilityContext: expor sinal pronto"]
        F1 --> F2["ProdutividadeCard: dropdown no título com 3 grupos"]
        F2 --> F3["Recharts: connectNulls=true para semExpediente e linha tracejada opcional"]
        F3 --> F4["Menu Exportar no Card Produtividade e no Fluxo de Caixa (AuditoriaPage.tsx)"]
    end

    subgraph 3. Homologação (QA)
        F4 --> Q1["Matriz de 5 perfis (Dono, Dentista, Recepção, Financeiro, Estoque)"]
        B3 --> Q1
        Q1 --> Q2["Validação final de segurança e relatórios gerados"]
    end
```

### Matriz de Testes do QA (Casos Críticos):
1. **QA-SEM-EXPEDIENTE:** Em dias de domingo com `semExpediente: true` e `valor: null`, validar que a linha gráfica do frontend não desaba a zero, conectando o sábado à segunda-feira de forma contínua.
2. **QA-FUTURO-AGENDAMENTOS:** Para `AGENDAMENTOS` e `A_RECEBER`, validar que os dias futuros trazem valores numéricos reais (não null), e que a navegação permite alcançar meses/anos futuros com agendamentos marcados.
3. **QA-COMPARECIMENTO:** Validar que agendamentos futuros não derrubam a taxa de comparecimento do dia corrente e que cancelados não entram no cálculo.
4. **QA-INADIMPLENCIA-HISTORICA:** Validar que títulos vencidos de meses passados não criam picos irreais no dia 1º, e que o valor acumulado é exibido como inadimplência histórica.
5. **QA-EXPORTAR-FLUXO:** Validar o botão Exportar do card Fluxo de Caixa da Auditoria nos 3 formatos e com trava do olho de privacidade.


---

## 11. Notas de Implementação do Backend (divergências e decisões; o restante do contrato foi mantido)

> Autor: agente Backend · Data: 23/10/2026 · Código: `ProdutividadeController`, `ProdutividadeService`, `AgregacaoDiariaService`, `JanelaProdutividade`, `scripts/indices-dashboard.js`.

### 11.1 Divergências entre o contrato e o código real

1. **`PARCIAL` não existe.** O enum real de `ContaReceber` é `PENDENTE`, `PAGO`, `ATRASADO` e `CANCELADO` (o front trata um `PARCIAL` que o backend nunca grava). `A_RECEBER` usa as contas **`PENDENTE` e `ATRASADO`**: saldo em aberto = `max(0, valorTotal − valorPago)`; pagamento parcial mantém a conta `PENDENTE` com `valorPago` parcial. `PAGO` e `CANCELADO` ficam fora, e só entram títulos com saldo maior que zero. `ATRASADO` nunca é atribuído automaticamente (não há rotina), então "vencida" é conta em aberto com `dataVencimento < hoje` (dia de São Paulo); o campo `detalhes.atrasado` do ponto e `inadimplenciaHistorica` seguem essa regra.
2. **"(ou `PROPRIETARIO`/`SOCIO`)" sem bypass por papel.** O 403 usa `User.permissoesEfetivas()`, a mesma fonte do JWT, do menu e de `PendenciaCadastroService`. Por padrão `PROPRIETARIO` e `SOCIO` têm todas as permissões (`RoleEnum`), então na prática veem as 9 métricas. Porém `permissoes` personalizadas sobrescrevem o padrão do papel, inclusive o do proprietário (ele pode editar as próprias permissões em `PUT /api/usuarios/{id}`): um proprietário que tirou `AUDITORIA_FINANCEIRA` de si mesmo recebe 403 nas métricas financeiras, coerente com o que o front esconde. O 403 é checado logo após ler a métrica, antes de validar os demais parâmetros, e nada é consultado no banco. Mensagem: `Sem permissão para ver esta métrica.`

### 11.2 Decisões onde o contrato não detalha

| Tema | Decisão |
| :-- | :-- |
| `variacaoPercentual` | Variação relativa com 1 casa decimal. Para `PERCENTUAL` (`COMPARECIMENTO`) é a **diferença em pontos percentuais** (a UI mostra `+2,1 p.p.`). `null` quando falta um dos lados ou o anterior é 0 (sem selo); `-100.0` quando o atual é 0 e o anterior é maior que 0; sempre `null` em `TODOS`. |
| Casas decimais | `CONTAGEM`: inteiro. `MOEDA`: 2 casas. `PERCENTUAL`: 2 casas (`88.89`), o front formata. |
| Rótulos | `SEMANA`: `rotulo` `Seg`..`Dom`, `rotuloSecundario` número do dia (`19`). `MES`: `rotulo` número do dia (`20`), `rotuloSecundario` `seg`..`dom`. `ANO`: `rotulo` `Jan`..`Dez`, `rotuloSecundario` `null`. `TODOS`: `rotulo` ano, `rotuloSecundario` `null`. A `chave` é sempre `AAAA-MM-DD` (primeiro dia do mês ou do ano nos agrupados). |
| `rotuloPeriodo` | `SEMANA`: `19 a 25 de outubro de 2026`; cruzando mês `26 de outubro a 1 de novembro de 2026`; cruzando ano `28 de dezembro de 2026 a 3 de janeiro de 2027`. `MES`: `Outubro de 2026`. `ANO`: `2026`. `TODOS`: `Todos os anos`. |
| `detalhes` | `CONSULTAS_REALIZADAS`/`FALTAS`/`CANCELAMENTOS`: `{agendamentos}`. `AGENDAMENTOS`: `{realizadas, faltas, canceladas, agendadas}` (`agendadas` = `AGENDADO` + `CONFIRMADO`, fecha com o total). `COMPARECIMENTO`: `{realizadas, faltas, previstos}`. `NOVOS_PACIENTES`: `{}`. `FATURAMENTO`: `{recebimentos}`. `TICKET_MEDIO`: `{pacientesPagantes}`. `A_RECEBER`: `{atrasado}`. Fica `null` quando o ponto não tem valor por ser `semExpediente` ou por ser futuro de um fato passado. |
| `semExpediente` | Dia sem **nenhuma** movimentação da clínica: agendamento (qualquer status), recebimento quitado, paciente cadastrado ou conta com vencimento (qualquer status, inclusive paga ou cancelada). Considera a clínica toda (ignora `dentistaId`). Vale para as 9 métricas em `SEMANA` e `MES`; em `ANO`/`TODOS` é sempre `false` (e nada é consultado). O `anterior` de um dia do período anterior sem expediente também é `null`. O `total` não muda. |
| Pontos futuros | `AGENDAMENTOS` e `A_RECEBER` (`MetricaProdutividadeEnum.projetaFuturo`): janela até o fim do período, `total` do período inteiro, `totalAnterior` do período anterior **inteiro**, pontos futuros com valor numérico (zero quando há movimento da clínica no dia mas nada da métrica; `null` + `semExpediente` quando não há movimento nenhum). `textoVariacao`: `vs mês anterior (período inteiro)` só com o período em andamento. As outras 7: pontos futuros `null`, `totalAnterior` no mesmo intervalo. Referência em período futuro de fato passado devolve `total` 0 (ou `null` em taxa/ticket) e pontos `null`, sem erro. |
| Comparação no mesmo intervalo | Corte = menor entre hoje (São Paulo) e o fim do período; o anterior corta no mesmo número de dias, limitado ao fim dele; período já encerrado compara o anterior inteiro. |
| `anosDisponiveis` | Contíguo, do menor ano com registro da métrica ao maior. Métricas de fato passado param no ano corrente; `AGENDAMENTOS` e `A_RECEBER` incluem anos futuros com dados. Sem dados: só o ano corrente. Respeita `dentistaId` nas de agenda. |
| `COMPARECIMENTO` | `REALIZADO / (REALIZADO + FALTOU)` só com `dataHoraInicio <= agora` (horário de São Paulo); `total` é do período inteiro, não média dos dias; dia sem base: `valor null`. |
| `TICKET_MEDIO` | `total` e cada ponto são calculados no nível do bucket (dia, mês ou ano); pacientes distintos não somam de um bucket para outro. Dia sem pagamento: `null`. |
| `FATURAMENTO` | `valorPago`, ou `valorTotal` quando `valorPago` não existe, de contas `PAGO` por `dataPagamento`. |
| `NOVOS_PACIENTES` | Ativos e inativos, por `createdAt` no dia de São Paulo (instante real, fronteiras em instantes). |
| Fuso | Datas de negócio (`dataHoraInicio`, `dataVencimento`, `dataPagamento`) agrupam no fuso de gravação da JVM (UTC na VPS, São Paulo no ambiente local); `createdAt` agrupa em `America/Sao_Paulo`. Coberto por teste com a JVM em UTC, São Paulo e Pacific/Kiritimati. |
| `dentistaId` | Só vale para as 5 métricas de agenda; desconhecido ou de outra clínica: 404. Nas demais é ignorado. |
| Erros | 400 no formato por campo (`metrica`, `periodo`, `referencia`); 401 sem sessão; 403 sem permissão; 404 dentista. `periodo` e `metrica` aceitam minúsculas. |
| Índices | Os 5 do §5.2 estão em `scripts/indices-dashboard.js` (nomes e chaves idênticos). `FATURAMENTO` e `A_RECEBER` têm leitura de documentos (`FETCH`) limitada ao período porque usam `valorTotal`, que não está no índice de pagamento; as consultas de agenda e de novos pacientes são cobertas. |
