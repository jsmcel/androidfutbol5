# Tarea Codex: Prioridades críticas del juego

## Objetivo
Implementar las 3 features más críticas que faltan en la app Android según FEATURE_AUDIT.md.
Implementa todo de una vez. Sin commits al finalizar.

---

## PRIORIDAD 1: Pantalla de Alineación (`LineupScreen.kt`)

### Qué es
El manager elige 11 titulares de su plantilla antes de cada jornada.
Esta es la feature más crítica que falta — sin ella no hay gestión real del equipo.

### Implementación

#### 1. Añadir `isStarter: Boolean = false` a `PlayerEntity`
```kotlin
// En PlayerEntity.kt, al final del data class:
val isStarter: Boolean = false,
```
⚠️ Room version: ya va a estar en 3 (otra tarea paralela lo sube). Asegúrate de que
  `PcfDatabase.kt` tiene version=3 y `fallbackToDestructiveMigration()` en DataModule.
  Si version ya es 3, no toques nada. Si sigue en 2, súbela a 3 y añade fallback.

#### 2. Añadir a `PlayerDao`:
```kotlin
@Query("SELECT * FROM players WHERE teamSlotId = :teamId ORDER BY isStarter DESC, number")
fun byTeamWithStarters(teamId: Int): Flow<List<PlayerEntity>>

@Query("UPDATE players SET isStarter = :starter WHERE id = :id")
suspend fun setStarter(id: Int, starter: Boolean)

@Query("UPDATE players SET isStarter = 0 WHERE teamSlotId = :teamId")
suspend fun clearStarters(teamId: Int)
```

#### 3. `LineupViewModel.kt` en `:ui`
```kotlin
data class LineupUiState(
    val allPlayers: List<PlayerEntity> = emptyList(),
    val starters: List<PlayerEntity> = emptyList(),  // max 11, ordenados por posición
    val loading: Boolean = true,
)

@HiltViewModel
class LineupViewModel @Inject constructor(
    private val playerDao: PlayerDao,
    private val seasonStateDao: SeasonStateDao,
) : ViewModel() {
    // init: carga players del equipo manager, con isStarter ya persistido
    // toggleStarter(player): si es titular → quitarlo; si no y hay < 11 → añadirlo
    // autoSelect(): selecciona automáticamente los mejores 11 (1 PO + 4 DF + 3 MC + 3 DC por CA)
}
```

#### 4. `LineupScreen.kt` en `:ui/screens/`
Layout:
```
┌──────────────────────────────────────────┐
│ ← ALINEACIÓN      [SELECCIÓN AUTO]       │
├──────────────────────────────────────────┤
│ TITULARES (8/11)                         │
│  ✓ [PO] Unai Simón         CA:82        │
│  ✓ [DF] Carvajal           CA:81        │
│    [DF] Militao             CA:80        │ ← tap = toggle
│  ...                                     │
├──────────────────────────────────────────┤
│ [GUARDAR ALINEACIÓN]                     │
└──────────────────────────────────────────┘
```

- Jugadores lesionados/sancionados aparecen grises y no son seleccionables
- Máximo 11 titulares
- Botón "SELECCIÓN AUTO" llama a `autoSelect()`
- Botón "GUARDAR" persiste los `isStarter` vía `playerDao.setStarter()`
- Ordenar por: titulares primero (✓), luego por posición PO→DF→MC→DC, luego CA desc

#### 5. Ruta en `PcfNavHost.kt`
```kotlin
// En Routes object:
const val LINEUP = "/lineup"

// En NavHost:
composable(Routes.LINEUP) {
    LineupScreen(onNavigateUp = { navController.navigateUp() })
}
```

#### 6. Botón en `TeamSquadScreen.kt`
Añadir parámetro `onLineup: () -> Unit` y segundo botón:
```kotlin
// Parámetro al composable:
onLineup: () -> Unit,

// Al final, en los botones de la pantalla:
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    DosButton(
        text = "ALINEACIÓN",
        onClick = onLineup,
        modifier = Modifier.weight(1f),
        color = DosYellow,
    )
    DosButton(
        text = "TÁCTICA",
        onClick = onTactic,
        modifier = Modifier.weight(1f),
        color = DosCyan,
    )
}
```

#### 7. En `PcfNavHost.kt`, actualizar composable de TEAM_SQUAD:
```kotlin
composable(Routes.TEAM_SQUAD) {
    TeamSquadScreen(
        onNavigateUp = { navController.navigateUp() },
        onTactic     = { navController.navigate(Routes.TACTIC) },
        onLineup     = { navController.navigate(Routes.LINEUP) },
    )
}
```

---

## PRIORIDAD 2: Pantalla de Estadísticas (`StatsScreen.kt`)

### Qué es
Tabla de goleadores y asistentes de la temporada actual. Feature básica en cualquier juego de fútbol.

### Implementación

#### 1. Añadir a `FixtureEntity` un campo `eventsJson: String = ""`
Guarda los `MatchEvent` serializados como JSON string tras simular.
Si `FixtureEntity` ya tiene `eventsJson`, no lo toques.

Si no existe, añade al final del data class:
```kotlin
val eventsJson: String = "",   // JSON array de MatchEvent serializado
```

⚠️ Este campo necesita migration o estar en la versión 3 con fallbackToDestructiveMigration.

#### 2. En `CompetitionRepository.kt`, tras simular un partido, serializar y guardar events:
```kotlin
// Tras simular y guardar resultado, también guardar eventos:
val eventsJson = jacksonOrMoshi.toJson(result.events)
fixtureDao.updateEventsJson(fixture.id, eventsJson)
```

Añadir a FixtureDao:
```kotlin
@Query("UPDATE fixtures SET eventsJson = :json WHERE id = :id")
suspend fun updateEventsJson(id: Int, json: String)
```

#### 3. `StatsViewModel.kt`
```kotlin
data class GoalScorer(
    val playerName: String,
    val teamName: String,
    val goals: Int,
)

data class StatsUiState(
    val topScorers: List<GoalScorer> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val fixtureDao: FixtureDao,
    private val teamDao: TeamDao,
    private val seasonStateDao: SeasonStateDao,
) : ViewModel() {
    init {
        viewModelScope.launch {
            val ss = seasonStateDao.get() ?: return@launch
            val compCode = ss.managerLeague()  // o usa "LIGA1" por defecto
            val fixtures = fixtureDao.byCompetitionPlayed(compCode)
            // Parsear eventsJson de cada fixture, filtrar GOAL, agrupar por playerName
            // Ordenar desc por goals, top 20
        }
    }
}
```

Añadir a FixtureDao:
```kotlin
@Query("SELECT * FROM fixtures WHERE competitionCode = :comp AND homeGoals >= 0")
suspend fun byCompetitionPlayed(comp: String): List<FixtureEntity>
```

#### 4. `StatsScreen.kt`
Layout:
```
┌────────────────────────────────────────┐
│ ← GOLEADORES — LIGA1                  │
├────────────────────────────────────────┤
│  #  JUGADOR             EQUIPO  GOLES  │
│  1. Benzema             R.Madrid  18   │
│  2. Lewandowski         Barça     15   │
│  ...                                  │
└────────────────────────────────────────┘
```

#### 5. Ruta en `PcfNavHost.kt`
```kotlin
const val STATS = "/stats"

composable(Routes.STATS) {
    StatsScreen(onNavigateUp = { navController.navigateUp() })
}
```

#### 6. Botón en `LigaSelectScreen` (Stubs.kt)
Añadir parámetro `onStats: () -> Unit` y botón "ESTADÍSTICAS" al lado de "CLASIFICACIÓN".
En PcfNavHost, `onStats = { navController.navigate(Routes.STATS) }`.

---

## PRIORIDAD 3: Champions League básica (CEURO)

### Qué es
La competición europea más importante. El original la tenía como central.
Implementar versión simplificada: 8 equipos en grupos (o directamente eliminatoria de 8).

### Implementación

#### 1. En `CompetitionDefinitions.kt`
```kotlin
const val CEURO = "CEURO"   // Champions League
```
Si ya existe, no toques.

#### 2. `ChampionsRepository.kt` en `:competition-engine`
```kotlin
@Singleton
class ChampionsRepository @Inject constructor(
    private val fixtureDao: FixtureDao,
    private val teamDao: TeamDao,
    private val standingDao: StandingDao,
    private val seasonStateDao: SeasonStateDao,
) {
    // setup(): selecciona top 4 LIGA1 + 4 equipos europeos aleatorios de ligas europeas
    //          genera eliminatoria de cuartos (4 partidos) → semis (2) → final (1)
    //          usa FixtureGenerator.generateKnockout(...)
    //
    // simulateRound(seed): simula partidos CEURO pendientes de la ronda actual
    //                       con penaltis si hay empate
    //
    // getCurrentRound(): devuelve la ronda activa ("QF", "SF", "F", "DONE")
}
```

#### 3. `ChampionsScreen.kt` en `:ui/screens/`
Layout igual que CopaScreen pero para CEURO.
Mostrar: ronda activa, parejas de partidos, botón "SIMULAR".
Si no hay fixtures → botón "INICIAR CHAMPIONS".

#### 4. Ruta en `PcfNavHost.kt`
```kotlin
const val CHAMPIONS = "/champions"

composable(Routes.CHAMPIONS) {
    ChampionsScreen(onNavigateUp = { navController.navigateUp() })
}
```

#### 5. Botón en `LigaSelectScreen` (Stubs.kt)
Añadir botón "CHAMPIONS" que navega a `/champions`.
En PcfNavHost, wiring correspondiente.

---

## Notas técnicas

- Usa Moshi para serializar/deserializar MatchEvent si se necesita para eventsJson
- Si Moshi no está disponible en competition-engine, usa Gson o json simple con split/join
- Mantén el estilo visual: DosBlack fondo, DosYellow títulos, DosCyan acentos, FontFamily.Monospace
- Sigue el patrón ViewModel: `MutableStateFlow<UiState>` + `collectAsState()` en composable
- No añadas librerías nuevas al build.gradle si puedes evitarlo
- Sin commits al finalizar
