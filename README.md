# AndroidFutbol

Un clon moderno de PC Fútbol 5 (Dinamic Multimedia, 1997) para Android.
Basado en ingeniería inversa completa del juego original.

## ¿Qué es esto?

- **`android/`** — App Android nativa (Kotlin + Jetpack Compose). Para jugadores humanos.
- **`cli/`** — Simulador de terminal Python. Ego de IA para agentes que desarrollan el juego.
- **`docs/`** — Arquitectura del rewrite y especificaciones técnicas.
- **`inversa/`** — Documentación de ingeniería inversa del binario original.
- **`data/`** — Datos de equipos y jugadores temporada 2025/26 (extraídos de Transfermarkt).
- **`tools/`** — Scripts Python de extracción y patching de datos.

## Stack

| Capa | Tecnología |
|---|---|
| App Android | Kotlin + Jetpack Compose + Room + Hilt |
| Lógica | Módulos Kotlin puros (testables) |
| CLI / IA | Python 3, JSON save files |
| Datos | CSV + JSON (260 equipos, ~11.000 jugadores, 2025/26) |

## Módulos Android

```
:app                   ← Application, DI, NavGraph
:ui                    ← Screens + ViewModels
:core:data             ← Room entities, SeedLoader, DAOs
:competition-engine    ← Liga, Copa, Europa, fixtures, standings
:match-sim             ← Simulador determinístico (Poisson + seed)
:manager-economy       ← Contratos, mercado, presupuesto, fin de temporada
:promanager            ← Carrera ProManager, ofertas, contraseña
```

## CLI — Para agentes IA

```bash
python cli/pcfutbol_cli.py
```

El CLI es el "ego" del proyecto: permite a agentes de IA jugar, testear y desarrollar
la lógica del juego sin necesidad de un dispositivo Android.

## Temporada

**2025/26** — 260 equipos de 19 países, datos reales de Transfermarkt.
