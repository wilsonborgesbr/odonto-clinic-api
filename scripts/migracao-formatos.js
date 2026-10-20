/*
 * Migração de formatos canônicos (CPF, CNPJ, CRO, telefone, e-mail, CEP; users.email em minúsculo) + índices únicos por clínica.
 *
 * Idempotente. Por padrão roda em DRY-RUN (não grava nada, só gera o relatório).
 *
 * Uso (mongosh):
 *   Dry-run : mongosh "<uri-com-banco>" --quiet --file scripts/migracao-formatos.js
 *   Apply   : APPLY=true CONFIRM_DB=<nome-do-banco> mongosh "<uri-com-banco>" --quiet --file scripts/migracao-formatos.js
 *   Self-test das funções de normalização: SELFTEST=true mongosh --quiet --nodb --file scripts/migracao-formatos.js
 *
 * No Docker local (Git Bash: MSYS_NO_PATHCONV=1):
 *   docker cp scripts/migracao-formatos.js bokka-mongo:/tmp/migracao-formatos.js
 *   docker exec bokka-mongo mongosh sistema_clinica --quiet --file /tmp/migracao-formatos.js
 *   docker exec -e APPLY=true -e CONFIRM_DB=sistema_clinica bokka-mongo mongosh sistema_clinica --quiet --file /tmp/migracao-formatos.js
 *
 * APPLY exige CONFIRM_DB igual ao nome do banco conectado (trava contra rodar no banco errado).
 *
 * users: só o e-mail é normalizado (trim + minúsculo). Se dois usuários da MESMA clínica colidirem após minúsculo
 * (ex.: Ana@x.com e ana@x.com), nenhum dos dois é alterado e o grupo é listado como DUPLICATA. O índice
 * email_clinica_unique não é criado nem dropado por este script (só informado).
 * telefone de convenios e clinicas aceita também 0800/0300 (formato '0800 123 4567').
 *
 * Ordem do APPLY: (1) cria os índices compostos parciais; (2) dropa os índices únicos globais antigos;
 * (3) grava os valores normalizados. Valores inválidos e grupos duplicados dentro da mesma clínica NÃO são
 * alterados: ficam como estão e aparecem no relatório para correção manual.
 */

// ───────────────────────── normalizadores (espelho de com.example.demo.util.FormatadorTexto) ─────────────────────────

const UFS = new Set(['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA',
  'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']);

const vazio = (v) => v === null || v === undefined || (typeof v === 'string' && v.trim() === '');
const somenteDigitos = (s) => s.replace(/\D/g, '');
const todosIguais = (s) => new Set(s.split('')).size === 1;

function cpfValido(d) {
  if (!/^\d{11}$/.test(d) || todosIguais(d)) return false;
  const dig = (n) => {
    let soma = 0;
    for (let i = 0; i < n; i++) soma += (d.charCodeAt(i) - 48) * (n + 1 - i);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };
  return dig(9) === d.charCodeAt(9) - 48 && dig(10) === d.charCodeAt(10) - 48;
}

function cnpjValido(c) {
  if (!/^[0-9A-Z]{12}\d{2}$/.test(c) || todosIguais(c)) return false;
  const dig = (pesos) => {
    let soma = 0;
    for (let i = 0; i < pesos.length; i++) soma += (c.charCodeAt(i) - 48) * pesos[i];
    const resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  };
  return dig([5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]) === c.charCodeAt(12) - 48
    && dig([6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]) === c.charCodeAt(13) - 48;
}

function cpf(valor) {
  if (vazio(valor)) return null;
  const texto = String(valor).trim();
  const d = somenteDigitos(texto);
  if (!/^[\d.\-\s]+$/.test(texto) || !cpfValido(d)) throw new Error('CPF inválido (dígito verificador/tamanho)');
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

function cnpj(valor) {
  if (vazio(valor)) return null;
  const texto = String(valor).trim();
  if (!/^[0-9A-Za-z.\-\/\s]+$/.test(texto)) throw new Error('CNPJ inválido (caracteres)');
  const c = texto.replace(/[.\-\/\s]/g, '').toUpperCase();
  if (!cnpjValido(c)) throw new Error('CNPJ inválido (dígito verificador/tamanho)');
  return `${c.slice(0, 2)}.${c.slice(2, 5)}.${c.slice(5, 8)}/${c.slice(8, 12)}-${c.slice(12)}`;
}

function cro(valor) {
  if (vazio(valor)) return null;
  const m = /^CRO\s*[-\/]?\s*([A-Z]{2})\s*[-\/]?\s*(\d{4,6})(?:\s*\/\s*(\d{4}))?$/.exec(String(valor).trim().toUpperCase());
  if (!m || !UFS.has(m[1])) throw new Error('CRO inválido (formato/UF)');
  return `CRO-${m[1]} ${m[2]}${m[3] ? '/' + m[3] : ''}`;
}

function telefone(valor, aceitaCelular, aceitaFixo, rotulo, aceita0800) {
  if (vazio(valor)) return null;
  const texto = String(valor).trim();
  let d = somenteDigitos(texto);
  const charsOk = /^[\d()\-+.\s]+$/.test(texto);
  if (aceita0800 && charsOk && d.length === 11 && (d.startsWith('0800') || d.startsWith('0300'))) {
    return `${d.slice(0, 4)} ${d.slice(4, 7)} ${d.slice(7)}`;
  }
  if (charsOk && (d.length === 12 || d.length === 13) && d.startsWith('55')) d = d.slice(2);
  const ddd = d.length >= 2 && d[0] !== '0' && d[1] !== '0';
  const celular = d.length === 11 && d[2] === '9';
  const fixo = d.length === 10 && d[2] >= '2' && d[2] <= '5';
  if (!charsOk || !ddd || !((aceitaCelular && celular) || (aceitaFixo && fixo))) {
    throw new Error(`${rotulo} inválido (tamanho/DDD/formato)`);
  }
  const pref = `(${d.slice(0, 2)}) `;
  return celular ? `${pref}${d.slice(2, 7)}-${d.slice(7)}` : `${pref}${d.slice(2, 6)}-${d.slice(6)}`;
}

const celularFn = (v) => telefone(v, true, false, 'Celular', false);
const fixoFn = (v) => telefone(v, false, true, 'Telefone fixo', false);
const telefoneFn = (v) => telefone(v, true, true, 'Telefone', false);
const telefoneCentralFn = (v) => telefone(v, true, true, 'Telefone', true);

function email(valor) {
  if (vazio(valor)) return null;
  return String(valor).trim().toLowerCase();
}

function cep(valor) {
  if (vazio(valor)) return null;
  const texto = String(valor).trim();
  const d = somenteDigitos(texto);
  if (!/^[\d.\-\s]+$/.test(texto) || d.length !== 8) throw new Error('CEP inválido');
  return `${d.slice(0, 5)}-${d.slice(5)}`;
}

// ───────────────────────── self-test ─────────────────────────

function selfTest() {
  const eq = (a, b, msg) => { if (a !== b) throw new Error(`SELFTEST falhou: ${msg} esperado=${b} obtido=${a}`); };
  const falha = (fn, msg) => { try { fn(); } catch (e) { return; } throw new Error(`SELFTEST falhou: esperava erro em ${msg}`); };
  eq(cpf('52998224725'), '529.982.247-25', 'cpf sem máscara');
  eq(cpf('529.982.247-25'), '529.982.247-25', 'cpf com máscara');
  eq(cpf('  '), null, 'cpf vazio');
  ['529.982.247-24', '11111111111', '123', '529.982.247-2x'].forEach((v) => falha(() => cpf(v), `cpf ${v}`));
  eq(cnpj('11222333000181'), '11.222.333/0001-81', 'cnpj sem máscara');
  eq(cnpj('11.444.777/0001-61'), '11.444.777/0001-61', 'cnpj com máscara');
  ['11.222.333/0001-80', '00000000000000', '1122233300018'].forEach((v) => falha(() => cnpj(v), `cnpj ${v}`));
  eq(cro('cro-se   12345'), 'CRO-SE 12345', 'cro');
  eq(cro('CRO SE 12345 / 2022'), 'CRO-SE 12345/2022', 'cro ano');
  ['CRO-XX 12345', 'CRO-SE 123', 'CRO-SE 12345/22', '12345'].forEach((v) => falha(() => cro(v), `cro ${v}`));
  eq(celularFn('+55 79 99999-1234'), '(79) 99999-1234', 'celular');
  eq(fixoFn('7932221234'), '(79) 3222-1234', 'fixo');
  eq(telefoneFn('7932221234'), '(79) 3222-1234', 'telefone fixo');
  eq(telefoneFn('79999991234'), '(79) 99999-1234', 'telefone celular');
  ['(79) 3222-1234', '9999-1234', '(00) 99999-1234'].forEach((v) => falha(() => celularFn(v), `celular ${v}`));
  ['(79) 99999-1234', '(79) 1222-1234'].forEach((v) => falha(() => fixoFn(v), `fixo ${v}`));
  eq(telefoneCentralFn('08001234567'), '0800 123 4567', '0800');
  eq(telefoneCentralFn('0300-313-3003'), '0300 313 3003', '0300');
  eq(telefoneCentralFn('1140040019'), '(11) 4004-0019', 'central fixo');
  falha(() => telefoneFn('08001234567'), 'telefone generico rejeita 0800');
  falha(() => celularFn('08001234567'), 'celular rejeita 0800');
  falha(() => telefoneCentralFn('0900 123 4567'), '0900');
  eq(email('  Ana@Mail.COM '), 'ana@mail.com', 'email');
  eq(cep('49000123'), '49000-123', 'cep');
  falha(() => cep('4900012'), 'cep curto');
  print('SELFTEST OK');
}

if (typeof process !== 'undefined' && process.env.SELFTEST === 'true') {
  selfTest();
} else {
  executar();
}

// ───────────────────────── migração ─────────────────────────

function executar() {
  const APPLY = process.env.APPLY === 'true';
  const database = db.getSiblingDB(db.getName());
  const dbName = database.getName();

  if (APPLY && process.env.CONFIRM_DB !== dbName) {
    throw new Error(`APPLY exige CONFIRM_DB=${dbName} (banco conectado). Abortado sem alterar nada.`);
  }

  // campos: [caminho, normalizador, único?]
  const COLECOES = {
    pacientes: [
      ['cpf', cpf, true], ['responsavelCpf', cpf], ['email', email], ['telefoneCelular', celularFn],
      ['telefoneFixo', fixoFn], ['telefoneEmergencia', telefoneFn], ['endereco.cep', cep],
    ],
    funcionarios: [['cpf', cpf, true], ['email', email], ['telefoneCelular', celularFn], ['endereco.cep', cep]],
    dentistas: [
      ['cro', cro, true], ['cpf', cpf, true], ['email', email], ['telefoneCelular', celularFn], ['endereco.cep', cep],
    ],
    convenios: [['cnpj', cnpj, true], ['telefone', telefoneCentralFn], ['email', email], ['endereco.cep', cep]],
    clinicas: [['cnpj', cnpj], ['telefone', telefoneCentralFn], ['emailContato', email], ['endereco.cep', cep]],
    users: [['email', email, true]],
  };

  const PARCIAL_PADRAO = { $type: 'string', $gt: '' };
  const INDICES = [
    { col: 'pacientes', campo: 'cpf', nome: 'clinica_cpf_unique', parcial: { $gt: '' } },
    { col: 'funcionarios', campo: 'cpf', nome: 'clinica_cpf_unique', parcial: PARCIAL_PADRAO },
    { col: 'dentistas', campo: 'cro', nome: 'clinica_cro_unique', parcial: PARCIAL_PADRAO },
    { col: 'dentistas', campo: 'cpf', nome: 'clinica_cpf_unique', parcial: PARCIAL_PADRAO },
    { col: 'convenios', campo: 'cnpj', nome: 'clinica_cnpj_unique', parcial: PARCIAL_PADRAO },
  ];

  const get = (doc, caminho) => caminho.split('.').reduce((o, k) => (o === null || o === undefined ? undefined : o[k]), doc);

  const nomesClinicas = {};
  database.clinicas.find({}, { nome: 1, codigo: 1 }).forEach((c) => { nomesClinicas[c._id.toString()] = `${c.codigo} (${c.nome})`; });
  const rotuloClinica = (id) => (id === null || id === undefined ? '(sem clinicaId)' : (nomesClinicas[id.toString()] || String(id)));

  print('='.repeat(78));
  print(`Migração de formatos — banco: ${dbName} — modo: ${APPLY ? 'APPLY (GRAVA)' : 'DRY-RUN (não grava)'}`);
  print('='.repeat(78));

  const relatorio = {}; // clinica -> { colecao -> {docs, alterados, invalidos[], duplicatas[], mudancas[]} }
  const ops = {}; // colecao -> bulk ops
  const totais = { docs: 0, alterados: 0, invalidos: 0, duplicatas: 0 };

  const entrada = (clinica, colecao) => {
    relatorio[clinica] = relatorio[clinica] || {};
    relatorio[clinica][colecao] = relatorio[clinica][colecao] || { docs: 0, alterados: 0, invalidos: [], duplicatas: [], mudancas: [] };
    return relatorio[clinica][colecao];
  };

  Object.keys(COLECOES).forEach((colecao) => {
    const campos = COLECOES[colecao];
    const planos = []; // { doc, clinica, mudancas: { caminho: novo }, ... }
    const porUnico = {}; // `${caminho}|${clinica}|${valorFinal}` -> [planos]

    database.getCollection(colecao).find({}).forEach((doc) => {
      const clinica = colecao === 'clinicas' ? doc._id.toString() : (doc.clinicaId === undefined ? null : doc.clinicaId);
      const clinicaKey = clinica === null ? 'null' : clinica.toString();
      const rel = entrada(colecao === 'clinicas' ? clinicaKey : clinicaKey, colecao);
      const nome = doc.nomeCompleto || doc.nome || doc.name || doc.codigo || '';
      const plano = { doc, clinicaKey, mudancas: {}, original: {} };
      rel.docs++;
      totais.docs++;

      campos.forEach(([caminho, fn, unico]) => {
        const atual = get(doc, caminho);
        if (atual === undefined) return;
        let novo;
        try {
          novo = fn(atual);
        } catch (e) {
          rel.invalidos.push({ id: doc._id.toString(), nome, campo: caminho, valor: atual, motivo: e.message });
          totais.invalidos++;
          novo = atual;
        }
        if (novo !== atual) {
          plano.mudancas[caminho] = novo;
          plano.original[caminho] = atual;
        }
        if (unico && typeof (novo) === 'string' && novo !== '') {
          const chave = `${caminho}|${clinicaKey}|${novo}`;
          (porUnico[chave] = porUnico[chave] || []).push({ plano, caminho, nome, valor: novo });
        }
      });
      planos.push(plano);
    });

    // duplicatas dentro da mesma clínica: não normaliza o campo dos envolvidos
    Object.keys(porUnico).forEach((chave) => {
      const grupo = porUnico[chave];
      if (grupo.length < 2) return;
      const { caminho, valor } = grupo[0];
      const clinicaKey = grupo[0].plano.clinicaKey;
      entrada(clinicaKey, colecao).duplicatas.push({
        campo: caminho,
        valor,
        registros: grupo.map((g) => ({ id: g.plano.doc._id.toString(), nome: g.nome, valorAtual: g.plano.original[caminho] !== undefined ? g.plano.original[caminho] : valor })),
      });
      totais.duplicatas++;
      grupo.forEach((g) => { delete g.plano.mudancas[caminho]; delete g.plano.original[caminho]; });
    });

    ops[colecao] = [];
    planos.forEach((plano) => {
      const campos2 = Object.keys(plano.mudancas);
      if (campos2.length === 0) return;
      const rel = entrada(plano.clinicaKey, colecao);
      rel.alterados++;
      totais.alterados++;
      campos2.forEach((c) => rel.mudancas.push({ id: plano.doc._id.toString(), campo: c, de: plano.original[c], para: plano.mudancas[c] }));
      const set = {};
      campos2.forEach((c) => { set[c] = plano.mudancas[c]; });
      ops[colecao].push({ updateOne: { filter: { _id: plano.doc._id }, update: { $set: set } } });
    });
  });

  // ── relatório por clínica ──
  Object.keys(relatorio).sort().forEach((clinicaKey) => {
    print('\n' + '-'.repeat(78));
    print(`CLÍNICA: ${rotuloClinica(clinicaKey === 'null' ? null : clinicaKey)}  [${clinicaKey}]`);
    Object.keys(relatorio[clinicaKey]).forEach((colecao) => {
      const r = relatorio[clinicaKey][colecao];
      print(`  ${colecao}: ${r.docs} docs | ${r.alterados} seriam alterados | ${r.invalidos.length} valores inválidos | ${r.duplicatas.length} grupos duplicados`);
      r.mudancas.slice(0, 200).forEach((m) => print(`      ~ ${m.id} ${m.campo}: ${JSON.stringify(m.de)} -> ${JSON.stringify(m.para)}`));
      if (r.mudancas.length > 200) print(`      ... (+${r.mudancas.length - 200} mudanças)`);
      r.invalidos.forEach((i) => print(`      ! INVÁLIDO ${i.id} "${i.nome}" ${i.campo}=${JSON.stringify(i.valor)} — ${i.motivo} (mantido)`));
      r.duplicatas.forEach((d) => {
        print(`      ! DUPLICATA ${d.campo}=${d.valor} (não normalizado):`);
        d.registros.forEach((x) => print(`          - ${x.id} "${x.nome}" valor atual=${JSON.stringify(x.valorAtual)}`));
      });
    });
  });

  print('\n' + '='.repeat(78));
  print(`TOTAL: ${totais.docs} docs | ${totais.alterados} a alterar | ${totais.invalidos} valores inválidos (mantidos) | ${totais.duplicatas} grupos duplicados (mantidos)`);

  // ── índices ──
  const idxUsers = database.getCollection('users').exists() ? database.getCollection('users').getIndexes().find((e) => e.name === 'email_clinica_unique') : null;
  print('\nÍNDICES:');
  print(`  users.email_clinica_unique: ${idxUsers ? 'existe (mantido; não é alterado por este script)' : 'NÃO existe (este script não o cria)'}`);
  const planoIndices = INDICES.map((ix) => {
    const col = database.getCollection(ix.col);
    const existentes = col.exists() ? col.getIndexes() : [];
    const chave = { clinicaId: 1, [ix.campo]: 1 };
    const igual = (a, b) => JSON.stringify(a) === JSON.stringify(b);
    const novoExiste = existentes.find((e) => e.name === ix.nome);
    const antigos = existentes.filter((e) => e.unique === true && igual(e.key, { [ix.campo]: 1 }));
    const statusNovo = novoExiste
      ? (igual(novoExiste.key, chave) ? 'já existe (mantido)' : `CONFLITO: já existe índice "${ix.nome}" com outra chave ${JSON.stringify(novoExiste.key)}`)
      : 'será criado';
    print(`  ${ix.col}.${ix.nome} {clinicaId:1,${ix.campo}:1} parcial ${JSON.stringify(ix.parcial)}: ${statusNovo}`);
    antigos.forEach((a) => print(`  ${ix.col}: índice único GLOBAL antigo "${a.name}" ${JSON.stringify(a.key)} -> será dropado`));
    return { ix, existente: !!novoExiste, conflito: novoExiste && !igual(novoExiste.key, chave), antigos };
  });

  if (!APPLY) {
    print('\nDRY-RUN: nada foi gravado. Para aplicar: APPLY=true CONFIRM_DB=' + dbName + ' (ver cabeçalho do script).');
    return;
  }

  // ── apply: 1) cria compostos, 2) dropa globais, 3) grava valores ──
  planoIndices.forEach((p) => {
    if (p.conflito) throw new Error(`Índice ${p.ix.col}.${p.ix.nome} existe com chave diferente. Resolva manualmente. Nada foi alterado além de índices já criados.`);
  });
  planoIndices.forEach((p) => {
    if (p.existente) return;
    const col = database.getCollection(p.ix.col);
    col.createIndex({ clinicaId: 1, [p.ix.campo]: 1 }, { name: p.ix.nome, unique: true, partialFilterExpression: { [p.ix.campo]: p.ix.parcial } });
    print(`  criado ${p.ix.col}.${p.ix.nome}`);
  });
  planoIndices.forEach((p) => {
    const col = database.getCollection(p.ix.col);
    p.antigos.forEach((a) => {
      if (col.getIndexes().some((e) => e.name === a.name)) {
        col.dropIndex(a.name);
        print(`  dropado ${p.ix.col}."${a.name}"`);
      }
    });
  });
  Object.keys(ops).forEach((colecao) => {
    if (ops[colecao].length === 0) return;
    const res = database.getCollection(colecao).bulkWrite(ops[colecao], { ordered: false });
    print(`  ${colecao}: ${res.modifiedCount} docs atualizados (de ${ops[colecao].length} planejados)`);
  });
  print('\nAPPLY concluído. Rode novamente em DRY-RUN: deve reportar 0 a alterar (restam só inválidos/duplicatas para correção manual).');
}
