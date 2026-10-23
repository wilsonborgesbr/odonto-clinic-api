/*
 * Índices da coleção auth_tokens (tokens de reset de senha, confirmação de e-mail e ativação de conta).
 *
 * O projeto NÃO cria índices automaticamente (auto-index-creation desligado), então estes dois índices
 * só existem se este script for executado:
 *   - tokenHash_unique : único em tokenHash (um hash nunca se repete; a busca do token usa este índice)
 *   - expiraEm_ttl     : TTL em expiraEm com expireAfterSeconds = 0 (o MongoDB apaga o token logo após expirar)
 *
 * Idempotente. Por padrão roda em DRY-RUN (não grava nada, só informa o que faria).
 * Índice existente com o mesmo nome e as mesmas opções é mantido; com opções diferentes é reportado como
 * CONFLITO e NÃO é alterado (resolver manualmente).
 *
 * Uso (mongosh):
 *   Dry-run : mongosh "<uri-com-banco>" --quiet --file scripts/indices-auth-tokens.js
 *   Apply   : APPLY=true CONFIRM_DB=<nome-do-banco> mongosh "<uri-com-banco>" --quiet --file scripts/indices-auth-tokens.js
 *   Self-test da lógica de comparação: SELFTEST=true mongosh --quiet --nodb --file scripts/indices-auth-tokens.js
 *
 * No Docker local (Git Bash: MSYS_NO_PATHCONV=1):
 *   docker cp scripts/indices-auth-tokens.js bokka-mongo:/tmp/indices-auth-tokens.js
 *   docker exec bokka-mongo mongosh sistema_clinica --quiet --file /tmp/indices-auth-tokens.js
 *   docker exec -e APPLY=true -e CONFIRM_DB=sistema_clinica bokka-mongo mongosh sistema_clinica --quiet --file /tmp/indices-auth-tokens.js
 *
 * APPLY exige CONFIRM_DB igual ao nome do banco conectado (trava contra rodar no banco errado).
 * Criar índices em coleção vazia ou inexistente é seguro; a coleção é criada pelo createIndex se não existir.
 */

const COLECAO = 'auth_tokens';

const DESEJADOS = [
  { nome: 'tokenHash_unique', key: { tokenHash: 1 }, opcoes: { unique: true } },
  { nome: 'expiraEm_ttl', key: { expiraEm: 1 }, opcoes: { expireAfterSeconds: 0 } },
];

// Compara só as opções que importam para o comportamento do índice.
function opcoesIguais(existente, desejado) {
  return (existente.unique === true) === (desejado.opcoes.unique === true)
    && existente.expireAfterSeconds === desejado.opcoes.expireAfterSeconds;
}

function chaveIgual(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

// Para cada índice desejado devolve { acao: 'CRIAR' | 'MANTER' | 'CONFLITO', motivo? }.
function planejar(existentes, desejados) {
  return desejados.map((d) => {
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
  const acoes = (ex) => planejar(ex, DESEJADOS).map((p) => p.acao).join(',');

  eq(acoes([{ name: '_id_', key: { _id: 1 } }]), 'CRIAR,CRIAR', 'coleção só com _id');
  eq(acoes([]), 'CRIAR,CRIAR', 'coleção inexistente');
  eq(acoes([
    { name: 'tokenHash_unique', key: { tokenHash: 1 }, unique: true },
    { name: 'expiraEm_ttl', key: { expiraEm: 1 }, expireAfterSeconds: 0 },
  ]), 'MANTER,MANTER', 'idempotente');
  eq(acoes([{ name: 'tokenHash_unique', key: { tokenHash: 1 } }]), 'CONFLITO,CRIAR', 'mesmo nome sem unique');
  eq(acoes([{ name: 'tokenHash_1', key: { tokenHash: 1 }, unique: true }]), 'MANTER,CRIAR', 'equivalente com outro nome');
  eq(acoes([{ name: 'expiraEm_1', key: { expiraEm: 1 } }]), 'CRIAR,CONFLITO', 'índice comum em expiraEm bloqueia o TTL');
  eq(acoes([{ name: 'expiraEm_ttl', key: { expiraEm: 1 }, expireAfterSeconds: 60 }]), 'CRIAR,CONFLITO', 'TTL com outro valor');
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
  print(`Índices de ${COLECAO} — banco: ${dbName} — modo: ${APPLY ? 'APPLY (GRAVA)' : 'DRY-RUN (não grava)'}`);
  print('='.repeat(78));

  const col = database.getCollection(COLECAO);
  const existe = col.exists() !== null && col.exists() !== undefined;
  const existentes = existe ? col.getIndexes() : [];
  print(`Coleção ${COLECAO}: ${existe ? `existe (${col.countDocuments({})} docs)` : 'não existe (será criada pelo createIndex)'}`);

  const plano = planejar(existentes, DESEJADOS);
  plano.forEach((p) => {
    const desc = `${p.indice.nome} ${JSON.stringify(p.indice.key)} ${JSON.stringify(p.indice.opcoes)}`;
    if (p.acao === 'CRIAR') print(`  ${APPLY ? 'criando' : 'seria criado'}: ${desc}`);
    else if (p.acao === 'MANTER') print(`  já existe (mantido): ${desc}${p.motivo ? ' — ' + p.motivo : ''}`);
    else print(`  ! CONFLITO (não alterado): ${desc} — ${p.motivo}`);
  });

  const aCriar = plano.filter((p) => p.acao === 'CRIAR');
  const conflitos = plano.filter((p) => p.acao === 'CONFLITO');
  print(`\nResumo: ${aCriar.length} a criar | ${plano.filter((p) => p.acao === 'MANTER').length} já existentes | ${conflitos.length} conflitos`);

  if (!APPLY) {
    print('\nDRY-RUN: nada foi gravado. Para aplicar: APPLY=true CONFIRM_DB=' + dbName + ' (ver cabeçalho do script).');
    return;
  }

  aCriar.forEach((p) => {
    col.createIndex(p.indice.key, Object.assign({ name: p.indice.nome }, p.indice.opcoes));
    print(`  criado ${COLECAO}.${p.indice.nome}`);
  });
  print('\nAPPLY concluído. Rode novamente em DRY-RUN: deve reportar 0 a criar.');
  if (conflitos.length > 0) print(`ATENÇÃO: ${conflitos.length} conflito(s) não foram resolvidos; revisar manualmente.`);
}

if (typeof process !== 'undefined' && process.env.SELFTEST === 'true') {
  selfTest();
} else {
  executar();
}
