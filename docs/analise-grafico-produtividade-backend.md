# Análise: gráfico de Produtividade (backend)

Autor: agente Backend. Data: 2026-10-23. Base: leitura do código e do Mongo **local** (nada de produção).
Destinatário: Arquiteto (contrato final do endpoint de agregação).

## Atualização (decisões do Wilson e fatos novos)

**Decisões já tomadas**
- Consulta = status `REALIZADO`.
- Semana = segunda a domingo.
- Variação "justa": o período anterior é medido até o mesmo ponto do período atual.
- Exportação PDF/Excel no navegador, sem endpoint de exportação; o backend só acrescenta campos opcionais ao JSON (`titulo`, `clinicaNome`, `geradoEm`, `filtros`).
- O endpoint `GET /api/dashboard/produtividade` espera o contrato final do Arquiteto (mais métricas: faltas, novos pacientes, ticket médio; regras de permissão).

**Fuso da JVM em produção: UTC (`Etc/UTC`), confirmado pelo DevOps (sem `-Duser.timezone`/`TZ`)**
- Produção: `LocalDate` 10/09 gravado como `2026-09-10T00:00:00Z`; `LocalDateTime` 09:00 gravado como `09:00Z`.
- Local (JVM em São Paulo): 10/09 gravado como `03:00Z`; 09:00 gravado como `12:00Z`. Ambos confirmados por teste automatizado (`AgregacaoDiariaMongoLocalTest`).
- Não mudar o fuso da VPS: os horários já gravados deslocariam.

**Estratégia de agregação fechada (correta nos dois ambientes) — já implementada como componente interno, sem endpoint**
1. Agrupar sempre no fuso de gravação: `ZoneId.systemDefault()` da JVM, passado em `timezone` do `$dateToString` (`util/FusoGravacao`). Nunca fixar `America/Sao_Paulo` na agregação: sob JVM UTC, `00:30Z` viraria `21:30` do dia anterior (o teste prova o erro).
2. Calcular as fronteiras do período em Java (`LocalDate`/`LocalDateTime`) e deixar o conversor do Spring convertê-las: é o mesmo conversor da gravação, então a fronteira cai no mesmo fuso do dado.
3. Uma única agregação **por dia** (`service/AgregacaoDiariaService`: `contarAgendamentosPorDia`, `somarReceitaPorDia`), sempre com `clinicaId` no primeiro `$match`. Semana, mês, ano e "todos" são somados em Java a partir dos dias (no máximo cerca de 3.650 pontos para 10 anos).
4. O "hoje" e os limites de semana/mês/ano do negócio continuam em `America/Sao_Paulo` (`FusoBrasil.hoje()`), pois são calendário da clínica, não fuso de armazenamento.
5. Teste que simula a JVM em UTC, Etc/UTC, America/Sao_Paulo, Pacific/Kiritimati e Pacific/Pago_Pago (`TimeZone.setDefault`), com atendimentos rente à meia-noite; a suíte também foi rodada com `-Duser.timezone=UTC` no processo.

**Entregue junto (tarefas aprovadas)**
- `GET /api/agendamentos?de=AAAA-MM-DD&ate=AAAA-MM-DD`: filtro por dia, ambos inclusivos, opcionais e sem quebrar o comportamento anterior. Corrige o limite de 300 do painel.
- `ContaReceberService`: `dataPagamento` agora usa o dia de São Paulo (`FusoBrasil.hoje`), não o da JVM.
- `scripts/indices-dashboard.js`: 4 índices (agendamentos, 2 em contas_receber, pacientes; ver Atualização 2), mais o de dentista só com `INCLUIR_DENTISTA=true` (aguardando aprovação do Arquiteto). Não aplicado em lugar nenhum além de banco temporário local.

**Corrigido depois:** `ContaPagarService` (2 pontos), `AnamneseService` e `OdontogramaService` passaram a usar `FusoBrasil.hoje(relogio)`, com testes que simulam a JVM em UTC. Dados já gravados não foram alterados.

## Atualização 2: o que as 9 métricas exigem além de `AgregacaoDiariaService`

Duas famílias de campo de data, com regras de fuso diferentes:
- **Horário de parede digitado pelo usuário ou data de negócio** (`dataHoraInicio`, `dataVencimento`, `dataPagamento`): agrupar com `FusoGravacao.idMongo()` (fuso da JVM), como já está.
- **Instante gerado pelo sistema** (`createdAt`, `updatedAt`, via `LocalDateTime.now()`): como a gravação e a leitura usam o mesmo fuso da JVM, o valor gravado é o **instante real**, em qualquer ambiente. O dia de negócio é obtido agrupando com `America/Sao_Paulo` explícito, e as fronteiras do período devem ser instantes (`dia.atStartOfDay(FusoBrasil.ZONA).toInstant()`), não `LocalDateTime`. Agrupar `createdAt` pelo fuso da JVM (UTC na VPS) jogaria cadastros feitos entre 21h e 24h de São Paulo para o dia seguinte. Verificação local: `createdAt` de pacientes coincide com o instante do `_id` (ObjectId).

| Métrica | Fonte | Consulta nova | Índice |
|---|---|---|---|
| CONSULTAS_REALIZADAS, AGENDAMENTOS, FALTAS, COMPARECIMENTO, CANCELAMENTOS | `agendamentos` por `dataHoraInicio` | `contarAgendamentosPorDia` passa a devolver contagem por dia **e status** (uma consulta serve às cinco métricas; comparecimento = realizadas / (realizadas + faltas), definição do Arquiteto) | `agendamentos_clinica_data_status` (coberta) |
| NOVOS_PACIENTES | `pacientes` por `createdAt` | nova contagem por dia com fuso de São Paulo explícito e fronteiras em instantes | `pacientes_clinica_criacao` (coberta) |
| FATURAMENTO | `contas_receber` PAGO por `dataPagamento` | `somarReceitaPorDia` (pronta) | `contas_receber_clinica_status_pagamento` (coberta) |
| TICKET_MEDIO | faturamento / pacientes distintos que pagaram | agregação por bucket (dia, mês ou ano) e `pacienteId`: distintos **não somam** de dia para mês, então o total do período e cada ponto da série precisam ser calculados no nível do bucket, não somando dias. Com parcelamento, "contas pagas" conta cada parcela e distorce o ticket (um paciente que paga 12 parcelas vira 12); por isso a definição do Designer (pacientes distintos) é a mais adequada, e eu retiro a proposta anterior de dividir por contas pagas | o mesmo de FATURAMENTO (inclui `pacienteId`, coberta) |
| A_RECEBER | `contas_receber` PENDENTE/ATRASADO por `dataVencimento`, soma de `valorTotal - valorPago` | nova soma por dia (dias de vencimento, fuso da JVM); `ATRASADO` nunca é atribuído automaticamente, então vencidas continuam PENDENTE | `contas_receber_clinica_status_vencimento` (lê poucos documentos) |

Observações:
- Filtro por dentista só faz sentido nas métricas de agenda: `contas_receber` e `pacientes` não têm `dentistaId`. O índice opcional ficou `{clinicaId, dentistaId, dataHoraInicio, status}` (coberto).
- Pagamento parcial segue fora do FATURAMENTO por data (não há histórico de pagamentos); pacientes de convênio não geram conta a receber.
- Plano de execução medido com `explain` em banco temporário local (3.000 documentos, 3 clínicas): consultas de agenda, faturamento, pacientes distintos, novos pacientes e filtro por dentista saem do índice com 0 documentos lidos; só A_RECEBER busca documentos (50).

---

# Análise original

STATUS: DONE (somente analise, nenhum codigo/banco alterado; so leitura no Mongo LOCAL; producao nao tocada; sem commit).

## RESUMO
Hoje NAO existe nada no backend que sirva ao grafico: so listagens cruas. Recomendo SIM criar GET /api/dashboard/produtividade (agregacao no Mongo) + 2 indices novos. E ha um BUG ja existente que pesa na decisao (item A).

## A) ACHADO CRITICO NO DASHBOARD ATUAL
Dashboard.tsx chama GET /api/agendamentos?pagina=0&tamanho=300 e agrega tudo no navegador. O backend ordena por dataHoraInicio CRESCENTE (default do controller; o "ordem" do front nao e o "sort" do Spring) e nao tem filtro de data. Logo, depois de 300 agendamentos a tela so enxerga os 300 MAIS ANTIGOS: "Agenda de hoje", "Confirmadas", mini-calendario e Produtividade ficam errados/vazios. Uma clinica com 10 atendimentos/dia estoura isso em ~1 mes. Do lado financeiro, GET /api/contas-receber devolve TODAS as contas (sem paginacao, entidade inteira) e o "Receita mes"/"Pendente" sao somados no navegador: cresce sem limite.

## B) O QUE JA EXISTE
Agendamentos (/api/agendamentos): GET paginado (Pageable, default 20, sort dataHoraInicio; max do Spring 2000), /paciente/{id}, /dentista/{id}, /status/{status} (listas sem paginacao), por id. Campos: dataHoraInicio/Fim (LocalDateTime), status (AGENDADO, CONFIRMADO, REALIZADO, FALTOU, CANCELADO), tipoAgendamento, dentistaId. NAO ha filtro por intervalo de datas, nem contagem, nem agregacao (so a query findConflitos, que e de conflito de horario).
Financeiro (/api/contas-receber): GET todas (lista), /paciente/{id}, /status/{status}, PATCH /{id}/pagamento. Campos: valorTotal, valorPago, dataVencimento, dataPagamento (LocalDate), status (PENDENTE, PAGO, ATRASADO, CANCELADO). NAO ha filtro de data, soma, nem paginacao. ContaPagar (despesas) tem formato analogo (dataPagamento, valor) se um dia quiserem lucro.
Agregacao no Mongo: nenhuma no projeto hoje (so MongoTemplate pontual em Paciente/AuthToken), mas o padrao esta pronto para usar.
Isolamento por clinica: toda query usa AuthContextHelper.currentClinicaId(); a agregacao teria que ter clinicaId como 1o $match.

## C) O QUE FALTA
1) Endpoint de agregacao (abaixo). 2) Indices (nao existe NENHUM util). 3) Filtro de data em /api/agendamentos (de/ate) - corrige "Agenda de hoje"/mini-calendario independente do grafico (sugiro tarefa separada, pequena). 4) Opcional: GET /api/dashboard/resumo com os KPIs (agendamentos hoje, confirmadas, receita mes, pendente) - hoje sofrem do mesmo problema do item A.

## D) INDICES (verificado no Mongo LOCAL, read-only)
agendamentos, contas_receber, contas_pagar e procedimentos so tem o indice _id_. Faz sentido: o projeto nao liga auto-index-creation, entao os @Indexed sao so documentacao. Producao nao foi consultada, mas pelo mesmo motivo devo assumir o mesmo. Consequencia: HOJE toda consulta por clinicaId e COLLSCAN na colecao inteira (de todas as clinicas, por ser multi-tenant). Para o grafico isso e inaceitavel; para clinicas com anos de dados tambem pesa nas listagens atuais.
Indices propostos (script scripts/indices-dashboard.js, mongosh, idempotente, DRY-RUN padrao, APPLY exige CONFIRM_DB, SELFTEST - mesmo molde do indices-auth-tokens.js; NAO criei agora):
 1. agendamentos  { clinicaId:1, dataHoraInicio:1, status:1 }  nome agendamentos_clinica_data_status. Serve a agregacao (cobre a consulta: so le o indice, sem buscar documento), o futuro filtro de/ate e a ordenacao por data da listagem.
 2. contas_receber { clinicaId:1, status:1, dataPagamento:1, valorPago:1 }  nome contas_receber_clinica_status_pagamento. Receita = status PAGO + faixa de dataPagamento + soma de valorPago; incluir valorPago deixa a consulta coberta.
 Opcional fase 2 (so se o filtro por dentista for muito usado): { clinicaId:1, dentistaId:1, dataHoraInicio:1 }.
Risco de criar: baixo (colecoes pequenas hoje; build hibrido do Mongo 5 quase sem bloqueio), mas e escrita em producao: so com ordem do Wilson, dry-run antes, e ANTES do deploy do endpoint (mesma ordem do auth_tokens).

## E) FUSO HORARIO - ponto que precisa de atencao
O Spring Data grava LocalDateTime/LocalDate no Mongo convertendo com o fuso PADRAO DA JVM (nao com America/Sao_Paulo). Evidencia no Mongo local (JVM em SP): agendamento 09:00 local esta como 12:00Z; dataVencimento 10/09 esta como 03:00Z. Na VPS o fuso da JVM e DESCONHECIDO (nao ha -Duser.timezone no repo; argumentos ficam no systemd): se for UTC, 10/09 foi gravado como 00:00Z.
Se a agregacao fixar timezone "America/Sao_Paulo" e a JVM que gravou for UTC, os dias deslocam (00:00Z vira 21:00 do dia ANTERIOR; um LocalDate cairia sempre no dia anterior) e o grafico erra o dia e ate o mes. Proposta: agrupar com o MESMO fuso de gravacao (ZoneId.systemDefault() da JVM, passado em timezone do $dateToString), e calcular as fronteiras do periodo em Java (LocalDate/LocalDateTime) deixando o conversor do Spring fazer a mesma conversao que usou ao gravar. Assim o resultado e correto em qualquer fuso de JVM. O "hoje"/"semana"/"mes" de referencia continua em America/Sao_Paulo (FusoBrasil.hoje(), que ja existe).
ATENCAO (para DevOps/Wilson): NAO trocar o fuso da JVM/VPS em producao sem migracao - os horarios ja gravados deslocariam (agendamentos apareceriam horas diferentes). Pedir ao DevOps so a LEITURA do fuso atual da VPS (timedatectl e argumentos do bokka.service) para eu fechar este ponto.
Bug pequeno relacionado (existente): ContaReceberService.registrarPagamento/atualizar usam LocalDate.now() (fuso da JVM). Em JVM UTC, pagamento feito entre 21h e 24h de SP ganha a data do dia seguinte (no ultimo dia do mes, cai no mes seguinte) e muda a receita do periodo. Na implementacao eu trocaria por FusoBrasil.hoje().

## F) CONTRATO PROPOSTO
GET /api/dashboard/produtividade?periodo=semana|mes|ano|todos&metrica=consultas|receita&referencia=YYYY-MM-DD&dentistaId=&status=
 - periodo: obrigatorio (default mes). semana = segunda a domingo (casa com o mini-calendario); mes = mes civil; ano = ano civil; todos = do 1o registro da clinica ate hoje.
 - metrica: consultas (default) ou receita.
 - referencia: opcional, default = hoje em SP. Permite navegar para periodos passados e torna os testes deterministas.
 - dentistaId: opcional (validado como da clinica). Vale so para consultas.
 - status: opcional, so consultas, lista separada por virgula (ex.: REALIZADO,CONFIRMADO). Default depende da decisao 1 abaixo.
Resposta 200:
{
  "periodo": "mes", "metrica": "consultas", "unidade": "QUANTIDADE",   // receita => "BRL"
  "referencia": "2026-10-23",
  "atual":    { "inicio": "2026-10-01", "fim": "2026-10-31", "ateData": "2026-10-23", "total": 142 },
  "anterior": { "inicio": "2026-09-01", "fim": "2026-09-30", "ateData": "2026-09-23", "total": 120, "totalPeriodo": 131 },
  "variacaoPercentual": 18.3,
  "serie": [ { "chave": "2026-10-01", "rotulo": "01", "atual": 5, "anterior": 4 }, ... ]
}
 - Receita: total e serie em reais com 2 casas (numero JSON, arredondado no servidor).
 - Comparacao "justa": variacaoPercentual = (atual.total - anterior.total) / anterior.total, com anterior.total medido ate o MESMO ponto do periodo (23 de outubro x 23 de setembro; ate quinta desta semana x ate quinta da semana passada). anterior.totalPeriodo e o periodo anterior inteiro (para quem quiser exibir). Se referencia e um periodo ja encerrado, ateData = fim e a comparacao e completa.
 - variacaoPercentual = null quando anterior.total = 0 (nao existe %; o front mostra "novo"/"-") e quando periodo=todos (nao tem periodo anterior; "anterior" vem null).
 - serie: semana = 7 pontos (seg..dom, rotulo "Seg"...); mes = 1 ponto por dia (28-31); ano = 12 pontos (Jan..Dez); todos = 1 ponto por ano (do primeiro ano com dado ate o atual). Buckets sem registro vem com 0 (o servidor preenche os buracos); buckets futuros vem com "atual": null para a area parar no hoje; "anterior" alinhado por posicao (dia do mes / mes / dia da semana).
 - Erros: 400 pt-BR com a chave do campo (periodo/metrica/referencia/status invalidos, no formato mensagem-por-campo ja usado); 403 sem permissao; 404 se dentistaId nao e da clinica.
 - Permissao NO SERVIDOR: hoje o backend nao checa permissao por endpoint (SecurityConfig = anyRequest().authenticated(), sem @PreAuthorize): quem esconde o financeiro e so o front, e /api/contas-receber responde a qualquer perfil logado da clinica. O endpoint novo deve exigir AGENDAMENTOS para metrica=consultas e AUDITORIA_FINANCEIRA para metrica=receita (403 caso contrario). Registro: e uma lacuna geral (outros endpoints), fora do escopo desta analise.
Duas chamadas (consultas e receita) em paralelo bastam; nao vejo necessidade de uma chamada combinada.

## G) COMO AGREGAR (sem implementar)
Consultas: $match { clinicaId, dataHoraInicio: [inicio, fim), status: $in [...], (dentistaId) } -> $group por $dateToString (%Y-%m-%d para semana/mes, %Y-%m para ano, %Y para todos; timezone = fuso de gravacao) com $sum 1. Receita: $match { clinicaId, status: "PAGO", dataPagamento: [inicio, fim) } -> $group igual com $sum valorPago. UMA consulta por metrica cobrindo periodo atual + anterior (faixa unica), separada em Java; zeros e rotulos montados em Java (classe pura de calculo de periodos, testavel com Clock fixo). Mongo >= 5 do projeto: $dateToString basta, sem depender de $dateTrunc.
Dinheiro e Double no modelo todo; somar poucos milhares de valores com 2 casas e arredondar com BigDecimal no fim e seguro para exibicao.

## H) CUSTO/RISCO DE PERFORMANCE
Com os indices: a resposta tem no maximo ~31 pontos (ou N anos), a leitura e so de chaves de indice. Ordem de grandeza (estimativa, nao medida em producao): semana/mes/ano = alguns milhares de chaves = poucos ms; "todos" para clinica com 10 anos x ~30 mil agendamentos e ~36 mil contas pagas = dezenas de ms, consulta coberta (sem buscar documentos). Memoria irrelevante, payload minusculo. Cache nao e necessario na v1 (se quiserem: 30-60 s por clinica+periodo+metrica em memoria).
SEM os indices: COLLSCAN em todas as clinicas a cada abertura do painel -> risco ALTO, cresce com a base inteira (nao so a da clinica). Por isso indice e pre-requisito, nao otimizacao.
Escrita: 2 indices a mais = custo marginal em insert/update de agendamento e conta (desprezivel para o volume).

## I) LIMITES DO DADO (explicar na tela)
 - Receita = regime de caixa, igual ao "Receita mes" de hoje: conta PAGO no dia de dataPagamento. Pagamento PARCIAL nao aparece por data: registrarPagamento soma valorPago mas so grava dataPagamento quando quita; nao existe historico de pagamentos. Mostrar receita parcial por data exigiria uma colecao de pagamentos (mudanca maior, nao proposta).
 - Pacientes de CONVENIO nao geram ContaReceber automatica (ProcedimentoService), entao receita de convenio nao entra no grafico.
 - ATRASADO nunca e atribuido automaticamente (nao ha rotina), irrelevante para receita mas o front trata "PARCIAL", status que nao existe no enum.
 - Hoje o grafico conta TODOS os status, incluindo CANCELADO e FALTOU.

## J) DECISOES PARA O WILSON/MAESTRO
 1. Qual status conta como "consulta" na produtividade? Recomendo REALIZADO (e, no futuro, parametro para ampliar). Alternativa: tudo exceto CANCELADO/FALTOU (AGENDADO+CONFIRMADO+REALIZADO), que mostra agenda futura mas mistura previsto com feito. Se a clinica nao costuma marcar REALIZADO, o grafico fica vazio com a recomendacao.
 2. Semana = segunda a domingo (proposto) ou ultimos 7 dias?
 3. OK a variacao "justa" (ate o mesmo ponto do periodo anterior)?
 4. Autorizar tarefa separada para filtro de/ate em /api/agendamentos + (opcional) /api/dashboard/resumo, que corrige o bug do item A.
 5. DevOps: ler o fuso da JVM da VPS (somente leitura).

## K) ESCOPO DA IMPLEMENTACAO (quando aprovado)
Controller + service + calculo de periodos + 2 agregacoes via MongoTemplate + DTOs + checagem de permissao; script scripts/indices-dashboard.js (dry-run, mesmo molde); testes: unitarios do calculo de periodos (virada de mes/ano, ano bissexto, semana cruzando meses, referencia passada), integracao em Mongo LOCAL temporario (isolamento entre 2 clinicas, buraco preenchido com zero, fuso: dados gravados com JVM UTC e SP dao o mesmo dia, 403 por permissao), suite completa. Estimativa: ~1 dia. Ordem de deploy: indices (dry-run -> apply com ordem do Wilson) ANTES do backend; depois frontend.

## L) EXPORTACAO PDF + EXCEL (complemento do Wilson)
L1) O que existe hoje: SO o CSV do Dashboard.tsx (exportProdutividadeCSV, linhas 170-187): monta o texto no navegador, separador ";", BOM UTF-8, arquivo bokka-produtividade-AAAA.csv. Sem escape de aspas/valores e numero decimal com ponto (no Excel pt-BR, valor de receita com ponto vira texto/data). Em TODO o resto do projeto nao ha exportacao: nenhum codigo de PDF/Excel/CSV no backend (nenhum Content-Disposition/attachment), nenhuma tela com window.print, nenhuma lib no front.
L2) Bibliotecas ja presentes: NENHUMA para PDF ou Excel.
 - Backend (pom.xml): so mongodb, validation, webmvc, security, java-jwt, springdoc. Sem Apache POI/OpenPDF/PDFBox. O BOM do Spring Boot nao gerencia POI/OpenPDF: versoes teriam que ser fixadas por nos.
 - Frontend (package.json): recharts, date-fns, axios, lucide... Sem jspdf/xlsx/exceljs/html2canvas.
L3) Frontend ou backend? Recomendo FRONTEND na v1, usando o MESMO JSON do endpoint de agregacao (zero endpoint de exportacao novo):
 + O arquivo e identico ao que esta na tela (mesma fonte, mesmo periodo/metrica/filtros), sem segunda consulta nem divergencia de numeros.
 + Os dados exportados sao agregados (<= 31 linhas, sem dado pessoal de paciente): nao ha risco LGPD em gerar no navegador; a permissao ja foi cobrada no endpoint que entregou o JSON.
 + Nenhuma dependencia nova no backend (jar maior, CVEs, memoria na VPS), nenhuma carga de CPU de PDF no servidor, sem fluxo de download autenticado (o JWT vai em header; link direto nao funciona).
 + O PDF pode incluir o grafico: desenhar a area em vetor no proprio PDF (poucas linhas, nitido, fundo branco para imprimir) - evita capturar o SVG escuro do dashboard.
 - Contras: formatacao depende do navegador/lib; se um dia o relatorio precisar de DETALHE (lista de consultas/recebimentos com nome de paciente) ou de volume grande, nao serve.
 Backend passa a ser a escolha SE o Wilson quiser relatorio detalhado (nomes, centenas/milhares de linhas, auditoria de quem exportou) - ai e outro endpoint e outro escopo (v2 abaixo).
L4) Dependencias e custo (ordem de grandeza; medir com o analisador do Vite e rodar npm audit/mvn dependency:tree na instalacao):
 Frontend (carregadas por import() dinamico no clique em Exportar, ficam FORA do bundle inicial):
  - PDF: jspdf (MIT; ~130-150 KB gzip) + opcional jspdf-autotable (~30 KB gzip) para a tabela; fonte padrao Helvetica cobre acentos do portugues e "R$". Alternativa maior: pdfmake (~1 MB+ com fontes) - nao recomendo.
  - Excel: write-excel-file (MIT, dezenas de KB; gera .xlsx real com cabecalho, tipos e formato monetario) - minha escolha; exceljs (MIT, ~250 KB gzip + jszip, mais recursos que nao precisamos). Evitar o pacote "xlsx" do npm (SheetJS): versao publicada no npm esta parada em 0.18.5 com CVEs conhecidas (prototype pollution, ReDoS); versoes corrigidas so via CDN do fornecedor.
  - Custo real: 2 pacotes npm (+ transitivas) a manter/atualizar, ~200 KB extras baixados so quando alguem clica em Exportar. Sem custo de licenca (MIT).
 Backend (so se v2):
  - Excel: Apache POI SXSSF (Apache-2.0, padrao do mercado, +10-15 MB no jar com poi-ooxml, xmlbeans, commons-compress, log4j-api; usar SXSSF streaming, nunca XSSF em memoria) OU FastExcel/dhatim (Apache-2.0, ~0,2 MB, so escrita, suficiente para tabela simples; minha escolha se for preciso).
  - PDF: OpenPDF (LGPL/MPL, ~1,5 MB, API simples, Helvetica com acentos) OU Apache PDFBox (Apache-2.0, ~3,5 MB, mais baixo nivel = mais codigo). NAO usar iText 5+/7/8 (AGPL/comercial). Grafico no PDF teria que ser desenhado em vetor (ou JFreeChart, +2 MB) - mais trabalho que no front.
  - Custo: jar maior no upload por SFTP, mais superficie de CVE (POI/commons-compress ja tiveram), PDF e CPU-bound (precisa limite de taxa), testes de arquivo gerado.
L5) Contrato proposto
 v1 (recomendado) - NENHUM endpoint novo; o GET /api/dashboard/produtividade (item F) ganha campos opcionais para o arquivo se descrever sem chamadas extras:
   "titulo": "Consultas realizadas - outubro de 2026",   // montado no servidor (pt-BR)
   "clinicaNome": "Clinica Tainah",
   "geradoEm": "2026-10-23T14:05:00-03:00",
   "filtros": { "dentistaId": null, "dentistaNome": null, "status": ["REALIZADO"] }
  O front gera: CSV (corrigido: decimal com virgula, aspas, BOM), XLSX e PDF a partir de serie/atual/anterior/variacaoPercentual. Linhas do arquivo: Periodo | Atual | Anterior | (Variacao no rodape); cabecalho com titulo, clinica, periodo (inicio-fim), metrica, filtros, geradoEm; rodape com total atual, total anterior (mesmo trecho) e variacao. Nome sugerido: bokka-produtividade-{metrica}-{periodo}-{AAAA-MM-DD}.{csv|xlsx|pdf}. Buckets futuros ("atual": null) saem em branco.
 v2 (so se aprovarem relatorio detalhado/auditado) - backend:
   GET /api/dashboard/produtividade/exportar?formato=xlsx|pdf|csv&periodo=&metrica=&referencia=&dentistaId=&status=   (mesmos parametros do endpoint de agregacao + formato)
   200 com Content-Type (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet | application/pdf | text/csv;charset=UTF-8), Content-Disposition: attachment; filename="bokka-produtividade-receita-mes-2026-10.xlsx" (sem dado pessoal no nome), Cache-Control: no-store. Mesmo 400/403/404 do endpoint de agregacao; permissao por metrica (AGENDAMENTOS / AUDITORIA_FINANCEIRA) cobrada no servidor; limite de taxa por usuario (ex.: 10 exportacoes/min, reaproveitando o RateLimitService, 429 pt-BR); log de auditoria (usuario, clinica, metrica, periodo, formato - sem conteudo). O front baixa via axios responseType=blob (header Authorization) e dispara o download. Detalhe por linha (paciente/dentista/valor) seria um contrato adicional, com paginacao/limite maximo de linhas (ex.: 50 mil) e LGPD revisada.
L6) Decisoes extras para o Wilson/Maestro:
 6. Exportar so o que esta no grafico (serie agregada) ou tambem relatorio DETALHADO (lista de consultas/recebimentos)? Serie agregada => v1 no front, sem dependencia nova no backend. Detalhado => v2 no backend.
 7. A escolha das libs do front (jspdf + write-excel-file, import dinamico) e do agente Frontend; eu so entrego os campos extras do JSON. Se for v2, a escolha FastExcel/OpenPDF (ou POI/PDFBox) fica para aprovacao antes de eu mexer no pom.xml.
 Impacto no escopo K: v1 soma so os campos extras no JSON (+ ~2 h); v2 soma ~1-1,5 dia (dependencias, geracao, limite de taxa, testes de arquivo gerado).

Arquivos alterados: nenhum. Documento desta analise: C:\Users\Wilson\AppData\Local\Temp\claude\C--Users-Wilson-Desktop-CODE-sistema-tainah--maestri-roles-f08b8cbf-ec86-4c06-9a30-d2a7702004e1\c35c7e29-b40e-461e-b3db-5648da70950f\scratchpad\analise_produtividade.txt
Testes: nao se aplica (sem codigo alterado).
