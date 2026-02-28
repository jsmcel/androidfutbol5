# PC Futbol 5 - Ingeniería Inversa Completa

> **Creado:** 2026-02-27
> **Juego:** PC Fútbol 5.0 (Dinamic Multimedia, 1997)
> **Plataforma original:** MS-DOS + DOS4GW (modo protegido 32-bit)
> **Herramientas:** xxd, strings, Node.js v24, análisis binario manual

---

## Índice

1. [Arquitectura del Juego](#1-arquitectura-del-juego)
2. [Árbol de Archivos](#2-árbol-de-archivos)
3. [Ejecutables](#3-ejecutables)
4. [Formato PKF (Archivos empaquetados)](#4-formato-pkf)
5. [Formato DBC (Bases de datos)](#5-formato-dbc)
6. [Archivos DAT/Datos del juego](#6-archivos-dat)
7. [Strings y referencias internas](#7-strings-del-juego)
8. [Flujo de ejecución](#8-flujo-de-ejecución)

---

## 1. Arquitectura del Juego

PC Fútbol 5 es un juego de gestión de fútbol español de 1997 desarrollado por **Dinamic Multimedia**.
Usa el extensor **DOS4GW** (Watcom DOS/4G) para ejecutar código de 32 bits en MS-DOS.

### Stack tecnológico
- **Compilador:** Watcom C/C++ 16 y 32 bits (1988–1995)
- **Extensor DOS:** DOS/4GW Protected Mode Extender
- **Formato binario:** MZ (stub 16-bit) + LE (Linear Executable 32-bit)
- **Gráficos:** VESA 1.2+ requerido (mínimo 1MB VRAM)
- **Memoria:** Mínimo 7 MB XMS libres (8 MB RAM recomendado)
- **Disco:** 18 MB de espacio libre en HDD

---

## 2. Árbol de Archivos

```
FUTBOL5/
├── FUTBOL5.EXE       ← Launcher principal (16-bit Watcom, 28 KB)
├── INFODOS.EXE       ← Motor del juego (32-bit LE/DOS4GW, 1.46 MB)
├── DBASEDOS.DAT      ← Motor de BD del juego (32-bit LE disfrazado de .DAT, 1.7 MB)
├── MANDOS.DAT        ← Aplicación de gestión (32-bit LE, 5.1 MB - el más grande)
├── TESTSYS.DAT       ← Detector de requisitos del sistema (32-bit LE, 40 KB)
├── DISCO.EXE         ← Creador de disco de arranque (16-bit Borland C++, 15 KB)
├── DOS4GW.EXE        ← Runtime DOS/4GW extender (265 KB)
├── PROQUINI.EXE      ← Lanzador de ProQuiniela (4 KB)
├── FUTBOL5.INI       ← Configuración (texto plano)
├── INFOSURF.ARG/.WCF ← Configuración del navegador interno
│
├── BMP.PKF           ← Pack de gráficos BMP (183 KB, 63 archivos)
├── DAT.PKF           ← Pack de datos DAT (786 KB, 15 archivos)
├── DATSIM.PKF        ← Pack de simulación (5.0 MB)
├── IMG.PKF           ← Pack de imágenes (1.75 MB, 32 archivos)
├── RECURSOS.PKF      ← Pack de recursos (5.65 MB, 32 archivos)
├── RC_DBASE.PKF      ← Pack de recursos de BD (2.34 MB, 32 archivos)
│
├── DBDAT/
│   ├── EQUIPOS.PKF   ← Imágenes de equipos (2.69 MB, 32 archivos por banco)
│   ├── BANDERAS.PKF  ← Banderas nacionales (68 KB, 32 archivos)
│   ├── CAMISAS.PKF   ← Camisetas de equipos (126 KB, 32 archivos)
│   ├── MINIBAND.PKF  ← Banderas miniatura (24 KB)
│   ├── MINIENTR.PKF  ← Entrenadores miniatura (49 KB)
│   ├── MINIESC.PKF   ← Escudos miniatura (1.6 MB)
│   ├── NANOESC.PKF   ← Escudos nano (436 KB)
│   ├── RIDIESC.PKF   ← Escudos ridículo (246 KB)
│   ├── LIGA.DBC      ← BD de la Liga española (22 KB)
│   ├── COPAREY.DBC   ← BD de la Copa del Rey (14 KB)
│   ├── UEFA.DBC      ← BD de la UEFA (3 KB)
│   ├── EUROPA.DBC    ← BD de la Copa de Europa (8 KB)
│   ├── RECOPA.DBC    ← BD de la Recopa (3 KB)
│   ├── ARBITROS.DBC  ← BD de árbitros (4 KB)
│   ├── ROBIN0.DBC    ← BD Robin (50 KB)
│   ├── JORN101-116.DBC ← Jornadas División 1 (11-13 KB c/u)
│   ├── JORN201-214.DBC ← Jornadas División 2 (1.9 KB c/u - vacías)
│   ├── JORNAD1M.DBC  ← Jornada 1M (3 KB)
│   ├── JORNAD2M.DBC  ← Jornada 2M (2.3 KB)
│   ├── SCESPANA.DBC  ← Selección España (7 KB)
│   ├── SCEUROPA.DBC  ← Selección Europa (13 KB)
│   ├── SCINTERC.DBC  ← Selección Intercontinental (18 KB)
│   └── NOMBRES.DAT   ← Lista de nombres de jugadores (4.4 KB)
│
├── PLAYERS/
│   └── PLAY0000.DAT  ← Partida guardada (con copyright DINAMIC)
├── WINFONTS/*.FNT    ← Fuentes bitmap custom
├── CURSORES/*.ANI    ← Cursores animados
├── TACTICS/          ← Táctica del jugador
├── NOTAS/            ← Notas del jugador
└── INFOFUT/          ← Sistema de ayuda HTML interno
    └── INFOGRAF.PKF  ← Gráficos del help
```

---

## 3. Ejecutables

Ver archivo detallado: [ejecutables.md](ejecutables.md)

### Resumen rápido
| Archivo | Tipo | Compilador | Tamaño | Función |
|---------|------|------------|--------|---------|
| FUTBOL5.EXE | MZ 16-bit DOS | Watcom C++16 | 28 KB | Launcher/selector modo |
| INFODOS.EXE | MZ+LE 32-bit | Watcom C++32 | 1.46 MB | Motor principal del juego |
| DBASEDOS.DAT | MZ+LE 32-bit* | Watcom C++32 | 1.7 MB | Motor de base de datos |
| MANDOS.DAT | MZ+LE 32-bit* | Watcom C++32 | 5.1 MB | Aplicación principal (mayor) |
| TESTSYS.DAT | MZ+LE 32-bit* | Watcom C++32 | 40 KB | Detector requisitos sistema |
| DISCO.EXE | MZ 16-bit DOS | Borland C++ | 15 KB | Creador disco arranque |

> *Nota: Archivos `.DAT` son en realidad ejecutables MZ/LE disfrazados con extensión .DAT

---

## 4. Formato PKF

Ver archivo detallado: [formato_PKF.md](formato_PKF.md)

---

## 5. Formato DBC

Ver archivo detallado: [formato_DBC.md](formato_DBC.md)

---

## 6. Archivos DAT

Ver archivo detallado: [formato_DAT.md](formato_DAT.md)

---

## 7. Strings del Juego

Ver archivo detallado: [strings_futbol5.txt](strings_futbol5.txt) y [base_datos.md](base_datos.md)

---

## 8. Flujo de Ejecución

```
Usuario ejecuta FUTBOL5.EXE
    ↓
FUTBOL5.EXE (16-bit Watcom):
    1. Lee OPT.TXT (opciones de inicio)
    2. Ejecuta TESTSYS.DAT → verifica ratón, VESA, XMS, espacio disco
    3. Si OK → lanza INFODOS.EXE (motor principal) ó DBASEDOS.DAT
    4. Configura DOS4GVM (memoria virtual): maxmen=16384KB swapmin=16384KB
    5. Referencia a ACTDOS.EXE (actualizaciones) y MANDOS.DAT
    ↓
INFODOS.EXE (32-bit LE + DOS4GW):
    - Lee PKFs (BMP, DAT, IMG, RECURSOS, RC_DBASE)
    - Lee DBCs de DBDAT/ para datos de liga/copa
    - Muestra interfaz HTML de INFOFUT/
    ↓
DBASEDOS.DAT (32-bit LE, el cerebro del juego):
    - Carga WinFonts/*.fnt y CURSORES/*.ANI
    - Lee DBDAT/arbitros.dbc, liga.dbc, etc.
    - Rutas de imágenes: DBDAT/BIGESC/%s.bmp, DBDAT/EQUIPOS/%s.dbc
    - Escribe ERRORES.TXT si hay errores, MEMORIA.TXT para debug
    - Nombres de países: ALBANIA, ALEMANIA, ARGENTINA, ...
```

---

## Notas de Reversado

- Los archivos PKF tienen los nombres de entrada **ofuscados** (no simples XOR)
- Los archivos DBC tienen datos binarios, algunos con header de copyright en texto plano
- MANDOS.DAT (5.1 MB) es el ejecutable más grande y probablemente contiene todo el motor + assets embebidos
- Los desarrolladores incluyen referencias a debug: "Markin's Mem", "Pabloari's Mem", "David's Mem", "Leo's Mem"
- El juego detecta CPUs Intel: busca la string "GenuineIntel"
- `DBDAT\EQUIPOS\%s.dbc` sugiere un DBC por equipo en un subdirectorio no incluido en el ZIP original
