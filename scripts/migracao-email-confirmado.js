/*
 * Migração D3: marca os usuários que ainda NÃO têm os campos novos como confirmados e ativos.
 *   users.emailConfirmado = true   (somente onde o campo está ausente/null)
 *   users.statusConta     = "ATIVO" (somente onde o campo está ausente/null)
 *
 * Nunca altera quem já tem valor: um proprietário recém-cadastrado com emailConfirmado=false continua false,
 * e um statusConta BLOQUEADO continua BLOQUEADO. Não mexe em senha, e-mail nem nos demais campos.
 * O backend já trata campo ausente como confirmado/ativo, então esta migração é para deixar o banco
 * explícito e consistente; ninguém é deslogado nem precisa trocar senha.
 *
 * Idempotente. Por padrão roda em DRY-RUN (não grava nada, só relata).
 *
 * Uso (mongosh):
 *   Dry-run : mongosh "<uri-com-banco>" --quiet --file scripts/migracao-email-confirmado.js
 *   Apply   : APPLY=true CONFIRM_DB=<nome-do-banco> mongosh "<uri-com-banco>" --quiet --file scripts/migracao-email-confirmado.js
 *   Self-test: SELFTEST=true mongosh --quiet --nodb --file scripts/migracao-email-confirmado.js
 *
 * No Docker local (Git Bash: MSYS_NO_PATHCONV=1):
 *   docker cp scripts/migracao-email-confirmado.js bokka-mongo:/tmp/migracao-email-confirmado.js
 *   docker exec bokka-mongo mongosh sistema_clinica --quiet --file /tmp/migracao-email-confirmado.js
 *   docker exec -e APPLY=true -e CONFIRM_DB=sistema_clinica bokka-mongo mongosh sistema_clinica --quiet --file /tmp/migracao-email-confirmado.js
 *
 * APPLY exige CONFIRM_DB igual ao nome do banco conectado (trava contra rodar no banco errado).
 */

const COLECAO = 'users';

function ausente(valor) {
  return valor === undefined || valor === null;
}

function planejarUsuario(doc) {
  const set = {};
  if (ausente(doc.emailConfirmado)) set.emailConfirmado = true;
  if (ausente(doc.statusConta)) set.statusConta = 'ATIVO';
  return set;
}

function selfTest() {
  const eq = (a, b, msg) => { if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`SELFTEST falhou: ${msg} esperado=${JSON.stringify(b)} obtido=${JSON.stringify(a)}`); };
  eq(planejarUsuario({}), { emailConfirmado: true, statusConta: 'ATIVO' }, 'legado sem campos');
  eq(planejarUsuario({ emailConfirmado: null, statusConta: null }), { emailConfirmado: true, statusConta: 'ATIVO' }, 'campos null');
  eq(planejarUsuario({ emailConfirmado: false, statusConta: 'ATIVO' }), {}, 'dono ainda não confirmado não muda');
  eq(planejarUsuario({ emailConfirmado: true, statusConta: 'BLOQUEADO' }), {}, 'bloqueado não muda');
  eq(planejarUsuario({ emailConfirmado: false }), { statusConta: 'ATIVO' }, 'só o status faltando');
  eq(planejarUsuario({ statusConta: 'PENDENTE_ATIVACAO' }), { emailConfirmado: true }, 'só o e-mail faltando');
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
  print(`Migração de e-mail confirmado — banco: ${dbName} — modo: ${APPLY ? 'APPLY (GRAVA)' : 'DRY-RUN (não grava)'}`);
  print('='.repeat(78));

  const col = database.getCollection(COLECAO);
  if (col.exists() === null || col.exists() === undefined) {
    print(`Coleção ${COLECAO} não existe: nada a fazer.`);
    return;
  }

  const clinicas = {};
  database.getCollection('clinicas').find({}, { codigo: 1, nome: 1 }).forEach((c) => { clinicas[c._id.toString()] = c.codigo; });

  let total = 0;
  let aAlterar = 0;
  const operacoes = [];
  const jaOk = { confirmado: 0, naoConfirmado: 0, bloqueado: 0 };

  col.find({}, { email: 1, name: 1, clinicaId: 1, emailConfirmado: 1, statusConta: 1 }).forEach((u) => {
    total += 1;
    const set = planejarUsuario(u);
    if (Object.keys(set).length === 0) {
      if (u.emailConfirmado === false) jaOk.naoConfirmado += 1; else jaOk.confirmado += 1;
      if (u.statusConta === 'BLOQUEADO') jaOk.bloqueado += 1;
      return;
    }
    aAlterar += 1;
    print(`  ~ ${u._id} ${u.email} (clínica ${clinicas[u.clinicaId] || u.clinicaId}): ${JSON.stringify(set)}`);
    operacoes.push({ updateOne: { filter: { _id: u._id }, update: { $set: set } } });
  });

  print('\n' + '='.repeat(78));
  print(`TOTAL: ${total} users | ${aAlterar} a alterar | já completos: ${total - aAlterar} (confirmados ${jaOk.confirmado}, aguardando confirmação ${jaOk.naoConfirmado}, bloqueados ${jaOk.bloqueado})`);

  if (!APPLY) {
    print('\nDRY-RUN: nada foi gravado. Para aplicar: APPLY=true CONFIRM_DB=' + dbName + ' (ver cabeçalho do script).');
    return;
  }

  if (operacoes.length > 0) {
    const res = col.bulkWrite(operacoes, { ordered: false });
    print(`\nAPPLY: ${res.modifiedCount} users atualizados (de ${operacoes.length} planejados).`);
  } else {
    print('\nAPPLY: nada a atualizar.');
  }
  print('Rode novamente em DRY-RUN: deve reportar 0 a alterar.');
}

if (typeof process !== 'undefined' && process.env.SELFTEST === 'true') {
  selfTest();
} else {
  executar();
}
