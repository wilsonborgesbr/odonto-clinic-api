/*
 * Índices que sustentam o painel: agenda por período (filtro de/ate) e gráfico de produtividade (9 métricas).
 * Contrato: docs/contrato-grafico-produtividade.md, seção 5.2 (5 índices obrigatórios).
 *
 * O projeto NÃO cria índices automaticamente (auto-index-creation desligado), então estes índices só existem
 * se este script for executado. Sem eles, toda consulta por clinicaId varre a coleção inteira (de todas as clínicas).
 *   - agendamentos_clinica_data_status                  : agendamentos { clinicaId, dataHoraInicio, status }
 *       filtro de/ate de GET /api/agendamentos e contagem por dia/status (consulta coberta):
 *       CONSULTAS_REALIZADAS, AGENDAMENTOS, FALTAS, COMPARECIMENTO e CANCELAMENTOS
 *   - agendamentos_clinica_dentista_data_status         : agendamentos { clinicaId, dentistaId, dataHoraInicio, status }
 *       as mesmas 5 métricas com o filtro dentistaId (consulta coberta)
 *   - contas_receber_clinica_status_pagamento_paciente  : contas_receber { clinicaId, status, dataPagamento, pacienteId, valorPago }
 *       FATURAMENTO e TICKET_MEDIO (pacientes distintos que pagaram): PAGO + faixa de dataPagamento
 *   - contas_receber_clinica_status_vencimento          : contas_receber { clinicaId, status, dataVencimento, valorTotal, valorPago }
 *       A_RECEBER: PENDENTE/ATRASADO por faixa de dataVencimento, saldo = valorTotal - valorPago
 *   - pacientes_clinica_created_at                      : pacientes { clinicaId, createdAt }
 *       NOVOS_PACIENTES por createdAt (também serve ao filtro "cadastrados desde" da listagem de pacientes)
 *
 * Idempotente. Por padrão roda em DRY-RUN (não grava nada, só informa o que faria).
 * Índice existente com o mesmo nome e a mesma chave é mantido; com chave/opções diferentes é reportado como
 * CONFLITO e NÃO é alterado (resolver manualmente). Chave equivalente com outro nome também é mantida.
 *
 * Uso (mongosh):
 *   Dry-run : mongosh "<uri-com-banco>" --quiet --file scripts/indices-dashboard.js
 *   Apply   : APPLY=true CONFIRM_DB=<nome-do-banco> mongosh "<uri-com-banco>" --quiet --file scripts/indices-dashboard.js
 *   Self-test da lógica de comparação: SELFTEST=true mongosh --quiet --nodb --file scripts/indices-dashboard.js
 *
 * No Docker local (Git Bash: MSYS_NO_PATHCONV=1):
 *   docker cp scripts/indices-dashboard.js bokka-mongo:/tmp/indices-dashboard.js
 *   docker exec bokka-mongo mongosh sistema_clinica --quiet --file /tmp/indices-dashboard.js
 *   docker exec -e APPLY=true -e CONFIRM_DB=sistema_clinica bokka-mongo mongosh sistema_clinica --quiet --file /tmp/indices-dashboard.js
 *
 * APPLY exige CONFIRM_DB igual ao nome do banco conectado (trava contra rodar no banco errado).
 * Em produção: rodar o dry-run primeiro e aplicar só com ordem do Wilson, ANTES de publicar o backend que usa os índices.
 * Criar índices em coleção vazia ou inexistente é seguro; a coleção é criada pelo createIndex se não existir.
 * Os índices são criados em segundo plano pelo build híbrido do MongoDB 5 (não bloqueia leituras/escritas por muito tempo);
 * prefira horário de pouco movimento em coleções grandes.
 */

const INDICES_PAINEL = [
  { colecao: 'agendamentos', nome: 'agendamentos_clinica_data_status', key: { clinicaId: 1, dataHoraInicio: 1, status: 1 }, opcoes: {} },
  { colecao: 'agendamentos', nome: 'agendamentos_clinica_dentista_data_status', key: { clinicaId: 1, dentistaId: 1, dataHoraInicio: 1, status: 1 }, opcoes: {} },
  { colecao: 'contas_receber', nome: 'contas_receber_clinica_status_pagamento_paciente', key: { clinicaId: 1, status: 1, dataPagamento: 1, pacienteId: 1, valorPago: 1 }, opcoes: {} },
  { colecao: 'contas_receber', nome: 'contas_receber_clinica_status_vencimento', key: { clinicaId: 1, status: 1, dataVencimento: 1, valorTotal: 1, valorPago: 1 }, opcoes: {} },
  { colecao: 'pacientes', nome: 'pacientes_clinica_created_at', key: { clinicaId: 1, createdAt: 1 }, opcoes: {} },
];

// Compara só as opções que importam para o comportamento do índice.
function opcoesIguais(existente, desejado) {
  return (existente.unique === true) === (desejado.opcoes.unique === true)
    && existente.expireAfterSeconds === desejado.opcoes.expireAfterSeconds
    && !existente.partialFilterExpression
    && !existente.sparse;
}

function chaveIgual(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

// Para cada índice desejado de UMA coleção devolve { acao: 'CRIAR' | 'MANTER' | 'CONFLITO', motivo? }.
function planejar(existentes, desejadosDaColecao) {
  return desejadosDaColecao.map((d) => {
    const porNome = existentes.find((e) => e.name === d.nome);
    const porChave = existentes.find((e) => chaveIgual(e.key, d.key));
    if (porNome) {
      if (chaveIgual(porNome.key, d.key) && opcoesIguais(porNome, d)) return { indice: d, acao: 'MANTER' };
      return { indice: d, acao: 'CONFLITO', motivo: `já existe "${d.nome}" com chave/opções diferentes: ${JSON.stringify(porNome)}` };
    }
    if (porChave) {
      if (opcoesIguais(porChave, d)) return { indice: d, acao: 'MANTER', motivo: `equivalente já existe como "${porChave.name}"` };
      return { indice: d, acao: 'CONFLITO', motivo: `a chave ${JSON.stringify(d.key)} já tem o índice "${porChave.name}" com opções diferentes: ${JSON.stringify(porChave)}` };
    }
    return { indice: d, acao: 'CRIAR' };
  });
}

function selfTest() {
  const eq = (a, b, msg) => { if (a !== b) throw new Error(`SELFTEST falhou: ${msg} esperado=${b} obtido=${a}`); };
  const acoes = (existentes, lista) => planejar(existentes, lista).map((p) => p.acao).join(',');
  const da = (colecao) => INDICES_PAINEL.filter((d) => d.colecao === colecao);
  const ag = da('agendamentos');
  const cr = da('contas_receber');
  const pac = da('pacientes');
  const existente = (d) => ({ name: d.nome, key: d.key });

  eq(INDICES_PAINEL.length, 5, '5 índices do contrato');
  eq(ag.length + cr.length + pac.length, 5, 'só agendamentos, contas_receber e pacientes');
  eq(new Set(INDICES_PAINEL.map((d) => d.nome)).size, 5, 'nomes únicos');
  eq(acoes([{ name: '_id_', key: { _id: 1 } }], ag), 'CRIAR,CRIAR', 'agendamentos só com _id');
  eq(acoes([], cr), 'CRIAR,CRIAR', 'coleção inexistente');
  eq(acoes([], pac), 'CRIAR', 'pacientes sem índice');
  eq(acoes(ag.map(existente), ag), 'MANTER,MANTER', 'idempotente (agendamentos)');
  eq(acoes(cr.map(existente), cr), 'MANTER,MANTER', 'idempotente (contas)');
  eq(acoes(pac.map(existente), pac), 'MANTER', 'idempotente (pacientes)');
  eq(acoes([{ name: 'ag_manual', key: { clinicaId: 1, dataHoraInicio: 1, status: 1 } }], ag), 'MANTER,CRIAR', 'equivalente com outro nome');
  eq(acoes([{ name: 'agendamentos_clinica_data_status', key: { clinicaId: 1, dataHoraInicio: 1 } }], ag), 'CONFLITO,CRIAR', 'mesmo nome, chave diferente');
  eq(acoes([{ name: 'agendamentos_clinica_data_status', key: { clinicaId: 1, dataHoraInicio: 1, status: 1 }, unique: true }], ag), 'CONFLITO,CRIAR', 'mesmo nome, unique');
  eq(acoes([{ name: 'x', key: { clinicaId: 1, dataHoraInicio: 1, status: 1 }, partialFilterExpression: { ativo: true } }], ag), 'CONFLITO,CRIAR', 'mesma chave com filtro parcial');
  eq(acoes([{ name: 'agendamentos_clinica_data_status', key: { status: 1, clinicaId: 1, dataHoraInicio: 1 } }], ag), 'CONFLITO,CRIAR', 'ordem das chaves importa');
  eq(acoes([{ name: 'contas_receber_clinica_status_pagamento_paciente', key: { clinicaId: 1, status: 1, dataPagamento: 1, valorPago: 1 } }], cr),
    'CONFLITO,CRIAR', 'versão sem pacienteId é conflito e não é alterada');
  eq(acoes([{ name: 'pacientes_clinica_created_at', key: { clinicaId: 1, createdAt: -1 } }], pac), 'CONFLITO', 'pacientes: direção diferente');
  print('SELFTEST OK');
}

function executar() {
  const APPLY = process.env.APPLY === 'true';
  const database = db.getSiblingDB(db.getName());
  const dbName = database.getName();

  if (APPLY && process.env.CONFIRM_DB !== dbName) {
    throw new Error(`APPLY exige CONFIRM_DB=${dbName} (banco conectado). Abortado sem alterar nada.`);
  }

  print('='.repeat(78));
  print(`Índices do painel — banco: ${dbName} — modo: ${APPLY ? 'APPLY (GRAVA)' : 'DRY-RUN (não grava)'}`);
  print('='.repeat(78));

  const colecoes = INDICES_PAINEL.map((d) => d.colecao).filter((c, i, arr) => arr.indexOf(c) === i);
  let totalCriar = 0;
  let totalManter = 0;
  const conflitos = [];
  const criacoes = [];

  colecoes.forEach((nomeColecao) => {
    const col = database.getCollection(nomeColecao);
    const existe = col.exists() !== null && col.exists() !== undefined;
    const existentes = existe ? col.getIndexes() : [];
    print(`\nColeção ${nomeColecao}: ${existe ? `existe (${col.countDocuments({})} docs)` : 'não existe (será criada pelo createIndex)'}`);

    planejar(existentes, INDICES_PAINEL.filter((d) => d.colecao === nomeColecao)).forEach((p) => {
      const desc = `${p.indice.nome} ${JSON.stringify(p.indice.key)}`;
      if (p.acao === 'CRIAR') {
        print(`  ${APPLY ? 'criando' : 'seria criado'}: ${desc}`);
        totalCriar += 1;
        criacoes.push({ col, indice: p.indice });
      } else if (p.acao === 'MANTER') {
        print(`  já existe (mantido): ${desc}${p.motivo ? ' — ' + p.motivo : ''}`);
        totalManter += 1;
      } else {
        print(`  ! CONFLITO (não alterado): ${desc} — ${p.motivo}`);
        conflitos.push(p);
      }
    });
  });

  print(`\nResumo: ${totalCriar} a criar | ${totalManter} já existentes | ${conflitos.length} conflitos`);

  if (!APPLY) {
    print('\nDRY-RUN: nada foi gravado. Para aplicar: APPLY=true CONFIRM_DB=' + dbName + ' (ver cabeçalho do script).');
    return;
  }

  criacoes.forEach((c) => {
    c.col.createIndex(c.indice.key, Object.assign({ name: c.indice.nome }, c.indice.opcoes));
    print(`  criado ${c.indice.colecao}.${c.indice.nome}`);
  });
  print('\nAPPLY concluído. Rode novamente em DRY-RUN: deve reportar 0 a criar.');
  if (conflitos.length > 0) print(`ATENÇÃO: ${conflitos.length} conflito(s) não foram resolvidos; revisar manualmente.`);
}

if (typeof process !== 'undefined' && process.env.SELFTEST === 'true') {
  selfTest();
} else {
  executar();
}
