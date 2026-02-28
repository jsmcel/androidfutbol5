# Formato PKF - PC Fútbol 5

> Formato propietario de Dinamic Multimedia para empaquetar múltiples archivos en uno.
> Extensión: `.PKF` (PacK File, presumiblemente)

---

## Estructura General

Un archivo PKF contiene:
1. **Cabecera global** (237 bytes = 0xED)
2. **Uno o más bancos** de hasta 32 entradas cada uno
3. **Datos** de los archivos almacenados

```
[CABECERA 237B] [BANCO_0_DIR] [DATOS_0] [BANCO_1_DIR] [DATOS_1] ...
```

---

## Cabecera Global (0x00 - 0xEC)

```
Offset  Tamaño  Descripción
0x00    16      ID específico del PKF (varía por archivo, parcialmente encriptado)
0x10    4       MAGIC: e0 de e0 e3  (CONSTANTE en todos los PKF)
0x14    4       uint32 LE = 0x000001E8 = 488  (CONSTANTE en todos los PKF)
                  → Significado: desconocido (posible versión o count total)
0x18    4       uint32 LE = 0x00000000  (siempre cero)
0x1C    4       uint32 LE = varía por archivo (posible tamaño total de datos)
0x20    0xC8    Padding de ceros (200 bytes)
0xE8    5       Pre-directorio: 04 ED 00 00 00
                  → Byte 0: 0x04 = marcador de tipo "bloque especial"
                  → Bytes 1-4: 0x000000ED = 237 = offset del inicio del directorio
```

### Valores del campo 0x1C por archivo
| Archivo | val1C (hex) | val1C (dec) |
|---------|-------------|-------------|
| BMP.PKF | 0x0004E800 | 321,536 |
| DAT.PKF | 0x0004E800 | 321,536 |
| IMG.PKF | 0x00018D85 | 101,765 |
| RECURSOS.PKF | 0x00019683 | 104,067 |
| EQUIPOS.PKF | 0x0004E800 | 321,536 |

---

## Estructura de un Banco (Directorio)

Cada banco comienza en el offset señalado y contiene:
- Hasta 32 entradas de 38 bytes cada una
- Un terminador de 5 bytes

### Primer banco siempre en offset 0xED = 237

### Entrada de Directorio (38 bytes)

```
Byte    Tamaño  Descripción
0       1       Marcador: 0x02 (entrada normal)
1-11    11      Nombre de archivo ofuscado (8+3 truncado a 11 chars)
                  → Encodificación no determinada (no simple XOR)
12-13   2       constA: varía por entrada y PKF (posible parte del nombre enc.)
14-20   7       constB: sufijo parcialmente constante
                  → Últimos bytes de constB siempre terminan en 0x...e8
21-25   5       Ceros: 00 00 00 00 00
26-29   4       uint32 LE: offset absoluto del dato en el PKF
30-33   4       uint32 LE: tamaño del dato en bytes
34-37   4       uint32 LE: flags
                  → 0x01 = archivo normal
                  → 0x02 = tipo desconocido (aparece en RECURSOS.PKF)
                  → 0x05 = tipo desconocido (aparece en IMG.PKF)
```

### Terminador de Banco (5 bytes)

```
Byte 0: 0x04 (marcador terminador)
Bytes 1-4: valor uint32 LE (significado desconocido, varía)
```

### Hexdump de entrada típica (BMP.PKF, entry 0)

```
Offset 0xED:
02 9d 81 f1 ba 41 1a 0e  60 1f 10 03 f8 d7 af a2
^  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^ ^^^^
|  |--nombre ofuscado 11B--|  |constA| |--constB--
marcador=0x02

e0 fa df a3 e8 00 00 00  00 00 b2 05 00 00 f8 2b
^^^^^^^^^^^    ^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^
|--constB cont.|  |---zeros 5B---|  |offset=0x05B2|

00 00 01 00 00 00
^^^^^^^^^ ^^^^^^^^
|size=11256|flags=1|
```

---

## Datos del PKF

Los datos de los archivos son consecutivos:
- El primer bloque de datos comienza siempre en offset **0x5B2 = 1458**
- Cada entrada en el directorio apunta con offset absoluto al inicio de su dato
- Los offsets son CONSECUTIVOS sin gaps (verificado en múltiples PKFs)

### Verificación de consecutividad
```
Entry[n].offset + Entry[n].size == Entry[n+1].offset  ← siempre se cumple
```

---

## Archivos Multi-Banco

Algunos PKF contienen **dos bancos** de entradas:

| PKF | Banco 0 (entries) | Banco 1 (entries) | Total |
|-----|-------------------|-------------------|-------|
| BMP.PKF | 32 | 31 | **63** |
| Otros | 32 | — | 32 |

El banco 1 comienza inmediatamente después de que termina el último dato del banco 0.

### BMP.PKF - Estructura de dos bancos
```
0x000000ED: Directorio banco 0 (32 entradas × 38 bytes = 1216 bytes)
0x000005AD: Terminador banco 0 (5 bytes)
0x000005B2: Datos banco 0 (comienzan aquí)
  ...
0x0001A744: Directorio banco 1 (31 entradas × 38 bytes ≈ 1178 bytes)
0x0001ABDE: Terminador banco 1 (5 bytes)
0x0001AC09: Datos banco 1 (comienzan aquí)
  ...
0x0002CBC1: Fin del archivo
```

---

## Archivos PKF y su contenido probable

| PKF | Tamaño | Entradas | Contenido probable |
|-----|--------|----------|--------------------|
| BMP.PKF | 183 KB | 63 | Gráficos BMP pequeños |
| DAT.PKF | 786 KB | 15 | Datos binarios varios |
| DATSIM.PKF | 5.0 MB | ? | Datos de simulación (el mayor) |
| IMG.PKF | 1.75 MB | 32 | Imágenes del juego |
| RECURSOS.PKF | 5.65 MB | 32 | Recursos varios (UI, etc.) |
| RC_DBASE.PKF | 2.34 MB | 32 | Recursos de base de datos |
| EQUIPOS.PKF | 2.69 MB | 32 | Escudos de equipos (grandes) |
| BANDERAS.PKF | 68 KB | 32 | Banderas (696 bytes c/u = 32×32 px?) |
| CAMISAS.PKF | 126 KB | 32 | Camisetas (2944 bytes c/u) |
| MINIBAND.PKF | 24 KB | ? | Banderas miniatura |
| MINIENTR.PKF | 49 KB | ? | Entrenadores miniatura |
| MINIESC.PKF | 1.6 MB | ? | Escudos miniatura |
| NANOESC.PKF | 436 KB | ? | Escudos nano |
| RIDIESC.PKF | 246 KB | ? | Escudos pequeños |

---

## Ofuscación del Nombre de Archivo

Los 11 bytes del nombre (posiciones 1-11 de cada entrada) están ofuscados.
El esquema de ofuscación **no es un XOR simple** con clave constante:
- Distintas entradas del mismo PKF tienen la misma `constA` (bytes 12-13)
- Distintas entradas de distintos PKF tienen `constA` diferente
- Los bytes `14-20` tienen un sufijo parcialmente constante: `?? a2 e0 fa df a3 e8`

**Hipótesis**: la ofuscación usa una clave rodante (rolling XOR) donde la clave se actualiza por CRC/hash del nombre anterior, produciendo los patrones observados.

---

## Parser PKF en Node.js

```javascript
const fs = require('fs');

function parsePKF(filepath) {
  const data = fs.readFileSync(filepath);

  // Verificar magic
  if (data.readUInt32BE(0x10) !== 0xE0DEE0E3) {
    throw new Error('No es un PKF válido');
  }

  const banks = [];
  let bankOffset = 0xED; // Primer banco siempre en 0xED

  while (bankOffset < data.length) {
    const entries = [];
    let pos = bankOffset;

    while (pos < data.length - 40) {
      const marker = data[pos];
      if (marker === 0x02) {
        const nameBytes = data.slice(pos+1, pos+12);  // 11 bytes ofuscados
        const dataOffset = data.readUInt32LE(pos+26);
        const dataSize = data.readUInt32LE(pos+30);
        const flags = data.readUInt32LE(pos+34);
        entries.push({ nameBytes, dataOffset, dataSize, flags });
        pos += 38;
      } else if (marker === 0x04) {
        pos += 5; // Terminator
        break;
      } else {
        break;
      }
    }

    if (entries.length === 0) break;

    banks.push(entries);

    // El siguiente banco comienza donde terminan los datos de este
    const lastEntry = entries[entries.length-1];
    bankOffset = lastEntry.dataOffset + lastEntry.dataSize;
  }

  return banks;
}
```

---

## Pendiente de Investigación

- [ ] Algoritmo de ofuscación del nombre de archivo
- [ ] Significado exacto del campo val14 (0x01E8 = 488)
- [ ] Significado del campo val1C (varía por archivo)
- [ ] Formato de los datos dentro de las entradas (¿están comprimidos?)
- [ ] Diferencia entre flags=1, flags=2, flags=5
