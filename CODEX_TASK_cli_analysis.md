# CODEX TASK — Análisis exhaustivo del CLI

## Objetivo

Hacer un análisis completo de `cli/pcfutbol_cli.py` y producir un informe detallado.
El CLI es el "ego" del proyecto AndroidFutbol — lo usan agentes IA para desarrollar y testear el juego.

## Qué analizar

### 1. Inventario de funciones
Lista todas las funciones del CLI con:
- Nombre
- Propósito
- Parámetros de entrada/salida
- Bugs conocidos o sospechosos
- Cobertura de test (¿existe test automatizado para esta función?)

### 2. Cobertura de funcionalidades
Compara las funcionalidades del CLI con las de la app Android (`android/`):

| Funcionalidad | CLI | Android | Alineado |
|---|---|---|---|
| Liga completa (38 jornadas) | ? | ? | ? |
| Copa del Rey | ? | ? | ? |
| ProManager | ? | ? | ? |
| Mercado fichajes | ? | ? | ? |
| Táctica | ? | ? | ? |
| Evolución jugadores | ? | ? | ? |
| Multi-jugador | ? | ? | ? |
| Selección nacional | ? | ? | ? |
| Modo Entrenador (minuto a minuto) | ? | ? | ? |
| Fin de temporada | ? | ? | ? |

### 3. Análisis del save system
- Estructura del JSON de save (`~/.pcfutbol_career.json`)
- ¿Qué datos se persisten y cuáles se pierden entre sesiones?
- ¿Cómo se cargan/inicializan los datos de equipos/jugadores?
- ¿Soporta múltiples partidas/managers?

### 4. Bugs e inconsistencias
Lista todos los bugs, edge cases y comportamientos inconsistentes:
- Ya conocidos: calibración de fuerza plana (47-52), handler táctica (FIXED), misma equipo (FIXED)
- Buscar nuevos: inputs no validados, división por cero, overflow, estado inconsistente

### 5. Arquitectura del simulador
- ¿Cómo funciona el motor Poisson?
- ¿Cómo se calculan los atributos de equipo?
- ¿Es reproducible (mismo seed → mismo resultado)?
- ¿Cómo se compara con `match-sim/` en Android?

### 6. Modo Entrenador
- Análisis completo de `_match_entrenador()`:
  - Flujo de 90 minutos
  - Sistema VAR
  - Sustituciones (3 ventanas, 5 subs)
  - Lesiones
  - ¿Es testeable con stdin piped?

### 7. Recomendaciones prioritarias
Lista las 10 mejoras más importantes, ordenadas por impacto/esfuerzo.

## Instrucciones

1. Lee `cli/pcfutbol_cli.py` completo (son ~1800 líneas)
2. Lee `android/android/cli/pcfutbol_cli.py` para comparar si son diferentes
3. Lee `docs/pcf55_rewrite_architecture.md` para entender el diseño objetivo
4. Ejecuta el CLI con algunos inputs básicos para verificar funcionamiento:
   ```bash
   cd /path/to/androidfutbol
   printf "0\n" | python cli/pcfutbol_cli.py
   printf "1\nLIGA1\n0\n" | python cli/pcfutbol_cli.py
   ```
5. Produce el informe en `CLI_ANALYSIS_REPORT.md` en la raíz del proyecto

## Output esperado

Archivo `CLI_ANALYSIS_REPORT.md` con:
- Todas las secciones del análisis
- Tabla de alineación CLI ↔ Android completa
- Lista de bugs encontrados
- Lista de recomendaciones priorizadas

El informe debe ser suficientemente detallado para que otro agente IA pueda implementar las mejoras sin preguntar.
