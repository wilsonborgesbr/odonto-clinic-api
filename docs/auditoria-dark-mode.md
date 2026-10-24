# Auditoria do modo escuro (dark mode) — frontend Bokka

> Fase de **auditoria, somente leitura**. Nenhum arquivo do sistema foi alterado; este documento é a única gravação.
> Escopo: `odonto-clinic-web/src` (107 arquivos `.ts/.tsx/.css`). Data: 2026-10-26.
> Referência visual aprovada pelo Wilson: tema **Escuro** do protótipo `docs/prototipo-grafico-produtividade.html` (botão Claro/Escuro). Contrastes verificados por `docs/verificar-contraste-grafico.js` (rodado hoje: **0 falhas** nos dois temas).
> Spec já existente que este documento complementa: `docs/design-grafico-produtividade.md` §14 (tokens por tema).

---

## 1. Resumo

- **O sistema já está ~95% tokenizado.** São 1.671 usos de classes `bokka-*` nos `.tsx` contra **~320 pontos de cor fixa** (a maioria concentrada em 3 áreas: odontograma, modais de detalhe e Auditoria financeira). Isso é bom: trocar o conjunto de variáveis em `:root[data-theme="dark"]` resolve a maior parte sem tocar em componente.
- **Confirmado no build:** o Tailwind v4 **não inlina** os valores. O CSS gerado em `dist/assets/*.css` mantém `.bg-bokka-surface{background-color:var(--color-bokka-surface)}`, `.text-bokka-ink{color:var(--color-bokka-ink)}` etc. Portanto sobrescrever `--color-bokka-*` em `:root[data-theme="dark"]` funciona (era a dúvida em aberto do §14.1 da spec).
- **O maior risco não é a cor literal, é o token que inverte.** Se `bokka-ink` virar `#F1F5F9` no escuro (como o protótipo faz), **7 usos de `bg-bokka-ink`** (menu lateral ativo, overlays de modal, cartão "Caixa disponível", avatares) viram fundo claro com `text-white` por cima = ilegível. Além disso, **`bg-white` + `text-bokka-ink`** (calendário do Dashboard, botão do modal de paciente) vira branco sobre branco. Esses casos são **ALTA gravidade** e estão listados no §4.
- **Odontograma:** as cores clínicas (vermelho/azul/verde/**preto**) não podem entrar no tema. O preto clínico `#111827` sobre a superfície escura `#0B1220` tem contraste **1,06:1** (invisível). Recomendação: desenhar o odontograma sobre um painel "papel" claro fixo nos dois temas (§6).
- **PDF/Excel/CSV:** documentos devem ficar **sempre claros**; hoje já são independentes do CSS (cores RGB fixas em `lib/exportacao.ts`). Nada a migrar, só proteger (§7).
- **Esforço estimado:** ~45–55 h de front (Opção A do odontograma) + ~2 h de backend para guardar a preferência. Ordem recomendada no §12.

---

## 2. Como contei os pontos

Um **ponto** = uma ocorrência de cor fixa (que não vem de `var(--color-bokka-*)` ou de variável semântica) ou de um token que muda de significado no escuro. Varredura por regex em todos os `.ts/.tsx/.css` de `src/`, depois conferência manual dos casos de maior risco.

| Categoria | Total em `src/` | O que é |
| :-- | --: | :-- |
| Branco **sobre cor** (`text-white`, `bg-white/15`, `border-white/10`, `hover:text-white`…) | 97 | Em geral **intencional** (texto/vidro sobre azul, gradiente, vermelho). Só quebra se o fundo for um token que inverte (ver `bg-bokka-ink`). |
| Branco **como superfície** (`bg-white`, `hover:bg-white/85`) | 8 | Quebra no escuro, **exceto** as 2 bolinhas de chave (toggle) em Configuracoes e PacienteAnamneses. |
| Classes Tailwind cromáticas literais (`bg-blue-100`, `text-emerald-700`, `hover:bg-red-600`…) | 14 | Não acompanham o tema. |
| Hex em código (`#2A6BF2` etc.) | 160 | Dos quais 90 no odontograma, ~34 nos modais de detalhe, 17 em Pacientes, 12 na Auditoria. |
| `rgb()/rgba()` em código | 9 | Sombras, vidro, gradientes. |
| Cores nomeadas (`'white'`, `fill="white"`) | 4 | Toast e logo. |
| `bg-bokka-ink` (inverte no escuro) | 7 | Menu lateral, overlays, cartões escuros, avatares. |
| Atributos SVG `fill/stroke/stopColor` | 145 | 102 só em `Odontograma3D.tsx` (já contados no hex). |
| `style={{…}}` inline | 38 | Só ~14 carregam cor literal ou dinâmica; o resto é largura/altura. |
| `shadow-[…]` / `bg-[#…]` / `text-[#…]` (valor arbitrário) | 0 | Nenhum. |
| `dark:` / `data-theme` / `prefers-color-scheme` no código | 0 | Nada implementado ainda. |

Fora da contagem de "pontos" (mas anotados abaixo): `text-bokka-primary*` (113 ocorrências), `bokka-primary-ring` (25), `bg-bokka-surface-2` usado como fundo de página (2) e 2 tokens indefinidos (`bokka-primary-ink`, `bokka-primary-strong`).

---

## 3. Como os tokens estão organizados hoje

### 3.1 Arquivo `src/index.css` (220 linhas)
- `@theme { … }` define **todos** os tokens: `--color-bokka-*` (primary, ink 1–3, surface 1–3, border, success/warning/danger/info/neutral em `soft` e `ink`), `--font-sans`, `--radius-*`, `--shadow-sm/md`, breakpoint `tv`.
- Já existe um bloco de **tokens "semânticos para o escuro"** dentro do `@theme` (valores do tema claro): `--color-bokka-page`, `-panel`, `-hover`, `-hover-strong`, `-on-primary`, `-link`, `-link-hover`, `-focus`, `-toast-bg/-fg/-err-bg`. **Mas só os cards Produtividade e Fluxo de caixa os usam**; o resto do sistema ainda lê `surface-2` como fundo de página, `primary` como cor de texto etc.
- `:root { color-scheme: light; --grid … --skeleton }` — 21 variáveis dos gráficos (`--line`, `--positive`, `--tip-*`, `--band` …), fora do `@theme` porque são lidas por SVG/CSS do Recharts.
- Literais restantes no CSS global (11 pontos): `.bokka-bg-pattern` (`#F1F4F9` + 7 `rgba(148,163,184,…)`), `select option { background-color:#FFFFFF }`, e `--shadow-sm/md` com `rgb(11 18 32 / …)`.

### 3.2 Uso real (top do que aparece nos `.tsx`)
`bokka-ink-3` 305 · `bokka-ink` 226 · `bokka-border` 184 · `bokka-primary` 173 · `bokka-surface` 101 · `surface-3` 86 · `ink-2` 86 · `primary-soft` 72 · `danger-ink` 71 · `danger-soft` 52 · `surface-2` 47 · `danger` 47 · `success-ink` 30 · `primary-ring` 22 · `warning-ink` 18 · `warning-soft` 13.
Quem usa os tokens novos (`link`, `panel`, `hover`…): 5 + 3 + 8 usos. Ou seja, **a migração de `primary`→`link` e `primary-ring`→`focus` ainda não foi feita fora dos dois cards.**

### 3.3 O que falta para trocar por tema
1. Bloco `:root[data-theme="dark"]` com os valores do §8 (um único lugar).
2. Tokens que **não existem** e o escuro precisa: `scrim` (overlay), `strong`/`inverse` (cartão escuro fixo), `clinical-canvas` (papel do odontograma), `warning`/`info` em versão escura, `primary-ink`.
3. Mover as 21 variáveis de gráfico para terem versão escura (já definidas no protótipo, §14.3 da spec).
4. Provedor de tema + script anti-"flash" + botão (§10–11).
5. Codemods mecânicos (§12, etapa 1) e correção dos ~320 pontos (§4).
6. `color-scheme: dark` no escuro (hoje fixo em `light`): sem isso scrollbars, `<select>`, `<input type=date>` e autofill continuam claros.

---

## 4. Pontos por área, arquivos, gravidade e esforço

Legenda de gravidade: **ALTA** = quebra legibilidade/uso; **MÉDIA** = fica feio/inconsistente mas legível; **BAIXA** = funciona nos dois temas ou é intencional.
Esforço em horas de desenvolvimento + revisão visual, **já depois** do bloco de tokens (§12 etapa 0).
Os modais `*DetalheModal` ficam só em "Modais" para não contar duas vezes (também aparecem em Pacientes/Dentistas/Funcionários).

| # | Área | Pontos | Gravidade | Esforço |
| :-- | :-- | --: | :-- | --: |
| 1 | Odontograma | **93** | ALTA (decisão) | 2 h (Opção A) · 12–16 h (Opção B) |
| 2 | Modais | **71** | MÉDIA/ALTA | 6–8 h |
| 3 | Financeiro | **37** | ALTA | 4–5 h |
| 4 | Pacientes | **35** | MÉDIA | 3–4 h |
| 5 | Dashboard | **26** | ALTA | 3–4 h |
| 6 | Tabelas / UI base | **17** | MÉDIA | 2 h |
| 7 | Exportação PDF/Excel/CSV | **13** | BAIXA (intencional) | 0–1 h |
| 8 | Layout / base global (CSS) | **11** | ALTA (pequeno) | 1 h |
| 9 | Cadastros / Config | **5** | MÉDIA | 1 h |
| 10 | Toasts | **4** | BAIXA | 0,5 h |
| 11 | Menu lateral | **3** | ALTA | 1 h |
| 12 | Login e autenticação | **2** | MÉDIA | 1 h |
| 13 | Topbar | **2** | BAIXA | 0,5 h |
| 14 | Formulários | **1** | BAIXA | 1 h (revisão de foco/autofill) |
| 15 | Agenda | **0** | BAIXA | 1 h (só verificação visual) |
| 16 | Estoque | **0** | BAIXA | 1 h (só verificação visual) |
| | **Total** | **≈ 320** | | |

### 4.1 Odontograma — 93 pontos (ver §6)
`pages/odontograma/Odontograma3D.tsx` (92: 90 hex + 2 rgba) e `OdontogramaPacientePage.tsx` (1 `text-white` + 3 `style` com `condicaoColor`). O resto da página já usa tokens (95 usos).

### 4.2 Modais — 71 pontos
- `components/ui/Modal.tsx` (1): overlay `bg-bokka-ink/40`. **ALTA no escuro**: com `ink` claro vira uma cortina branca em vez de escurecer o fundo → trocar por `bg-bokka-scrim`.
- `pages/pacientes/PacienteDetalheModal.tsx` (29), `dentistas/DentistaDetalheModal.tsx` (23), `funcionarios/FuncionarioDetalheModal.tsx` (20): cabeçalho com `linear-gradient(135deg,#4F46E5,#7C3AED,#2A6BF2)` inline, `text-white`, `bg-white/15`, `border-white/15`, paleta de avatar por hash em hex (10 cores), arcada SVG decorativa com `stopColor="#FFFFFF"` e `rgba(255,255,255,…)`.
  - **Pode ficar fixo** (BAIXA): gradiente + texto branco funciona nos dois temas (branco sobre `#4F46E5` ≈ 6,3:1).
  - **Quebra** (ALTA): `PacienteDetalheModal.tsx:402` e `PacienteDetalhePage.tsx:519` — botão `bg-white text-bokka-ink hover:bg-white/90` sobre o gradiente: com `ink` claro vira texto claro em fundo branco. Trocar `text-bokka-ink` por cor fixa escura (token `bokka-on-light`) ou `bg-bokka-surface`.
  - `hover:text-white` (Paciente 172/194, Dentista 179/201, Funcionário 223/251): verificar o fundo do hover em cada um.
- `SeletorClinicaModal.tsx`, `PagamentoModal.tsx`: só tokens (0 pontos), mas usam `primary-ring` (ver §5.3).

### 4.3 Financeiro — 37 pontos (todos em `AuditoriaPage.tsx`, 1.319 linhas)
- Cartão "Caixa disponível" (`1221`): `bg-bokka-ink text-white` + camadas `bg-white/5`, `/10`, `/15`, `text-white/60..85` (24 pontos de branco). **ALTA**: com `ink` claro o cartão vira branco com texto branco. Opções: token `bokka-strong` (cartão escuro fixo, igual ao `#0B1220` do claro e `#16213A`-ish no escuro) ou reaproveitar o padrão do `FluxoCaixaCard` (só variáveis).
- Paleta de 10 categorias em hex (`categoriaColors`, linhas 87–96) usada em barras, bolinhas (`style background`) e `<Cell fill>` do gráfico de pizza. **MÉDIA**: são cores de dado; recalcular contraste ≥ 3:1 sobre `#0B1220` (as cores atuais passam em sua maioria; `#64748B` dá ~3,9:1, `#2A6BF2` 4,0:1). Mover para `--cat-1..10` com versão escura levemente mais clara.
- Tooltip do Recharts com `contentStyle` hex (`#FFFFFF`, `#E2E8F0`, linhas 953–954): **ALTA** (balão branco no escuro) → usar `var(--tip-bg)`, `var(--tip-border)` (já existem).
- 5 `text-white` em botões coloridos: BAIXA.

### 4.4 Pacientes — 35 pontos
- `PacienteDetalhePage.tsx` (27): igual ao modal (gradiente indigo, 16 brancos, `#FFFFFF/#E2E8F0/#FCA5A5/#EF4444/#CBD5E1` na arcada SVG decorativa do cabeçalho) + `bg-bokka-ink` em `:487` (cartão escuro: **ALTA**) e `:519` (botão branco, **ALTA**, §4.2).
- `PacienteProcedimentos.tsx` (8): mini-arcada SVG em tons de creme/terracota (`#FBF7EE`, `#E6DCC1`, `#C97A5E`, `#8F4E36`, `#B9A97F`), com fallback `var(--color-bokka-primary, #C97A5E)` e um token inexistente `--color-bokka-primary-strong`. **MÉDIA**: dente claro sobre fundo escuro até fica bom; trocar os fallbacks por tokens e definir os dois que faltam.
- `PacienteAnamneses.tsx` (1): `bg-white` do botão deslizante do toggle: BAIXA (é o "dedo" branco do switch).

### 4.5 Dashboard — 26 pontos (`pages/Dashboard.tsx`; os componentes `produtividade/*` e `fluxo/*` têm **0**)
- **Mini-calendário** (`387–449`, container `bg-bokka-primary-soft`): botões `bg-white text-bokka-ink`, dias `bg-white/60 text-bokka-ink hover:bg-white`, `ring-white` no dia atual, estrela dourada com gradiente `#FFE066/#FFC300/#D68F00` e contorno `#B37800`/`rgba(255,255,255,0.9)`. **ALTA**: no escuro `primary-soft` vira `#142447` e as células ficam brancas com texto claro. Trocar por `bg-bokka-surface/60 hover:bg-bokka-surface`, `ring-bokka-surface`; estrela dourada pode ficar (cor de marca/destaque, contraste ok sobre fundo escuro).
- KPI destacado (`HighlightKpi`, `bg-bokka-primary … text-white`, `bg-white/15`): BAIXA (azul sólido + branco funciona nos dois temas; 4,68:1).
- Avatares "+N" `bg-bokka-ink text-white` (`722`): **ALTA** (inverte). `statusTone` (`AGENDADO bg-bokka-primary text-white`, `CONFIRMADO bg-bokka-success text-white`, `FALTOU bg-bokka-warning text-white`): ver §5.5 (já é problema no claro).

### 4.6 Tabelas / UI base — 17 pontos
- `Avatar.tsx` (13): 6 pares cromáticos `bg-{blue,emerald,amber,rose,violet,cyan}-100 text-…-700` (**MÉDIA**: claros demais no escuro) + `text-white`. Trocar por 6 tokens `--avatar-1..6-bg/-fg` com versão escura (`rgb(cor / .18)` + texto 300).
- `Button.tsx` (4): `hover:bg-red-600 active:bg-red-700` no variant `danger` (**MÉDIA**, trocar por `bokka-danger-hover`), `text-white`.
- Não existe componente de tabela compartilhado: **as tabelas vivem dentro das páginas e já usam só tokens** (`border-bokka-border`, `bg-bokka-surface-2` no cabeçalho, `hover:bg-bokka-surface-3`). `Pagination`, `EmptyState`, `Card`, `Field`, `Skeleton`: 0 cores literais.

### 4.7 Layout / base global — 11 pontos (`index.css`)
- `.bokka-bg-pattern` (1 hex + 7 rgba) é o **fundo do app inteiro** (`AppLayout.tsx:13`) — **ALTA**: precisa de versão escura (usar `var(--color-bokka-page)` e gradientes `rgb(255 255 255 / .02..04)`), ou remover o padrão no escuro.
- `select option { background-color:#FFFFFF }` — **ALTA**: dropdown nativo branco no escuro (trocar por `var(--color-bokka-panel)`).
- `--shadow-sm/md` com `rgb(11 18 32 / …)`: no escuro as sombras do protótipo são `rgb(0 0 0 / .4/.45)`.
- `html { background: var(--color-bokka-page) }` já usa token.
- `App.css` (184 linhas, sobra do template Vite: `.counter`, `.hero`…) **não é importado em lugar nenhum**; ignorar (ou apagar numa limpeza à parte).

### 4.8 Exportação — 13 pontos (`lib/exportacao.ts`) → ver §7.

### 4.9 Cadastros / Config — 5 pontos
`Usuarios.tsx`: avatar `bg-bokka-ink` + `text-white` (`236`, **ALTA** pequena) e `text-white` (`535`); `Configuracoes.tsx`: `text-white` (`193`) e `bg-white` do knob do switch (`346`, BAIXA). Dentistas, Funcionários, Convênios, Pendências e Perfil: **0** literais (302 usos de token na área).

### 4.10 Toasts — 4 pontos
- `ui/Toast.tsx` (toast custom) usa só tokens: acompanha o tema, mas o protótipo define toast **invertido** (`--toast-bg:#E2E8F0`, `--toast-fg:#0B1220` no escuro; já existem `bokka-toast-bg/-fg/-err-bg`, ainda não aplicados).
- `main.tsx` (`<Toaster toastOptions>`): `boxShadow` com `rgb(11 18 32 / …)` e `iconTheme.secondary: 'white'` ×2. Trocar sombra por `var(--shadow-md)`; `secondary` pode ficar `white`.

### 4.11 Menu lateral — 3 pontos
`Sidebar.tsx`: overlay mobile `bg-bokka-ink/40` (`81`), item ativo `bg-bokka-ink text-white` (`117`). **ALTA**: no escuro o item ativo vira pílula clara com texto branco. O protótipo usa o **item ativo = `primary-soft` + texto `fg`** (`.sb span.on`) e a barra inferior ativa em `link` (`.bn span.on`); adotar isso. `BottomNav.tsx`: 0 literais (6 usos de token).

### 4.12 Login e autenticação — 2 pontos
`BokkaMark.tsx` (`fill="white"` na letra, `fill="var(--color-bokka-primary)"` no quadrado): funciona nos dois temas. `public/favicon.svg` (fora de `src`): `#2A6BF2` + `white`, idem. `AuthShell.tsx` e `Registro.tsx:52` usam **`bg-bokka-surface-2` como fundo da página** → no escuro vira `#0F192C`, **mais claro que o card** (`#0B1220`), invertendo a hierarquia; trocar por `bg-bokka-page`. Login, RecuperarSenha, RedefinirSenha, ConfirmarEmail: 0 literais.

### 4.13 Topbar — 2 pontos
`NotificationsPanel.tsx`: 2 `text-white` em contadores coloridos (BAIXA). `Header`, `GlobalSearch`, `PendenciasBanner`, `EmailNaoConfirmadoBanner`: 0 literais; usam `primary-ring` e `text-bokka-primary` (§5).

### 4.14 Formulários — 1 ponto
`DentistaForm.tsx:286` `text-white`. `ui/Field.tsx` (506 linhas): 0 literais, mas 2 `primary-ring`, SVG de chevron com `stroke="currentColor"` (ok), autofill/`placeholder` e `<input type="date|time">` dependem de `color-scheme: dark` (§3.3).

### 4.15 Agenda e Estoque — 0 pontos
Só tokens (38 e 66 usos). Verificar visualmente `statusTone` e badges de estoque baixo/crítico (warning/danger).

---

## 5. Riscos que não aparecem como "cor fixa"

### 5.1 Tokens que **invertem** de significado no escuro
No protótipo `--fg` (= `bokka-ink`) vai de `#0B1220` para `#F1F5F9`. Qualquer uso de `bokka-ink` **como fundo** quebra:

| Onde | Linha | Hoje | No escuro (sem correção) |
| :-- | :-- | :-- | :-- |
| `ui/Modal.tsx` | 65 | overlay `bg-bokka-ink/40` | cortina branca translúcida |
| `Sidebar.tsx` | 81 | overlay mobile `bg-bokka-ink/40` | idem |
| `Sidebar.tsx` | 117 | item ativo `bg-bokka-ink text-white` | pílula clara + texto branco |
| `Dashboard.tsx` | 722 | avatar "+N" `bg-bokka-ink text-white` | idem |
| `Usuarios.tsx` | 236 | avatar não-proprietário `bg-bokka-ink text-white` | idem |
| `AuditoriaPage.tsx` | 1221 | cartão "Caixa disponível" `bg-bokka-ink text-white` | cartão branco com texto branco |
| `PacienteDetalhePage.tsx` | 487 | cartão `bg-bokka-ink` | idem |

Correção: criar `--color-bokka-scrim` (`rgb(11 18 32 / .40)` claro · `rgb(0 0 0 / .60)` escuro) e `--color-bokka-strong` (cartão escuro fixo; no escuro, `--surface-3 #16213A` para destacar do card), e trocar os 7 usos. **Não** usar `dark:` — continua "um componente, dois conjuntos de variáveis".
Os 4 usos de `bg-bokka-ink-3` (Badge dot, bolinhas de timeline) **não** invertem (ink-3 vai de `#64748B` a `#94A3B8`): ok.

### 5.2 `bg-white` + `text-bokka-ink` / `text-bokka-*`
Mini-calendário do Dashboard (4 pontos) e botão do cabeçalho do paciente (2) — §4.2 e §4.5.

### 5.3 `text-bokka-primary` e `primary-ring`
- 113 ocorrências de `text-bokka-primary*` (incl. `hover:`): `#2A6BF2` como **texto** sobre `#0B1220` dá **4,00:1** (falha 4,5:1 para texto pequeno). A spec §14.2 já manda usar `--link`. **Codemod mecânico**: `text-bokka-primary` → `text-bokka-link` e `hover:text-bokka-primary-hover` → `hover:text-bokka-link-hover` (5 + 5 + 4 + 4 + 4 + 3 … em ~35 arquivos). `bg-bokka-primary` (fundo com texto branco) **permanece** (4,68:1 nos dois temas).
- 25 usos de `bokka-primary-ring` (borda de foco/hover em GlobalSearch, Field, ConveniosPage ×4, EstoquePage ×3…): `#93B8FC` é decorativo no claro (2,0:1) e no escuro funciona; ainda assim o foco deve ir para `--focus` (`#2A6BF2` claro, `#93B8FC` escuro). Trocar `*:focus-visible` em `index.css` para `var(--color-bokka-focus)`.

### 5.4 Tokens indefinidos
`bokka-primary-ink` (`Badge.tsx:20`, variant `primary`) e `--color-bokka-primary-strong` (`PacienteProcedimentos.tsx:684`) **não existem** no `@theme`: hoje o texto do badge `primary` herda a cor do pai. Definir os dois (claro `#1E4FBE`, escuro `#A9C7FD`).

### 5.5 Contraste que **já é problema no claro** (não é do dark mode, mas aparece na revisão)
Branco sobre `bokka-success #10B981` = **2,54:1**, sobre `bokka-warning #F59E0B` = **2,15:1**, sobre `bokka-danger #EF4444` = **3,76:1**. Aparecem em `statusTone` do Dashboard (`CONFIRMADO`, `FALTOU`) e em botões. Para ambos os temas, usar texto `#0B1220` sobre success/warning (7,38:1 e 8,72:1) ou os tokens `*-soft` + `*-ink`. Sugiro tratar junto, pois exige tocar nas mesmas linhas.

### 5.6 Sombras, imagens e fundos
- Sombras: só `shadow-sm`/`shadow-md` via token (nenhuma `shadow-[…]`). No escuro, usar as do protótipo (`rgb(0 0 0 / .4)` e `0 8px 24px rgb(0 0 0 / .45)`); a separação visual vem do fundo mais claro + borda `#1E2B47`.
- Imagens/fundos: não há `<img>` com fundo claro embutido nem `background-image: url()`; há fotos de perfil (`lib/profilePhotos.ts`, `Avatar`) que ficam como estão. `bg-gradient-*`/`bg-linear-*` Tailwind: 0. Gradientes só inline (3 de cabeçalho + 1 estrela + dentes SVG).
- `backdrop-blur-sm` nos overlays: ok nos dois temas.
- `@media print` / `window.print`: **não há** — nada a tratar de impressão.

---

## 6. Odontograma (atenção especial)

### 6.1 O que existe
`Odontograma3D.tsx` (1.385 linhas) é SVG com 90 hex e 102 atributos `fill/stroke`. Três camadas de cor, com significados diferentes:

1. **Cores clínicas (significado, padrão USP)** — `condicaoColor()` (linhas ~500–540):

| Grupo | Condições | fill | stroke/texto |
| :-- | :-- | :-- | :-- |
| Vermelho — precisa tratamento | cárie, restauração insatisfatória, fratura, extração indicada (+ legados) | `#DC2626` | `#991B1B` |
| Azul — tratamento realizado | restauração satisfatória, endodontia, prótese fixa/removível, implante (+ legados) | `#2563EB` | `#1D4ED8` |
| Verde — hígido | hígido, selante (+ legado) | `#059669` | `#047857` |
| **Preto — ausente/incluso** | ausente, dente incluso | `#111827` | `#111827` |
| Neutro (sem condição) | — | `#FFFCF2` | `#B0A78F` / texto `#64748B` |

   Esses fills também aparecem **fora do SVG**: legenda e seletor em `OdontogramaPacientePage.tsx` (linhas 385, 388, 678: `style={{background: fill}}` / `color: textInk`) e em `Odontograma3D.tsx` (legenda, linha ~1349).
2. **Anatomia (cenário)**: gradientes de esmalte/dentina/gengiva (`#FFF6D9→#D8B573`, `#F9C0CC→#B93E63`, brancos translúcidos, sombra `tooth-shadow`), `crownFill` `#FFFCF2/#E8ECF0/#CBD5E1`, contornos `#B0A78F/#8A7F5F/#B08D5D`.
3. **Cromo de interface**: grade de faces (`#FDFEFF` fundo, `#94A3B8` contorno), seleção `#2563EB` tracejada, rótulos `#475569/#64748B`, marca de extração `#B91C1C`, linha-guia `#CBD5E1`.

### 6.2 Por que não pode entrar no tema "cru"
Contrastes calculados (WCAG, mesma fórmula do script de contraste):

| Par | Contraste |
| :-- | --: |
| Preto clínico `#111827` sobre a superfície escura `#0B1220` | **1,06:1** (some) |
| Preto clínico `#111827` sobre branco | 17,74:1 |
| Vermelho `#DC2626` sobre `#0B1220` | 3,88:1 |
| Azul `#2563EB` sobre `#0B1220` | 3,62:1 |
| Verde `#059669` sobre `#0B1220` | 4,97:1 |
| Contorno de face `#94A3B8` sobre `#0B1220` | 7,30:1 |

Vermelho/azul/verde sobrevivem (≥ 3:1, mínimo para não-texto), mas **"ausente/incluso" em preto desaparece** em fundo escuro, e justamente é o grupo que se confunde com "sem registro". Mudar o significado da cor (ex.: ausente virar branco) é decisão **clínica**, não de design.

### 6.3 Recomendação
- **Opção A (recomendada): painel "papel" claro fixo.** O desenho do odontograma (SVG + legenda + seletor de condição) fica dentro de um container com `bg-bokka-clinical-canvas` (`#FFFFFF` nos dois temas) e borda do tema. As 3 camadas de cor permanecem **idênticas** nos dois temas; zero risco clínico; esforço baixo (envolver o SVG e a legenda, e revisar rótulos com `text-bokka-*` que ficam sobre o papel — usar um conjunto `--canvas-fg/-fg-3` fixo escuro). O restante da página (cabeçalho, listas de procedimentos, `ProcedimentoQuickLaunch`) segue o tema normalmente. **~2 h.**
- **Opção B: odontograma escuro de verdade.** Fundo `#0B1220`; "preto" vira preenchimento `#111827` com **anel claro de 2 px** (`#F1F5F9`) + hachura para ausente/incluso; gradientes de esmalte recalibrados; seleção e rótulos em `--link`/`--fg-3`. Exige aprovação clínica da legenda e testes com a Tainah e **~12–16 h**. Só vale se o Wilson quiser o odontograma escuro como diferencial.
- Em qualquer opção: `condicaoColor()` **não deve ler variáveis de tema**; manter as cores clínicas como constantes (inclusive para o uso em legenda, seletor e eventual impressão/PDF futuro).
- A spec §14.1 já registra: "as cores clínicas não entram nos temas (padrão USP)". Este item só fecha o "como".

---

## 7. Exportação PDF / Excel / CSV

- `lib/exportacao.ts` (427 linhas): **PDF** com `jsPDF` + `jspdf-autotable` e tabela `COR` em RGB (azul `[42,107,242]`, tinta `[11,18,32]`, cinza, linha `[226,232,240]`, faixa `[248,250,252]`, cabeçalho `[234,241,255]`, bom `[4,120,87]`, ruim `[185,28,28]`…) com `fillColor:[255,255,255]`; **Excel** com `write-excel-file` (`#EAF1FF`, `#0B1220`, `#B91C1C`); **CSV** sem cor. 3 hex + 10 tuplas RGB = **13 pontos**, todos **intencionais**.
- Esses arquivos são **documentos** (papel/planilha, abertos fora do app, impressos): **devem permanecer claros independentemente do tema do usuário**. Eles não leem CSS nem `getComputedStyle`, então **não mudam sozinhos** quando o tema escuro entrar (confirmado por busca: o único uso de `document` no módulo é a âncora de download; não há `var(--…)`, `matchMedia` nem `getComputedStyle`).
- Ações: (1) deixar um comentário no topo de `COR` e das constantes do Excel dizendo "fixo de propósito — não migrar para tokens"; (2) **teste de regressão manual**: exportar PDF/XLSX/CSV com o tema escuro ligado e abrir; (3) se um dia o gráfico for exportado como imagem, renderizar com as variáveis do tema claro (container isolado com `data-theme="light"`), nunca com a tela atual.
- Odontograma em PDF/impressão: **hoje não existe** (sem `window.print`, sem `@media print`). Se vier, usar papel branco e as cores clínicas do §6.1 sem alteração.
- Esforço: 0 h de código; 0,5–1 h de comentário e teste.

---

## 8. Paleta de referência (tema Escuro do protótipo)

Fonte: bloco `:root[data-theme="dark"]` de `docs/prototipo-grafico-produtividade.html` (linhas 38–66). Tema claro no mesmo arquivo (linhas 12–37). "Token atual" é o nome em `src/index.css`; "novo" = ainda não existe ou ainda não é usado fora dos dois cards.

### 8.1 Base do sistema: valores e mapeamento para `bokka-*`

| Papel | Variável do protótipo | Token Bokka | Claro | **Escuro** | Observação |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Fundo da página | `--bg` | `--color-bokka-page` (novo uso) | `#F8FAFC` | **`#060A13`** | Hoje a página usa `surface-2`/`.bokka-bg-pattern`; migrar (§4.7, §4.12). |
| Superfície (card, **menu lateral**, barra inferior) | `--surface` | `--color-bokka-surface` | `#FFFFFF` | **`#0B1220`** | O protótipo usa `--surface` + `border-right: var(--border)` no menu lateral e `border-top` na barra inferior. |
| Superfície 2 (faixas internas, cabeçalho de tabela) | `--surface-2` | `--color-bokka-surface-2` | `#F8FAFC` | **`#0F192C`** | Não usar como fundo de página. |
| Superfície 3 (trilho de segmentos, hover de linha, esqueleto) | `--surface-3` | `--color-bokka-surface-3` | `#F1F5F9` | **`#16213A`** | |
| Painel flutuante (menus, popovers, dropdown) | `--panel-bg` | `--color-bokka-panel` | `#FFFFFF` | **`#111B31`** | Serve também ao `select option`. |
| Borda | `--border` | `--color-bokka-border` | `#E2E8F0` | **`#1E2B47`** | |
| Borda forte (botão, campo) | `--border-strong` | `--color-bokka-border-strong` | `#CBD5E1` | **`#33456B`** | |
| Hover transparente | `--hover` | `--color-bokka-hover` | `#F1F5F9` | **`#17233D`** | |
| Hover de botão cheio | `--hover-strong` | `--color-bokka-hover-strong` | `#E2E8F0` | **`#24345A`** | |
| Texto principal | `--fg` | `--color-bokka-ink` | `#0B1220` | **`#F1F5F9`** | ⚠ inverte: ver §5.1. |
| Texto secundário | `--fg-2` | `--color-bokka-ink-2` | `#334155` | **`#CBD5E1`** | |
| Texto discreto / ícones | `--fg-3` | `--color-bokka-ink-3` | `#64748B` | **`#94A3B8`** | |
| **Azul de destaque** (fundo de botão/seleção) | `--primary` | `--color-bokka-primary` | `#2A6BF2` | **`#2A6BF2`** | Não muda. Só fundo (texto branco 4,68:1). |
| Texto sobre o azul | `--on-primary` | `--color-bokka-on-primary` | `#FFFFFF` | **`#FFFFFF`** | Substitui `text-white` em fundo `primary`. |
| Fundo azul suave | `--primary-soft` | `--color-bokka-primary-soft` | `#EAF1FF` | **`#142447`** | Item ativo do menu, hover de menu. |
| Azul em **texto** (link, selecionado) | `--link` | `--color-bokka-link` | `#1E56D6` | **`#7BA6F8`** | Substitui `text-bokka-primary` (113 usos). |
| Link hover | `--link-hover` | `--color-bokka-link-hover` | `#1747B0` | **`#A9C7FD`** | |
| Anel de foco | `--focus` | `--color-bokka-focus` | `#2A6BF2` | **`#93B8FC`** | Substitui `primary-ring`. |
| **Verde** (fundo do selo) | `--success-soft` | `--color-bokka-success-soft` | `#D1FAE5` | **`rgb(16 185 129 / .16)`** | |
| Verde (texto do selo) | `--success-fg` | `--color-bokka-success-ink` | `#047857` | **`#6EE7B7`** | |
| **Vermelho** (fundo do selo) | `--danger-soft` | `--color-bokka-danger-soft` | `#FEE2E2` | **`rgb(239 68 68 / .18)`** | |
| Vermelho (texto do selo) | `--danger-fg` | `--color-bokka-danger-ink` | `#B91C1C` | **`#FCA5A5`** | |
| Neutro (fundo do selo) | `--neutral-soft` | `--color-bokka-neutral-soft` | `#F1F5F9` | **`rgb(148 163 184 / .16)`** | |
| Neutro (texto do selo) | `--neutral-fg` | `--color-bokka-neutral-ink` | `#475569` | **`#CBD5E1`** | |
| Sombra pequena | `--shadow-sm` | `--shadow-sm` | `0 1px 2px 0 rgb(11 18 32 / .04)` | **`0 1px 2px 0 rgb(0 0 0 / .4)`** | |
| Sombra de painel | `--shadow-md` | `--shadow-md` | `0 4px 12px -2px … / .08, 0 2px 4px -2px … / .04` | **`0 8px 24px rgb(0 0 0 / .45)`** | |
| Toast (fundo / texto / erro) | `--toast-bg/-fg/-err-bg` | `--color-bokka-toast-*` | `#0B1220` / `#FFFFFF` / `#B91C1C` | **`#E2E8F0` / `#0B1220` / `#B91C1C`** | Toast invertido no escuro. |
| `color-scheme` | `color-scheme` | `:root` | `light` | **`dark`** | Scrollbar, `<select>`, date picker, autofill. |

**Elevação no escuro (de baixo para cima):** página `#060A13` → card/menu lateral `#0B1220` → painel `#111B31` → balão `#16213A`. A separação vem do preenchimento mais claro + borda, não da sombra.

### 8.2 Variáveis dos gráficos (cards Produtividade e Fluxo de caixa — já só usam variáveis)

| Variável | Claro | **Escuro** | Uso |
| :-- | :-- | :-- | :-- |
| `--grid` | `#E2E8F0` | **`rgb(255 255 255 / .08)`** | grade tracejada |
| `--axis` | `#64748B` | **`#94A3B8`** | rótulos dos eixos |
| `--baseline` | `#64748B` | **`#64748B`** | linha do zero |
| `--line` / `--line-fill` | `#4C85F4` / `#2A6BF2` | **`#6FA3FA`** / **`#6FA3FA`** | linha e área da Produtividade |
| `--line-fill-a` | `.22` | **`.28`** | opacidade do topo do gradiente |
| `--cursor` | `#93B8FC` | **`rgb(147 184 252 / .45)`** | cursor vertical |
| `--dot-bg` / `--dot-ring` | `#FFFFFF` / `#2A6BF2` | **`#0B1220`** / **`#93B8FC`** | ponto marcado |
| `--compare` | `#64748B` | **`#94A3B8`** | linha do período anterior |
| `--positive` | `#047857` | **`#10B981`** | barras de Receita (**verde**) |
| `--negative` | `#EF4444` | **`#EF4444`** | barras de Despesa (**vermelho**) |
| `--profit` | `#2A6BF2` | **`#93B8FC`** | linha de Lucro |
| `--tip-bg/-fg/-fg-3/-border` | `#FFFFFF/#0B1220/#64748B/#E2E8F0` | **`#16213A/#F1F5F9/#94A3B8/#33456B`** | balão |
| `--band` | `rgb(42 107 242 / .06)` | **`rgb(255 255 255 / .05)`** | faixa do mês |
| `--skeleton` | `#F1F5F9` | **`rgb(255 255 255 / .06)`** | esqueleto de carregamento |

### 8.3 Lacuna: **âmbar/aviso e info não existem no protótipo**
O protótipo só define verde (sucesso), vermelho (perigo) e neutro. O sistema usa `warning` (18 `warning-ink`, 13 `warning-soft`, 10 `warning`) e `info` (3 usos) e `primary-ink` (indefinido). Proposta **derivada** (não vem do protótipo; contrastes calculados por mim com a mesma fórmula do script, **não** estão no `verificar-contraste-grafico.js` — incluí-los lá é parte da implementação):

| Token | Claro (atual) | **Escuro (proposto)** | Contraste escuro |
| :-- | :-- | :-- | --: |
| `--color-bokka-warning` (sólido/indicador) | `#F59E0B` | `#F59E0B` | 8,72:1 sobre `#0B1220` |
| `--color-bokka-warning-soft` | `#FEF3C7` | `rgb(245 158 11 / .16)` | — |
| `--color-bokka-warning-ink` | `#B45309` | `#FCD34D` | **10,02:1** sobre o soft composto |
| `--color-bokka-info-soft` | `#EAF1FF` | `rgb(42 107 242 / .18)` | — |
| `--color-bokka-info-ink` | `#1E4FBE` | `#7BA6F8` | **6,46:1** |
| `--color-bokka-primary-ink` (hoje indefinido) | — (`#1E4FBE`) | `#A9C7FD` | **8,94:1** sobre `#142447` |
| `--color-bokka-primary-strong` (hoje indefinido) | — | claro `#1E56D6` · escuro `#A9C7FD` | — |
| `--color-bokka-danger` sólido | `#EF4444` | `#EF4444` | 5,26:1 sobre `#060A13` |

### 8.4 Tokens novos necessários (não existem no protótipo, vêm desta auditoria)
`--color-bokka-scrim` (overlay) · `--color-bokka-strong` (cartão escuro fixo, §5.1) · `--color-bokka-clinical-canvas` e `--canvas-fg/-fg-3` (papel do odontograma, §6.3) · `--color-bokka-danger-hover` · `--cat-1..10` (categorias da Auditoria) · `--avatar-1..6-bg/-fg` · `--color-bokka-on-light` (texto escuro fixo para botão branco sobre gradiente).

### 8.5 Contrastes já verificados (`docs/verificar-contraste-grafico.js`, rodado em 2026-10-26)
Resultado do script: **"Falhas: 0"** (código de saída 0) — todos os pares exigidos passam nos dois temas. Resumo (o script compõe cores semitransparentes sobre o fundo real antes da conta):

**Texto (mínimo 4,5:1)**

| Par | Claro | Escuro |
| :-- | --: | --: |
| Texto principal (`fg` / `surface`) | 18,72 | 17,09 |
| Texto secundário (`fg-2`) | 10,35 | 12,61 |
| Texto discreto (`fg-3`) | 4,76 | 7,30 |
| Rótulos dos eixos (11 px) | 4,76 | 7,30 |
| Link / item selecionado (`link` / `surface`) | 6,25 | 7,71 |
| Link sobre faixa `surface-2` | 5,98 | 7,24 |
| Texto do menu sobre painel (`fg` / `panel`) | 18,72 | 15,65 |
| Descrição do menu (`fg-2` / `panel`) | 10,35 | 11,55 |
| Texto sobre item em foco (`fg` / `primary-soft`) | 16,52 | 13,96 |
| Link sobre item em foco (`link` / `primary-soft`) | 5,52 | 6,30 |
| Botão Exportar (`fg-2` / `surface`) | 10,35 | 12,61 |
| Botões de ano (`fg-2` / `surface-3`) | 9,45 | 10,77 |
| **Branco sobre azul** (`on-primary` / `primary`) | 4,68 | 4,68 |
| **Selo verde** (`success-fg` / `success-soft`) | 4,84 | 9,65 |
| **Selo vermelho** (`danger-fg` / `danger-soft`) | 5,30 | 8,31 |
| Selo neutro | 6,92 | 9,81 |
| Balão: valor / data | 18,72 / 4,76 | 14,60 / 6,24 |
| Toast / toast de erro | 18,72 / 6,47 | 15,19 / 6,47 |
| Estado vazio: título / texto | 17,89 / 4,55 | 16,03 / 6,85 |

**Não-texto (mínimo 3:1)**

| Par | Claro | Escuro |
| :-- | --: | --: |
| Linha da Produtividade | 3,52 | 7,40 |
| Ponto marcado (anel) | 4,68 | 9,36 |
| Linha de comparação | 4,76 | 7,30 |
| Barras de **Receita (verde)** | 5,48 | 7,38 |
| Barras de **Despesa (vermelho)** | 3,76 | 4,98 |
| Linha de Lucro | 4,68 | 9,36 |
| Linha do zero | 4,76 | 3,93 |
| Marcas no balão (receita / despesa / lucro) | 5,48 / 3,76 / 4,68 | 6,31 / 4,25 / 8,00 |
| Anel de foco | 4,68 | 9,36 |
| Ícone do olho e das setas | 4,76 | 7,30 |
| Chave “Comparar” desligada / ligada | 4,76 / 4,68 | 7,30 / **4,00** |
| (informativos) receita×despesa lado a lado | 1,46 | 1,48 |

Ressalvas já documentadas na spec: Receita e Despesa têm luminância parecida (1,46–1,48:1) de propósito para daltonismo; a cor nunca é o único sinal. O script **não cobre**: o resto do sistema (menu lateral, tabelas, formulários, badges `warning/info`), o odontograma e os 7 casos do §5.1; esses precisam entrar no script como novos pares na implementação (sugestão de lista no §9.3).

---

## 9. Proposta de variáveis semânticas

### 9.1 Princípio
**Um componente, dois conjuntos de variáveis**; nenhum `dark:` com cor dentro de componente. O tema troca só as variáveis em `:root[data-theme="dark"]`. Nomes públicos continuam `bokka-*` (as 1.671 classes existentes seguem funcionando).

### 9.2 Conjunto mínimo (nome semântico → token)

| Grupo | Tokens |
| :-- | :-- |
| **Fundo da página** | `bokka-page` |
| **Superfície** | `bokka-surface` (card/menu) · `bokka-surface-2` · `bokka-surface-3` · `bokka-panel` (flutuantes) · `bokka-strong` (cartão escuro fixo) · `bokka-scrim` (overlay) |
| **Borda** | `bokka-border` · `bokka-border-strong` · `bokka-focus` |
| **Texto** | `bokka-ink` (primário) · `bokka-ink-2` (secundário) · `bokka-ink-3` (discreto) · `bokka-on-primary` · `bokka-on-light` |
| **Destaque** | `bokka-primary` (fundo) · `bokka-primary-soft` · `bokka-link` / `-hover` (texto) · `bokka-primary-ink` |
| **Interação** | `bokka-hover` · `bokka-hover-strong` |
| **Sucesso** | `bokka-success` · `-soft` · `-ink` |
| **Erro** | `bokka-danger` · `-soft` · `-ink` · `-hover` |
| **Aviso** | `bokka-warning` · `-soft` · `-ink` |
| **Informação / neutro** | `bokka-info(-soft/-ink)` · `bokka-neutral(-soft/-ink)` |
| **Toast** | `bokka-toast-bg/-fg/-err-bg` |
| **Dados** | `--cat-1..10` · `--avatar-1..6-*` · as 21 variáveis de gráfico |
| **Clínico (fixo)** | `bokka-clinical-canvas` (e `--canvas-*`); as cores de condição **não** viram variáveis de tema |

### 9.3 Pares que devem entrar no verificador de contraste (ao implementar)
`link/page`, `ink-3/page`, `ink/surface-2`, `warning-ink/warning-soft`, `info-ink/info-soft`, `primary-ink/primary-soft`, `on-light/branco`, `border/surface` (≥ 1,3 ok porque é decorativo; `border-strong/surface` ≥ 3 só em campo, hoje 1,97 → ajustar o escuro do campo ou usar `--hover-strong`), `canvas-fg/canvas`, e os pares de `statusTone`.
Nota: `border-strong #33456B` sobre `#0B1220` = **1,97:1** e `border #1E2B47` = 1,33:1. Para campos de formulário (borda é o único limite visual do controle) o WCAG 1.4.11 pede 3:1: avaliar `#4A5F8A` para `border-strong` no escuro ou fundo `surface-3` nos campos.

---

## 10. Onde guardar a preferência

### 10.1 Modelo
Três opções para o usuário: **Claro · Escuro · Automático** (segue o sistema). Padrão recomendado: **Claro** para quem nunca escolheu (a spec §14.1 sugere o mesmo, e o `DESIGN.md` lista "dark mode default" como anti-padrão), com *Automático* como opção explícita. (Decisão do Wilson, §13.)

### 10.2 Por usuário, como o olho de privacidade
Padrão já existente em `context/FinancialVisibilityContext.tsx`: `localStorage` (`bokka:financeiro-visivel`) como **cache otimista** + backend como **fonte de verdade por usuário** (`GET /api/usuarios/me` → `preferences.hideFinancialInfo`; `PATCH /api/usuarios/me/preferences`, com rollback e toast em erro, sincronização entre abas por `storage`). Replicar o mesmo desenho:
- Chave `bokka:tema` (`'claro'|'escuro'|'auto'`) no `localStorage` (cache) e campo `theme` em `preferences`.
- **Backend (fora do escopo do Frontend — avisar o Maestro):** `UserPreferences.java` hoje só tem `hideFinancialInfo`; `AtualizarPreferenciasDTO` tem `@NotNull hideFinancialInfo`. Para gravar o tema é preciso: campo `theme` (enum/string) em `UserPreferences`, DTO aceitar **PATCH parcial** (hoje enviar só `theme` falharia a validação do `hideFinancialInfo`), e `UsuarioPreferencesDTO` no front ganhar `theme?`. ~1–2 h de backend + deploy. Alternativa sem backend (v1): só `localStorage` (por navegador, não por usuário) — perde a sincronia entre dispositivos e o cache é compartilhado entre usuários do mesmo computador; **não recomendada**.
- Como `usuarioService.useAtualizarPreferenciasUsuario` hoje tem o payload `{hideFinancialInfo}` e o `onMutate` reescreve só esse campo, ele precisa virar `Partial<>` (merge dos campos).
- O cache do React Query sobrevive ao logout (`pronto` em `FinancialVisibilityContext`): **ao sair, voltar o tema para o da tela de login** (sistema/claro) e só aplicar o tema do usuário quando `usuarioMe.id === user.id`.

### 10.3 `prefers-color-scheme`
- No modo **Automático**: `window.matchMedia('(prefers-color-scheme: dark)')` com `addEventListener('change')` para reagir a mudanças do sistema em tempo real; `data-theme` no `<html>` recebe sempre o valor **resolvido** (`light`/`dark`), nunca `auto`.
- Telas públicas (login, registro, recuperar/redefinir senha, confirmar e-mail) **não têm usuário logado**: usam o último valor em `localStorage` (cache) ou, na falta, o sistema (se for `auto`) ou claro.

### 10.4 Evitar "flash" de tema errado
Script síncrono inline no `<head>` do `index.html` (antes do bundle), como o do protótipo (linha 8): lê `localStorage.getItem('bokka:tema')`, resolve com `matchMedia` e faz `document.documentElement.setAttribute('data-theme', …)` + `style.colorScheme`. Envolver em `try/catch` (modo privado). Hoje `index.html` não tem nada disso. Adicionar `<meta name="color-scheme" content="light dark">` e `<meta name="theme-color">` (dois, com `media`) para a barra do navegador mobile.

### 10.5 Arquitetura no front
- `context/ThemeContext.tsx` (`ThemeProvider` + `useTheme()`), irmão do `FinancialVisibilityProvider` em `App.tsx:173`, exportando `{ preferencia, tema (resolvido), setPreferencia }`.
- Persistência via React Query + service (regra do projeto: nada de `useState` para dado do servidor nem fetch direto), com cache em `localStorage` apenas para o primeiro paint.
- Recharts e SVG: ler cores por `var(--…)` (já é assim nos dois cards). Não existe hook de "tema" nos gráficos a criar.

---

## 11. Botão de alternar

- **Onde:** (1) menu do usuário na `Header` (topbar), com 3 itens (Claro/Escuro/Automático, ícones `Sun`/`Moon`/`Monitor` do Lucide) — acesso rápido; (2) seção "Aparência" em `Configuracoes.tsx` e/ou `Perfil.tsx` — onde ficam as outras preferências. (3) Nas telas de login um botão discreto de alternar (Claro/Escuro) no canto, sem Automático explícito.
- **Controle:** grupo segmentado (`role="radiogroup"`, `aria-checked`) como o `.demo .btns` do protótipo: trilho `surface-3`, item ativo `surface` + texto `link` + `shadow-sm`. Rótulos em português, `aria-label` completo ("Tema: Escuro"); foco com `bokka-focus`.
- **Comportamento:** aplica na hora (otimista), salva em segundo plano (toast só em erro, como o olho); respeita `useReduzirMovimento` (sem transição de cor quando o usuário reduz movimento; senão `transition: background-color .15s, color .15s, border-color .15s` somente no `html`/`body` para não animar o app inteiro).
- **Teclado/leitor de tela:** setas navegam entre opções; anunciar "Tema escuro ativado" (`aria-live="polite"`).
- **Mobile:** o menu lateral vira gaveta; o item de tema deve estar também na gaveta/Configurações, já que a topbar é compacta.
- Esforço: ~3 h (componente + integração + a11y), incluso no §12 etapa 0.

---

## 12. Esforço total e ordem recomendada

### 12.1 Ordem

| Etapa | O que | Por que nessa ordem | Esforço |
| :-- | :-- | :-- | --: |
| **0. Fundação** | Bloco `:root[data-theme="dark"]` com §8 (inclui `color-scheme`, sombras, `bokka-page`, tokens novos do §8.3–8.4); `ThemeProvider`; script anti-flash no `index.html`; botão em Configurações/Header; backend `theme` (PATCH parcial) | Sem isso nada é testável; também prova (teste de 5 min) que o override funciona no build real | 8–10 h (+1–2 h backend) |
| **1. Codemods mecânicos** | `text-bokka-primary*` → `text-bokka-link*` (113); `primary-ring` → `focus` (25); `bg-bokka-ink/40` → `bg-bokka-scrim`; `bg-bokka-ink` → `bg-bokka-strong` (7); `surface-2` de página → `page` (2); `.bokka-bg-pattern`; `select option`; `*:focus-visible`; definir `primary-ink`/`primary-strong` | Maior ganho com menor risco; resolve toda a classe "token que inverte" | 3–4 h |
| **2. Casca do app** | Layout, Menu lateral (item ativo = `primary-soft`), Topbar, Login/autenticação, Toasts (`toast-*`, sombra) | É o que o usuário vê em toda tela; poucos pontos (≈ 25) | 4–5 h |
| **3. UI base** | `Avatar` (6 tons), `Button` danger, `Badge`, `Skeleton`, `Field`/formulários (borda de campo 3:1, autofill) | Reaproveitados em todas as páginas; corrigir aqui evita retrabalho | 3–4 h |
| **4. Dashboard** | Mini-calendário (`bg-white` → `surface`), avatares, `statusTone` (contraste de texto sobre success/warning) | Tela inicial; 26 pontos, 2 ALTA | 3–4 h |
| **5. Financeiro** | Cartão "Caixa disponível" (reusar padrão `FluxoCaixaCard`), tooltip do Recharts, `--cat-1..10` | 37 pontos, 3 ALTA; gráfico de pizza precisa de revisão de contraste | 4–5 h |
| **6. Pacientes + Modais** | Cabeçalhos em gradiente (manter fixos), botões `bg-white text-ink`, arcadas SVG, paleta de avatar por hash (10 hex) | 106 pontos, mas a maioria BAIXA (branco sobre gradiente); só ~8 quebram | 8–11 h |
| **7. Odontograma** | **Opção A:** painel "papel" claro fixo; ou **Opção B** | Depende da decisão do Wilson/clínica; independe das demais etapas | 2 h (A) · 12–16 h (B) |
| **8. Cadastros, Agenda, Estoque** | Só verificação visual (+ 5 pontos em Usuarios/Configuracoes) | Quase tudo é token | 3–4 h |
| **9. Exportação** | Comentários "fixo de propósito" + teste manual PDF/XLSX/CSV com tema escuro | Garante que o documento continue claro | 0,5–1 h |
| **10. QA final** | Passada tela a tela nos 2 temas + mobile; adicionar os pares do §9.3 ao `verificar-contraste-grafico.js`; axe/Lighthouse | Fecha a a11y (4,5:1 texto, 3:1 componentes) | 6–8 h |

**Total:** ≈ **45–55 h** de front com a Opção A do odontograma (≈ 58–70 h com a B) + **1–2 h** de backend.
Paralelizável: etapas 4, 5, 6 e 8 após as 0–3 (áreas independentes).

### 12.2 Critérios de pronto
- Nenhum `text-white/bg-white/#hex` fora da lista de exceções intencionais (§4: gradientes de cabeçalho, botões sólidos, knobs de switch, odontograma, exportação).
- Texto ≥ 4,5:1 e componentes/ícones ≥ 3:1 nos dois temas (script estendido).
- Sem flash ao recarregar; tema por usuário persistido; troca de aba sincroniza.
- PDF/XLSX/CSV idênticos nos dois temas; odontograma com as quatro cores clínicas inalteradas.

---

## 13. Decisões que dependem do Wilson

1. **Padrão do tema** para quem nunca escolheu: *Claro* (recomendado; alinhado ao `DESIGN.md`) ou *Automático*?
2. **Odontograma:** Opção A (papel claro fixo, recomendada) ou B (escuro de verdade, exige validação clínica)?
3. **Backend:** autorizar o campo `theme` em `UserPreferences` + PATCH parcial (fora do escopo do Frontend; vai para o Backend/Maestro).
4. **Contraste do claro** (success/warning com texto branco, 2,15–2,54:1, §5.5): tratar junto nesta entrega ou separar?
5. **Telas de login**: mostrar botão de tema ou só seguir o sistema?
6. **Limpeza**: remover `App.css` (sobra do template Vite, não importado)?

---

## Apêndice A — Arquivos com cor literal (ordenados por pontos)

| Arquivo | Pontos | Tipo principal |
| :-- | --: | :-- |
| `pages/odontograma/Odontograma3D.tsx` | 92 | hex SVG (clínico, anatomia, interface) |
| `pages/pacientes/PacienteDetalheModal.tsx` | 29 | gradiente, branco, SVG |
| `pages/pacientes/PacienteDetalhePage.tsx` | 27 | gradiente, branco, SVG, `bg-bokka-ink` |
| `pages/Dashboard.tsx` | 26 | mini-calendário `bg-white`, estrela, `bg-bokka-ink` |
| `pages/dentistas/DentistaDetalheModal.tsx` | 23 | gradiente, paleta de especialidade (10 hex) |
| `pages/funcionarios/FuncionarioDetalheModal.tsx` | 20 | gradiente, paleta de grupo (12 hex) |
| `pages/financeiro/AuditoriaPage.tsx` | 37 | cartão `bg-bokka-ink`, 10 cores de categoria, tooltip |
| `components/ui/Avatar.tsx` | 13 | 6 pares cromáticos |
| `lib/exportacao.ts` | 13 | RGB/hex de documento (intencional) |
| `index.css` | 11 | `.bokka-bg-pattern`, `select option`, sombras |
| `pages/pacientes/PacienteProcedimentos.tsx` | 8 | mini-arcada SVG |
| `components/ui/Button.tsx` | 4 | `red-600/700`, `text-white` |
| `main.tsx` | 4 | sombra do toast, `'white'` ×2 |
| `components/Sidebar.tsx` | 3 | overlay e item ativo `bg-bokka-ink` |
| `pages/Usuarios.tsx` | 3 | avatar `bg-bokka-ink` |
| `components/BokkaMark.tsx` | 2 | `fill="white"` (ok) |
| `components/NotificationsPanel.tsx` | 2 | `text-white` (ok) |
| `pages/Configuracoes.tsx` | 2 | knob do switch (ok) |
| `components/ui/Modal.tsx` | 1 | overlay `bg-bokka-ink/40` |
| `pages/odontograma/OdontogramaPacientePage.tsx` | 1 | `text-white` (+3 `style` com cor clínica) |
| `pages/pacientes/PacienteAnamneses.tsx` | 1 | knob do switch (ok) |
| `pages/dentistas/DentistaForm.tsx` | 1 | `text-white` (ok) |

Sem cor literal (só tokens): `Agenda*`, `Estoque*`, `ConveniosPage`, `PendenciasPage`, `FuncionariosPage`, `FuncionarioForm`, `DentistasPage`, `Perfil`, `Login`, `Registro`, `RecuperarSenha`, `RedefinirSenha`, `ConfirmarEmail`, `AuthShell`, `Header`, `GlobalSearch`, `BottomNav`, `ProdutividadeCard`, `FluxoCaixaCard`, `GraficoProdutividade`, `GraficoFluxoCaixa`, `ui/Field`, `ui/Card`, `ui/Pagination`, `ui/EmptyState`, `ui/Skeleton`, `ui/Toast`.

## Apêndice B — Como reproduzir a contagem
Regex sobre `src/**/*.{ts,tsx,css}` para: prefixos Tailwind (`bg|text|border|ring|divide|fill|stroke|from|to|via|placeholder|outline|decoration|accent|caret|shadow`) + `white|black|slate|gray|zinc|neutral|stone` e as famílias cromáticas; `#[0-9a-f]{3,8}`; `rgba?|hsla?()`; `style={{`; `fill|stroke|stopColor=`; `bg-bokka-ink`; e conferência manual dos arquivos listados. O script de contraste foi executado com `node docs/verificar-contraste-grafico.js` (somente leitura do protótipo).
