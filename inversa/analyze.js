// PC Futbol 5 - Analizador de formatos binarios
const fs = require('fs');
const path = require('path');

const GAME_DIR = 'C:\\Users\\Jose-Firebat\\proyectos\\pcfutbol\\pcf55\\FUTBOL5\\FUTBOL5';

// ============================================================
// ANÁLISIS FORMATO PKF
// ============================================================
function analyzePKF(filepath) {
  const data = fs.readFileSync(filepath);
  const name = path.basename(filepath);

  console.log('\n' + '='.repeat(70));
  console.log('PKF:', name, '| Tamaño:', data.length, 'bytes');

  // Header (primeros 0xE0 bytes)
  console.log('\n-- HEADER --');
  console.log('Bytes 00-0F (ID específico):', data.slice(0, 16).toString('hex'));
  console.log('Bytes 10-13 (MAGIC PKF):     ', data.slice(0x10, 0x14).toString('hex'));
  console.log('Bytes 14-17 (val14):         ', data.readUInt32LE(0x14).toString(16).padStart(8,'0'), '=', data.readUInt32LE(0x14));
  console.log('Bytes 18-1B (val18):         ', data.readUInt32LE(0x18).toString(16).padStart(8,'0'));
  console.log('Bytes 1C-1F (val1C):         ', data.readUInt32LE(0x1C).toString(16).padStart(8,'0'));
  console.log('Bytes E8-EC (pre-dir):       ', data.slice(0xE8, 0xED).toString('hex'));

  // El byte en 0xE8 es tipo 0x04 = bloque especial
  // Los 4 bytes siguientes podrían ser tamaño o count
  const preDir = data.readUInt32LE(0xE9);
  console.log('Val @ E9 (LE):               ', preDir.toString(16), '=', preDir);

  // Buscar primer marcador de entrada (0x02) después de 0xEC
  let firstEntryPos = -1;
  for (let i = 0xEC; i < 0x200; i++) {
    if (data[i] === 0x02) { firstEntryPos = i; break; }
  }

  if (firstEntryPos < 0) {
    console.log('No se encontró primer entry (0x02)');
    return;
  }

  console.log('\nPrimer entry en offset:', firstEntryPos.toString(16));

  // Parsear entries de 38 bytes: 1+11+9+5+4+4+4
  const entries = [];
  let i = firstEntryPos;
  while (i < data.length - 40) {
    const marker = data[i];
    if (marker === 0x02) {
      const nameBytes = data.slice(i+1, i+12);  // 11 bytes nombre ofuscado
      const constA = data.slice(i+12, i+14).toString('hex');  // 2 bytes variable por PKF
      const constB = data.slice(i+14, i+21).toString('hex');  // 7 bytes constantes
      const zeros  = data.slice(i+21, i+26).toString('hex');  // 5 bytes ceros
      const offset = data.readUInt32LE(i+26);
      const size   = data.readUInt32LE(i+30);
      const flags  = data.readUInt32LE(i+34);
      entries.push({ pos: i, nameBytes, constA, constB, offset, size, flags });
      i += 38;
    } else if (marker === 0x04) {
      console.log('Terminador 0x04 en offset:', i.toString(16));
      break;
    } else {
      console.log('Byte inesperado', marker.toString(16), 'en offset', i.toString(16));
      break;
    }
  }

  console.log('Total entries:', entries.length);

  if (entries.length > 0) {
    // Verificar que const7 es siempre el mismo dentro del PKF
    const constASet = new Set(entries.map(e => e.constA));
    const constBSet = new Set(entries.map(e => e.constB));
    const zerosSet = new Set(entries.map(e => e.zeros));
    console.log('Valores únicos constA (2B):', [...constASet]);
    console.log('Valores únicos constB (7B):', [...constBSet]);

    // Mostrar primeras 5 y últimas 3 entries
    console.log('\n-- ENTRIES --');
    const show = [...entries.slice(0,5)];
    if (entries.length > 5) show.push(...entries.slice(-3));
    show.forEach((e, n) => {
      const idx = n < 5 ? n : entries.length - 3 + (n - 5);
      console.log(`  [${String(idx).padStart(4)}] @${e.pos.toString(16).padStart(6,'0')} | constA:${e.constA} constB:${e.constB} | offset:${e.offset.toString(16).padStart(8,'0')} | size:${String(e.size).padStart(8)} | flags:${e.flags.toString(16)} | name:${e.nameBytes.toString('hex')}`);
    });

    // Verificar consecutividad de offsets
    let consistent = true;
    for (let j = 1; j < entries.length; j++) {
      const expected = entries[j-1].offset + entries[j-1].size;
      if (entries[j].offset !== expected) {
        console.log(`ATENCIÓN: gap en entry ${j}: expected ${expected.toString(16)} got ${entries[j].offset.toString(16)}`);
        consistent = false;
      }
    }
    if (consistent) console.log('\nOffsets CONSECUTIVOS ✓ (sin gaps entre archivos)');

    const lastEntry = entries[entries.length-1];
    const dataEnd = lastEntry.offset + lastEntry.size;
    console.log(`Data end: 0x${dataEnd.toString(16)} | File size: 0x${data.length.toString(16)} | Match: ${dataEnd === data.length ? '✓' : '✗ diff=' + (data.length - dataEnd)}`);
  }

  return entries;
}

// ============================================================
// ANÁLISIS FORMATO DBC
// ============================================================
function analyzeDBC(filepath) {
  const data = fs.readFileSync(filepath);
  const name = path.basename(filepath);

  console.log('\n' + '='.repeat(70));
  console.log('DBC:', name, '| Tamaño:', data.length, 'bytes');

  // Intentar leer el header de texto
  const head16 = data.slice(0, Math.min(60, data.length));
  const headStr = head16.toString('latin1').replace(/[^\x20-\x7e]/g, '·');
  console.log('Inicio (ASCII):', headStr);

  // Detectar si empieza con nombre de archivo (ASCII imprimible)
  const isPrintableStart = head16[0] >= 0x41 && head16[0] <= 0x5A;
  if (isPrintableStart) {
    console.log('¡Comienza con nombre de archivo en texto plano!');
    // Buscar copyright
    const cpIdx = head16.indexOf('Copyright');
    if (cpIdx >= 0) {
      const cpEnd = data.indexOf(0x00, cpIdx);
      const copyright = data.slice(cpIdx, cpEnd > 0 ? cpEnd : cpIdx+50).toString('latin1').replace(/[^\x20-\x7e]/g, '·');
      console.log('Copyright:', copyright);
      console.log('Datos comienzan en offset:', cpEnd + 1);
      // Mostrar primeros bytes de datos
      const dataStart = cpEnd + 1;
      console.log('Primeros bytes de datos:', data.slice(dataStart, dataStart+32).toString('hex'));
    }
  } else {
    console.log('Datos codificados desde el inicio');
    console.log('Primeros 32 bytes:', data.slice(0,32).toString('hex'));
  }
}

// ============================================================
// ANÁLISIS EXEs
// ============================================================
function analyzeEXE(filepath) {
  const data = fs.readFileSync(filepath);
  const name = path.basename(filepath);

  console.log('\n' + '='.repeat(70));
  console.log('EXE:', name, '| Tamaño:', data.length, 'bytes');

  if (data[0] === 0x4D && data[1] === 0x5A) {
    console.log('Formato MZ (DOS/Windows)');
    const e_cblp = data.readUInt16LE(2);
    const e_cp = data.readUInt16LE(4);
    const e_crlc = data.readUInt16LE(6);
    const e_cparhdr = data.readUInt16LE(8);
    const e_ip = data.readUInt16LE(0x14);
    const e_cs = data.readUInt16LE(0x16);
    const e_lfanew = data.readUInt32LE(0x3C);

    const fileSize = (e_cp - 1) * 512 + e_cblp;
    console.log('Tamaño calculado:', fileSize, '| Real:', data.length);
    console.log('Relocaciones:', e_crlc, '| Header párrafos:', e_cparhdr);
    console.log('Entry point CS:IP =', e_cs.toString(16) + ':' + e_ip.toString(16));
    console.log('PE/LE offset (@3C):', e_lfanew.toString(16), '=', e_lfanew);

    if (e_lfanew > 0 && e_lfanew < data.length - 4) {
      const sig = data.slice(e_lfanew, e_lfanew+4).toString('ascii').replace(/\0/g,'.');
      console.log('Signature @PE offset:', sig, '(' + data.slice(e_lfanew, e_lfanew+4).toString('hex') + ')');
    }
  }

  // Extraer strings legibles (ASCII 5+ chars)
  const strings = [];
  let current = '';
  for (let b = 0; b < data.length; b++) {
    const c = data[b];
    if (c >= 0x20 && c <= 0x7E) {
      current += String.fromCharCode(c);
    } else {
      if (current.length >= 5) strings.push(current);
      current = '';
    }
  }
  if (current.length >= 5) strings.push(current);

  console.log('Strings encontradas:', strings.length);
  console.log('Muestra (primeras 50):');
  strings.slice(0, 50).forEach((s, i) => console.log(`  [${i}] ${s}`));
}

// ============================================================
// ANÁLISIS DBASEDOS.DAT
// ============================================================
function analyzeDBASEDOS(filepath) {
  const data = fs.readFileSync(filepath);
  console.log('\n' + '='.repeat(70));
  console.log('DBASEDOS.DAT | Tamaño:', data.length, 'bytes');

  if (data[0] === 0x4D && data[1] === 0x5A) {
    console.log('¡ATENCIÓN: Es un ejecutable MZ disfrazado de .DAT!');
    const e_cblp = data.readUInt16LE(2);
    const e_cp = data.readUInt16LE(4);
    const fileSize = (e_cp - 1) * 512 + e_cblp;
    console.log('Tamaño calculado:', fileSize, '| Real:', data.length);
  }

  // Buscar strings
  const strings = [];
  let current = '';
  for (let b = 0; b < data.length; b++) {
    const c = data[b];
    if (c >= 0x20 && c <= 0x7E) {
      current += String.fromCharCode(c);
    } else {
      if (current.length >= 5) strings.push({str: current, off: b - current.length});
      current = '';
    }
  }
  console.log('Strings en DBASEDOS.DAT:');
  strings.filter(s => s.str.length >= 4).forEach(s => console.log(`  @${s.off.toString(16).padStart(6,'0')}: ${s.str}`));
}

// ============================================================
// MAIN
// ============================================================
console.log('=== PC FUTBOL 5 - INGENIERÍA INVERSA ===\n');

// Analizar PKFs
const pkfFiles = [
  'BMP.PKF', 'DAT.PKF', 'IMG.PKF', 'RECURSOS.PKF', 'RC_DBASE.PKF',
  'DBDAT\\EQUIPOS.PKF', 'DBDAT\\BANDERAS.PKF', 'DBDAT\\CAMISAS.PKF'
];
pkfFiles.forEach(f => {
  try { analyzePKF(path.join(GAME_DIR, f)); }
  catch(e) { console.log('Error en', f, ':', e.message); }
});

// Analizar DBCs
const dbcFiles = ['DBDAT\\LIGA.DBC','DBDAT\\ARBITROS.DBC','DBDAT\\JORN101.DBC','DBDAT\\COPAREY.DBC'];
dbcFiles.forEach(f => {
  try { analyzeDBC(path.join(GAME_DIR, f)); }
  catch(e) { console.log('Error en', f, ':', e.message); }
});

// Analizar EXEs
['FUTBOL5.EXE', 'INFODOS.EXE', 'DISCO.EXE'].forEach(f => {
  try { analyzeEXE(path.join(GAME_DIR, f)); }
  catch(e) { console.log('Error en', f, ':', e.message); }
});

// Analizar DBASEDOS.DAT
try { analyzeDBASEDOS(path.join(GAME_DIR, 'DBASEDOS.DAT')); }
catch(e) { console.log('Error:', e.message); }
