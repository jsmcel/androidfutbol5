# CODEX TASK â€” AnÃ¡lisis exhaustivo del CLI

## Objetivo

Hacer un anÃ¡lisis completo de `cli/pcfutbol_cli.py` y producir un informe detallado.
El CLI es el "ego" del proyecto AndroidFutbol â€” lo usan agentes IA para desarrollar y testear el juego.

## QuÃ© analizar

### 1. Inventario de funciones
Lista todas las funciones del CLI con:
- Nombre
- PropÃ³sito
- ParÃ¡metros de entrada/salida
- Bugs conocidos o sospechosos
- Cobertura de test (Â¿existe test automatizado para esta funciÃ³n?)

### 2. Cobertura de funcionalidades
Compara las funcionalidades del CLI con las de la app Android (`android/`):

| Funcionalidad | CLI | Android | Alineado |
|---|---|---|---|
| Liga completa (38 jornadas) | ? | ? | ? |
| Copa del Rey | ? | ? | ? |
| ProManager | ? | ? | ? |
| Mercado fichajes | ? | ? | ? |
| TÃ¡ctica | ? | ? | ? |
| EvoluciÃ³n jugadores | ? | ? | ? |
| Multi-jugador | ? | ? | ? |
| SelecciÃ³n nacional | ? | ? | ? |
| Modo Entrenador (minuto a minuto) | ? | ? | ? |
| Fin de temporada | ? | ? | ? |

### 3. AnÃ¡lisis del save system
- Estructura del JSON de save (`~/.pcfutbol_career.json`)
- Â¿QuÃ© datos se persisten y cuÃ¡les se pierden entre sesiones?
- Â¿CÃ³mo se cargan/inicializan los datos de equipos/jugadores?
- Â¿Soporta mÃºltiples partidas/managers?

### 4. Bugs e inconsistencias
Lista todos los bugs, edge cases y comportamientos inconsistentes:
- Ya conocidos: calibraciÃ³n de fuerza plana (47-52), handler tÃ¡ctica (FIXED), misma equipo (FIXED)
- Buscar nuevos: inputs no validados, divisiÃ³n por cero, overflow, estado inconsistente

### 5. Arquitectura del simulador
- Â¿CÃ³mo funciona el motor Poisson?
- Â¿CÃ³mo se calculan los atributos de equipo?
- Â¿Es reproducible (mismo seed â†’ mismo resultado)?
- Â¿CÃ³mo se compara con `match-sim/` en Android?

### 6. Modo Entrenador
- AnÃ¡lisis completo de `_match_entrenador()`:
  - Flujo de 90 minutos
  - Sistema VAR
  - Sustituciones (3 ventanas, 5 subs)
  - Lesiones
  - Â¿Es testeable con stdin piped?

### 7. Recomendaciones prioritarias
Lista las 10 mejoras mÃ¡s importantes, ordenadas por impacto/esfuerzo.

## Instrucciones

1. Lee `cli/pcfutbol_cli.py` completo (son ~1800 lÃ­neas)
2. Usa `cli/pcfutbol_cli.py` como única fuente CLI y compara contra módulos Android en `android/`
3. Lee `docs/pcf55_rewrite_architecture.md` para entender el diseÃ±o objetivo
4. Ejecuta el CLI con algunos inputs bÃ¡sicos para verificar funcionamiento:
   ```bash
   cd /path/to/androidfutbol
   printf "0\n" | python cli/pcfutbol_cli.py
   printf "1\nLIGA1\n0\n" | python cli/pcfutbol_cli.py
   ```
5. Produce el informe en `CLI_ANALYSIS_REPORT.md` en la raÃ­z del proyecto

## Output esperado

Archivo `CLI_ANALYSIS_REPORT.md` con:
- Todas las secciones del anÃ¡lisis
- Tabla de alineaciÃ³n CLI â†” Android completa
- Lista de bugs encontrados
- Lista de recomendaciones priorizadas

El informe debe ser suficientemente detallado para que otro agente IA pueda implementar las mejoras sin preguntar.

