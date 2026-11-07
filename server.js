// MKVCast - servidor: biblioteca, transcodificación en vivo (ffmpeg) y control de Chromecast
// Se usa dentro de la app de escritorio (main.js, Electron) o solo con "node server.js".
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const child_process = require('child_process');
const ChromecastAPI = require('chromecast-api');

// --- ESCUDO ANTI-CRASH ---
process.on('uncaughtException', function(err) {
  console.error('Error interno (ignorado):', err.message);
});
process.on('unhandledRejection', function(err) {
  console.error('Promesa rechazada (ignorada):', err);
});
// -------------------------

// Carpeta donde viven config.json, ffmpeg.exe, ffprobe.exe, uploads, subs, thumbs y state.json
function appBase() {
  if (process.env.PORTABLE_EXECUTABLE_DIR) return process.env.PORTABLE_EXECUTABLE_DIR; // exe portable (electron-builder)
  if (process.pkg) return path.dirname(process.execPath);
  if (process.versions && process.versions.electron) {
    try { if (require('electron').app.isPackaged) return path.dirname(process.execPath); } catch (e) {}
  }
  return __dirname;
}
const base = appBase();

let cfg = {};
try {
  cfg = JSON.parse(fs.readFileSync(path.join(base, 'config.json'), 'utf8'));
} catch (e) {
  cfg = {};
}
if (!Array.isArray(cfg.folders)) cfg.folders = [];
const PORT = cfg.port || 8099;
function exe(n) {
  const p = path.join(base, n + '.exe');
  return fs.existsSync(p) ? p : n;
}
const FFMPEG = exe('ffmpeg');
const FFPROBE = exe('ffprobe');
const stateFile = path.join(base, 'state.json');
const uploadsDir = path.join(base, 'uploads');
const thumbDir = path.join(base, 'thumbs');
try { fs.mkdirSync(uploadsDir, { recursive: true }); } catch (e) {}
try { fs.mkdirSync(thumbDir, { recursive: true }); } catch (e) {}
// Orden de las "raíces": carpetas de config.json, luego uploads, luego las agregadas arrastrando
function roots() { return (cfg.folders || []).concat([uploadsDir]).concat((saved && saved.extra) || []); }
const VIDEO_RE = /\.(mkv|mp4|avi|mov|m4v|webm)$/i;

// Límites del Chromecast 2ª generación (configurables en la pantalla de Ajustes o en config.json)
let MAX_HEIGHT = 1080, MAX_LEVEL = 41, MAX_FPS = 30;
function applyLimits() {
  MAX_HEIGHT = cfg.maxHeight || 1080;   // alto máximo del video
  MAX_LEVEL = cfg.maxLevel || 41;       // nivel H.264 máximo (41 = 4.1)
  MAX_FPS = cfg.maxFps || 30;           // fps máximos (1080p@4.1 llega a ~30)
}
applyLimits();

const ENCODERS = {
  nvenc: ['-c:v', 'h264_nvenc', '-preset', 'p4', '-rc', 'vbr', '-cq', '23', '-b:v', '0'],
  qsv:   ['-c:v', 'h264_qsv', '-preset', 'veryfast', '-global_quality', '23'],
  amf:   ['-c:v', 'h264_amf', '-quality', 'speed', '-b:v', '6M'],
  cpu:   ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22']
};
// Perfil y nivel por codificador (amf no recibe -level porque puede dar error)
const PROFILE_ARGS = {
  nvenc: ['-profile:v', 'high', '-level', '4.1'],
  qsv:   ['-profile:v', 'high', '-level', '41'],
  amf:   ['-profile:v', 'high'],
  cpu:   ['-profile:v', 'high', '-level', '4.1']
};
let encoder = 'cpu';

function testEncoder(n) {
  return new Promise(function(ok) {
    const args = ['-v', 'error', '-f', 'lavfi', '-i', 'color=black:s=320x240:d=0.2', '-pix_fmt', 'yuv420p'].concat(ENCODERS[n]).concat(PROFILE_ARGS[n]).concat(['-f', 'null', '-']);
    child_process.execFile(FFMPEG, args, { timeout: 20000 }, function(err) { ok(!err); });
  });
}

async function detectEncoder() {
  const want = String(cfg.gpu || 'auto').toLowerCase();
  const order = want === 'auto' ? ['nvenc', 'qsv', 'amf'] : (ENCODERS[want] && want !== 'cpu' ? [want] : []);
  let found = 'cpu';
  for (let i = 0; i < order.length; i++) {
    if (await testEncoder(order[i])) { found = order[i]; break; }
  }
  encoder = found;
  console.log('Codificador de video: ' + (encoder === 'cpu' ? 'CPU (libx264)' : 'GPU (' + encoder.toUpperCase() + ')'));
}
detectEncoder();

// Corrección de colores HDR: necesita los filtros zscale y tonemap en el ffmpeg
let HAS_ZSCALE = false;
child_process.execFile(FFMPEG, ['-hide_banner', '-filters'], { maxBuffer: 1 << 24 }, function(err, out) {
  HAS_ZSCALE = !err && /\bzscale\b/.test(out) && /\btonemap\b/.test(out);
  console.log('Corrección de colores HDR: ' + (HAS_ZSCALE ? 'disponible' : 'no disponible en este ffmpeg (los HDR se verán apagados)'));
});

let saved = { queue: [], qi: -1, positions: {}, extra: [], playlists: [] };
try { saved = Object.assign(saved, JSON.parse(fs.readFileSync(stateFile, 'utf8'))); } catch (e) {}
if (!Array.isArray(saved.extra)) saved.extra = [];
if (!Array.isArray(saved.playlists)) saved.playlists = [];
if (!saved.subDelays || typeof saved.subDelays !== 'object') saved.subDelays = {};   // sincronía de subtítulos por video (segundos)
if (!Array.isArray(saved.recent)) saved.recent = [];                                  // "Continuar viendo": [{ r, p, pos, dur, t }]
if (!saved.seen || typeof saved.seen !== 'object') saved.seen = {};                   // videos marcados como vistos
// Ajustes de subtítulos: autoSub = elegir solos los del archivo; subSize = tamaño de letra
saved.settings = Object.assign({ autoSub: true, subSize: 'normal', assRespect: true, hdrFix: true, audioMode: 'normal' }, saved.settings || {});
// Tamaños (FontSize de libass, el estándar de los .srt es 16)
const SUB_SIZES = { small: 12, normal: 16, large: 24, xlarge: 32 };
if (!SUB_SIZES[saved.settings.subSize]) saved.settings.subSize = 'normal';
const AUDIO_MODES = ['normal', 'dialog', 'night'];   // normal | diálogos (mezcla 5.1 con voces más fuertes) | noche (diálogos + compresor)
if (AUDIO_MODES.indexOf(saved.settings.audioMode) < 0) saved.settings.audioMode = 'normal';

// Estilo de los subtítulos: color, fondo (caja), opacidad del fondo, grosor del borde, sombra y posición
const SUB_COLORS = { white: 'FFFFFF', yellow: '00FFFF', cyan: 'FFFF00', green: '00FF00', orange: '0099FF', pink: 'FF80FF', gray: 'CCCCCC' };   // BGR (formato ASS)
const SUB_POS = { bottom: [2, 10], raised: [2, 40], middle: [10, 10], top: [6, 10] };   // [Alignment, MarginV]
const STYLE_DEFAULT = { color: 'white', bg: 'none', bgOpacity: 60, outline: 1, shadow: 0, pos: 'bottom' };
saved.settings.style = Object.assign({}, STYLE_DEFAULT, saved.settings.style || {});
function cleanStyle(st) {
  const o = Object.assign({}, STYLE_DEFAULT, st || {});
  if (!SUB_COLORS[o.color]) o.color = STYLE_DEFAULT.color;
  if (o.bg !== 'box') o.bg = 'none';
  o.bgOpacity = Math.max(0, Math.min(100, Math.round(Number(o.bgOpacity)))); if (isNaN(o.bgOpacity)) o.bgOpacity = 60;
  o.outline = Math.max(0, Math.min(4, Math.round(Number(o.outline)))); if (isNaN(o.outline)) o.outline = 1;
  o.shadow = Math.max(0, Math.min(4, Math.round(Number(o.shadow)))); if (isNaN(o.shadow)) o.shadow = 0;
  if (!SUB_POS[o.pos]) o.pos = 'bottom';
  return o;
}
saved.settings.style = cleanStyle(saved.settings.style);
function hex2(n) { n = Math.max(0, Math.min(255, n)); return (n < 16 ? '0' : '') + n.toString(16).toUpperCase(); }
function forceStyle(fontSize) {
  const st = cleanStyle(saved.settings.style), pos = SUB_POS[st.pos];
  const parts = ['FontSize=' + fontSize, 'PrimaryColour=&H00' + SUB_COLORS[st.color], 'Alignment=' + pos[0], 'MarginV=' + pos[1]];
  if (st.bg === 'box') {
    // Caja detrás del texto: libass usa OutlineColour como color de la caja (BorderStyle=3); el alfa va en el primer byte
    const a = hex2(Math.round(255 * (100 - st.bgOpacity) / 100));
    parts.push('BorderStyle=3', 'OutlineColour=&H' + a + '000000', 'BackColour=&H' + a + '000000', 'Outline=' + Math.max(2, st.outline), 'Shadow=0');
  } else {
    parts.push('BorderStyle=1', 'OutlineColour=&H00000000', 'BackColour=&H80000000', 'Outline=' + st.outline, 'Shadow=' + st.shadow);
  }
  return parts.join(',');
}
function persist() { fs.writeFile(stateFile, JSON.stringify(saved), function() {}); }
function key(it) { return it.r + '|' + it.p; }

const tokens = new Set();
const streamKey = crypto.randomBytes(8).toString('hex');
const devices = new Map();
let curDev = null, now = null, lastPlayAt = 0, status = { state: 'IDLE', pos: 0 }, wasPlaying = false;
let lastVolume = 0.5, volDev = null;   // último volumen (0 a 1) del Chromecast, para que las pantallas muestren la barra donde corresponde

// IP de esta PC en la red local: se prefieren las redes privadas reales y se evitan los adaptadores virtuales
function pickIp() {
  if (cfg.ip) return String(cfg.ip);
  let best = null, bestScore = -100;
  try {
    const ifaces = os.networkInterfaces();
    for (const name in ifaces) {
      (ifaces[name] || []).forEach(function(i) {
        if ((i.family !== 'IPv4' && i.family !== 4) || i.internal) return;
        let score = 1;
        if (/^192\.168\./.test(i.address)) score = 4;
        else if (/^10\./.test(i.address)) score = 3;
        else if (/^172\.(1[6-9]|2\d|3[01])\./.test(i.address)) score = 2;
        if (/virtual|vmware|vbox|vethernet|wsl|hyper-v|loopback|tailscale|zerotier|vpn|docker/i.test(name)) score -= 1.5;
        if (score > bestScore) { best = i.address; bestScore = score; }
      });
    }
  } catch (e) {}
  return best || '127.0.0.1';
}
let ip = pickIp();

const client = new ChromecastAPI();
client.on('device', function(d) {
  devices.set(d.friendlyName, d);
  if (!curDev) curDev = d.friendlyName;
});

function resolvePath(r, p) {
  const rr = roots()[r];
  if (rr == null) throw new Error('Carpeta no válida');
  const root = path.resolve(rr);
  const full = path.resolve(root, p || '');
  if (full !== root && !full.startsWith(root + path.sep)) throw new Error('Ruta no permitida');
  return full;
}

function walk(dir, depth, name, size) {
  let ents;
  try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return null; }
  for (let i = 0; i < ents.length; i++) {
    const e = ents[i];
    if (e.isFile() && e.name === name) {
      const f = path.join(dir, e.name);
      try { if (fs.statSync(f).size === size) return f; } catch (e2) {}
    }
  }
  if (depth < 8) {
    for (let i = 0; i < ents.length; i++) {
      const e = ents[i];
      if (e.isDirectory()) {
        const h = walk(path.join(dir, e.name), depth + 1, name, size);
        if (h) return h;
      }
    }
  }
  return null;
}

function findFile(name, size) {
  const rs = roots();
  for (let r = 0; r < rs.length; r++) {
    const hit = walk(path.resolve(rs[r]), 0, name, size);
    if (hit) return { r: r, p: path.relative(path.resolve(rs[r]), hit) };
  }
  return null;
}

// ¿La ruta absoluta f está dentro de la raíz root? Devuelve la ruta relativa o null
function relInside(root, f) {
  const rel = path.relative(path.resolve(root), path.resolve(f));
  if (rel === '') return '';
  if (rel.startsWith('..') || path.isAbsolute(rel)) return null;
  return rel;
}

// Busca (o crea) la raíz que contiene una carpeta; devuelve { r, p }
function rootFor(dirPath) {
  const rs = roots();
  for (let i = 0; i < rs.length; i++) {
    const rel = relInside(rs[i], dirPath);
    if (rel !== null) return { r: i, p: rel };
  }
  saved.extra.push(path.resolve(dirPath));
  persist();
  return { r: roots().length - 1, p: '' };
}

// Cuando cambia la lista de carpetas, se reasignan los índices de la cola y las posiciones guardadas
function remapRoots(oldR, newR) {
  const map = oldR.map(function(p) { return newR.indexOf(p); });
  const q = [];
  let newQi = -1;
  saved.queue.forEach(function(it, i) {
    const n = map[it.r];
    if (n != null && n >= 0) { q.push({ r: n, p: it.p }); if (i === saved.qi) newQi = q.length - 1; }
  });
  saved.queue = q;
  saved.qi = newQi;
  const pos = {};
  Object.keys(saved.positions).forEach(function(k) {
    const bar = k.indexOf('|');
    const n = map[parseInt(k.slice(0, bar), 10)];
    if (n != null && n >= 0) pos[n + k.slice(bar)] = saved.positions[k];
  });
  saved.positions = pos;
  const sd = {};
  Object.keys(saved.subDelays).forEach(function(k) {
    const bar = k.indexOf('|');
    const n = map[parseInt(k.slice(0, bar), 10)];
    if (n != null && n >= 0) sd[n + k.slice(bar)] = saved.subDelays[k];
  });
  saved.subDelays = sd;
  const rc = [];
  saved.recent.forEach(function(it) { const n = map[it.r]; if (n != null && n >= 0) rc.push(Object.assign({}, it, { r: n })); });
  saved.recent = rc;
  const sn = {};
  Object.keys(saved.seen).forEach(function(k) {
    const bar = k.indexOf('|');
    const n = map[parseInt(k.slice(0, bar), 10)];
    if (n != null && n >= 0) sn[n + k.slice(bar)] = 1;
  });
  saved.seen = sn;
  saved.playlists.forEach(function(pl) {
    pl.items = pl.items.map(function(it) { return { r: map[it.r], p: it.p }; }).filter(function(it) { return it.r != null && it.r >= 0; });
  });
  if (now) {
    const n = map[now.r];
    if (n != null && n >= 0) now.r = n; else now = null;
  }
}

function parseFps(r) {
  if (!r) return 0;
  const p = String(r).split('/');
  const n = parseFloat(p[0]), d = parseFloat(p[1] || '1');
  return d ? n / d : 0;
}

const probeCache = new Map();
function probe(full) {
  if (probeCache.has(full)) return Promise.resolve(probeCache.get(full));
  return new Promise(function(ok, fail) {
    child_process.execFile(FFPROBE, ['-v', 'error', '-print_format', 'json', '-show_streams', '-show_format', full],
      { maxBuffer: 1 << 24 }, function(err, out) {
        if (err) return fail(err);
        const j = JSON.parse(out);
        const s = j.streams || [];
        const pick = function(t) { return s.filter(function(x) { return x.codec_type === t; }); };
        const v = pick('video')[0] || {};
        const info = {
          duration: parseFloat(j.format.duration) || 0,
          vcodec: v.codec_name,
          pix: v.pix_fmt,
          width: parseInt(v.width, 10) || 0,
          height: parseInt(v.height, 10) || 0,
          level: parseInt(v.level, 10) || 0,
          fps: parseFps(v.avg_frame_rate || v.r_frame_rate),
          profile: v.profile,
          transfer: v.color_transfer,
          hdr: v.color_transfer === 'smpte2084' || v.color_transfer === 'arib-std-b67',
          dvp: ((v.side_data_list || []).filter(function(x) { return /DOVI/i.test(x.side_data_type || ''); })[0] || {}).dv_profile,
          audio: pick('audio').map(function(x, i) { return { i: i, lang: x.tags && x.tags.language, title: x.tags && x.tags.title, codec: x.codec_name, channels: x.channels || 0 }; }),
          subs: pick('subtitle').map(function(x, i) { return { i: i, lang: x.tags && x.tags.language, title: x.tags && x.tags.title, codec: x.codec_name, def: !!(x.disposition && x.disposition.default), forced: !!(x.disposition && x.disposition.forced) }; })
        };
        probeCache.set(full, info);
        ok(info);
      });
  });
}

// Miniaturas: un fotograma del video, en caché, de a 2 por vez para no cargar la PC
const thumbFail = new Set();
const thumbQ = [];
let thumbBusy = 0;
function makeThumb(full, out, ss) {
  return new Promise(function(ok) {
    child_process.execFile(FFMPEG, ['-v', 'error', '-ss', String(ss), '-i', full, '-frames:v', '1', '-vf', 'scale=400:-2', '-q:v', '5', '-y', out],
      { timeout: 30000 }, function(err) {
        let good = false;
        try { good = !err && fs.statSync(out).size > 0; } catch (e) {}
        if (!good) { try { fs.unlinkSync(out); } catch (e) {} }
        ok(good);
      });
  });
}
function pumpThumbs() {
  while (thumbBusy < 2 && thumbQ.length) {
    const j = thumbQ.shift();
    thumbBusy++;
    makeThumb(j.full, j.out, 30)
      .then(function(good) { return good ? true : makeThumb(j.full, j.out, 0); })
      .then(function(good) {
        if (!good) thumbFail.add(j.out);
        thumbBusy--;
        j.ok(good ? j.out : null);
        pumpThumbs();
      });
  }
}
function thumbFor(full) {
  let st;
  try { st = fs.statSync(full); } catch (e) { return Promise.resolve(null); }
  const name = crypto.createHash('md5').update(full + '|' + st.size + '|' + Math.floor(st.mtimeMs)).digest('hex').slice(0, 20) + '.jpg';
  const out = path.join(thumbDir, name);
  if (fs.existsSync(out)) return Promise.resolve(out);
  if (thumbFail.has(out)) return Promise.resolve(null);
  return new Promise(function(ok) { thumbQ.push({ full: full, out: out, ok: ok }); pumpThumbs(); });
}

// Quita los tamaños de letra incrustados en el texto del subtítulo (<font size=..> y \fs.. de ASS),
// que pisan el tamaño elegido en el selector y hacían que algunos archivos no cambiaran de tamaño
function cleanSrt(file) {
  try {
    let txt = fs.readFileSync(file, 'utf8');
    txt = txt.replace(/<font([^>]*?)\s+size\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)([^>]*)>/gi, '<font$1$3>');
    txt = txt.replace(/\{[^}]*\}/g, function(blk) {
      const c = blk.replace(/\\fs[\d.]+/g, '').replace(/\\fsc[xy][\d.]+/g, '').replace(/\\fsp-?[\d.]+/g, '').replace(/\\fn[^\\}]*/g, '');
      return c === '{}' ? '' : c;
    });
    fs.writeFileSync(file, txt, 'utf8');
  } catch (e) {}
}

// Los subtítulos se extraen a un archivo temporal con nombre simple (evita problemas con
// espacios, corchetes, "E:\" y otros caracteres de la ruta dentro de los filtros de ffmpeg)
const subDir = path.join(base, 'subs');
try { fs.mkdirSync(subDir, { recursive: true }); } catch (e) {}
// Los subtítulos ASS/SSA (típicos del anime) guardan posición, fuente, color y tamaño de cada línea.
// Si "respetar estilo" está activo se dejan como ASS; si no, se pasan a SRT con un estilo único.
function isAssSub(sb) {
  if (!sb) return false;
  return sb.ext ? /\.(ass|ssa)$/i.test(sb.file) : /^(ass|ssa)$/i.test(sb.codec || '');
}
function respectAss(sb) { return saved.settings.assRespect !== false && isAssSub(sb); }

// Fuentes incrustadas en el mkv (attachments): hacen falta para que el ASS se vea con su tipografía original
function extractFonts(full) {
  const rel = 'fonts_' + crypto.createHash('md5').update(full).digest('hex').slice(0, 12);
  const dir = path.join(subDir, rel);
  try { if (fs.existsSync(path.join(dir, '.listo'))) return Promise.resolve(rel); } catch (e) {}
  try { fs.mkdirSync(dir, { recursive: true }); } catch (e) { return Promise.resolve(null); }
  return new Promise(function(ok) {
    // ffmpeg termina con error porque no hay salida, pero igual vuelca los adjuntos en la carpeta actual
    child_process.execFile(FFMPEG, ['-v', 'quiet', '-y', '-dump_attachment:t', '', '-i', full], { cwd: dir, timeout: 120000, maxBuffer: 1 << 24 }, function() {
      try { fs.writeFileSync(path.join(dir, '.listo'), '1'); } catch (e) {}
      ok(rel);
    });
  });
}

function extractSub(full, s, ass) {
  // el prefijo "s2_" hace que se ignoren los .srt viejos de la carpeta subs (sin limpiar)
  const name = (ass ? 's3_' : 's2_') + crypto.createHash('md5').update(full + '|' + s).digest('hex').slice(0, 16) + (ass ? '.ass' : '.srt');
  const out = path.join(subDir, name);
  try { if (fs.existsSync(out) && fs.statSync(out).size > 0) return Promise.resolve(name); } catch (e) {}
  return new Promise(function(ok) {
    child_process.execFile(FFMPEG, ['-v', 'error', '-y', '-i', full, '-map', '0:s:' + s, '-c:s', ass ? 'ass' : 'srt', out],
      { timeout: 120000, maxBuffer: 1 << 24 }, function(err) {
        let good = false;
        try { good = !err && fs.existsSync(out) && fs.statSync(out).size > 0; } catch (e) {}
        if (good && !ass) cleanSrt(out);
        else if (!good) console.log('No se pudieron extraer los subtítulos (¿son de imagen?). Se reproduce sin ellos.');
        ok(good ? name : null);
      });
  });
}

// ---------- Subtítulos externos (.srt / .ass / .ssa / .vtt junto al video) ----------
const SUB_EXT_RE = /\.(srt|ass|ssa|vtt)$/i;
const LANG_MAP = {
  es: 'spa', spa: 'spa', esp: 'spa', spanish: 'spa', 'español': 'spa', espanol: 'spa', castellano: 'spa', latino: 'spa', lat: 'spa', la: 'spa',
  en: 'eng', eng: 'eng', english: 'eng', ingles: 'eng', 'inglés': 'eng',
  pt: 'por', por: 'por', ptbr: 'por', br: 'por', portuguese: 'por', portugues: 'por',
  fr: 'fra', fre: 'fra', fra: 'fra', french: 'fra', it: 'ita', ita: 'ita', de: 'deu', ger: 'deu', deu: 'deu'
};
function findExtSubs(full) {
  const dir = path.dirname(full);
  const baseName = path.basename(full).replace(/\.[^.]+$/, '');
  const low = baseName.toLowerCase();
  const out = [], seen = new Set();
  [dir, path.join(dir, 'Subs'), path.join(dir, 'Subtitles'), path.join(dir, 'Sub'), path.join(dir, 'subs')].forEach(function(d) {
    const k = d.toLowerCase();
    if (seen.has(k)) return;
    seen.add(k);
    let ents;
    try { ents = fs.readdirSync(d, { withFileTypes: true }); } catch (e) { return; }
    ents.forEach(function(e) {
      if (!e.isFile() || !SUB_EXT_RE.test(e.name)) return;
      const nm = e.name.replace(SUB_EXT_RE, '');
      const nl = nm.toLowerCase();
      if (nl !== low && nl.indexOf(low + '.') !== 0) return;
      const toks = nm.slice(baseName.length).split(/[._\-\s]+/).filter(Boolean);
      let lang = '', forced = false;
      toks.forEach(function(t) {
        const tl = t.toLowerCase();
        if (tl === 'forced' || tl === 'forzado' || tl === 'forzados') forced = true;
        else if (!lang && LANG_MAP[tl]) lang = LANG_MAP[tl];
      });
      out.push({ lang: lang, title: e.name, codec: 'externo', def: false, forced: forced, ext: true, file: path.join(d, e.name) });
    });
  });
  out.sort(function(a, b) { return a.file.localeCompare(b.file); });
  return out;
}
// Lista completa: primero los subtítulos incrustados, después los archivos externos (el índice sigue de corrido)
function subsOf(full, info) {
  const emb = info.subs || [];
  return emb.concat(findExtSubs(full).map(function(x, k) { return Object.assign({}, x, { i: emb.length + k }); }));
}
function publicSubs(list) {
  return list.map(function(x) { const c = Object.assign({}, x); delete c.file; return c; });
}
// Muchos .srt viejos están en ISO-8859-1 (los acentos salen rotos): se detecta y se pasa a UTF-8
function decodeText(buf) {
  if (buf.length >= 3 && buf[0] === 0xEF && buf[1] === 0xBB && buf[2] === 0xBF) buf = buf.slice(3);
  try { return new TextDecoder('utf-8', { fatal: true }).decode(buf); } catch (e) {}
  try { return new TextDecoder('windows-1252').decode(buf); } catch (e) {}
  return buf.toString('latin1');
}
function prepareExtSub(file) {
  let st;
  try { st = fs.statSync(file); } catch (e) { return Promise.resolve(null); }
  if (saved.settings.assRespect !== false && /\.(ass|ssa)$/i.test(file)) {
    const an = 's3x_' + crypto.createHash('md5').update(file + '|' + st.size + '|' + Math.floor(st.mtimeMs)).digest('hex').slice(0, 16) + '.ass';
    try { fs.writeFileSync(path.join(subDir, an), decodeText(fs.readFileSync(file)), 'utf8'); return Promise.resolve(an); }
    catch (e) { console.log('No se pudo leer el subtítulo externo:', e.message); return Promise.resolve(null); }
  }
  const name = 's2x_' + crypto.createHash('md5').update(file + '|' + st.size + '|' + Math.floor(st.mtimeMs)).digest('hex').slice(0, 16) + '.srt';
  const out = path.join(subDir, name);
  try { if (fs.existsSync(out) && fs.statSync(out).size > 0) return Promise.resolve(name); } catch (e) {}
  if (/\.srt$/i.test(file)) {
    try { fs.writeFileSync(out, decodeText(fs.readFileSync(file)), 'utf8'); cleanSrt(out); return Promise.resolve(name); }
    catch (e) { console.log('No se pudo leer el subtítulo externo:', e.message); return Promise.resolve(null); }
  }
  // .ass / .ssa / .vtt: se pasan a .srt con ffmpeg (queda sin estilos, para que el tamaño sea parejo)
  const tmp = path.join(subDir, 'tmp_' + name.replace(/\.srt$/, path.extname(file).toLowerCase()));
  try { fs.writeFileSync(tmp, decodeText(fs.readFileSync(file)), 'utf8'); } catch (e) { return Promise.resolve(null); }
  return new Promise(function(ok) {
    child_process.execFile(FFMPEG, ['-v', 'error', '-y', '-i', tmp, '-c:s', 'srt', out], { timeout: 60000 }, function(err) {
      try { fs.unlinkSync(tmp); } catch (e) {}
      let good = false;
      try { good = !err && fs.statSync(out).size > 0; } catch (e) {}
      if (good) cleanSrt(out); else console.log('No se pudo convertir el subtítulo externo:', path.basename(file));
      ok(good ? name : null);
    });
  });
}

// Subtítulo por defecto del archivo: el marcado como predeterminado, si no el forzado, si no el español
// Subtítulos de imagen (PGS de Blu-ray, VobSub de DVD, DVB): se superponen al video con ffmpeg (overlay).
// XSUB no tiene decodificador utilizable, así que ese no se ofrece.
const IMG_SUB_RE = /^(hdmv_pgs_subtitle|dvd_subtitle|dvb_subtitle)$/i;
const NOSUPPORT_SUB_RE = /^(xsub)$/i;
function pickDefaultSub(subs) {
  return subs.find(function(x) { return x.def; }) ||
         subs.find(function(x) { return x.forced; }) ||
         subs.find(function(x) { return /^(spa|es)/i.test(x.lang || ''); });
}
// Automático: primero los de texto (más livianos y con estilo propio) y, si no hay, los de imagen
function defaultSub(info) {
  const all = (info.subs || []).filter(function(x) { return !NOSUPPORT_SUB_RE.test(x.codec || ''); });
  const txt = all.filter(function(x) { return !IMG_SUB_RE.test(x.codec || ''); });
  const img = all.filter(function(x) { return IMG_SUB_RE.test(x.codec || ''); });
  const t = pickDefaultSub(txt) || pickDefaultSub(img);
  return t ? t.i : -1;
}

async function startPlay(item, o) {
  if (!o) o = {};
  const dev = devices.get(curDev);
  if (!dev) throw new Error('Elige un Chromecast primero');
  const full = resolvePath(item.r, item.p);
  const info = await probe(full);
  const start = o.start != null ? o.start : (saved.positions[key(item)] || 0);
  now = { r: item.r, p: item.p, name: path.basename(item.p), audio: o.audio || 0, sub: o.sub != null ? o.sub : (saved.settings.autoSub ? defaultSub({ subs: subsOf(full, info) }) : -1), offset: start, duration: info.duration, delay: saved.subDelays[key(item)] || 0 };
  const url = 'http://' + ip + ':' + PORT + '/stream?r=' + item.r + '&p=' + encodeURIComponent(item.p) + '&t=' + start + '&a=' + now.audio + '&s=' + now.sub + '&k=' + streamKey + '&n=' + Date.now();
  lastPlayAt = Date.now();
  wasPlaying = false;
  status = { state: 'BUFFERING', pos: 0, volume: lastVolume };
  // la primera vez con cada Chromecast arranca al volumen guardado (50% por defecto), así no suena al máximo
  if (volDev !== curDev) { volDev = curDev; try { dev.setVolume(lastVolume, function() {}); } catch (e) {} }
  dev.play({ url: url, title: now.name }, { startTime: 0 }, function(err) { if (err) console.log('Cast error:', err.message || err); });
}

async function playNext(step) {
  if (step == null) step = 1;
  const ni = saved.qi + step;
  if (ni < 0 || ni >= saved.queue.length) { now = null; return; }
  saved.qi = ni;
  persist();
  try {
    await startPlay(saved.queue[ni], { start: 0 });
  } catch (e) {
    console.log(e.message);
  }
}

setInterval(function() {
  const dev = devices.get(curDev);
  if (!dev || !now || typeof dev.getStatus !== 'function') return;
  try { dev.getStatus(function(err, st) {
    if (err || !st || !now) return;
    const s = st.playerState || 'IDLE';
    // OJO: el volumen que viene en el estado del medio es el del stream (siempre 1.0), no el del equipo;
    // por eso se usa solo el último volumen que se mandó desde acá
    status = { state: s, pos: now.offset + (st.currentTime || 0), volume: lastVolume };
    if (s === 'PLAYING') wasPlaying = true;
    if (s === 'PLAYING' || s === 'PAUSED') {
      const k = key(now);
      if (now.duration && status.pos > now.duration - 30) { delete saved.positions[k]; markSeen(now); }
      else { saved.positions[k] = Math.floor(status.pos); touchRecent(now, status.pos); }
      persist();
    }
    if (s === 'IDLE' && wasPlaying && Date.now() - lastPlayAt > 5000) {
      wasPlaying = false; delete saved.positions[key(now)]; markSeen(now); playNext(1);
    }
  }); } catch (e) { /* el Chromecast todavía no está conectado */ }
}, 2000);

// "Continuar viendo" y "visto"
function touchRecent(it, pos) {
  if (pos < 30) return;
  const k = key(it);
  let e = saved.recent.filter(function(x) { return key(x) === k; })[0];
  if (!e) { e = { r: it.r, p: it.p }; saved.recent.unshift(e); }
  else if (saved.recent[0] !== e) { saved.recent.splice(saved.recent.indexOf(e), 1); saved.recent.unshift(e); }
  e.pos = Math.floor(pos); e.dur = Math.floor(it.duration || 0); e.t = Date.now();
  if (saved.recent.length > 30) saved.recent.length = 30;
  delete saved.seen[k];   // si lo retomó, ya no figura como visto
}
function markSeen(it) {
  const k = key(it);
  saved.seen[k] = 1;
  saved.recent = saved.recent.filter(function(x) { return key(x) !== k; });
}
function recentList() {
  const out = [];
  saved.recent.forEach(function(e) {
    if (out.length >= 12) return;
    try { if (!fs.existsSync(resolvePath(e.r, e.p))) return; } catch (x) { return; }
    out.push({ r: e.r, p: e.p, name: path.basename(e.p), pos: e.pos || 0, dur: e.dur || 0 });
  });
  return out;
}

function shuffleArr(a) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    const t = a[i]; a[i] = a[j]; a[j] = t;
  }
  return a;
}

function getBody(req) {
  return new Promise(function(ok) {
    let d = '';
    req.on('data', function(c) { d += c; });
    req.on('end', function() {
      try { ok(JSON.parse(d || '{}')); } catch (e) { ok({}); }
    });
  });
}

function send(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(obj));
}

// Las pantallas que cambian configuración o leen rutas de la PC solo se aceptan desde la propia PC
function isLocal(req) {
  const a = (req.socket && req.socket.remoteAddress) || '';
  return a === '127.0.0.1' || a === '::1' || a === '::ffff:127.0.0.1';
}

function publicConfig() {
  return {
    folders: cfg.folders || [],
    extra: saved.extra || [],
    gpu: String(cfg.gpu || 'auto').toLowerCase(),
    maxHeight: MAX_HEIGHT,
    maxFps: MAX_FPS,
    hwdecode: cfg.hwdecode !== false,
    pin: cfg.pin ? String(cfg.pin) : '',
    customIp: cfg.ip ? String(cfg.ip) : '',
    ip: ip,
    port: PORT,
    encoder: encoder,
    base: base
  };
}

function writeConfig() {
  try { fs.writeFileSync(path.join(base, 'config.json'), JSON.stringify(cfg, null, 2), 'utf8'); } catch (e) { console.log('No se pudo guardar config.json:', e.message); }
}

const server = http.createServer(async function(req, res) {
  const u = new URL(req.url, 'http://x');
  const q = u.searchParams;
  try {
    if (u.pathname === '/') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(fs.readFileSync(path.join(__dirname, 'index.html')));
    }
    if (u.pathname === '/desktop') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(fs.readFileSync(path.join(__dirname, 'desktop.html')));
    }

    if (u.pathname === '/stream') {
      if (q.get('k') !== streamKey) { res.writeHead(403); return res.end(); }
      const full = resolvePath(parseInt(q.get('r') || '0', 10), q.get('p'));
      const info = await probe(full);
      const t = parseFloat(q.get('t')) || 0;
      const a = parseInt(q.get('a') || '0', 10);
      const s = parseInt(q.get('s') || '-1', 10);
      // HDR / Dolby Vision con base HDR10: se convierte a SDR para que los colores no se vean lavados
      const hdrFix = saved.settings.hdrFix !== false && !!info.hdr && HAS_ZSCALE;
      if (saved.settings.hdrFix !== false && info.hdr && !HAS_ZSCALE) console.log('Aviso: este ffmpeg no tiene zscale/tonemap; el video HDR se verá con colores apagados.');
      if (info.dvp === 5) console.log('Aviso: Dolby Vision perfil 5 no tiene base HDR10; los colores pueden verse verdes o violetas.');
      // Solo se copia el video si ya es H.264 8-bit, <= 1080p y nivel <= 4.1 (lo que soporta el Chromecast 2)
      const copyV = info.vcodec === 'h264' && info.pix === 'yuv420p' && s < 0
                    && info.height > 0 && info.height <= MAX_HEIGHT
                    && info.level > 0 && info.level <= MAX_LEVEL
                    && !hdrFix
                    && (!info.fps || info.fps <= MAX_FPS + 0.5);
      // Si el video tiene más fps de los que soporta el Chromecast, se reduce a la mitad (o a la fracción necesaria)
      let targetFps = 0;
      if (info.fps > MAX_FPS + 0.5) targetFps = info.fps / Math.ceil(info.fps / MAX_FPS);
      const needScale = info.height > MAX_HEIGHT;
      let subName = null, subAss = false, fontsRel = null, imgSub = false;
      if (s >= 0) {
        const sb = subsOf(full, info)[s];
        if (sb && !sb.ext && IMG_SUB_RE.test(sb.codec || '')) {
          imgSub = true;   // PGS / VobSub / DVB: se dibuja con overlay, no hay archivo que extraer
        } else {
          subAss = respectAss(sb);
          if (sb && sb.ext) subName = await prepareExtSub(sb.file);
          else if (sb) subName = await extractSub(full, s, subAss);
          if (subName && subAss && !sb.ext) fontsRel = await extractFonts(full);
        }
      }
      // Sincronía: con +, los subtítulos aparecen más tarde; con −, más temprano
      const subDelay = saved.subDelays[parseInt(q.get('r') || '0', 10) + '|' + q.get('p')] || 0;
      const args = ['-v', 'error', '-stats', '-nostdin'];
      if (encoder !== 'cpu' && cfg.hwdecode !== false && !copyV) args.push('-hwaccel', 'auto');
      if (t > 0) args.push('-ss', String(t));
      args.push('-i', full);
      if (!(imgSub && !copyV)) args.push('-map', '0:v:0');
      args.push('-map', '0:a:' + a + '?');
      if (copyV) {
        args.push('-c:v', 'copy');
      } else {
        const filters = [];
        // Primero se reduce y después se dibujan los subtítulos (así se renderizan a 1080p y no a 4K)
        if (needScale) filters.push('scale=-2:' + MAX_HEIGHT);
        if (hdrFix) {
          const tin = info.transfer === 'arib-std-b67' ? 'arib-std-b67' : 'smpte2084';
          filters.push('zscale=tin=' + tin + ':min=bt2020nc:pin=bt2020:rin=tv:t=linear:npl=100', 'format=gbrpf32le', 'zscale=p=bt709',
                       'tonemap=tonemap=hable:desat=0', 'zscale=t=bt709:m=bt709:r=tv', 'format=yuv420p');
        }
        if (subName) {
          const fontSize = SUB_SIZES[saved.settings.subSize] || 16;
          // ASS con estilo propio: se dibuja tal cual (posición, fuente, color y tamaño originales), sin force_style
          const subOpts = subAss ? (fontsRel ? ':fontsdir=' + fontsRel : '') : ":force_style='" + forceStyle(fontSize) + "'";
          filters.push('setpts=PTS+(' + (t - subDelay).toFixed(3) + ')/TB', 'subtitles=' + subName + subOpts, 'setpts=PTS-STARTPTS');
        }
        if (imgSub) {
          // Subtítulo de imagen: se escala al ancho del video ya reducido y se pega abajo, centrado.
          // La sincronía mueve el flujo de subtítulos: con +, aparecen más tarde.
          const vw = needScale ? Math.max(2, Math.round(info.width * MAX_HEIGHT / info.height / 2) * 2) : info.width;
          let g = '[0:v:0]' + (filters.length ? filters.join(',') : 'null') + '[v0];[0:s:' + s + ']scale=' + vw + ':-2';
          if (subDelay) g += ',setpts=PTS+(' + subDelay.toFixed(3) + ')/TB';
          g += '[sb];[v0][sb]overlay=x=(W-w)/2:y=H-h:eof_action=pass' + (targetFps ? ',fps=' + targetFps.toFixed(3) : '') + '[vout]';
          args.push('-filter_complex', g, '-map', '[vout]');
        } else {
          if (targetFps) filters.push('fps=' + targetFps.toFixed(3));
          if (filters.length) args.push('-vf', filters.join(','));
        }
        args.push.apply(args, ENCODERS[encoder]);
        args.push.apply(args, PROFILE_ARGS[encoder]);
        args.push('-pix_fmt', 'yuv420p');
      }
      // Sonido: "diálogos" sube las voces al mezclar 5.1 a estéreo; "noche" además comprime los picos de volumen
      const amode = saved.settings.audioMode;
      if (amode !== 'normal') {
        const chn = (info.audio[a] && info.audio[a].channels) || 2;
        const af = [];
        if (chn > 2) af.push('aformat=channel_layouts=5.1', 'pan=stereo|FL=0.35*FL+0.85*FC+0.25*BL+0.2*LFE|FR=0.35*FR+0.85*FC+0.25*BR+0.2*LFE', 'volume=2.2');   // volume: compensa lo que baja la mezcla
        if (amode === 'night') af.push('acompressor=threshold=0.05:ratio=4:attack=20:release=300:makeup=1.5');
        af.push('alimiter=limit=0.95');
        args.push('-af', af.join(','));
      }
      args.push('-c:a', 'aac', '-b:a', '192k', '-ac', '2', '-movflags', 'frag_keyframe+empty_moov+default_base_moof', '-f', 'mp4', 'pipe:1');
      res.writeHead(200, { 'Content-Type': 'video/mp4', 'Access-Control-Allow-Origin': '*' });
      const ff = child_process.spawn(FFMPEG, args, { cwd: subDir });
      ff.stdout.pipe(res);
      ff.stderr.on('data', function(d) { process.stdout.write(d); });
      res.on('close', function() { ff.kill('SIGKILL'); });
      return;
    }

    if (u.pathname === '/api/login') {
      const b = await getBody(req);
      if (cfg.pin && String(b.pin) !== String(cfg.pin)) return send(res, 401, { error: 'PIN incorrecto' });
      const tokenHex = crypto.randomBytes(16).toString('hex');
      tokens.add(tokenHex);
      return send(res, 200, { token: tokenHex });
    }
    // El PIN protege el acceso desde otros dispositivos; la propia PC (app de escritorio) entra sin PIN
    if (cfg.pin && !isLocal(req) && !tokens.has(req.headers['x-token'])) return send(res, 401, { error: 'auth' });

    if (u.pathname === '/api/state') {
      const devicesArray = [];
      devices.forEach(function(val, key) { devicesArray.push(key); });
      return send(res, 200, { devices: devicesArray, device: curDev, folders: roots(), uploadsIdx: (cfg.folders || []).length, encoder: encoder, queue: saved.queue, qi: saved.qi, now: now, status: status, settings: saved.settings, playlists: saved.playlists, recent: recentList(), ip: ip, port: PORT });
    }

    if (u.pathname === '/api/ls') {
      const r = parseInt(q.get('r') || '0', 10);
      const p = q.get('p') || '';
      const dir = resolvePath(r, p);
      const ents = fs.readdirSync(dir, { withFileTypes: true });
      const dirs = [], files = [];
      for (let i = 0; i < ents.length; i++) {
        if (ents[i].isDirectory()) dirs.push(ents[i].name);
        else if (ents[i].isFile() && VIDEO_RE.test(ents[i].name)) {
          const n = ents[i].name;
          const itemPath = path.join(p, n);
          files.push({ name: n, p: itemPath, pos: saved.positions[r + '|' + itemPath] || 0, seen: !!saved.seen[r + '|' + itemPath] });
        }
      }
      dirs.sort();
      files.sort(function(a, b) { return a.name.localeCompare(b.name); });
      return send(res, 200, { dirs: dirs, files: files });
    }

    if (u.pathname === '/api/thumb') {
      const full = resolvePath(parseInt(q.get('r') || '0', 10), q.get('p'));
      const f = await thumbFor(full);
      if (!f) { res.writeHead(404); return res.end(); }
      res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Cache-Control': 'public, max-age=86400' });
      return fs.createReadStream(f).pipe(res);
    }

    if (u.pathname === '/api/find') return send(res, 200, findFile(q.get('name'), parseInt(q.get('size') || '0', 10)) || {});

    if (u.pathname === '/api/upload' && req.method === 'PUT') {
      const name = path.basename(q.get('name') || '');
      if (!VIDEO_RE.test(name)) return send(res, 400, { error: 'Formato no soportado' });
      const ws = fs.createWriteStream(path.join(uploadsDir, name));
      ws.on('finish', function() { send(res, 200, { r: (cfg.folders || []).length, p: name }); });
      ws.on('error', function(e) { send(res, 500, { error: e.message }); });
      req.pipe(ws);
      return;
    }

    if (u.pathname === '/api/info') {
      const full = resolvePath(parseInt(q.get('r') || '0', 10), q.get('p'));
      const info = await probe(full);
      return send(res, 200, Object.assign({}, info, { subs: publicSubs(subsOf(full, info)) }));
    }

    if (u.pathname === '/api/config' && req.method === 'GET') {
      if (!isLocal(req)) return send(res, 403, { error: 'Los ajustes solo se cambian desde la PC' });
      return send(res, 200, publicConfig());
    }

    const b = req.method === 'POST' ? await getBody(req) : {};
    if (u.pathname === '/api/device') { curDev = b.name; return send(res, 200, {}); }
    if (u.pathname === '/api/settings') {
      if (typeof b.autoSub === 'boolean') saved.settings.autoSub = b.autoSub;
      if (SUB_SIZES[b.subSize]) saved.settings.subSize = b.subSize;
      if (typeof b.assRespect === 'boolean') saved.settings.assRespect = b.assRespect;
      if (typeof b.hdrFix === 'boolean') saved.settings.hdrFix = b.hdrFix;
      if (AUDIO_MODES.indexOf(b.audioMode) >= 0) saved.settings.audioMode = b.audioMode;
      if (b.style && typeof b.style === 'object') saved.settings.style = cleanStyle(Object.assign({}, saved.settings.style, b.style));
      persist();
      return send(res, 200, saved.settings);
    }
    if (u.pathname === '/api/seen') {   // marcar / desmarcar como visto
      const it = { r: parseInt(b.r, 10), p: String(b.p || '') }, k = key(it);
      if (b.seen) { markSeen(it); delete saved.positions[k]; } else delete saved.seen[k];
      persist();
      return send(res, 200, {});
    }
    if (u.pathname === '/api/recent') {   // quitar de "Continuar viendo" (olvida también el minuto guardado)
      const it = { r: parseInt(b.r, 10), p: String(b.p || '') }, k = key(it);
      saved.recent = saved.recent.filter(function(x) { return key(x) !== k; });
      delete saved.positions[k];
      persist();
      return send(res, 200, {});
    }
    if (u.pathname === '/api/config') {
      if (!isLocal(req)) return send(res, 403, { error: 'Los ajustes solo se cambian desde la PC' });
      const oldRoots = roots();
      let gpuChanged = false;
      if (Array.isArray(b.folders)) cfg.folders = b.folders.map(String).filter(Boolean);
      if (Array.isArray(b.extra)) saved.extra = b.extra.map(String).filter(Boolean);
      if (typeof b.gpu === 'string' && (b.gpu === 'auto' || ENCODERS[b.gpu])) {
        gpuChanged = b.gpu !== String(cfg.gpu || 'auto').toLowerCase();
        cfg.gpu = b.gpu;
      }
      if (Number(b.maxHeight) > 0) cfg.maxHeight = Number(b.maxHeight);
      if (Number(b.maxFps) > 0) cfg.maxFps = Number(b.maxFps);
      if (typeof b.hwdecode === 'boolean') cfg.hwdecode = b.hwdecode;
      if (typeof b.pin === 'string') { if (b.pin.trim()) cfg.pin = b.pin.trim(); else delete cfg.pin; }
      if (typeof b.customIp === 'string') { if (b.customIp.trim()) cfg.ip = b.customIp.trim(); else delete cfg.ip; ip = pickIp(); }
      applyLimits();
      remapRoots(oldRoots, roots());
      writeConfig();
      persist();
      if (gpuChanged) await detectEncoder();
      return send(res, 200, publicConfig());
    }
    if (u.pathname === '/api/addpath') {
      if (!isLocal(req)) return send(res, 403, { error: 'Solo desde la PC' });
      const fp = path.resolve(String(b.path || ''));
      let st;
      try { st = fs.statSync(fp); } catch (e) { return send(res, 400, { error: 'No se encuentra: ' + fp }); }
      if (st.isDirectory()) {
        const r = rootFor(fp);
        return send(res, 200, { dir: true, r: r.r, p: r.p });
      }
      if (!VIDEO_RE.test(fp)) return send(res, 400, { error: 'Formato no soportado: ' + path.basename(fp) });
      const rs = roots();
      for (let i = 0; i < rs.length; i++) {
        const rel = relInside(rs[i], fp);
        if (rel) return send(res, 200, { r: i, p: rel });
      }
      const r = rootFor(path.dirname(fp));
      return send(res, 200, { r: r.r, p: path.join(r.p, path.basename(fp)) });
    }
    if (u.pathname === '/api/playlist') {
      const pls = saved.playlists;
      const cleanItems = function(a) {
        return (Array.isArray(a) ? a : []).filter(function(it) { return it && it.r != null && it.p; }).map(function(it) { return { r: it.r, p: it.p }; });
      };
      const cleanName = function(n, d) { return String(n || '').trim().slice(0, 80) || d; };
      if (b.action === 'create' || b.action === 'saveQueue') {
        const pl = { id: crypto.randomBytes(4).toString('hex'), name: cleanName(b.name, 'Playlist'), items: cleanItems(b.action === 'saveQueue' ? saved.queue : b.items) };
        pls.push(pl);
        persist();
        return send(res, 200, { id: pl.id });
      }
      const pl = pls.find(function(x) { return x.id === b.id; });
      if (!pl) return send(res, 404, { error: 'Esa playlist ya no existe' });
      if (b.action === 'rename') pl.name = cleanName(b.name, pl.name);
      else if (b.action === 'delete') pls.splice(pls.indexOf(pl), 1);
      else if (b.action === 'add') pl.items = pl.items.concat(cleanItems([{ r: b.r, p: b.p }]));
      else if (b.action === 'remove') { if (b.i >= 0 && b.i < pl.items.length) pl.items.splice(b.i, 1); }
      else if (b.action === 'move') {
        if (b.i >= 0 && b.i < pl.items.length && b.to >= 0 && b.to < pl.items.length) {
          const it = pl.items.splice(b.i, 1)[0];
          pl.items.splice(b.to, 0, it);
        }
      }
      else if (b.action === 'load') {
        let items = pl.items.map(function(it) { return { r: it.r, p: it.p }; });
        if (b.shuffle) shuffleArr(items);
        if (b.mode === 'append') {
          saved.queue = saved.queue.concat(items);
          persist();
          return send(res, 200, { n: items.length });
        }
        saved.queue = items;
        saved.qi = items.length && b.play ? 0 : -1;
        persist();
        if (b.play && items.length) {
          try { await startPlay(items[0], { start: 0 }); }
          catch (e) { return send(res, 200, { n: items.length, error: e.message }); }
        }
        return send(res, 200, { n: items.length });
      }
      persist();
      return send(res, 200, {});
    }
    if (u.pathname === '/api/subdelay') {
      if (!now) return send(res, 200, { delay: 0 });
      const k = key(now);
      let d = saved.subDelays[k] || 0;
      if (b.reset) d = 0;
      else if (typeof b.value === 'number') d = b.value;
      else if (typeof b.delta === 'number') d += b.delta;
      d = Math.max(-60, Math.min(60, Math.round(d * 10) / 10));
      if (d) saved.subDelays[k] = d; else delete saved.subDelays[k];
      now.delay = d;
      persist();
      if (now.sub >= 0) {   // se reinicia el video en el mismo punto para aplicar el cambio
        try { await startPlay(now, { start: Math.max(0, status.pos > 0 ? status.pos : now.offset), audio: now.audio, sub: now.sub }); }
        catch (e) { return send(res, 200, { delay: d, error: e.message }); }
      }
      return send(res, 200, { delay: d });
    }
    if (u.pathname === '/api/play') {
      await startPlay({ r: b.r, p: b.p }, { audio: b.audio, sub: b.sub, start: b.start });
      let kIdx = -1;
      const bKey = key({ r: b.r, p: b.p });
      for (let i = 0; i < saved.queue.length; i++) {
        if (key(saved.queue[i]) === bKey) { kIdx = i; break; }
      }
      if (kIdx >= 0) saved.qi = kIdx;
      persist();
      return send(res, 200, {});
    }
    if (u.pathname === '/api/control') {
      const dev = devices.get(curDev);
      if (!dev) return send(res, 400, { error: 'Sin dispositivo' });
      if (b.cmd === 'pause') dev.pause(function() {});
      else if (b.cmd === 'resume') dev.resume(function() {});
      else if (b.cmd === 'stop') { dev.stop(function() {}); now = null; wasPlaying = false; }
      else if (b.cmd === 'volume') {
        const lv = Math.max(0, Math.min(1, Number(b.value)));
        if (!isNaN(lv)) { lastVolume = Math.round(lv * 100) / 100; status.volume = lastVolume; dev.setVolume(lv, function() {}); }
      }
      else if (b.cmd === 'seek' && now) await startPlay(now, { start: Math.max(0, b.to), audio: now.audio, sub: now.sub });
      else if (b.cmd === 'next') await playNext(1);
      else if (b.cmd === 'prev') await playNext(-1);
      return send(res, 200, {});
    }
    if (u.pathname === '/api/queue') {
      if (b.action === 'add') saved.queue.push({ r: b.r, p: b.p });
      if (b.action === 'remove') { saved.queue.splice(b.i, 1); if (b.i < saved.qi) saved.qi--; }
      if (b.action === 'clear') { saved.queue = []; saved.qi = -1; }
      persist();
      return send(res, 200, {});
    }
    send(res, 404, {});
  } catch (e) {
    console.log(e.message);
    if (!res.headersSent) send(res, 500, { error: e.message });
  }
});

// "ready" avisa a la app de escritorio cuando el servidor ya está escuchando (o si el puerto está ocupado)
const ready = new Promise(function(ok, fail) {
  server.once('listening', ok);
  server.once('error', fail);
});
ready.catch(function(e) {
  console.log('No se pudo abrir el puerto ' + PORT + ': ' + e.message + ' (¿ya hay otro MKVCast abierto?)');
});
server.on('error', function(e) { console.log('Servidor:', e.message); });
server.listen(PORT, function() {
  console.log('MKVCast listo:  http://' + ip + ':' + PORT + '   (abre esa dirección en el celular)');
});

module.exports = { PORT: PORT, ready: ready, getIp: function() { return ip; } };
