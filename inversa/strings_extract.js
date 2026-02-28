const fs = require('fs');
const path = require('path');

const BASE = 'C:\\Users\\Jose-Firebat\\proyectos\\pcfutbol\\pcf55\\FUTBOL5\\FUTBOL5';

function extractStrings(filename, minLen) {
  const data = fs.readFileSync(path.join(BASE, filename));
  const strings = [];
  let cur = '', off = 0;
  for (let i = 0; i < data.length; i++) {
    const c = data[i];
    if (c >= 0x20 && c <= 0x7E) {
      if (!cur) off = i;
      cur += String.fromCharCode(c);
    } else {
      if (cur.length >= minLen) strings.push({str: cur, off});
      cur = '';
    }
  }
  if (cur.length >= minLen) strings.push({str: cur, off});
  return strings;
}

// Game strings filter
function isGameString(s) {
  const t = s.str;
  if (/[A-Z][a-z]/.test(t)) return true;
  if (/\.[A-Z]{2,3}$/.test(t)) return true;
  if (/\d{4}/.test(t)) return true;
  if (/[A-Z]{5,}/.test(t)) return true;
  if (/Copyright/.test(t)) return true;
  if (/Dinamic/.test(t)) return true;
  return false;
}

console.log('=== STRINGS DE DBASEDOS.DAT ===');
const dbstrings = extractStrings('DBASEDOS.DAT', 6).filter(isGameString);
dbstrings.slice(0, 200).forEach(s => console.log('  @' + s.off.toString(16).padStart(8,'0') + ': ' + s.str));

console.log('\n=== STRINGS DE TESTSYS.DAT ===');
const tsstrings = extractStrings('TESTSYS.DAT', 6);
tsstrings.forEach(s => console.log('  @' + s.off.toString(16).padStart(8,'0') + ': ' + s.str));

console.log('\n=== STRINGS DE FUTBOL5.EXE (completo) ===');
const f5strings = extractStrings('FUTBOL5.EXE', 6);
f5strings.forEach(s => console.log('  @' + s.off.toString(16).padStart(8,'0') + ': ' + s.str));

// PKF analysis: second half check
console.log('\n=== ANALISIS SEGUNDA MITAD PKF (BMP.PKF) ===');
const bmp = fs.readFileSync(path.join(BASE, 'BMP.PKF'));
// Data directory says last data ends at 0x1A744
// What's at 0x1A744?
const midpoint = 0x1a744;
console.log('Filesize:', bmp.length, '(', bmp.length.toString(16), ')');
console.log('First data section ends at:', midpoint.toString(16));
console.log('Remaining bytes:', bmp.length - midpoint, '(', (bmp.length - midpoint).toString(16), ')');
console.log('Bytes at midpoint:');
const chunk = bmp.slice(midpoint, midpoint + 32);
console.log(chunk.toString('hex'));
// Try XOR 0xAD (last byte of RIFF marker?)
const xored = Buffer.from(chunk.map((b, i) => b ^ 0xAD));
console.log('XOR 0xAD:', xored.toString('hex'), '->', xored.toString('latin1').replace(/[^\x20-\x7e]/g, '.'));

// Check if second half has PKF structure too
const halfOffset = Math.floor(bmp.length / 2);
console.log('\nBytes at file midpoint (', halfOffset.toString(16), '):');
console.log(bmp.slice(halfOffset, halfOffset + 32).toString('hex'));
