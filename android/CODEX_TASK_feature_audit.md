# Tarea Codex: Auditoría de features vs PC Fútbol 5 original

## Objetivo
Estudiar qué features tenía PC Fútbol 5 (Dinamic Multimedia, 1997) y hacer un
informe honesto de qué hemos implementado, qué falta, y qué es irrelevante para
una versión Android 2025/26.

## Fuentes de referencia disponibles en el repo

Lee TODO lo siguiente antes de redactar el informe:

1. `docs/pcf55_rewrite_architecture.md` — arquitectura planificada con todos los subsistemas
2. `docs/pcf55_reverse_spec.md` — especificación completa del original (ingeniería inversa)
3. `inversa/README.md` — hallazgos de RE sobre el ejecutable y formatos
4. `inversa/formato_PKF.md` — formato de archivos del juego
5. `tools/pcf55-updater/out/` — datos extraídos (para entender qué datos había)

Lee también el código actual de la app Android para entender qué está implementado:
- `core/data/src/main/kotlin/com/pcfutbol/core/data/` — entidades y seed
- `competition-engine/src/main/kotlin/com/pcfutbol/competition/` — motor de competición
- `match-sim/src/main/kotlin/com/pcfutbol/matchsim/` — simulador
- `manager-economy/src/main/kotlin/com/pcfutbol/economy/` — economía
- `promanager/src/main/kotlin/com/pcfutbol/promanager/` — ProManager
- `ui/src/main/kotlin/com/pcfutbol/ui/` — pantallas y ViewModels

## Output esperado

Genera el archivo `FEATURE_AUDIT.md` en el directorio `android/` con:

### 1. Features del PC Fútbol 5 original (lista completa)
Agrupa por categoría:
- Modos de juego (Liga/Manager, ProManager, Proquinielas, Selecciones)
- Gestión de equipo (plantilla, tácticas, fichajes, contratos)
- Motor de competición (liga, copa, europa)
- Simulador de partidos (estadísticas, eventos, modo TV)
- Economía (presupuesto, salarios, mercado)
- Carrera ProManager (ofertas, prestigio, historial)
- UI/UX (pantallas, efectos visuales, música/sonidos)
- Datos (equipos, jugadores, estadios)

### 2. Estado de implementación por feature
Para cada feature, indica:
- ✅ IMPLEMENTADO — funciona en la app Android actual
- 🔶 PARCIAL — implementado pero incompleto o simplificado
- ❌ FALTANTE — no implementado (con prioridad: ALTA/MEDIA/BAJA)
- 🚫 DESCARTADO — no aplica para versión Android (ej: sonidos DOS, modo DOS)

### 3. Features nuevas que NO estaban en el original
Cosas que hemos añadido que mejoran el juego (VAR, 5 cambios, ligas mundiales, etc.)

### 4. Top 10 features críticas que faltan (prioridad ALTA)
Lista ordenada por impacto en la experiencia de juego.

### 5. Estimación de completitud
Porcentaje aproximado de features del original que están implementadas.

## Formato del FEATURE_AUDIT.md
Usa markdown con tablas y emojis para fácil lectura. Sin código fuente.
Sé honesto: si algo está mal implementado dilo.
