# CODEX TASK — Módulo "Actualidad Futbolística" (todas las ligas del juego)

## Objetivo

Añadir una sección independiente al juego que muestre datos reales:
- Clasificación real de cualquier liga del juego
- Últimos resultados de esa liga

Es una sección separada del modo juego — no toca Season State ni DB.

## API: TheSportsDB (SIN API KEY, 100% gratuita)

Base URL: `https://www.thesportsdb.com/api/v1/json/3/`

### Endpoints

**Clasificación:**
`GET https://www.thesportsdb.com/api/v1/json/3/lookuptable.php?l={leagueId}&s=2024-2025`

**Últimos resultados:**
`GET https://www.thesportsdb.com/api/v1/json/3/eventspastleague.php?id={leagueId}`

---

## Mapa de ligas del juego → TheSportsDB ID

```kotlin
// En RealFootballViewModel.kt
val LEAGUE_MAP = mapOf(
    // España
    "LIGA1"   to Pair("Spanish La Liga",           4335),
    "LIGA2"   to Pair("Spanish La Liga 2",         4400),
    "LIGA2B"  to Pair("Primera RFEF Grupo 1",      5086),
    "LIGA2B2" to Pair("Primera RFEF Grupo 2",      5088),
    // Europa
    "PRML"    to Pair("English Premier League",    4328),
    "SERIA"   to Pair("Italian Serie A",           4332),
    "LIG1"    to Pair("French Ligue 1",            4334),
    "BUN1"    to Pair("German Bundesliga",         4331),
    "ERED"    to Pair("Dutch Eredivisie",          4337),
    "PRIM"    to Pair("Portuguese Primeira Liga",  4344),
    "BELGA"   to Pair("Belgian Pro League",        4338),
    "SUPERL"  to Pair("Turkish Super Lig",         4339),
    "SCOT"    to Pair("Scottish Premiership",      4330),
    "RPL"     to Pair("Russian Premier League",    4355),
    "DSL"     to Pair("Danish Superliga",          4340),
    "EKSTR"   to Pair("Polish Ekstraklasa",        4422),
    "ABUND"   to Pair("Austrian Bundesliga",       4621),
    // América
    "ARGPD"   to Pair("Argentine Primera Division",4406),
    "BRASEA"  to Pair("Brazilian Série A",         4351),
    "LIGAMX"  to Pair("Mexican Liga MX",           4350),
    // Asia
    "SPL"     to Pair("Saudi Pro League",          4668),
)
```

---

## Qué implementar

### Android

**Nuevo archivo:** `android/ui/src/main/kotlin/com/pcfutbol/ui/screens/RealFootballScreen.kt`

```kotlin
@Composable
fun RealFootballScreen(onNavigateUp: () -> Unit, vm: RealFootballViewModel = hiltViewModel())
```

La pantalla tiene:
1. **Selector de liga** — lista scrollable con todos los nombres del LEAGUE_MAP, estilo DOS (border + background resaltado al seleccionar)
2. **Dos tabs** debajo del selector: "CLASIFICACION" y "RESULTADOS"
3. **Tab CLASIFICACION**: tabla con columnas `Pos Equipo PJ G E P GF GC Pts`
4. **Tab RESULTADOS**: lista de últimos partidos con fecha, local, marcador, visitante
5. Mientras carga: `Text("Cargando...", color = DosGray)`
6. Si error: `Text("Sin conexión", color = DosRed)` — nunca crash

Estilo: igual que el resto (DosBlack, DosCyan, DosYellow, DosWhite, FontFamily.Monospace).

**Nuevo archivo:** `android/ui/src/main/kotlin/com/pcfutbol/ui/viewmodels/RealFootballViewModel.kt`

```kotlin
data class RealStandingRow(
    val position: Int,
    val teamName: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int,
)

data class RealMatchResult(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val date: String,
)

data class RealFootballUiState(
    val selectedLeagueCode: String = "LIGA1",
    val standings: List<RealStandingRow> = emptyList(),
    val results: List<RealMatchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val activeTab: Int = 0,  // 0=standings, 1=results
)

@HiltViewModel
class RealFootballViewModel @Inject constructor() : ViewModel()
```

El ViewModel:
- `fun selectLeague(code: String)` — actualiza selectedLeagueCode y llama fetchData
- `fun selectTab(tab: Int)` — cambia activeTab
- `private fun fetchData()` — lanza viewModelScope.launch(Dispatchers.IO), hace HTTP con `java.net.HttpURLConnection`, parsea con `org.json.JSONObject/JSONArray`
- NO Retrofit, NO Gson, NO dependencias externas — solo Android SDK

**Conectar al NavHost** (`android/ui/src/main/kotlin/com/pcfutbol/ui/PcfNavHost.kt`):
```kotlin
const val REAL_FOOTBALL = "/realfootball"
// en NavHost:
composable(Routes.REAL_FOOTBALL) {
    RealFootballScreen(onNavigateUp = { navController.navigateUp() })
}
```

**Añadir botón** en `LigaSelectScreen` (`android/ui/src/main/kotlin/com/pcfutbol/ui/screens/Stubs.kt`):

Añadir `onRealFootball: () -> Unit = {}` como parámetro de `LigaSelectScreen` y un botón:
```kotlin
DosButton(
    text = "ACTUALIDAD REAL",
    onClick = onRealFootball,
    modifier = Modifier.fillMaxWidth(),
    color = DosCyan,
)
```

En `PcfNavHost.kt`, pasar `onRealFootball = { navController.navigate(Routes.REAL_FOOTBALL) }` a `LigaSelectScreen`.

**Permiso** en `android/app/src/main/AndroidManifest.xml`:
Añadir antes de `<application>`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

---

### CLI

**Añadir en `cli/pcfutbol_cli.py`** (y también en `android/cli/pcfutbol_cli.py`):

```python
# Mapa completo de ligas → TheSportsDB ID
REAL_LEAGUES = [
    ("Spanish La Liga",            4335, "LIGA1"),
    ("Spanish La Liga 2",          4400, "LIGA2"),
    ("English Premier League",     4328, "PRML"),
    ("Italian Serie A",            4332, "SERIA"),
    ("French Ligue 1",             4334, "LIG1"),
    ("German Bundesliga",          4331, "BUN1"),
    ("Dutch Eredivisie",           4337, "ERED"),
    ("Portuguese Primeira Liga",   4344, "PRIM"),
    ("Belgian Pro League",         4338, "BELGA"),
    ("Turkish Super Lig",          4339, "SUPERL"),
    ("Scottish Premiership",       4330, "SCOT"),
    ("Russian Premier League",     4355, "RPL"),
    ("Danish Superliga",           4340, "DSL"),
    ("Polish Ekstraklasa",         4422, "EKSTR"),
    ("Austrian Bundesliga",        4621, "ABUND"),
    ("Argentine Primera Division", 4406, "ARGPD"),
    ("Brazilian Série A",          4351, "BRASEA"),
    ("Mexican Liga MX",            4350, "LIGAMX"),
    ("Saudi Pro League",           4668, "SPL"),
]

def menu_real_football() -> None:
    """Muestra clasificación y resultados reales de cualquier liga via TheSportsDB."""
    import urllib.request, json

    BASE = "https://www.thesportsdb.com/api/v1/json/3"

    while True:
        print(_c(CYAN, "\n=== ACTUALIDAD FUTBOLISTICA ==="))
        for i, (name, _, code) in enumerate(REAL_LEAGUES, 1):
            print(f"  {i:>2}. {name}")
        print("   0. Volver")

        op = input_int("  Liga: ", 0, len(REAL_LEAGUES))
        if op == 0:
            return

        name, league_id, _ = REAL_LEAGUES[op - 1]
        print(_c(YELLOW, f"\n  {name}"))
        print("  1. Clasificación")
        print("  2. Últimos resultados")
        sub = input_int("  Opción: ", 1, 2)

        try:
            if sub == 1:
                url = f"{BASE}/lookuptable.php?l={league_id}&s=2024-2025"
                with urllib.request.urlopen(url, timeout=8) as r:
                    data = json.loads(r.read())
                rows = data.get("table") or []
                if not rows:
                    print(_c(RED, "  Sin datos para esta liga."))
                else:
                    print(f"\n{'Pos':<4} {'Equipo':<28} {'PJ':>3} {'G':>3} {'E':>3} {'P':>3} {'GF':>4} {'GC':>4} {'Pts':>4}")
                    print("-" * 58)
                    for i, row in enumerate(rows, 1):
                        print(f"{i:<4} {row.get('name','')[:27]:<28} "
                              f"{row.get('played','')!s:>3} {row.get('win','')!s:>3} "
                              f"{row.get('draw','')!s:>3} {row.get('loss','')!s:>3} "
                              f"{row.get('goalsfor','')!s:>4} {row.get('goalsagainst','')!s:>4} "
                              f"{row.get('total','')!s:>4}")
            else:
                url = f"{BASE}/eventspastleague.php?id={league_id}"
                with urllib.request.urlopen(url, timeout=8) as r:
                    data = json.loads(r.read())
                events = (data.get("events") or [])[-15:][::-1]
                if not events:
                    print(_c(RED, "  Sin resultados disponibles."))
                else:
                    print()
                    for ev in events:
                        hs = ev.get("intHomeScore") or "-"
                        aws = ev.get("intAwayScore") or "-"
                        home = ev.get("strHomeTeam","")[:22]
                        away = ev.get("strAwayTeam","")[:22]
                        date = ev.get("dateEvent","")
                        print(f"  {date}  {home:>22} {hs}-{aws}  {away}")
        except Exception as e:
            print(_c(RED, f"  Error de conexión: {e}"))

        _pause()
```

Añadir al menú principal opción 8:
```python
print("  8. Actualidad futbolística (datos reales)")
```
Handler:
```python
elif op == 8:
    menu_real_football()
```
Y actualizar el rango del input principal de 0-7 a 0-8.

---

## Restricciones

- NO dependencias externas en build.gradle.kts — solo `java.net.HttpURLConnection` + `org.json`
- Si la API devuelve null/vacío para alguna liga, mostrar "Sin datos disponibles" — no crashear
- El módulo NO modifica nada del estado del juego
- Aplicar los cambios tanto en `cli/pcfutbol_cli.py` como en `android/cli/pcfutbol_cli.py`

## Verificación final

```bash
cd android
./gradlew :ui:compileDebugKotlin --no-daemon 2>&1 | tail -5
```
