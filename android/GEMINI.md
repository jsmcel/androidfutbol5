# PC Fútbol Android — Contexto para Gemini

## Tu rol en este proyecto
Eres el especialista en **documentación técnica y análisis**. Claude Code te delega tareas de:
- Documentar módulos y funciones con KDoc
- Escribir specs técnicas (formato markdown)
- Analizar código existente y proponer mejoras arquitectónicas
- Generar docs de API para los módulos

## Proyecto
Clon Android de PC Fútbol 5 (1997) para temporada 2025/26.
Stack: Kotlin + Jetpack Compose + Room + Hilt

## Módulos y sus responsabilidades
- `:core:data` → entidades Room, DAOs, SeedLoader (carga datos 2025/26 desde assets)
- `:match-sim` → simulador Poisson determinístico (mismo seed = mismo resultado)
- `:competition-engine` → FixtureGenerator, StandingsCalculator (regla española)
- `:promanager` → OfferPoolGenerator, carrera multi-temporada
- `:ui` → Jetpack Compose con tema DOS oscuro (cyan/amarillo/monospace)

## Atributos de jugador (0..99)
ve, re, ag, ca, remate, regate, pase, tiro, entrada, portero

## Competiciones (claves internas)
LIGA1, LIGA2, LIGA2B, CREY, CEURO, RECOPA, CUEFA, SCESP, SCEUR

## Convenciones de documentación
- KDoc en español para funciones públicas
- Formato markdown para specs técnicas
- No crear archivos de código — solo documentación y análisis
