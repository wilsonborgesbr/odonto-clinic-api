# Design: gráfico "Produtividade" do painel (v1 com todas as métricas)

> Autor: Designer · Para: Frontend, Backend e Arquiteto (contrato em `docs/contrato-grafico-produtividade.md`) · Sem código: só especificação.
> **Protótipo navegável:** `docs/prototipo-grafico-produtividade.html` (abre com duplo clique, sem internet obrigatória, dados falsos), com alternador **Claro / Escuro**.
> **Temas:** os dois cards (Produtividade e Fluxo de caixa) são escritos só com variáveis semânticas. Tokens por tema, contrastes calculados e o que o Frontend prepara agora: seções **14 e 15**. As cores citadas nas seções 6 e 12 são as do tema claro.
> Base: `Dashboard.tsx` (hoje um card escuro `bg-bokka-ink` com barras brancas + linha do ano anterior), referência do Wilson (card "Balance Overview"), `AuditoriaPage.tsx`, `FinancialVisibilityContext.tsx`, tokens de `src/index.css`, `Card.tsx`, Recharts 3.10 (já instalado).

## 1. Resumo das decisões

| Tema | Decisão |
| :-- | :-- |
| Princípio | **Uma métrica por vez, um único número grande, uma escala.** Tudo que é detalhe vai para o tooltip. A tela em repouso tem só: nome da métrica, controle de período, Exportar, número, selo e gráfico. |
| Métricas | **9 na v1**, em 3 grupos, cada uma só para quem tem a permissão: Agenda (5), Pacientes (1), Financeiro (3). Padrão: **Consultas realizadas**. |
| Seletor de métrica | O **próprio título do card** é o seletor (`Consultas realizadas ▾`), abre uma lista agrupada. Sem abas, sem chips, sem rolagem horizontal. |
| Seletor de período | **Um controle só**: `‹ Outubro de 2026 ▾ ›`. As setas navegam; o centro abre um painel com Semana/Mês/Ano/Todos, anos disponíveis e a opção de comparar. |
| Comparação | Por padrão só o **selo %** (ou p.p.). A linha tracejada do período anterior existe, mas fica **desligada** e escondida dentro do painel de período. Mesma escala, nunca duas. |
| Visual | Card claro, gráfico de área com linha suave. Azul `#2A6BF2` como base, linha e gradiente em tons mais claros. **Os dois cards (Produtividade e Fluxo de caixa) seguem a mesma paleta branco e azul no modo claro**; os cards pretos viram a versão do futuro modo escuro (seção 14). |
| Temas | Um componente por card, **sem versão escura própria**: o tema troca só um conjunto de variáveis CSS (`--card-bg`, `--line`, `--positive`...). Seção 14 (tokens e contrastes) e seção 15 (o que o Frontend prepara agora). |
| Privacidade | Reaproveita o olho que já existe (salvo por usuário no servidor). Com valores ocultos, **exportar métrica financeira fica bloqueado** ("Mostre os valores para exportar"); métricas sem dinheiro exportam normalmente. |
| Exportar | Menu **Excel · PDF · CSV** no card Produtividade **e** no card Fluxo de caixa da Auditoria (hoje sem ação), mesmo padrão. O Fluxo de caixa segue a versão clara A (seção 13). |
| Pré-requisito | Endpoint agregado (a consulta de hoje, 300 agendamentos, não serve). Seção 10, para o Arquiteto. |

---

## 2. Pesquisa: como os sistemas brasileiros mostram isso

Fiz buscas na web e li as páginas que apareceram. **O que consegui confirmar é limitado**, então separo o verificado do não verificado.

**Confirmado**
- **Clinicorp** anuncia um *Dashboard Analítico* com ocupação da agenda, metas, receita e faturamento, mais de 50 relatórios (faltas, desmarcações, primeiras consultas, financeiro) e relatórios de produção por profissional e por cadeira. [clinicorp.com](https://www.clinicorp.com/) · [post sobre faturamento](https://www.clinicorp.com/post/faturamento-clinica-odontologica-gargalo)
- **Simples Dental** trata como indicadores centrais: agendamentos (com taxa de comparecimento), ocupação das cadeiras, ticket médio, **faturamento por período (mensal, semestral, anual)** e inadimplência. [artigo de indicadores](https://www.simplesdental.com/blog/indicadores-de-desempenho-na-clinica-odontologica/)
- Material de mercado ([Siodonto](https://siodonto.com.br/blog/p/dashboards-na-clinica-odontologica-transforme-dados-em-decisoes-semanais/)) lista **faltas (no-show)** como primeiro KPI e, entre os mais acionáveis, no-show, cancelamento em cima da hora, ocupação, conversão de orçamento, ticket médio e inadimplência.

**Não consegui verificar**
- Dental Office, Dentidesk, Controle Odonto, Amplimed e iClinic: nenhuma busca trouxe descrição pública do painel. Não abri demos nem central de ajuda.
- O **tipo de gráfico** de qualquer um deles. Não dá para afirmar paridade visual com nenhum concorrente.

**Leitura honesta:** o padrão que apareceu é *indicador em número + recorte por período + comparação*, focado em faturamento, agenda (comparecimento) e faltas. A proposta está de acordo com isso, e o catálogo da seção 3 cobre exatamente esses indicadores (faltas, comparecimento, ticket médio, faturamento, inadimplência via "A receber").

**Métrica principal (recomendação):** **Consultas realizadas** como padrão: é o que o dentista sente como produtividade e o card é liberado por `AGENDAMENTOS`, sem depender do olho financeiro. Faturamento e Ticket médio ficam a um toque, mas não como padrão porque o painel já tem o KPI "Receita do mês".

---

## 3. Catálogo de métricas

Ordem e grupos da lista. Cada item só aparece se o usuário tiver a permissão; grupo sem itens some; com **uma única** métrica disponível o título vira texto fixo (sem seta).

| Grupo | Métrica (rótulo) | Permissão | Definição | Número grande | Variação | Melhor quando |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| Agenda | **Consultas realizadas** | `AGENDAMENTOS` | Status `REALIZADO`, por `dataHoraInicio` | soma | % | maior |
| Agenda | **Agendamentos** | `AGENDAMENTOS` | Todos os horários marcados (qualquer status) | soma | % | maior |
| Agenda | **Faltas** | `AGENDAMENTOS` | Status `FALTOU` | soma | % | **menor** |
| Agenda | **Comparecimento** | `AGENDAMENTOS` | `REALIZADO` ÷ (`REALIZADO` + `FALTOU`), só horários já passados | taxa do período inteiro, `86,4%` | **p.p.** (`+2,1 p.p.`) | maior |
| Agenda | **Cancelamentos** | `AGENDAMENTOS` | Status `CANCELADO` | soma | % | **menor** |
| Pacientes | **Novos pacientes** | `PACIENTES` | Cadastros por `createdAt` | soma | % | maior |
| Financeiro | **Faturamento** | `AUDITORIA_FINANCEIRA` | Contas `PAGO`, `valorPago ?? valorTotal`, por `dataPagamento` (mesma regra do KPI "Receita do mês") | soma | % | maior |
| Financeiro | **Ticket médio** | `AUDITORIA_FINANCEIRA` | Faturamento ÷ pacientes distintos que pagaram no período (sugestão; o Arquiteto fecha a definição) | calculado no período, não é soma | % | maior |
| Financeiro | **A receber** | `AUDITORIA_FINANCEIRA` | Saldo em aberto (`valorTotal − valorPago`) de contas `PENDENTE`/`PARCIAL`/`ATRASADO`, por `dataVencimento` | soma | % | **neutro** |

Observações:
- "Melhor quando" só decide a **cor do selo** (seção 5). A seta sempre mostra a direção real.
- **Agendamentos** e **A receber** (o que já está marcado e o que ainda vai vencer) mostram pontos **futuros** e deixam navegar para períodos futuros que tenham dados; por isso o ano 2027 aparece na lista de anos dessas duas métricas e nas outras não. Nessas duas, a comparação é de período inteiro contra período inteiro. Todas as outras param em hoje.
- Faltas e comparecimento pedidos pelo Wilson aparecem como duas métricas separadas (contagem e taxa) porque respondem perguntas diferentes e misturar as duas seria duas escalas.
- Fora da v1, por falta de dado ou para não inflar a lista: ocupação da agenda (não existe capacidade cadastrada), conversão de orçamento e inadimplência por faixa de atraso.

**Detalhes que vão só para o tooltip** (uma linha extra, `text-xs text-bokka-ink-3`, pulando zeros):

| Métrica | Linha extra do tooltip |
| :-- | :-- |
| Consultas realizadas | `de 14 agendamentos` |
| Agendamentos | `9 realizadas · 2 faltas · 1 cancelada` |
| Faltas | `de 14 agendamentos` |
| Comparecimento | `9 de 11 atendimentos previstos` |
| Cancelamentos | `de 14 agendamentos` |
| Novos pacientes | (nenhuma) |
| Faturamento | `7 recebimentos` |
| Ticket médio | `4 pacientes pagantes` |
| A receber | `R$ 1.200,00 em atraso` (só se houver) |

---

## 4. Layout e controles

O card é o claro padrão do painel: `bg-bokka-surface border border-bokka-border rounded-2xl shadow-sm p-5 lg:p-6`, `lg:col-span-2`, `min-h-[440px] min-w-0 flex flex-col`, **sem `overflow-hidden`** (os painéis dos menus não podem ser cortados). **Se o usuário não tiver permissão para nenhuma métrica, o card simplesmente não é renderizado**: sem aviso, sem mensagem, sem placeholder e sem espaço reservado (o restante do painel ocupa o lugar). O front nem chama o endpoint. O protótipo mostra isso no perfil "Sem permissão" (só o painel de demonstração, fora do sistema, explica o que aconteceu).

### 4.1 Desktop (card com ~700px)
```
┌────────────────────────────────────────────────────────────────────────────┐
│ Consultas realizadas ▾          [‹  Outubro de 2026 ▾  ›]  [⬇ Exportar ▾]  │
│                                                                            │
│ 128   [↗ +12,5%] vs mês anterior                                           │
│                                                                            │
│ 20 ┤              ╭──╮                       ╭────                          │
│ 10 ┤   ╭─╮  ╭────╯  ╰──╮      ●────╮  ╭─────╯   (área azul clara)          │
│  0 ┤───╯ ╰──╯           ╰──────────╰──╯                                    │
│      1      5      10      15      20      25      31                      │
└────────────────────────────────────────────────────────────────────────────┘
```
Com métrica financeira, o olho aparece colado ao número: `R$ 24.580,50 👁  [↗ +12,5%] vs mês anterior`.

### 4.2 Celular (320 a 414px, o mesmo desenho em qualquer largura dessa faixa)
```
┌──────────────────────────────┐
│ Consultas realizadas ▾    ⬇  │   título (seletor) + só o ícone de exportar
│ [‹   Outubro de 2026 ▾   ›]  │   controle de período em largura total
│ 128                          │
│ [↗ +12,5%] vs mês anterior   │   o selo quebra para baixo, sem aperto
│                              │
│  gráfico (altura mín. 220px) │
└──────────────────────────────┘
```
- São **3 elementos interativos** acima do número (métrica, período, exportar) em vez de 4 ou 5. Nada de segmented control permanente, abas ou chips.
- Largura útil em 320px: cartão ~288px, miolo ~256px. O maior rótulo ("Consultas realizadas") ocupa ~190px com a seta; mais os 36px do botão Exportar e o respiro, cabe em 256px. Em 375 e 414px sobra espaço.
- Nenhuma linha passa da largura: título com `min-w-0 truncate`, selo com `flex-wrap`.

### 4.3 Seletor de métrica (o título do card)
- Gatilho: `inline-flex items-center gap-1.5 -ml-2 px-2 py-1 rounded-lg max-w-full hover:bg-bokka-surface-3 transition-colors`, texto `text-base font-semibold text-bokka-ink truncate` e `ChevronDown w-4 h-4 text-bokka-ink-3`. Visualmente é o título; a seta avisa que é clicável.
- Painel (Headless UI `Listbox` ou equivalente): `rounded-xl bg-bokka-surface border border-bokka-border shadow-md z-40 max-h-[60vh] overflow-y-auto p-1.5` (`z-40`: a barra inferior do celular e do tablet é `z-30`, e um painel aberto perto do rodapé não pode ficar escondido atrás dela).
  - Posição: ancorado no **cabeçalho do card** (que é `relative`). No celular `absolute left-0 right-0 mt-2` (largura do miolo, 256px); no desktop `sm:right-auto sm:w-80`.
  - Cabeçalho de grupo: `px-3 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-bokka-ink-3` (**Agenda**, **Pacientes**, **Financeiro**; 4,76:1 sobre branco).
  - Item: `w-full flex items-start gap-3 px-3 py-2 rounded-md text-left hover:bg-bokka-surface-3`, nome `text-sm font-medium text-bokka-ink`, descrição curta `text-xs text-bokka-ink-2` numa linha:

| Item | Descrição curta |
| :-- | :-- |
| Consultas realizadas | Atendimentos concluídos |
| Agendamentos | Todos os horários marcados |
| Faltas | Pacientes que não compareceram |
| Comparecimento | % que compareceu entre os previstos |
| Cancelamentos | Horários cancelados |
| Novos pacientes | Cadastros no período |
| Faturamento | Valor recebido |
| Ticket médio | Recebido por paciente que pagou |
| A receber | Vencimentos em aberto |

  - Selecionado: nome em `font-semibold` + `Check w-4 h-4 text-bokka-primary` à direita. **Não usar texto azul sobre `primary-soft`** (4,1:1, reprova AA).
  - Métricas financeiras ganham, ao lado do nome, `Wallet w-3.5 h-3.5 text-bokka-ink-3` (só decorativo, `aria-hidden`), para a pessoa saber que dependem do olho.
- A escolha é lembrada por navegador (`bokka:produtividade:metrica`, com `try/catch`); se a métrica lembrada não for mais permitida, cai em Consultas realizadas (ou na primeira disponível).
- Ao trocar de métrica o período e a navegação se mantêm.

### 4.4 Controle de período (um só)
- Estrutura: `inline-flex items-center gap-1` (no celular `flex w-full`): seta `‹` · botão central · seta `›`.
  - Setas: `w-8 h-8 rounded-full bg-bokka-surface-3 hover:bg-bokka-border text-bokka-ink-2`, `ChevronLeft`/`ChevronRight`, `aria-label="Período anterior"` / `"Próximo período"`, `disabled:opacity-40`. `›` desabilita no último período com dados (o corrente; em Agendamentos e A receber, o último que tenha dado futuro); `‹` no primeiro período com dados. Em **Todos os anos** as setas não aparecem.
  - Centro: `h-8 px-3 rounded-lg text-sm font-semibold text-bokka-ink hover:bg-bokka-surface-3 inline-flex items-center justify-center gap-1.5 min-w-[9rem]` (no celular `flex-1`) com `ChevronDown w-3.5 h-3.5 text-bokka-ink-3`. `aria-live="polite"` no texto.
  - Textos: Semana `19 a 25 de out` · Mês `Outubro de 2026` · Ano `2026` · Todos `Todos os anos`.
- **Painel** do centro (Headless UI `Popover`): `w-full` no celular, `sm:w-72` no desktop, alinhado à direita, mesmo estilo do painel de métrica, `p-3 space-y-3`. Três blocos:
  1. **Período:** `grid grid-cols-4` (container `rounded-lg bg-bokka-surface-3 p-0.5`), botões `h-8 text-xs font-semibold rounded-md`: **Semana · Mês · Ano · Todos** (nome acessível de "Todos": "Todos os anos"). Ativo `bg-bokka-surface text-bokka-primary shadow-sm`; inativo `text-bokka-ink-2` (**não** `ink-3`, que dá 4,3:1 sobre `surface-3`). `role="group"` + `aria-pressed`.
  2. **Ano** (só quando o período é Mês ou Ano): `grid grid-cols-4 gap-1.5 max-h-32 overflow-y-auto`, um botão por ano de `anosDisponiveis` (do mais novo para o mais antigo), `h-8 rounded-md text-xs font-semibold tabular-nums`. Normal `bg-bokka-surface-3 text-bokka-ink-2`; selecionado `bg-bokka-primary text-white` (4,69:1). Os anos vêm dos dados (todo ano com registro + o corrente), **sem lista fixa**: se existir 2019 ou 2027, entram. Escolher um ano mantém o mês no modo Mês.
  3. **Comparar com o período anterior:** linha com texto `text-sm text-bokka-ink-2` e `Switch` à direita (trilho `h-5 w-9 rounded-full`; ligado `bg-bokka-primary`; desligado `bg-bokka-surface-3 border border-bokka-ink-3`; bolinha branca). **Desligado por padrão.** Lembrado por navegador (`bokka:produtividade:comparar`). Desabilitado em "Todos os anos" com a dica `text-xs text-bokka-ink-3` "Sem período anterior para comparar."
- Trocar o tipo de período volta a referência para o período corrente.
- O período escolhido é lembrado por navegador (`bokka:produtividade:periodo`). Padrão: **Mês**.

### 4.5 Comparação (discreta por padrão)
- Sempre visível: só o **selo** de variação ao lado do número.
- Linha tracejada (só se o switch estiver ligado): período anterior **na mesma escala** do eixo Y (o eixo cresce se o anterior for maior; nunca um segundo eixo). Cor `#64748B` (`ink-3`, 4,76:1), `strokeWidth` 1.5, tracejado `4 4`, sem área, sem ponto. Ela se distingue pelo tracejado e não só pela cor.
- Legenda só quando ligada, sob o gráfico: `text-xs text-bokka-ink-2`, `— Outubro de 2026   ┈ Setembro de 2026`, quebrando linha no celular.
- Tooltip, com a comparação ligada, ganha uma linha: `Setembro: 11` (`text-xs text-bokka-ink-3`).
- Com valores financeiros ocultos, a linha tracejada também some.

---

## 5. Número grande e variação

- Linha: `flex flex-wrap items-center gap-x-3 gap-y-1`.
- **Número:** `text-3xl font-bold tracking-tight tabular-nums text-bokka-ink` (`tv:text-5xl`). Em R$ usa `formatCurrency`, com `text-2xl sm:text-3xl` para valores de 7+ dígitos. Taxa: `86,4%`.
- **Selo de variação:** `inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold` com `ArrowUpRight`/`ArrowDownRight` `w-3.5 h-3.5`. A **cor** depende da coluna "Melhor quando" do catálogo:

| Resultado | Estilo | Contraste |
| :-- | :-- | :-- |
| Mudança boa (subiu em "maior é melhor", desceu em "menor é melhor") | `bg-bokka-success-soft text-bokka-success-ink` | 4,8:1 |
| Mudança ruim | `bg-bokka-danger-soft text-bokka-danger-ink` | 5,3:1 |
| Métrica neutra (A receber) ou variação zero | `bg-bokka-neutral-soft text-bokka-neutral-ink` | acima de 4,5:1 |

  Exemplo: Faltas subiu 20% → seta para cima, selo **vermelho**. Faltas desceu 20% → seta para baixo, selo **verde**.
- Formato: `+12,5%` / `−8,3%` (menos real `−`), uma casa decimal, vírgula. Comparecimento usa pontos percentuais: `+2,1 p.p.`.
- Texto ao lado: `text-xs text-bokka-ink-3`: *vs semana anterior* · *vs mês anterior* · *vs ano anterior*.
- **Mesmo intervalo:** se o período ainda não terminou, compara os mesmos dias do período anterior (1 a 23 de outubro contra 1 a 23 de setembro; se o anterior for mais curto, limita ao último dia dele). Períodos passados comparam inteiros. Explicado no `title` do texto. (Não vale para Agendamentos e A receber, que comparam período inteiro com período inteiro.)
- **Bordas:** anterior = 0 e atual > 0 → sem selo, "Sem dados no período anterior". Os dois = 0 → sem selo. Atual = 0 e anterior > 0 → `−100%`. **Todos os anos:** sem selo; no lugar `text-xs text-bokka-ink-3`: "desde 2023 · 4 anos".
- Acessibilidade do selo: `<span className="sr-only">aumento de 12,5% em relação ao mês anterior, o que é ruim</span>` quando a mudança é ruim (o sentido não pode depender só da cor); visual com `aria-hidden`.

---

## 6. Gráfico de área

Recharts: `AreaChart` dentro de `ResponsiveContainer`. **Manter o truque atual** do bloco `relative flex-1 min-h-[240px] min-w-0` com o gráfico `absolute inset-0` (senão a largura trava em 585px no celular). No celular a altura mínima é 220px.

### 6.1 Cores exatas (tema claro)

Valores do tema claro. No escuro as mesmas funções usam outros valores; a tabela completa está na seção 14.3. No componente, nada disso fica escrito como hex: tudo vem das variáveis.

| Elemento | Valor | Observação |
| :-- | :-- | :-- |
| Linha | `#4C85F4`, `strokeWidth` 2.5, `type="monotone"` | 3,5:1 sobre branco (mínimo 3:1 para gráficos). Tom mais claro que a base `#2A6BF2`. `monotone` não passa do zero. |
| Gradiente da área | topo `#2A6BF2` `stopOpacity` 0.22 → base `#2A6BF2` `stopOpacity` 0 | `id` único (ex.: `produtividade-fill`). |
| Cursor | `#93B8FC`, 1px | |
| Ponto ativo | `r=5`, branco, contorno `#2A6BF2` 2.5px | |
| Linha de comparação | `#64748B`, 1.5, tracejado `4 4` | só com o switch ligado |
| Rótulos dos eixos | `#64748B`, 11px, peso 500 | 4,76:1 |
| Grade, linha de eixo, tracinhos | nenhum | |
| Fundo do card | `#FFFFFF` | |

### 6.2 Eixo Y (enxuto, uma escala)
- 4 marcas, sem linha de eixo nem tracinhos, `width` ~34px, começando em 0.
- Contagens: só inteiros. Dinheiro: formato compacto pt-BR (`R$ 1,5 mil`). **Comparecimento: fixo de 0% a 100%** com marcas `0%`, `50%`, `100%`.
- Domínio automático com folga de ~10%; se a comparação estiver ligada, considera o maior valor entre as duas linhas.
- Pontos sem base (comparecimento ou ticket médio num dia sem atendimento) ficam **sem valor**: a linha tem uma lacuna em vez de cair a zero; o tooltip, se aparecer, diz "Sem dados".

### 6.3 Eixo X por período

| Período | Pontos | Rótulos no desktop | Rótulos no celular (≤414px) |
| :-- | :-- | :-- | :-- |
| **Semana** | 7 (segunda a domingo, como o mini-calendário) | Duas linhas: `Seg` / `19`, todos os 7 | Igual (cabem, ~36px por coluna) |
| **Mês** | 28 a 31, um por **dia** | `1, 5, 10, 15, 20, 25` e o último dia | `1, 8, 15, 22, 29` |
| **Ano** | 12 (Jan a Dez) | os 12, três letras | `Jan, Mar, Mai, Jul, Set, Nov` |
| **Todos** | 1 por ano, do primeiro ao último com dado (anos sem dado entre eles valem 0) | `2023 2024 2025 2026` | acima de 4 anos, de 2 em 2 e sempre o último |

- **Período corrente:** pontos depois de hoje ficam sem valor (a linha para em hoje) e o eixo continua completo. **Exceção:** "Agendamentos" e "A receber" mostram o futuro normalmente.
- **Dia sem expediente** (visões por dia, Semana e Mês): dia sem nenhum agendamento, recebimento, cadastro ou vencimento (na prática, domingo). **Não é plotado: a linha liga os vizinhos**, para a curva não despencar a zero todo fim de semana. O tooltip nunca abre nesse ponto (pega o vizinho mais próximo), a tabela para leitor de tela diz "sem expediente" e o total não muda. Em Mês, no celular, o último rótulo (dia 30/31) só aparece se estiver a 4 dias ou mais do anterior (senão "29" e "31" se atropelam em 320px).
- **Ritmo semanal que sobra:** sábado de meio expediente ainda faz um "dente" por semana na visão Mês por dia. É o dado real, não é erro. Alternativa a avaliar com o Wilson: Mês por semana (5 pontos, mais liso, mas a última semana incompleta puxa a curva para baixo).
- **Todos com um único ano:** só o ponto e a nota `text-xs text-bokka-ink-3` "Só há dados de 2026 até agora."

### 6.4 Tooltip
- Caixa: `rounded-lg border border-bokka-border bg-bokka-surface px-3 py-2 shadow-md`. Três linhas no máximo:
  1. rótulo `text-xs text-bokka-ink-3`: Semana/Mês `qui, 23 de out` · Ano `Outubro de 2026` · Todos `2025`.
  2. valor `text-sm font-bold text-bokka-ink tabular-nums`: `12 consultas` (singular `1 consulta`), `R$ 3.480,00`, `86,4%`.
  3. linha extra da seção 3 (e a de comparação, se ligada), `text-xs text-bokka-ink-3`.
- **Quando aparece (card branco): só com o mouse por cima da linha, sem clicar.**
  - **Mouse:** o ponto marcado (`r=5`, branco com contorno `#2A6BF2`), a linha vertical do cursor e o balão aparecem quando o ponteiro está **sobre a curva** (a até **22px** dela, medidos até o traço, não só na horizontal). Ao passar por um ponto da curva, mostra **aquele ponto e aquele período** (o ponto de dado mais próximo na horizontal). Ponteiro longe da linha (no vazio acima ou abaixo, na zona futura sem dado, ou fora do gráfico) = nada aparece; ao sair, some. O cursor do mouse vira mão (`cursor: pointer`) enquanto está sobre a linha. **Nenhum clique é necessário.** Hoje o Recharts abre o tooltip em qualquer ponto da coluna; aqui isso precisa ser **ajustado** (no Recharts, calcular a distância ao traço no `onMouseMove` do gráfico e só ativar o ponto/tooltip dentro da tolerância; `activeDot` já só existe com tooltip ativo).
  - **Toque (celular e tablet):** tocar **ou arrastar** em qualquer ponto da área do gráfico mostra o ponto mais próximo na horizontal (o dedo é impreciso, então não há exigência de tocar exatamente na linha). O balão fica aberto ao soltar o dedo e some ao tocar fora do gráfico. Não trava a rolagem vertical da página (`touch-action: pan-y`).
  - **Teclado:** com foco no gráfico, ← → percorrem os pontos; Esc fecha.
  - Segue o ponteiro e não sai do card. Não aparece em pontos futuros sem valor nem em dia sem expediente (pega o vizinho).

---

## 7. Estados

**Carregando (primeira carga):** cabeçalho e controles aparecem, desabilitados. Número: `Skeleton h-9 w-32`; selo: `Skeleton h-6 w-20 rounded-full`; gráfico: `bg-bokka-surface-3 animate-pulse rounded-lg` ocupando a área, com 3 faixas mais claras sugerindo os eixos. `aria-busy="true"`.

**Trocando métrica/período (já havia dados):** sem skeleton. Mantém o gráfico anterior com `opacity-60 transition-opacity` e `aria-busy` até chegar o novo.

**Vazio:** número `0` (ou `R$ 0,00`), sem selo. A área do gráfico vira painel centralizado: chip `w-12 h-12 rounded-full bg-bokka-surface-3 text-bokka-ink-3` com `LineChart`, título `text-sm font-semibold text-bokka-ink`, texto `text-xs text-bokka-ink-3`. Mensagens: **"Sem consultas realizadas neste período"**, "Nenhum agendamento neste período", "Nenhuma falta neste período", "Sem dados de comparecimento neste período", "Nenhum cancelamento neste período", "Nenhum paciente novo neste período", "Sem recebimentos neste período", "Sem dados de ticket médio neste período", "Nada a receber neste período". Consultas e Agendamentos trazem o link `text-xs font-semibold text-bokka-primary` "Abrir agenda". Controles seguem ativos.

**Erro de carga:** painel no lugar do gráfico, `role="alert"`: **"Não foi possível carregar este indicador."** + `Button variant="secondary" size="sm"` **"Tentar de novo"**.

---

## 8. Privacidade: o olho (valores ocultos)

**O que já existe e não precisa ser construído:** o olho (`VisibilityToggle`, `FinancialVisibilityContext`) já é **salvo por usuário no servidor** (`preferences.hideFinancialInfo` em `/api/usuarios/me`) e usado como cache em `localStorage`, com sincronia entre abas e reversão em caso de erro. Ao voltar ao sistema, a escolha é restaurada. O gráfico só consome esse mesmo estado; **não cria preferência própria**.

**Um ponto de atenção:** num navegador sem cache (primeiro acesso ou outro computador), o estado inicial é "visível" até o `/api/usuarios/me` responder. Num painel mostrado a funcionários, esse instante poderia exibir valores. Recomendo o contexto expor um sinal de "preferência confirmada" (`pronto`) e o gráfico tratar valores financeiros como **ocultos e exportação bloqueada** até ele ficar verdadeiro.

**Com valores ocultos, em métrica financeira (Faturamento, Ticket médio, A receber):**
- Número vira `R$ ••••`; o selo some; os rótulos do eixo Y somem; tooltip mostra `R$ ••••` (sem % nem contagens que revelem o valor); linha de comparação some. A forma da curva continua (sem escala não vaza valor).
- O resumo para leitor de tela diz "Valores financeiros ocultos."
- O olho fica ao lado do número, no mesmo lugar dos KPIs.
- Métricas não financeiras não são afetadas.

---

## 9. Exportação (Excel, PDF e CSV)

### 9.1 O que existe hoje
- Um botão redondo (`w-9 h-9 bg-white/10`, `Download`) no card escuro, `aria-label="Exportar CSV"`, que chama `exportProdutividadeCSV` (`Dashboard.tsx`): CSV gerado no navegador, separador `;`, BOM UTF-8, colunas `Mês; <ano>; <ano anterior>`, arquivo `bokka-produtividade-<ano>.csv`, toast de sucesso, sem tratamento de erro, recorte fixo de 12 meses.
- **O botão "Exportar" do card Fluxo de caixa da Auditoria** (`AuditoriaPage.tsx`) **não tem ação nenhuma** (sem `onClick`).
- Não há biblioteca de PDF nem de Excel no `package.json`.
- Padrão de menu do projeto: Headless UI `Menu` no `Header.tsx`.

### 9.2 Regra geral
Qualquer formato exporta **exatamente o que está na tela**: métrica, período e referência navegada. Os dados são os que o gráfico já recebeu (sem nova chamada); o único tempo de espera é montar o arquivo.

### 9.3 Botão e menu (mesmo padrão nos dois cards)

**Gatilho no card Produtividade (claro)**
- Desktop (`sm:` em diante): `inline-flex items-center gap-1.5 h-8 px-3 rounded-lg border border-bokka-border-strong bg-bokka-surface text-xs font-semibold text-bokka-ink-2 hover:bg-bokka-hover`, com `Download w-4 h-4`, **Exportar** e `ChevronDown w-3.5 h-3.5`. Fica à direita do controle de período.
- Celular: `w-9 h-9 rounded-full bg-bokka-surface-3 hover:bg-bokka-hover-strong text-bokka-ink-2`, só o ícone, `aria-label="Exportar dados"`, na linha do título.

**Gatilho no card Fluxo de caixa:** **o mesmo componente** do card Produtividade, com as mesmas classes acima (o card agora também é claro; seção 13). Não existe mais gatilho próprio "para fundo escuro": no modo escuro, o mesmo gatilho muda de cor só pelas variáveis. Foco: `--focus` (seção 14).
- O painel do menu usa `bg-bokka-panel text-bokka-ink`, igual nos dois cards e nos dois temas.

**Painel (igual ao `Header.tsx`)**: `absolute right-0 mt-2 w-56 origin-top-right rounded-xl bg-bokka-surface border border-bokka-border shadow-md z-40 overflow-hidden`, com `Transition` (`z-40` pelo mesmo motivo: acima da barra inferior `z-30`). Em 320px o painel de 224px começa a ~80px da borda. Os cards não podem ter `overflow-hidden`.
- Topo (`px-3 pt-3 pb-1.5 text-xs text-bokka-ink-3`): o que será exportado, ex. **"Outubro de 2026 · Consultas realizadas"** (Fluxo de caixa: "Últimos 6 meses · maio a outubro de 2026").
- Itens (`p-1.5`): `w-full flex items-center gap-3 px-3 py-2.5 rounded-md text-left` (~52px de altura). Chip do ícone `w-8 h-8 rounded-lg bg-bokka-surface-3 text-bokka-ink-2`. Nome `text-sm font-semibold text-bokka-ink`; apoio `text-xs text-bokka-ink-2` (nunca `ink-3` sobre o fundo ativo: 4,2:1). Ativo: `bg-bokka-primary-soft`, chip `bg-bokka-surface text-bokka-primary`.

| Item | Ícone | Apoio |
| :-- | :-- | :-- |
| **Excel (.xlsx)** | `FileSpreadsheet` | Planilha para editar e somar |
| **PDF (.pdf)** | `FileText` | Relatório para imprimir ou enviar |
| **CSV (.csv)** | `FileDown` | Dados simples separados por ; |

### 9.4 Regra de bloqueio por privacidade

| Situação | Exportar |
| :-- | :-- |
| Métrica **sem dinheiro** (Consultas, Agendamentos, Faltas, Comparecimento, Cancelamentos, Novos pacientes) | **Sempre liberado**, com o olho ligado ou desligado. O arquivo não contém nenhum valor em R$: é o PDF que o Wilson pode mostrar aos funcionários. |
| Métrica **financeira** (Faturamento, Ticket médio, A receber) e valores **visíveis** | Liberado. |
| Métrica **financeira** e valores **ocultos** (ou preferência ainda não confirmada) | **Bloqueado.** |
| Fluxo de caixa (sempre financeiro) e valores ocultos | **Bloqueado.** |
| Carregando, erro de carga ou período sem dados | Gatilho desabilitado. |

**Como o bloqueio aparece:**
- O gatilho **continua ativo** (para a pessoa abrir e ver o motivo). Dentro do painel, no lugar da linha de contexto, um aviso: `flex items-center gap-2 mx-1.5 mt-1.5 rounded-md bg-bokka-surface-2 px-3 py-2 text-xs text-bokka-ink-2` com `EyeOff w-4 h-4 text-bokka-ink-3` e o texto exato **"Mostre os valores para exportar"**. Ao lado, o botão de texto `font-semibold text-bokka-primary underline` **"Mostrar valores"**, que liga o olho global.
- Os três itens ficam desabilitados: `opacity-50 cursor-not-allowed`, `aria-disabled="true"`, sem foco ativo nem hover azul.
- **A regra é conferida de novo no clique/geração**, não só na tela (o olho pode mudar em outra aba com o menu aberto).

### 9.5 Nome dos arquivos
Padrão `bokka-produtividade-<metrica>-<periodo>.<ext>`: minúsculo, sem acento nem espaço.
- Métrica: `consultas`, `agendamentos`, `faltas`, `comparecimento`, `cancelamentos`, `novos-pacientes`, `faturamento`, `ticket-medio`, `a-receber`.
- Período: Semana `semana-2026-10-19-a-2026-10-25` · Mês `mes-2026-10` · Ano `ano-2026` · Todos `todos-2023-2026`.
- Exemplos: `bokka-produtividade-consultas-mes-2026-10.pdf`, `bokka-produtividade-faturamento-ano-2026.xlsx`.
- **Fluxo de caixa:** `bokka-fluxo-de-caixa-ultimos-6-meses-2026-05-a-2026-10.<ext>`.
- O nome atual (`bokka-produtividade-2026.csv`) deixa de existir.

### 9.6 Colunas e conteúdo

**Produtividade** (iguais nos três formatos)

| Período | Coluna 1 | Coluna 2 (valor) |
| :-- | :-- | :-- |
| Semana | `Data`: `20/10/2026 (seg)` | valor |
| Mês | `Data`: `20/10/2026` | valor |
| Ano | `Mês`: `Janeiro` a `Dezembro` | valor |
| Todos | `Ano` | valor |

Nome da coluna de valor: **Consultas realizadas · Agendamentos · Faltas · Comparecimento (%) · Cancelamentos · Novos pacientes · Faturamento (R$) · Ticket médio (R$) · A receber (R$)**. Colunas extras só onde fazem sentido: **Agendamentos** leva `Realizadas`, `Faltas`, `Canceladas`; **Comparecimento** leva `Realizadas`, `Faltas`. Pontos futuros do período corrente não entram (exceto A receber).

**Fluxo de caixa:** `Mês` · `Receitas (R$)` · `Despesas (R$)` · `Lucro líquido (R$)`, 6 linhas, mais linha `Total`.

**CSV:** como o de hoje (`;`, BOM UTF-8, só cabeçalho e linhas de dados, sem total; dinheiro com vírgula decimal). A coluna do ano anterior sai; a comparação passa a aparecer na planilha e no PDF.

**Excel (.xlsx):** uma aba **Produtividade** (ou **Fluxo de caixa**). Topo: A1 título (negrito, 14pt) · A2 `Período: Outubro de 2026` · A3 valor principal (célula numérica, negrito) · A4 `Variação: +12,5% vs mês anterior (período anterior: 114)` (Todos: `2023 a 2026 · 4 anos`) · A5 `Gerado em 23/10/2026 às 14:05` (+ nome da clínica, se estiver no contexto de autenticação). Linha 6 em branco; **cabeçalho da tabela na linha 7** com fundo `#EAF1FF`, texto `#0B1220`, negrito; última linha `Total` em negrito (para taxa e média, a linha é `No período` com o valor calculado, não soma). **Valores como número, não texto:** contagens `0`, dinheiro `R$` pt-BR, taxa como percentual real, datas como data. Cabeçalho congelado, colunas ajustadas, números à direita; negativos do Fluxo de caixa em `#B91C1C` com sinal de menos.

**PDF** (A4 retrato, margens 16mm, fonte padrão Helvetica, texto selecionável):
1. **Cabeçalho:** quadrado `#2A6BF2` com "B" branco + **Bokka** (`#0B1220`); à direita "Gerado em 23/10/2026 às 14:05" (`#64748B`); linha `#E2E8F0`.
2. **Título:** `Produtividade` (18pt, negrito) e `Consultas realizadas · Outubro de 2026` (11pt, `#334155`); nome da clínica, se houver, 10pt `#64748B`. Fluxo de caixa: `Fluxo de caixa` e `Últimos 6 meses · maio a outubro de 2026`.
3. **Resumo** (faixa `#F8FAFC`, borda `#E2E8F0`): valor principal em 24pt negrito com rótulo (`Total no período`, ou `<Métrica> no período` para taxa e média) e **variação** (`+12,5%` em `#047857` se boa, `#B91C1C` se ruim, `#475569` se neutra/zero, com "vs mês anterior · período anterior: 114"; Comparecimento em p.p.). Fluxo de caixa: três números lado a lado (Receitas, Despesas, Lucro líquido), sem variação.
4. **Tabela** com as colunas acima: cabeçalho `#EAF1FF` em negrito, linhas alternadas `#FFFFFF`/`#F8FAFC`, bordas `#E2E8F0`, valores à direita, linha final em negrito; cabeçalho se repete em nova página.
5. **Rodapé** em todas as páginas: `Bokka · Sistema de Gestão Odontológica · Suporte: suporte@bokka.com.br` e `Página 1 de 2` (8pt, `#64748B`).
- Sem gráfico dentro do PDF na v1. Título do documento preenchido com `Produtividade · <métrica> · <período>`.
- **PDF de métrica sem dinheiro não contém nenhum valor em R$ nem referência a faturamento.**

### 9.7 Estados do exportar

| Estado | Comportamento |
| :-- | :-- |
| **Normal** | Menu com os 3 itens. |
| **Bloqueado** | Seção 9.4. |
| **Gerando** | O menu fecha; o gatilho fica desabilitado com `Loader2 animate-spin` no lugar do ícone (no desktop o texto vira **Gerando...**) e `aria-busy="true"`; região `sr-only` `role="status"`: "Gerando arquivo PDF." Sem barra de progresso. O primeiro PDF/Excel pode demorar mais porque a biblioteca é baixada nessa hora. |
| **Sucesso** | Download pelo navegador e `bokkaToast.success`: **"Excel de produtividade baixado."** / **"PDF de produtividade baixado."** / **"CSV de produtividade baixado."** (Fluxo de caixa: "… do fluxo de caixa baixado."). |
| **Erro** | `bokkaToast.error("Não foi possível gerar o PDF. Tente novamente.")` (Excel/CSV no lugar de PDF). Falha ao baixar a biblioteca (sem internet): "Não foi possível carregar o gerador de arquivos. Verifique sua conexão e tente de novo." Gatilho volta ao normal. |
| **Indisponível** | Gatilho `disabled` (`disabled:opacity-50 disabled:pointer-events-none`) com o motivo em `title` e em texto `sr-only` por `aria-describedby`. |
| **Duplo clique** | Ignorado enquanto gera. |

### 9.8 Dependências
Excel e PDF exigem **biblioteca nova** (o projeto não tem nenhuma): carregar **sob demanda** (`import()` dinâmico ao clicar), gerar no navegador, XLSX com células numéricas e PDF com texto real. Conferir licença e segurança da versão antes de adotar (não verifiquei). O card Fluxo de caixa já tem seus dados no cliente (`fluxo`); não precisa de endpoint novo.

---

## 10. Dados e contrato (para o Arquiteto, `docs/contrato-grafico-produtividade.md`)

1. **Endpoint agregado.** A consulta de hoje (`useAgendamentos({ pagina: 0, tamanho: 300, ordem: 'dataHoraInicio' })`) corta os dados mais novos em clínicas maiores (provável, não testei) e não serve para Ano/Todos.
2. **Entrada:** `metrica` (os 9 valores do catálogo) · `periodo` (`SEMANA`/`MES`/`ANO`/`TODOS`) · `referencia` (data).
3. **Permissão por métrica:** `AGENDAMENTOS` (5 de agenda), `PACIENTES`, `AUDITORIA_FINANCEIRA` (3 financeiras). Sem permissão → 403; o servidor **não devolve valores financeiros** a quem não tem `AUDITORIA_FINANCEIRA`. O olho é só preferência de tela, não filtra no servidor.
4. **Saída:** `pontos[]` com `chave`, `rotulo`, `valor` (ou `null`), `anterior` (mesmo índice do período anterior, ou `null`; alimenta a linha tracejada e o tooltip) e `detalhes` (números da linha extra do tooltip e das colunas extras da exportação); `total` do período (para **Comparecimento** e **Ticket médio** é a taxa/média do período inteiro, **não a soma dos pontos**); `totalAnterior` pela regra do **mesmo intervalo**; `anosDisponiveis` (todo ano com registro da métrica + o corrente); `unidade` (`CONTAGEM`, `MOEDA`, `PERCENTUAL`).
5. **Pontos futuros:** `null` no período corrente, exceto em **AGENDAMENTOS** e **A_RECEBER** (o que está marcado e o que vai vencer), que também deixam navegar para períodos futuros com dados e entram em `anosDisponiveis` (ex.: 2027). Nessas duas, `totalAnterior` é do período anterior inteiro. **Dia sem expediente** (sem agendamento, recebimento, cadastro nem vencimento): marcar `semExpediente: true` (e `valor: null`); o front não plota e liga os vizinhos.
6. **Pontos sem base** (comparecimento/ticket sem atendimento ou pagamento no dia): `null`, não zero.
7. **Fuso:** agrupar por data local da clínica (America/Sao_Paulo), não UTC.
8. **Definições que mudam números:** "Consultas realizadas" passa a contar só `REALIZADO` (o gráfico atual conta qualquer status; o total vai cair). **Ticket médio** (faturamento ÷ pacientes distintos que pagaram) e **Comparecimento** estão como sugestão; o Arquiteto fecha. Parcelas contam como recebimentos separados em Faturamento; por isso o ticket por paciente distinto, não por conta.
9. **Privacidade:** nenhuma mudança de servidor. A preferência já existe em `/api/usuarios/me` (`preferences.hideFinancialInfo`). Mudança de front sugerida: o contexto expor `pronto` (seção 8).

---

## 11. Acessibilidade

- **Contrastes dos dois temas:** tabela completa e calculada na seção 14.5 (todos passam). Resumo do claro, que continua valendo: `ink` sobre branco 17:1; `ink-2` sobre `surface-3` 9,4:1; `ink-3` sobre branco 4,76:1; selo bom 4,8:1, ruim 5,3:1; `primary` sobre branco 4,69:1; branco sobre `primary` 4,69:1. Gráficos (mín. 3:1): linha `#4C85F4` 3,5:1; linha de comparação `#64748B` 4,76:1. **Evitados por reprovar:** `ink-3` sobre `surface-3` (4,3:1) e `primary` sobre `primary-soft` (4,1:1).
- **Não depender de cor:** o selo tem seta, sinal e texto, e quando a mudança é ruim o texto para leitor de tela diz isso; a comparação se distingue pelo tracejado; selecionado na lista tem `Check`.
- **Alternativa textual:** o `svg` tem `role="img"` e `aria-label` com resumo: *"Gráfico de área. Faltas em outubro de 2026: 14 no total, 20% a mais que setembro, o que é ruim. Maior dia: 14 de outubro, com 3."* Mais uma tabela `sr-only` com os pontos. Com valores ocultos o resumo diz "Valores financeiros ocultos."
- **Teclado:** métrica (`Listbox`), período (`Popover` com botões) e exportar (`Menu`) são do Headless UI e cobrem Enter, Espaço, setas e Esc, devolvendo o foco ao gatilho. O Recharts 3 liga `accessibilityLayer` por padrão: no gráfico, ← → percorrem os pontos e abrem o tooltip.
- **Anúncios:** ao trocar de métrica ou período, uma região `sr-only` `aria-live="polite"` diz "Mostrando Faltas, outubro de 2026, total 14." (uma vez, não a cada tecla). Painéis de vazio/erro usam `role="status"`/`role="alert"`.
- **Movimento:** com `prefers-reduced-motion`, desligar a animação da área (`isAnimationActive={false}`).
- **Alvos de toque:** setas e botões do período de 32px (com 4px entre eles), itens das listas com 36px ou mais, itens do menu de exportar de ~52px, gatilho de exportar de 36px no celular.

---

## 12. Tokens e classes (resumo, tema claro)

Classes do tema claro. Como cada classe `bokka-*` lê uma variável, o escuro entra trocando as variáveis (seção 14); estas classes não mudam.

| Uso | Token / valor |
| :-- | :-- |
| Card | `bg-bokka-surface border border-bokka-border rounded-2xl shadow-sm p-5 lg:p-6` |
| Título-seletor | `text-base font-semibold text-bokka-ink` + `ChevronDown text-bokka-ink-3` |
| Painéis (métrica, período, exportar) | `rounded-xl bg-bokka-surface border border-bokka-border shadow-md z-40` |
| Número grande | `text-3xl font-bold tracking-tight tabular-nums text-bokka-ink` (`tv:text-5xl`) |
| Selo bom / ruim / neutro | `bg-bokka-success-soft text-bokka-success-ink` / `bg-bokka-danger-soft text-bokka-danger-ink` / `bg-bokka-neutral-soft text-bokka-neutral-ink` |
| Botões do período | `bg-bokka-surface-3 text-bokka-ink-2`; ativo `bg-bokka-surface text-bokka-primary shadow-sm`; ano selecionado `bg-bokka-primary text-white` |
| Linha / gradiente | `#4C85F4` / `#2A6BF2` opacidade 0.22 → 0 |
| Cursor / ponto ativo | `#93B8FC` / branco com contorno `#2A6BF2` |
| Comparação | `#64748B`, 1.5, tracejado `4 4` |
| Rótulos dos eixos | `#64748B` 11px |
| Tooltip | `rounded-lg border border-bokka-border bg-bokka-surface px-3 py-2 shadow-md` |
| Aviso de exportar bloqueado | `bg-bokka-surface-2 text-bokka-ink-2 text-xs` + `EyeOff` |
| Gatilho Exportar (os dois cards, mesmo componente) | abaixo de 640px `bg-bokka-surface-3 text-bokka-ink-2 hover:bg-bokka-hover-strong` (redondo, só ícone); a partir de 640px `border border-bokka-border-strong bg-bokka-surface hover:bg-bokka-hover` |

Reaproveitados: `Skeleton`, `Button`, `VisibilityToggle` (`Card.tsx`), `formatCurrency`, `MASKED_CURRENCY`, `bokkaToast`, Headless UI. Novos (UI pura): o título-seletor de métrica, o controle de período (stepper + painel), o selo de variação, o tooltip e o menu Exportar (que serve aos dois cards).

Fontes das buscas: [Clinicorp](https://www.clinicorp.com/), [Clinicorp: faturamento](https://www.clinicorp.com/post/faturamento-clinica-odontologica-gargalo), [Simples Dental: indicadores](https://www.simplesdental.com/blog/indicadores-de-desempenho-na-clinica-odontologica/), [Siodonto: dashboards](https://siodonto.com.br/blog/p/dashboards-na-clinica-odontologica-transforme-dados-em-decisoes-semanais/).

---

## 13. Card "Fluxo de caixa" da Auditoria (versão clara, base A)

**Decisão do Wilson (3ª rodada):** no modo **claro** (padrão do sistema) os dois cards, Produtividade e Fluxo de caixa, seguem a **mesma paleta branco e azul**, para o sistema inteiro ficar em harmonia. O card preto atual do Fluxo de caixa (e a versão escura do de Produtividade) viram a versão do futuro **modo escuro** (seção 14), então nada do trabalho anterior se perde. Como o Wilson não escolheu entre A e B, a **base é a opção A (atualização leve)**, que era a recomendada. A opção B (só o botão Exportar) saiu do protótipo.

### 13.1 Como é hoje (lido em `AuditoriaPage.tsx`)
- Card escuro `bg-bokka-ink rounded-2xl p-5 lg:p-6 text-white`. Título "Fluxo de caixa" e subtítulo "Últimos 6 meses · receitas, despesas e lucro líquido".
- Botão redondo `w-9 h-9 bg-white/10` com ícone de download: **não tem ação** (sem `onClick`).
- Gráfico `h-64` (256px fixos): duas barras por mês (Receita `#10B981`, Despesa `#EF4444`, canto superior arredondado 8, `maxBarSize` 26) e linha de Lucro `#93B8FC` (2,5px, pontos `r=3` sempre visíveis), grade tracejada `white/8`, rótulos `white/60` e `white/50`, tooltip escuro do Recharts, legenda com três pontinhos embaixo.
- Estado de carregamento: bloco `bg-white/5` pulsando. Não há estado vazio nem de erro.

### 13.2 Problemas encontrados (valem em qualquer tema)
1. **O gráfico não respeita o olho de privacidade.** Com os valores ocultos, o eixo Y e o tooltip continuam mostrando os valores; só os KPIs do topo mascaram. Isso precisa ser corrigido (a regra do Exportar bloqueado perderia o sentido). Oculto: sem rótulos no eixo Y, tooltip com `R$ ••••`, totais mascarados.
2. **Formatador do eixo Y:** `(v/1000).toFixed(0) + 'k'` arredonda errado (2.500 vira "3k", 1.499 vira "1k") e não abrevia negativos (mostra "-10000"). Num mês de prejuízo o gráfico fica feio.
3. **Eixo Y de 62px fixos** (com margem esquerda negativa) ocupa um quarto da largura útil em 320px.
4. **Altura fixa de 256px** em qualquer tela e **totais só no tooltip**: a pessoa precisa passar o mouse mês a mês para saber quanto entrou.
5. Sem estado vazio e sem estado de erro.
6. **Cores fixas no código** (`#10B981`, `#EF4444`, `#93B8FC`, `white/10`...). Impedem o modo escuro de entrar só trocando variáveis (seção 15).

### 13.3 Versão clara (A): o que muda em relação ao card atual
Mesma estrutura do card atual (título, subtítulo, barras duplas por mês, linha de lucro por cima), agora no **mesmo vocabulário visual do card Produtividade**.

1. **Cartão igual ao da Produtividade:** `bg-bokka-surface border border-bokka-border rounded-2xl shadow-sm p-5 lg:p-6`. Título `text-base font-semibold text-bokka-ink`, subtítulo `text-xs text-bokka-ink-3`. Nenhum `overflow-hidden`.
2. **Cores do gráfico no claro** (todas passam 3:1 sobre branco; tabela completa na seção 14.5):

   | Série | Claro | Escuro (card preto atual) | Observação |
   | :-- | :-- | :-- | :-- |
   | Receita (barra) | `#047857` (5,5:1) | `#10B981` (7,4:1) | No claro o verde de hoje (`#10B981`) dá só 2,5:1 sobre branco; usa o `bokka-success-ink` que já existe. |
   | Despesa (barra) | `#EF4444` (3,8:1) | `#EF4444` (5,0:1) | Igual nos dois temas (`bokka-danger`). |
   | Lucro líquido (linha e ponto) | `#2A6BF2` (4,7:1) | `#93B8FC` (9,4:1) | **O azul é a identidade do card**: a linha de lucro é a única azul do gráfico e o ponto ativo repete a cor da Produtividade. |

   Verde e vermelho têm luminância diferente (1,46:1 no claro, 1,48:1 no escuro) justamente para ajudar quem não distingue bem as duas cores; além disso a cor nunca é o único sinal (itens 3, 6 e 11).
3. **Faixa de totais no lugar da legenda** (acima do gráfico, abaixo do cabeçalho). Receita, Despesa e Lucro líquido (soma dos 6 meses), cada um com o pontinho colorido (que continua servindo de legenda).
   - Menos de 520px: lista vertical, rótulo à esquerda e valor à direita, divisores `border-bokka-border`, valor `text-base font-bold tabular-nums text-bokka-ink`.
   - 520px ou mais: três colunas lado a lado (rótulo em cima, valor embaixo), `gap-x-8`; em 1024px ou mais o valor sobe para `text-xl`.
   - Rótulo `text-xs text-bokka-ink-2`. Valores ocultos: `R$ ••••`. Lucro negativo aparece com sinal de menos.
4. **Eixo Y enxuto e correto:** rótulos compactos pt-BR (`R$ 20 mil`; abaixo de 420px de largura de gráfico, só `20 mil`), largura calculada pelo texto (nada de 62px fixos, sem margem negativa), 4 a 5 marcas, grade tracejada `3 3`. Rótulos `#64748B` 11px. Com valores negativos aparece a **linha do zero** sólida (`--baseline`, `#64748B`) e as barras saem dela. Sem rótulos quando os valores estão ocultos.
5. **Pontos da linha de lucro só no hover** (e não mais sempre visíveis): ponto ativo `r=5`, miolo `--dot-bg`, contorno `--profit` 2,5px. Mais limpo e igual ao card Produtividade.
6. **Tooltip claro, igual ao da Produtividade:** `rounded-[10px] border border-bokka-border bg-bokka-surface px-3 py-2.5 shadow-md`, mês por extenso ("Outubro de 2026") no topo em `text-xs font-semibold text-bokka-ink-3` e três linhas com pontinho colorido: **Receita**, **Despesa**, **Lucro líquido** (rótulo `text-bokka-ink-3`, valor `font-semibold tabular-nums text-bokka-ink`). **Abre ao passar o mouse sobre a coluna do mês** (como hoje, sem clicar; aqui a regra de "só sobre a linha" não vale, porque as barras são largas); no toque, tocar ou arrastar; no teclado ← →. Faixa de destaque do mês `--band` (`rgb(42 107 242 / .06)` no claro). Oculto: `R$ ••••`.
7. **Alturas por largura:** `h-56` (224px) abaixo de 640px, `h-64` (256px) a partir de 640px, `h-72` (288px) a partir de 1024px.
8. **Barras:** largura máxima 24px (16px quando o gráfico tem menos de 420px de largura), canto superior arredondado 8, 3px entre as duas barras do mês. Em telas largas as barras **não esticam**.
9. **Exportar:** igual ao do card Produtividade (mesmo componente). Abaixo de 640px, botão redondo de ícone `w-9 h-9 rounded-full bg-bokka-surface-3 text-bokka-ink-2 hover:bg-bokka-hover-strong`; a partir de 640px vira botão com texto `inline-flex items-center gap-1.5 h-8 px-3 rounded-lg border border-bokka-border-strong bg-bokka-surface hover:bg-bokka-hover text-xs font-semibold text-bokka-ink-2` com `Download` e `ChevronDown`. Foco: `--focus`. Abre o menu Excel · PDF · CSV da seção 9, com o bloqueio de valores ocultos.
10. **Estados:** carregando (`--skeleton`, cobrindo também a faixa de totais); **vazio**: chip `bg-bokka-surface-3`, título "Sem movimentações nos últimos 6 meses" e texto "Receitas e despesas pagas aparecem aqui."; **erro** (`role="alert"`): "Não foi possível carregar o fluxo de caixa." com botão "Tentar de novo" (`variant="secondary"`). Nos três, o Exportar fica desabilitado.
11. **Acessibilidade:** o `svg` tem `role="img"` e `aria-label` com resumo ("Receita total R$ …, despesa total R$ …, lucro líquido R$ …. Lucro negativo em julho.") mais tabela `sr-only` com os 6 meses; com valores ocultos o resumo diz "Valores financeiros ocultos." Receita e Despesa ficam em posições fixas (esquerda e direita) em cada mês e a legenda tem texto. Contrastes: seção 14.5.

### 13.4 Responsivo (320 a 1440px, incluindo 768 e 1024)
Medido no protótipo, com o menu lateral de 248px (a partir de 1024px) e a barra inferior (abaixo de 1024px) simulados. A tabela vale para os dois temas (o tema só troca cores).

| Largura | Largura do card | Faixa de totais | Altura do gráfico | Exportar |
| :-- | :-- | :-- | :-- | :-- |
| 320 | 288 | lista vertical | 224 | ícone redondo |
| 375 | 343 | lista vertical | 224 | ícone redondo |
| 414 | 382 | lista vertical | 224 | ícone redondo |
| 768 (tablet, sem menu lateral) | 736 | 3 colunas | 256 | botão com texto |
| 1024 (menu lateral aparece) | 736 | 3 colunas, valor maior | 288 | botão com texto |
| 1440 | 1152 | 3 colunas, valor maior | 288 | botão com texto |

- Sem rolagem horizontal em nenhuma dessas larguras, nos dois temas, com ou sem o menu de exportar aberto; o painel do menu fica sempre dentro da tela.
- **Barra inferior do celular e do tablet (`z-30`):** o painel do menu usa `z-40` e o card não pode ter `overflow-hidden`, senão o menu aberto perto do rodapé some atrás da barra ou é cortado.
- Em 1440px o card tem ~1150px de largura e só seis meses: por isso as barras têm largura máxima e a faixa de totais cabe na horizontal, em vez de esticar o gráfico.

### 13.5 Exportação do Fluxo de caixa
Mesmo menu e mesmas regras da seção 9: **bloqueado com valores ocultos** ("Mostre os valores para exportar" + "Mostrar valores"), conferido de novo no clique. Colunas `Mês`, `Receitas (R$)`, `Despesas (R$)`, `Lucro líquido (R$)`, mais linha `Total`; arquivo `bokka-fluxo-de-caixa-ultimos-6-meses-<AAAA-MM>-a-<AAAA-MM>.<ext>`; PDF com os três totais lado a lado no resumo. Usa os dados que a página já calcula (`fluxo`); nenhum endpoint novo. **O PDF e o Excel saem sempre com a paleta clara fixa (papel e planilha), independente do tema da tela.**

### 13.6 O que o Wilson ainda decide
- **Faixa de totais no topo:** é a mudança mais visível. Se parecer demais, dá para ficar só com a legenda embaixo (como hoje) e manter o resto.

---

## 14. Temas Claro e Escuro (tokens por tema)

### 14.1 Regra: um componente, dois conjuntos de variáveis
- **Nenhum componente tem versão escura própria.** Produtividade e Fluxo de caixa são **um componente cada**, escritos só com variáveis semânticas. Trocar de tema é trocar o conjunto de variáveis em `<html data-theme="dark">` (sem o atributo, ou com `light`, vale o claro). Nenhuma classe `dark:` com cor fixa dentro de componente.
- **O escuro parte dos cards pretos de hoje:** fundo do card `#0B1220` (o `bokka-ink` atual), barras `#10B981` e `#EF4444`, linha de lucro `#93B8FC`, grade branca a 8%. É o que o Wilson já aprovou visualmente. O que é novo no escuro: página `#060A13` (mais escura que o card), painéis `#111B31`, balão `#16213A`, link `#7BA6F8`, linha da Produtividade `#6FA3FA`.
- **Elevação no escuro** (de baixo para cima): página `#060A13` → card `#0B1220` → painel/menu `#111B31` → balão `#16213A`. No escuro a sombra quase não aparece; a separação vem do preenchimento mais claro e da borda `#1E2B47` (balão `#33456B`).
- **Como encaixa no Tailwind v4 do projeto:** os tokens `bokka-*` já vivem em `@theme` (`src/index.css`) e as utilities (`bg-bokka-surface`, `text-bokka-ink`...) leem `var(--color-bokka-*)`. Por isso o modo escuro entra como **um bloco** `:root[data-theme="dark"] { --color-bokka-surface: …; … }` que sobrescreve as mesmas variáveis, mais `color-scheme: dark`. *Não testei isso dentro do projeto* (não toquei no código do sistema): o Frontend deve confirmar com um teste de 5 minutos que o build não "inlina" os valores.
- **Preferência do usuário (decisão para a tarefa do modo escuro):** sugiro **Claro (padrão) / Escuro / Automático** (segue o sistema operacional), guardada junto das preferências que já existem (como `hideFinancialInfo`) e aplicada antes da primeira pintura para não piscar. No protótipo só existe o alternador Claro/Escuro, guardado no navegador.
- **Fora do escopo:** as cores clínicas do odontograma **não entram nos temas** e continuam fixas (padrão USP). Como o desenho do odontograma fica sobre fundo claro hoje, ele precisa de decisão própria na tarefa do modo escuro.

### 14.2 Tokens base (o tema troca só estes e os do gráfico)

| Variável (protótipo) | Nome no sistema | Claro | Escuro | Uso |
| :-- | :-- | :-- | :-- | :-- |
| `--bg` | novo `--color-bokka-page` | `#F8FAFC` | `#060A13` | Fundo da página (hoje é `bokka-surface-2`; no escuro precisa ser mais escuro que o card) |
| `--surface` | `--color-bokka-surface` | `#FFFFFF` | `#0B1220` | Card, painel de fundo, barra lateral |
| `--surface-2` | `--color-bokka-surface-2` | `#F8FAFC` | `#0F192C` | Faixas internas (aviso do menu Exportar, estado vazio) |
| `--surface-3` | `--color-bokka-surface-3` | `#F1F5F9` | `#16213A` | Botões redondos, trilho de segmentos, esqueleto de lista |
| `--panel-bg` | novo `--color-bokka-panel` | `#FFFFFF` | `#111B31` | Menus e painéis flutuantes (métrica, período, exportar) |
| `--border` | `--color-bokka-border` | `#E2E8F0` | `#1E2B47` | Borda de card, divisores |
| `--border-strong` | `--color-bokka-border-strong` | `#CBD5E1` | `#33456B` | Borda de botão e de campo |
| `--hover` | novo `--color-bokka-hover` | `#F1F5F9` | `#17233D` | Fundo de item transparente ao passar o mouse |
| `--hover-strong` | novo `--color-bokka-hover-strong` | `#E2E8F0` | `#24345A` | Fundo de botão preenchido ao passar o mouse |
| `--fg` | `--color-bokka-ink` | `#0B1220` | `#F1F5F9` | Texto principal |
| `--fg-2` | `--color-bokka-ink-2` | `#334155` | `#CBD5E1` | Texto secundário |
| `--fg-3` | `--color-bokka-ink-3` | `#64748B` | `#94A3B8` | Texto discreto, ícones |
| `--primary` | `--color-bokka-primary` | `#2A6BF2` | `#2A6BF2` | Fundo de botão/seleção com texto branco em cima; chave ligada |
| `--on-primary` | novo `--color-bokka-on-primary` | `#FFFFFF` | `#FFFFFF` | Texto/ícone sobre `--primary` |
| `--primary-soft` | `--color-bokka-primary-soft` | `#EAF1FF` | `#142447` | Fundo do item em foco ou hover nos menus |
| `--link` | novo `--color-bokka-link` | `#1E56D6` | `#7BA6F8` | **Texto** azul (links, item selecionado). Não usar `--primary` como cor de texto |
| `--link-hover` | novo `--color-bokka-link-hover` | `#1747B0` | `#A9C7FD` | Link ao passar o mouse |
| `--focus` | novo `--color-bokka-focus` | `#2A6BF2` | `#93B8FC` | Anel de foco do teclado (substitui `bokka-primary-ring`, que dá 2,0:1 no branco) |
| `--success-soft` | `--color-bokka-success-soft` | `#D1FAE5` | `rgb(16 185 129 / .16)` | Fundo do selo bom |
| `--success-fg` | `--color-bokka-success-ink` | `#047857` | `#6EE7B7` | Texto do selo bom |
| `--danger-soft` | `--color-bokka-danger-soft` | `#FEE2E2` | `rgb(239 68 68 / .18)` | Fundo do selo ruim |
| `--danger-fg` | `--color-bokka-danger-ink` | `#B91C1C` | `#FCA5A5` | Texto do selo ruim |
| `--neutral-soft` | `--color-bokka-neutral-soft` | `#F1F5F9` | `rgb(148 163 184 / .16)` | Fundo do selo neutro |
| `--neutral-fg` | `--color-bokka-neutral-ink` | `#475569` | `#CBD5E1` | Texto do selo neutro |
| `--shadow-sm` | `--shadow-sm` | `0 1px 2px 0 rgb(11 18 32 / .04)` | `0 1px 2px 0 rgb(0 0 0 / .4)` | Sombra do card |
| `--shadow-md` | `--shadow-md` | `0 4px 12px -2px rgb(11 18 32 / .08),0 2px 4px -2px rgb(11 18 32 / .04)` | `0 8px 24px rgb(0 0 0 / .45)` | Sombra de painel e balão |
| `--toast-bg` | novo (toast) | `#0B1220` | `#E2E8F0` | Fundo do aviso (invertido em relação ao tema) |
| `--toast-fg` | novo (toast) | `#FFFFFF` | `#0B1220` | Texto do aviso |
| `--toast-err-bg` | novo (toast) | `#B91C1C` | `#B91C1C` | Fundo do aviso de erro (igual nos dois temas) |

### 14.3 Tokens dos cards de gráfico

Os `--card-*` são apelidos dos tokens base (valem nos dois temas sem repetir o valor); os demais têm valor por tema.

| Variável | Claro | Escuro | Uso |
| :-- | :-- | :-- | :-- |
| `--card-bg` | `var(--surface)` | `var(--surface)` | Fundo do card de gráfico (alias de `--surface`) |
| `--card-fg` | `var(--fg)` | `var(--fg)` | Título e número grande (alias de `--fg`) |
| `--card-fg-2` | `var(--fg-2)` | `var(--fg-2)` | Legendas e rótulos de totais |
| `--card-fg-3` | `var(--fg-3)` | `var(--fg-3)` | Subtítulo, “vs mês anterior”, ícones do cabeçalho |
| `--card-border` | `var(--border)` | `var(--border)` | Borda do card |
| `--card-shadow` | `var(--shadow-sm)` | `var(--shadow-sm)` | Sombra do card |
| `--grid` | `#E2E8F0` | `rgb(255 255 255 / .08)` | Linhas de grade tracejadas (decorativas) |
| `--axis` | `#64748B` | `#94A3B8` | Rótulos dos eixos (texto 11px) |
| `--baseline` | `#64748B` | `#64748B` | Linha do zero do Fluxo de caixa (só aparece com lucro negativo) |
| `--line` | `#4C85F4` | `#6FA3FA` | Linha da Produtividade |
| `--line-fill` | `#2A6BF2` | `#6FA3FA` | Cor base do gradiente da área |
| `--line-fill-a` | `.22` | `.28` | Opacidade do topo do gradiente (base é sempre 0) |
| `--cursor` | `#93B8FC` | `rgb(147 184 252 / .45)` | Linha vertical do cursor (decorativa) |
| `--dot-bg` | `#FFFFFF` | `#0B1220` | Miolo do ponto marcado |
| `--dot-ring` | `#2A6BF2` | `#93B8FC` | Contorno do ponto marcado da Produtividade |
| `--compare` | `#64748B` | `#94A3B8` | Linha tracejada do período anterior |
| `--positive` | `#047857` | `#10B981` | Barras de **Receita** |
| `--negative` | `#EF4444` | `#EF4444` | Barras de **Despesa** |
| `--profit` | `#2A6BF2` | `#93B8FC` | Linha e ponto de **Lucro líquido** |
| `--tip-bg` | `#FFFFFF` | `#16213A` | Fundo do balão |
| `--tip-fg` | `#0B1220` | `#F1F5F9` | Valor no balão |
| `--tip-fg-3` | `#64748B` | `#94A3B8` | Data e rótulos no balão |
| `--tip-border` | `#E2E8F0` | `#33456B` | Borda do balão |
| `--tip-shadow` | `var(--shadow-md)` | `var(--shadow-md)` | Sombra do balão |
| `--band` | `rgb(42 107 242 / .06)` | `rgb(255 255 255 / .05)` | Faixa de destaque do mês no Fluxo de caixa |
| `--skeleton` | `#F1F5F9` | `rgb(255 255 255 / .06)` | Esqueleto de carregamento dos cards |

### 14.4 Mapa de uso (quem usa o quê)
- **Produtividade:** linha `--line`; área `--line-fill` com topo `--line-fill-a`; cursor `--cursor`; ponto `--dot-bg` + `--dot-ring`; comparação `--compare`; rótulos `--axis`; balão `--tip-*`; selo `--success-*`/`--danger-*`/`--neutral-*`; número `--card-fg`.
- **Fluxo de caixa:** barras `--positive` e `--negative`; linha e ponto `--profit`; grade `--grid`; zero `--baseline`; faixa do mês `--band`; balão `--tip-*`; totais `--card-fg` com pontinhos `--positive`/`--negative`/`--profit`.
- **Os dois:** cartão `--card-*`; menus `--panel-bg` + `--border` + `--shadow-md`; botões `--surface-3`/`--hover*`; link `--link`; foco `--focus`; esqueleto `--skeleton`; avisos `--toast-*`.

### 14.5 Contrastes calculados (WCAG, nos dois temas)
Calculados por script a partir dos próprios valores do protótipo (o script lê as variáveis do arquivo, então a tabela não tem erro de digitação). Cores semitransparentes (selos e grade no escuro) são compostas sobre o fundo real antes da conta. **Todos os 37 pares exigidos passam nos dois temas** (✔); ✘ não aparece.

**Texto (mínimo 4,5:1)**

| Par (primeiro plano sobre fundo) | Claro | Escuro | Mínimo |
| :-- | :-- | :-- | :-- |
| Texto principal (título, número grande) | 18,72:1 ✔<br><sub>`#0B1220` sobre `#FFFFFF`</sub> | 17,09:1 ✔<br><sub>`#F1F5F9` sobre `#0B1220`</sub> | 4,5:1 |
| Texto secundário (legendas, rótulos de totais) | 10,35:1 ✔<br><sub>`#334155` sobre `#FFFFFF`</sub> | 12,61:1 ✔<br><sub>`#CBD5E1` sobre `#0B1220`</sub> | 4,5:1 |
| Texto discreto (subtítulo, “vs mês anterior”) | 4,76:1 ✔<br><sub>`#64748B` sobre `#FFFFFF`</sub> | 7,3:1 ✔<br><sub>`#94A3B8` sobre `#0B1220`</sub> | 4,5:1 |
| Rótulos dos eixos (11 px) | 4,76:1 ✔<br><sub>`#64748B` sobre `#FFFFFF`</sub> | 7,3:1 ✔<br><sub>`#94A3B8` sobre `#0B1220`</sub> | 4,5:1 |
| Link e item selecionado (Mostrar valores, Abrir agenda) | 6,25:1 ✔<br><sub>`#1E56D6` sobre `#FFFFFF`</sub> | 7,71:1 ✔<br><sub>`#7BA6F8` sobre `#0B1220`</sub> | 4,5:1 |
| Link sobre faixa de aviso do menu Exportar | 5,98:1 ✔<br><sub>`#1E56D6` sobre `#F8FAFC`</sub> | 7,24:1 ✔<br><sub>`#7BA6F8` sobre `#0F192C`</sub> | 4,5:1 |
| Texto do menu sobre o painel | 18,72:1 ✔<br><sub>`#0B1220` sobre `#FFFFFF`</sub> | 15,65:1 ✔<br><sub>`#F1F5F9` sobre `#111B31`</sub> | 4,5:1 |
| Descrição do menu sobre o painel | 10,35:1 ✔<br><sub>`#334155` sobre `#FFFFFF`</sub> | 11,55:1 ✔<br><sub>`#CBD5E1` sobre `#111B31`</sub> | 4,5:1 |
| Texto do menu sobre o item em foco/hover | 16,52:1 ✔<br><sub>`#0B1220` sobre `#EAF1FF`</sub> | 13,96:1 ✔<br><sub>`#F1F5F9` sobre `#142447`</sub> | 4,5:1 |
| Link sobre item em foco/hover do menu | 5,52:1 ✔<br><sub>`#1E56D6` sobre `#EAF1FF`</sub> | 6,3:1 ✔<br><sub>`#7BA6F8` sobre `#142447`</sub> | 4,5:1 |
| Texto do botão Exportar sobre o card | 10,35:1 ✔<br><sub>`#334155` sobre `#FFFFFF`</sub> | 12,61:1 ✔<br><sub>`#CBD5E1` sobre `#0B1220`</sub> | 4,5:1 |
| Texto dos botões de ano (não selecionado) | 9,45:1 ✔<br><sub>`#334155` sobre `#F1F5F9`</sub> | 10,77:1 ✔<br><sub>`#CBD5E1` sobre `#16213A`</sub> | 4,5:1 |
| Branco sobre azul (ano selecionado, botão primário) | 4,68:1 ✔<br><sub>`#FFFFFF` sobre `#2A6BF2`</sub> | 4,68:1 ✔<br><sub>`#FFFFFF` sobre `#2A6BF2`</sub> | 4,5:1 |
| Selo bom (verde) | 4,84:1 ✔<br><sub>`#047857` sobre `#D1FAE5`</sub> | 9,65:1 ✔<br><sub>`#6EE7B7` sobre `#0C2D30`</sub> | 4,5:1 |
| Selo ruim (vermelho) | 5,3:1 ✔<br><sub>`#B91C1C` sobre `#FEE2E2`</sub> | 8,31:1 ✔<br><sub>`#FCA5A5` sobre `#341B26`</sub> | 4,5:1 |
| Selo neutro | 6,92:1 ✔<br><sub>`#475569` sobre `#F1F5F9`</sub> | 9,81:1 ✔<br><sub>`#CBD5E1` sobre `#212938`</sub> | 4,5:1 |
| Balão: valor | 18,72:1 ✔<br><sub>`#0B1220` sobre `#FFFFFF`</sub> | 14,6:1 ✔<br><sub>`#F1F5F9` sobre `#16213A`</sub> | 4,5:1 |
| Balão: data e rótulos | 4,76:1 ✔<br><sub>`#64748B` sobre `#FFFFFF`</sub> | 6,24:1 ✔<br><sub>`#94A3B8` sobre `#16213A`</sub> | 4,5:1 |
| Aviso (toast) | 18,72:1 ✔<br><sub>`#FFFFFF` sobre `#0B1220`</sub> | 15,19:1 ✔<br><sub>`#0B1220` sobre `#E2E8F0`</sub> | 4,5:1 |
| Aviso de erro (toast) | 6,47:1 ✔<br><sub>`#FFFFFF` sobre `#B91C1C`</sub> | 6,47:1 ✔<br><sub>`#FFFFFF` sobre `#B91C1C`</sub> | 4,5:1 |
| Estado vazio/erro: título | 17,89:1 ✔<br><sub>`#0B1220` sobre `#F8FAFC`</sub> | 16,03:1 ✔<br><sub>`#F1F5F9` sobre `#0F192C`</sub> | 4,5:1 |
| Estado vazio/erro: texto | 4,55:1 ✔<br><sub>`#64748B` sobre `#F8FAFC`</sub> | 6,85:1 ✔<br><sub>`#94A3B8` sobre `#0F192C`</sub> | 4,5:1 |

**Elementos gráficos e componentes (mínimo 3:1)**

| Par (primeiro plano sobre fundo) | Claro | Escuro | Mínimo |
| :-- | :-- | :-- | :-- |
| Linha da Produtividade | 3,52:1 ✔<br><sub>`#4C85F4` sobre `#FFFFFF`</sub> | 7,4:1 ✔<br><sub>`#6FA3FA` sobre `#0B1220`</sub> | 3:1 |
| Ponto marcado (anel) da Produtividade | 4,68:1 ✔<br><sub>`#2A6BF2` sobre `#FFFFFF`</sub> | 9,36:1 ✔<br><sub>`#93B8FC` sobre `#0B1220`</sub> | 3:1 |
| Linha tracejada de comparação | 4,76:1 ✔<br><sub>`#64748B` sobre `#FFFFFF`</sub> | 7,3:1 ✔<br><sub>`#94A3B8` sobre `#0B1220`</sub> | 3:1 |
| Barras de Receita | 5,48:1 ✔<br><sub>`#047857` sobre `#FFFFFF`</sub> | 7,38:1 ✔<br><sub>`#10B981` sobre `#0B1220`</sub> | 3:1 |
| Barras de Despesa | 3,76:1 ✔<br><sub>`#EF4444` sobre `#FFFFFF`</sub> | 4,98:1 ✔<br><sub>`#EF4444` sobre `#0B1220`</sub> | 3:1 |
| Linha de Lucro líquido | 4,68:1 ✔<br><sub>`#2A6BF2` sobre `#FFFFFF`</sub> | 9,36:1 ✔<br><sub>`#93B8FC` sobre `#0B1220`</sub> | 3:1 |
| Linha do zero (quando há lucro negativo) | 4,76:1 ✔<br><sub>`#64748B` sobre `#FFFFFF`</sub> | 3,93:1 ✔<br><sub>`#64748B` sobre `#0B1220`</sub> | 3:1 |
| Marcas de cor da Receita no balão | 5,48:1 ✔<br><sub>`#047857` sobre `#FFFFFF`</sub> | 6,31:1 ✔<br><sub>`#10B981` sobre `#16213A`</sub> | 3:1 |
| Marcas de cor da Despesa no balão | 3,76:1 ✔<br><sub>`#EF4444` sobre `#FFFFFF`</sub> | 4,25:1 ✔<br><sub>`#EF4444` sobre `#16213A`</sub> | 3:1 |
| Marcas de cor do Lucro no balão | 4,68:1 ✔<br><sub>`#2A6BF2` sobre `#FFFFFF`</sub> | 8:1 ✔<br><sub>`#93B8FC` sobre `#16213A`</sub> | 3:1 |
| Anel de foco do teclado | 4,68:1 ✔<br><sub>`#2A6BF2` sobre `#FFFFFF`</sub> | 9,36:1 ✔<br><sub>`#93B8FC` sobre `#0B1220`</sub> | 3:1 |
| Ícone do olho e das setas | 4,76:1 ✔<br><sub>`#64748B` sobre `#FFFFFF`</sub> | 7,3:1 ✔<br><sub>`#94A3B8` sobre `#0B1220`</sub> | 3:1 |
| Ícone dentro dos círculos do estado vazio/erro | 4,34:1 ✔<br><sub>`#64748B` sobre `#F1F5F9`</sub> | 6,24:1 ✔<br><sub>`#94A3B8` sobre `#16213A`</sub> | 3:1 |
| Chave “Comparar” desligada (contorno) | 4,76:1 ✔<br><sub>`#64748B` sobre `#FFFFFF`</sub> | 7,3:1 ✔<br><sub>`#94A3B8` sobre `#0B1220`</sub> | 3:1 |
| Chave “Comparar” ligada (fundo azul) | 4,68:1 ✔<br><sub>`#2A6BF2` sobre `#FFFFFF`</sub> | 4:1 ✔<br><sub>`#2A6BF2` sobre `#0B1220`</sub> | 3:1 |

**Informativo**

| Par (primeiro plano sobre fundo) | Claro | Escuro |
| :-- | :-- | :-- |
| Barra de Receita ao lado da de Despesa | 1,46:1 <br><sub>`#047857` sobre `#EF4444`</sub> | 1,48:1 <br><sub>`#10B981` sobre `#EF4444`</sub> |

**Decorativos (sem exigência, informados por transparência)**

| Par (primeiro plano sobre fundo) | Claro | Escuro |
| :-- | :-- | :-- |
| Linhas de grade (decorativas) | 1,23:1 <br><sub>`#E2E8F0` sobre `#FFFFFF`</sub> | 1,22:1 <br><sub>`#1F2532` sobre `#0B1220`</sub> |
| Linha vertical do cursor (decorativa; o ponto marcado carrega a informação) | 2:1 <br><sub>`#93B8FC` sobre `#FFFFFF`</sub> | 2,82:1 <br><sub>`#485D83` sobre `#0B1220`</sub> |

**Achados sobre os tokens atuais do sistema** (valem hoje, no claro):
- `bokka-primary-ring` (`#93B8FC`) como anel de foco dá só **2,0:1** no branco (mínimo 3:1). Recomendo o novo `--focus`: `#2A6BF2` (4,7:1) no claro e `#93B8FC` (9,4:1) no escuro.
- `bokka-primary` (`#2A6BF2`) como **texto** sobre `bokka-surface-2` (`#F8FAFC`) dá 4,48:1, um fio abaixo de 4,5. Por isso o texto azul usa o novo `--link` (`#1E56D6`, 5,98:1 ali). O `bokka-primary` fica só como fundo com texto branco em cima (4,68:1).
- `bokka-success` (`#10B981`) como cor de gráfico ou texto sobre branco dá **2,5:1**. No claro o gráfico usa `bokka-success-ink` (`#047857`).
- `ink-3` sobre `surface-3` dá 4,3:1: serve para ícone (3:1), **não para texto**. Nenhum texto do protótipo usa essa combinação.
- Linhas de grade e cursor são decorativos (a informação está nos rótulos, no ponto marcado e no balão), por isso não entram na exigência; os valores estão na tabela por transparência.

### 14.6 Como ver no protótipo
Em `docs/prototipo-grafico-produtividade.html` o painel de demonstração tem o alternador **Claro / Escuro** (também por endereço: `#tema=escuro` ou `#tema=claro`). Vale para os dois cards, para os menus, estados, olho de privacidade e para a largura de 320 a 1440px. O CSS do protótipo está organizado em dois blocos (`:root` e `:root[data-theme="dark"]`) e o restante do arquivo só consome variáveis. Não existe nenhuma classe de componente com cor escura própria.

### 14.7 Reconferir depois de mexer em cor
`docs/verificar-contraste-grafico.js` (Node, sem dependências) lê as variáveis do protótipo e refaz a tabela 14.5; sai com erro se algum par exigido cair abaixo do mínimo. Rode `node docs/verificar-contraste-grafico.js` depois de qualquer ajuste de cor.

---

## 15. O que o Frontend precisa preparar agora (para o modo escuro entrar depois sem retrabalho)

A regra é uma só: **componente novo ou mexido a partir de agora não tem cor escrita nele.** Ordem sugerida: (1) criar os tokens; (2) fazer os dois cards já com variáveis; (3) varrer o resto do sistema (outra tarefa, depois); (4) bloco escuro e alternador (tarefa do modo escuro). Os passos 1 e 2 não mudam nada visualmente no modo claro, exceto onde esta spec pede (cor de Receita, foco, link).

1. **Criar os tokens novos em `@theme`** (valores claros da tabela 14.2): `--color-bokka-page`, `-panel`, `-hover`, `-hover-strong`, `-on-primary`, `-link`, `-link-hover`, `-focus`, mais as sombras e o toast. Trocar `html { background: … }` para `var(--color-bokka-page)`. **Não** fixar `color-scheme: light` direto em `html` sem poder ser sobrescrito (hoje está fixo): passa a ser por tema.
2. **Variáveis do gráfico** (tabela 14.3) num bloco `:root` próprio (não precisam virar utilities do Tailwind: são lidas pelo CSS e pelo SVG).
3. **Nenhuma cor literal em componente.** Procurar e trocar: `#[0-9a-fA-F]{3,8}`, `rgb(`, `rgba(`, `text-white`, `bg-white`, `border-white`, `bg-white/10`, `bg-black`, `text-black`, `bg-[`, `text-[#`, `ring-[#`, `shadow-[`. Exemplo de busca: `rg -n "#[0-9A-Fa-f]{3,8}\b|\b(text|bg|border|ring|fill|stroke)-(white|black)\b|-\[#" src`. `text-white` só é aceitável como `text-bokka-on-primary` em cima de `bg-bokka-primary`.
4. **Recharts sem hex nas props.** Cores de `stroke`, `fill`, `stopColor`, `tick`, `cursor`, `activeDot`, `Bar`, `Line`, `Area` vêm de variáveis (`style={{ stroke: 'var(--line)' }}` ou classe CSS), não de string `'#4C85F4'`. O `id` do gradiente é único. O **tooltip é um componente próprio** com classes `--tip-*` (não o tooltip padrão do Recharts com estilo embutido). *Não testei `var()` em props do Recharts em Safari e Firefox*: confirmar nos dois.
5. **Foco global com `--focus`:** `:focus-visible { outline: 2px solid var(--color-bokka-focus); outline-offset: 2px }`, em vez de `ring-bokka-primary-ring`.
6. **Texto azul usa `text-bokka-link`**, nunca `text-bokka-primary`. `bokka-primary` é fundo (botão, chave ligada, ano selecionado) com `text-bokka-on-primary` em cima.
7. **Selos e status só pelos pares `soft` + `ink`** que já existem (`bokka-success-soft`/`-ink`...). Nada de `bg-emerald-100`, `text-red-600` e semelhantes.
8. **Menus, painéis e balões** usam `bg-bokka-panel` (menus) e `--tip-*` (balão), `border-bokka-border`, `shadow-md`; **sem `overflow-hidden` no card** e `z-40` nos painéis (barra inferior é `z-30`).
9. **Esqueletos:** `Skeleton` com `bg-bokka-surface-3` serve em listas; dentro dos cards de gráfico usar `--skeleton`.
10. **Cards pretos fixos viram o card padrão:** o Fluxo de caixa e o Produtividade deixam de ter `bg-bokka-ink text-white` e passam a usar o `Card` normal com tokens. Se existir variante escura fixa de `Card.tsx` ou de outros cards, **revisar caso a caso**: no modo escuro o card padrão já fica escuro sozinho.
11. **Fundos e imagens com cor própria:** `bokka-bg-pattern` (hoje `#F1F4F9` e gradientes fixos em `index.css`), logotipo, ilustrações e ícones coloridos precisam de variável ou de versão para fundo escuro. Listar quando for a tarefa do modo escuro.
12. **Exportação sempre clara:** PDF, Excel e CSV usam paleta fixa de papel, independente do tema (não leem variáveis).
13. **Não entram nos temas:** cores do odontograma e as cores dos e-mails (HTML de e-mail tem cores inline próprias).
14. **Primeira pintura:** quando o modo escuro existir, um script minúsculo no `index.html` (antes do React) aplica `data-theme` a partir da preferência salva, para a tela não piscar de claro para escuro.
15. **Teste de aceitação simples:** com o bloco escuro colado no console (`document.documentElement.dataset.theme = 'dark'`), os dois cards precisam ficar corretos **sem mudar nenhuma linha de componente**. Se algo ficar claro demais ou escuro demais, é uma cor literal que sobrou.
