# PC Fútbol Android — Contexto para Codex

## Qué es este proyecto
Clon Android nativo de PC Fútbol 5 (Dinamic Multimedia, 1997) para la temporada 2025/26.
Datos reales de Transfermarkt integrados.

## Stack
- **Kotlin + Jetpack Compose** (Material 3 con tema DOS personalizado)
- **Room** para persistencia
- **Hilt** para DI
- **Coroutines + Flow** para async
- **JUnit 5** para tests

## Estructura de módulos Gradle
```
:app                  → PcfApplication, MainActivity, AppInitializer
:ui                   → PcfNavHost, screens/, theme/PcfTheme, viewmodels/
:core:data            → 9 entidades Room, 9 DAOs, PcfDatabase, SeedLoader
:core:season-state    → equivalente a ACTLIGA/* del original
:competition-engine   → FixtureGenerator, StandingsCalculator, CompetitionRepository
:match-sim            → MatchSimulator (Poisson), StrengthCalculator — módulo Kotlin puro
:manager-economy      → contratos, mercado, presupuesto
:promanager           → OfferPoolGenerator, ProManagerRepository
:test-support         → fixtures de test compartidos
```

## Datos (assets en :core:data/src/main/assets/)
- `pcf55_players_2526.csv` — 6978 jugadores con atributos (VE,RE,AG,CA,ME,PORTERO,ENTRADA,REGATE,REMATE,PASE,TIRO)
- `pcf55_teams_extracted.json` — 260 equipos con slotId 1..260
- `pcf55_transfermarkt_mapping.json` — mapping a competición y valor de mercado TM

## Competiciones (claves internas del juego original)
`LIGA1` (1ª Div), `LIGA2` (2ª Div), `LIGA2B` (1ª RFEF), `CREY` (Copa del Rey),
`CEURO` (UCL), `RECOPA` (UEL), `CUEFA` (UECL), `SCESP`, `SCEUR`

## Atributos de jugador
`ve`(velocidad), `re`(resistencia), `ag`(agresividad), `ca`(calidad),
`remate`, `regate`, `pase`, `tiro`, `entrada`, `portero` — todos 0..99

## Parámetros tácticos del simulador
`tipoJuego`(1=def,2=eq,3=of), `tipoMarcaje`(1=hombre,2=zona), `tipoPresion`(1-3),
`tipoDespejes`, `faltas`, `porcToque`(0..100), `porcContra`(0..100),
`marcajeDefensas`, `marcajeMedios`, `puntoDefensa`, `puntoAtaque`, `area`

## Pantallas UI ya implementadas
- `MainMenuScreen` — menú principal estilo DOS
- `StandingsScreen` — clasificación con `StandingsViewModel`
- `ProManagerOffersScreen` — crear manager, login PIN, seleccionar oferta
- Resto en `Stubs.kt` — marcadores de posición para sprints futuros

## Paleta de colores (PcfTheme.kt)
`DosBlack`=#0A0A0F, `DosCyan`=#00E5FF, `DosYellow`=#FFD600, `DosGreen`=#00E676,
`DosNavy`=#0D1B2A, `DosWhite`=#E0E0E0, `DosGray`=#424242

## Convenciones
- Nombres de código en **inglés**, comentarios en **español**
- No crear archivos de documentación (*.md) salvo que se pida explícitamente
- Tests JUnit5 con `useJUnitPlatform()`
- No hacer commit automáticamente
- Reutilizar componentes existentes de `ui/components/PcfComponents.kt`

## Sprints pendientes
- Sprint 2: pantalla de liga completa (jornada, siguiente partido, sim manual)
- Sprint 3: simulador avanzado con plantilla configurable
- Sprint 4: mercado de fichajes (ventana verano/invierno)
- Sprint 5: modo ProManager completo con carrera multi-temporada
- Sprint 6: pantalla de táctica drag-and-drop
- Sprint 7: noticias y tablón del club
- Sprint 8: polish visual, animaciones, sonidos

## Referencia de ingeniería inversa
Documentación en `../inversa/` y `../docs/` — no es necesario modificar esos archivos.
