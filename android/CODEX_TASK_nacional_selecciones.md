# Tarea Codex: Selección Nacional y Proquinielas

## Objetivo
Estudiar cómo funcionaban estos dos modos en PC Fútbol 5 (usando docs/ e inversa/)
y luego IMPLEMENTARLOS en la app Android. Son los dos grandes huecos que quedan
sin implementar del original.

## Fuentes a leer primero
- `docs/pcf55_reverse_spec.md` — §7 competition keys (SCESP, SCEUR), §8 simulator contract
- `docs/pcf55_rewrite_architecture.md` — §2.3 competition-engine
- `inversa/README.md` — cualquier referencia a SCESP, SCEUR, Proquinielas

Busca también en el código cualquier constante o referencia a estas competiciones:
- `grep -r "SCESP\|SCEUR\|Proquini\|quiniela" --include="*.kt" android/`

---

## PARTE 1: Selección Nacional (SCESP / SCEUR)

### Cómo funcionaba en el original
- El manager podía gestionar la Selección Española en torneos internacionales
- SCESP = Selección España (mundial, eurocopa, amistosos)
- SCEUR = Selección europea (no siempre presente)
- Los jugadores convocados salían de los equipos de LIGA1/LIGA2
- El manager elegía la alineación y táctica para la selección
- Era un modo paralelo a la liga (semanas sin partido de liga = ventana internacional)

### Qué implementar (versión simplificada)

#### 1. Convocatoria automática
- Top 23 jugadores españoles de LIGA1 (por atributo CA + calidad media)
- Se recalcula al inicio de cada "ventana internacional" (jornadas 13-14, 26-27)
- Guardar en una nueva entidad Room `NationalSquadEntity` o simplemente
  un flag `isConvocado` en PlayerEntity

#### 2. Pantalla de Selección (`NationalTeamScreen.kt`)
```
┌─────────────────────────────────────────────────┐
│ ← SELECCION ESPAÑOLA          FIFA Ranking: #8   │
├─────────────────────────────────────────────────┤
│ CONVOCADOS (23)                                  │
│  1. [GK] Unai Simón        (Athletic)  CA:82    │
│  2. [GK] David Raya        (Arsenal)   CA:81    │
│  ...                                            │
│ [TÁCTICA]  [SIMULAR AMISTOSO]                   │
└─────────────────────────────────────────────────┘
```

#### 3. Competiciones internacionales (simplificadas)
Implementar como torneos knockout de 8 equipos (simplificación):
- `EUROCOPA` — cada 4 temporadas (temporadas pares)
- `MUNDIAL` — cada 4 temporadas (temporadas impares 1 y 3)
- Los 7 equipos rivales son las selecciones top de la época (Alemania, Francia, Brasil, etc.)

#### 4. Ventana Internacional
- Cuando `currentMatchday` llega a 13 o 26 → abrir ventana internacional
- Si el manager es el seleccionador → puede simular el partido de la selección
- Si no → el partido se simula automáticamente (IA)

#### 5. ViewModel y rutas
- `NationalTeamViewModel` — observa convocados, simula partido selección
- Ruta `/seleccion` en NavHost
- Botón "SELECCIÓN" en LigaSelectScreen

---

## PARTE 2: Proquinielas

### Cómo funcionaba en el original
- El jugador rellenaba la quiniela de la jornada siguiente
- 15 partidos: marcas 1 (local), X (empate), 2 (visitante)
- Puntos: acierto = 1 punto; se contabilizaban los 15
- Podías hacer múltiples filas (combinaciones) a mayor coste
- Al simular la jornada, se revelaba el resultado y cuántos acertaste
- Había premios ficticios por acertar 10, 12, 14 o 15

### Qué implementar

#### 1. Entidad Room `QuinielaEntry`
```kotlin
@Entity(tableName = "quiniela_entries")
data class QuinielaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val matchday: Int,
    val fixtureId: Int,
    val prediction: String,  // "1", "X", "2"
    val result: String? = null,  // null hasta que se juegue
    val correct: Boolean = false,
)
```

#### 2. Pantalla `ProquinielasScreen.kt`
Layout:
```
┌──────────────────────────────────────────────┐
│ PROQUINIELAS — JORNADA 15                    │
├──────────────────────────────────────────────┤
│ 1. Real Madrid - Barça        [ 1 ] [X] [ 2 ]│
│ 2. Atletico - Sevilla         [ 1 ] [X] [ 2 ]│
│ 3. Valencia - Villarreal      [ 1 ] [X] [ 2 ]│
│ ...  (15 partidos de LIGA1)                  │
├──────────────────────────────────────────────┤
│ Acertados: 0/15       [GUARDAR QUINIELA]     │
└──────────────────────────────────────────────┘
```

Al simular la jornada → la pantalla muestra qué acertaste:
```
│ 1. Real Madrid 2-1 Barça    ✅ Acertaste (1)  │
│ 2. Atletico 0-0 Sevilla     ❌ Fallaste (X→1) │
```

#### 3. Sistema de premios (ficticios)
- 10 aciertos → "¡Bien! Peña quintuple"
- 12 aciertos → "¡Excelente! Premio de plata"
- 14 aciertos → "¡Increíble! Premio de oro"
- 15 aciertos → "¡PLENO! Bote histórico"
- Guardar en NewsEntity como noticia

#### 4. QuinielaViewModel
- `loadMatchday(matchday)` — carga fixtures de LIGA1 jornada N
- `savePrediction(fixtureId, prediction)` — guarda 1/X/2
- `evaluateResults(matchday)` — tras simular jornada, cuenta aciertos
- Ruta `/proquinielas` en NavHost
- Botón "PROQUINIELAS" en MainMenuScreen (ya existe el botón disabled)

#### 5. QuinielaDao
```kotlin
@Dao
interface QuinielaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: QuinielaEntity)

    @Query("SELECT * FROM quiniela_entries WHERE matchday = :matchday")
    suspend fun byMatchday(matchday: Int): List<QuinielaEntity>

    @Query("UPDATE quiniela_entries SET result=:result, correct=:correct WHERE fixtureId=:fixtureId")
    suspend fun updateResult(fixtureId: Int, result: String, correct: Boolean)
}
```

---

## Cambios en archivos existentes

### NavHost (app/src/main/kotlin/com/pcfutbol/app/NavGraph.kt o similar)
Añadir rutas:
- `/seleccion` → `NationalTeamScreen`
- `/proquinielas` → `ProquinielasScreen`

### MainMenuScreen.kt
El botón "PROQUINIELAS" ya existe pero está `enabled = false`.
Cambiar a `enabled = true` y `onClick = { navController.navigate("proquinielas") }`.

### LigaSelectScreen (Stubs.kt)
Añadir botón "SELECCIÓN ESPAÑOLA" que navega a `/seleccion`.

### PcfDatabase.kt
- Añadir `QuinielaEntity` a las entidades
- Subir versión de Room a 3 (ya está en 2)
- Añadir migration o `fallbackToDestructiveMigration()`

---

## Prioridad de implementación
1. Proquinielas primero (más sencillo, feature completa en sí misma)
2. Selección Nacional después (requiere más lógica de convocatoria)

## Sin commits al finalizar.
