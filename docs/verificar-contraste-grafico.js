// Lê as variáveis do protótipo (docs/prototipo-grafico-produtividade.html) e confere os contrastes WCAG nos dois temas.
// Uso: node docs/verificar-contraste-grafico.js   (sai com código 1 se algum par exigido ficar abaixo do mínimo)
const fs = require('fs');
const html = fs.readFileSync(require('path').join(__dirname, 'prototipo-grafico-produtividade.html'), 'utf8');

function block(sel) {
  const a = html.indexOf(sel + '{'); const b = html.indexOf('\n}', a);
  const body = html.slice(a + sel.length + 1, b);
  const out = {};
  body.replace(/\/\*[\s\S]*?\*\//g, '').split(';').forEach((d) => {
    const i = d.indexOf(':'); if (i < 0) return;
    const k = d.slice(0, i).trim(); const v = d.slice(i + 1).trim();
    if (k.startsWith('--')) out[k] = v;
  });
  return out;
}
const light = block(':root');
const dark = Object.assign({}, light, block(':root[data-theme="dark"]'));

function resolve(tokens, name, depth = 0) {
  let v = tokens[name]; if (v == null) throw new Error('sem token ' + name);
  const m = /^var\((--[\w-]+)\)$/.exec(v); if (m && depth < 10) return resolve(tokens, m[1], depth + 1);
  return v;
}
function parse(c) {
  c = c.trim();
  let m = /^#([0-9a-f]{6})$/i.exec(c); if (m) { const n = parseInt(m[1], 16); return [n >> 16, (n >> 8) & 255, n & 255, 1]; }
  m = /^rgb\((\d+) (\d+) (\d+)(?: \/ ([\d.]+))?\)$/.exec(c); if (m) return [+m[1], +m[2], +m[3], m[4] == null ? 1 : +m[4]];
  throw new Error('cor inválida ' + c);
}
const over = (fg, bg) => [0, 1, 2].map((i) => fg[i] * fg[3] + bg[i] * (1 - fg[3])).concat([1]);
const lin = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
const lum = (c) => 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2]);
const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
const hex = (c) => '#' + c.slice(0, 3).map((v) => Math.round(v).toString(16).padStart(2, '0')).join('').toUpperCase();

// [descrição, token do primeiro plano, token do fundo(s), mínimo, tipo]
// fundo pode ser "a>b": b sob a (a semitransparente sobre b)
const P = [
  // ---- texto (4,5:1) ----
  ['Texto principal (título, número grande)', '--card-fg', '--card-bg', 4.5, 'texto'],
  ['Texto secundário (legendas, rótulos de totais)', '--card-fg-2', '--card-bg', 4.5, 'texto'],
  ['Texto discreto (subtítulo, “vs mês anterior”)', '--card-fg-3', '--card-bg', 4.5, 'texto'],
  ['Rótulos dos eixos (11 px)', '--axis', '--card-bg', 4.5, 'texto'],
  ['Link e item selecionado (Mostrar valores, Abrir agenda)', '--link', '--card-bg', 4.5, 'texto'],
  ['Link sobre faixa de aviso do menu Exportar', '--link', '--surface-2', 4.5, 'texto'],
  ['Texto do menu sobre o painel', '--fg', '--panel-bg', 4.5, 'texto'],
  ['Descrição do menu sobre o painel', '--fg-2', '--panel-bg', 4.5, 'texto'],
  ['Texto do menu sobre o item em foco/hover', '--fg', '--primary-soft', 4.5, 'texto'],
  ['Link sobre item em foco/hover do menu', '--link', '--primary-soft', 4.5, 'texto'],
  ['Texto do botão Exportar sobre o card', '--fg-2', '--card-bg', 4.5, 'texto'],
  ['Texto dos botões de ano (não selecionado)', '--fg-2', '--surface-3', 4.5, 'texto'],
  ['Branco sobre azul (ano selecionado, botão primário)', '--on-primary', '--primary', 4.5, 'texto'],
  ['Selo bom (verde)', '--success-fg', '--success-soft>--card-bg', 4.5, 'texto'],
  ['Selo ruim (vermelho)', '--danger-fg', '--danger-soft>--card-bg', 4.5, 'texto'],
  ['Selo neutro', '--neutral-fg', '--neutral-soft>--card-bg', 4.5, 'texto'],
  ['Balão: valor', '--tip-fg', '--tip-bg', 4.5, 'texto'],
  ['Balão: data e rótulos', '--tip-fg-3', '--tip-bg', 4.5, 'texto'],
  ['Aviso (toast)', '--toast-fg', '--toast-bg', 4.5, 'texto'],
  ['Aviso de erro (toast)', '#FFFFFF', '--toast-err-bg', 4.5, 'texto'],
  ['Estado vazio/erro: título', '--card-fg', '--surface-2', 4.5, 'texto'],
  ['Estado vazio/erro: texto', '--card-fg-3', '--surface-2', 4.5, 'texto'],
  // ---- elementos gráficos (3:1) ----
  ['Linha da Produtividade', '--line', '--card-bg', 3, 'gráfico'],
  ['Ponto marcado (anel) da Produtividade', '--dot-ring', '--card-bg', 3, 'gráfico'],
  ['Linha tracejada de comparação', '--compare', '--card-bg', 3, 'gráfico'],
  ['Barras de Receita', '--positive', '--card-bg', 3, 'gráfico'],
  ['Barras de Despesa', '--negative', '--card-bg', 3, 'gráfico'],
  ['Linha de Lucro líquido', '--profit', '--card-bg', 3, 'gráfico'],
  ['Linha do zero (quando há lucro negativo)', '--baseline', '--card-bg', 3, 'gráfico'],
  ['Marcas de cor da Receita no balão', '--positive', '--tip-bg', 3, 'gráfico'],
  ['Marcas de cor da Despesa no balão', '--negative', '--tip-bg', 3, 'gráfico'],
  ['Marcas de cor do Lucro no balão', '--profit', '--tip-bg', 3, 'gráfico'],
  ['Barra de Receita ao lado da de Despesa', '--positive', '--negative', 1.0, 'info'],
  ['Anel de foco do teclado', '--focus', '--card-bg', 3, 'gráfico'],
  ['Ícone do olho e das setas', '--card-fg-3', '--card-bg', 3, 'gráfico'],
  ['Ícone dentro dos círculos do estado vazio/erro', '--fg-3', '--surface-3', 3, 'gráfico'],
  ['Chave “Comparar” desligada (contorno)', '--fg-3', '--card-bg', 3, 'gráfico'],
  ['Chave “Comparar” ligada (fundo azul)', '--primary', '--card-bg', 3, 'gráfico'],
  // ---- decorativos (informados, sem exigência) ----
  ['Linhas de grade (decorativas)', '--grid>--card-bg', '--card-bg', 0, 'decorativo'],
  ['Linha vertical do cursor (decorativa; o ponto marcado carrega a informação)', '--cursor>--card-bg', '--card-bg', 0, 'decorativo'],
];

function colorOf(tokens, spec, bgForAlpha) {
  if (spec.startsWith('#')) return parse(spec);
  if (spec.includes('>')) {
    const [a, b] = spec.split('>');
    return over(parse(resolve(tokens, a)), colorOf(tokens, b));
  }
  const c = parse(resolve(tokens, spec));
  return c[3] < 1 ? over(c, bgForAlpha || parse(resolve(tokens, '--card-bg'))) : c;
}

const result = { claro: [], escuro: [] }; let falhas = 0;
for (const [nome, tokens] of [['claro', light], ['escuro', dark]]) {
  for (const [desc, fgS, bgS, min, tipo] of P) {
    const bg = colorOf(tokens, bgS);
    const fg = colorOf(tokens, fgS, bg);
    const r = ratio(fg, bg);
    const ok = tipo === 'info' || tipo === 'decorativo' ? null : r >= min;
    if (ok === false) falhas++;
    result[nome].push({ desc, fg: fgS.startsWith('#') ? fgS : fgS.replace(/>.*/, ''), fgHex: hex(fg), bgHex: hex(bg), r: Math.round(r * 100) / 100, min, tipo, ok });
  }
}
for (const t of ['claro', 'escuro']) {
  console.log('\n=== ' + t + ' ===');
  result[t].forEach((x) => console.log((x.ok === false ? 'FALHA ' : x.ok === null ? 'info  ' : 'ok    ') + String(x.r).padEnd(6) + ' (min ' + x.min + ') ' + x.fgHex + ' / ' + x.bgHex + '  ' + x.desc));
}
console.log('\nFalhas:', falhas);
process.exit(falhas ? 1 : 0);
