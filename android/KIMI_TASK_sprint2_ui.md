# Tarea para Kimi — Sprint 2 UI: TeamSquadScreen + TacticScreen

## Contexto
Proyecto Android: PC Fútbol 5 clon para temporada 2025/26.
Stack: Kotlin + Jetpack Compose + Room + Hilt.
Directorio de trabajo: `android/`

## Tema visual (ya implementado en PcfTheme.kt)
```kotlin
DosBlack=#0A0A0F, DosCyan=#00E5FF, DosYellow=#FFD600
DosGreen=#00E676, DosNavy=#0D1B2A, DosGray=#424242
FontFamily.Monospace para todo el texto
```

## Tarea 1: TeamSquadScreen

Reemplaza el stub en `ui/src/main/kotlin/com/pcfutbol/ui/screens/Stubs.kt` función `TeamSquadScreen`.

La pantalla debe mostrar:
1. Barra superior: flecha volver (DosCyan) + título "MI PLANTILLA" (DosYellow)
2. Lista lazy de jugadores del equipo del manager (PlayerEntity via Room Flow)
3. Cada fila: número, nombre (12 chars), posición (2 chars), atributos clave (CA, ME en amarillo), estadoForma barra
4. Filtros en la parte superior: Todos / PO / DF / MC / DC (botones pequeños)
5. Botón flotante "TÁCTICA" (DosGreen) que llama a onTactic()

ViewModel a crear: `ui/src/main/kotlin/com/pcfutbol/ui/viewmodels/TeamSquadViewModel.kt`
- Flow<List<PlayerEntity>> desde PlayerDao.byTeam(teamId)
- teamId viene de SeasonStateDao (managerTeamId)
- @HiltViewModel

## Tarea 2: TacticScreen

Reemplaza el stub `TacticScreen` en el mismo archivo.

La pantalla debe mostrar:
1. Barra superior con título "TÁCTICA"
2. Campo de fútbol verde oscuro con las 11 posiciones como círculos (representación visual)
3. Formación actual (ej. "4-4-2") editable con botones +/- por línea
4. Panel de parámetros tácticos con sliders:
   - Tipo de juego: DEFENSIVO ← → OFENSIVO
   - Presión: BAJA ← → ALTA
   - Toque: 0% ← → 100%
5. Botón "GUARDAR" (DosGreen) que persiste TacticPresetEntity en Room

ViewModel a crear: `ui/src/main/kotlin/com/pcfutbol/ui/viewmodels/TacticViewModel.kt`

## Nota importante
- Usa DosButton y DosPanel de `ui/src/main/kotlin/com/pcfutbol/ui/components/PcfComponents.kt`
- Mantén el tema DOS oscuro (sin Material 3 por defecto)
- No crees archivos de documentación (.md)
- Todos los textos de UI en español
