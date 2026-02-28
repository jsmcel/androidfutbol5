# Tarea para Codex — Todas las ligas europeas + Argentina + Brasil + México + Arabia Saudita

## Contexto
El CSV de assets (`pcf55_players_2526.csv`) ya tiene jugadores de estas ligas europeas
con código de competición TM. Actualmente el SeedLoader solo carga ES1→LIGA1 y ES2→LIGA2;
el resto va a "FOREIGN" y no se juega.

La tarea es activar TODAS las ligas como competiciones jugables con fixtures propios.

---

## PARTE 1 — Ligas europeas ya en el CSV

### Códigos TM disponibles en el CSV
| Código CSV | Liga | Equipos aprox | Clave interna a usar |
|---|---|---|---|
| ES1 | La Liga | 20 | LIGA1 (ya existe) |
| ES2 | Segunda División | 22 | LIGA2 (ya existe) |
| E3G1 | 1ª RFEF grupo 1 | ~18 | LIGA2B (ya existe) |
| E3G2 | 1ª RFEF grupo 2 | ~18 | LIGA2B2 |
| GB1 | Premier League | 20 | PRML |
| IT1 | Serie A | 20 | SERIA |
| FR1 | Ligue 1 | 18 | LIG1 |
| DE1 / L1 | Bundesliga | 18 | BUN1 |
| NL1 | Eredivisie | 18 | ERED |
| PO1 | Primeira Liga | 18 | PRIM |
| BE1 | Belgian Pro League | 16 | BELGA |
| TR1 | Süper Lig | 19 | SUPERL |
| SC1 | Scottish Premiership | 12 | SCOT |
| RU1 | Russian Premier League | 16 | RPL |
| DK1 | Danish Superliga | 14 | DSL |
| PL1 | Polish Ekstraklasa | 18 | EKSTR |
| A1 | Austrian Bundesliga | 12 | ABUND |

### Implementación para ligas del CSV
1. **`CompetitionDefinitions.kt`** — añadir entrada para cada clave interna:
   ```kotlin
   CompetitionDef(code="PRML", name="Premier League", country="ENG",
                  formatType="LEAGUE", teamCount=20, relegationSlots=3, promotionSlots=0)
   // ... etc para cada liga
   ```
   El `teamCount` debe calcularse automáticamente contando cuántos equipos hay en el CSV
   con ese código de competición.

2. **`SeedLoader.kt`** — actualizar `tmCompToKey` para mapear todos los códigos:
   ```kotlin
   val tmCompToKey = mapOf(
       "ES1" to "LIGA1",  "ES2" to "LIGA2",
       "E3G1" to "LIGA2B", "E3G2" to "LIGA2B2",
       "GB1" to "PRML",   "IT1" to "SERIA",
       "FR1" to "LIG1",   "L1"  to "BUN1",    // Bundesliga usa "L1" en el CSV
       "NL1" to "ERED",   "PO1" to "PRIM",
       "BE1" to "BELGA",  "TR1" to "SUPERL",
       "SC1" to "SCOT",   "RU1" to "RPL",
       "DK1" to "DSL",    "PL1" to "EKSTR",
       "A1"  to "ABUND",
   )
   ```

3. **`CompetitionRepository.setupLeague(competitionCode)`** — debe funcionar para
   cualquier código nuevo, no solo LIGA1/LIGA2.

---

## PARTE 2 — Ligas sin datos en el CSV (generar equipos sintéticos)

Argentina, Brasil, México y Arabia Saudita NO tienen datos en el CSV.
Codex debe generarlos como datos hard-coded en el código (no en assets).

### Claves internas
| Liga | Clave | Equipos | País |
|---|---|---|---|
| Primera División Argentina | ARGPD | 28 | ARG |
| Série A Brasileira | BRASEA | 20 | BRA |
| Liga MX | LIGAMX | 18 | MEX |
| Saudi Pro League | SPL | 18 | SAU |

### Archivo nuevo: `SyntheticLeagues.kt`
Crea `core/data/src/main/kotlin/com/pcfutbol/core/data/seed/SyntheticLeagues.kt`

Contenido — lista de equipos reales con atributos de fuerza aproximados:

```kotlin
object SyntheticLeagues {

    // Argentina — Primera División 2025/26
    // Strength basado en reputación: River/Boca ~72, resto 45-65
    val ARGENTINA = listOf(
        SyntheticTeam("River Plate",         "ARG", "ARGPD",  avgMe=72, squad=25),
        SyntheticTeam("Boca Juniors",        "ARG", "ARGPD",  avgMe=70, squad=25),
        SyntheticTeam("Racing Club",         "ARG", "ARGPD",  avgMe=63, squad=23),
        SyntheticTeam("Independiente",       "ARG", "ARGPD",  avgMe=61, squad=23),
        SyntheticTeam("San Lorenzo",         "ARG", "ARGPD",  avgMe=60, squad=23),
        SyntheticTeam("Estudiantes LP",      "ARG", "ARGPD",  avgMe=62, squad=23),
        SyntheticTeam("Vélez Sársfield",     "ARG", "ARGPD",  avgMe=60, squad=22),
        SyntheticTeam("Talleres Córdoba",    "ARG", "ARGPD",  avgMe=59, squad=22),
        SyntheticTeam("Huracán",             "ARG", "ARGPD",  avgMe=55, squad=22),
        SyntheticTeam("Lanús",               "ARG", "ARGPD",  avgMe=55, squad=22),
        SyntheticTeam("Belgrano",            "ARG", "ARGPD",  avgMe=54, squad=21),
        SyntheticTeam("Newell's Old Boys",   "ARG", "ARGPD",  avgMe=54, squad=21),
        SyntheticTeam("Rosario Central",     "ARG", "ARGPD",  avgMe=53, squad=21),
        SyntheticTeam("Godoy Cruz",          "ARG", "ARGPD",  avgMe=53, squad=21),
        SyntheticTeam("Tigre",               "ARG", "ARGPD",  avgMe=50, squad=21),
        SyntheticTeam("Defensa y Justicia",  "ARG", "ARGPD",  avgMe=52, squad=21),
        SyntheticTeam("Arsenal Sarandí",     "ARG", "ARGPD",  avgMe=48, squad=20),
        SyntheticTeam("Sarmiento Junín",     "ARG", "ARGPD",  avgMe=47, squad=20),
        SyntheticTeam("Central Córdoba",     "ARG", "ARGPD",  avgMe=47, squad=20),
        SyntheticTeam("Platense",            "ARG", "ARGPD",  avgMe=46, squad=20),
        SyntheticTeam("Gimnasia LP",         "ARG", "ARGPD",  avgMe=50, squad=21),
        SyntheticTeam("Colón Santa Fe",      "ARG", "ARGPD",  avgMe=49, squad=20),
        SyntheticTeam("Unión Santa Fe",      "ARG", "ARGPD",  avgMe=49, squad=20),
        SyntheticTeam("San Martín Tucumán",  "ARG", "ARGPD",  avgMe=46, squad=20),
        SyntheticTeam("Instituto Córdoba",   "ARG", "ARGPD",  avgMe=46, squad=20),
        SyntheticTeam("Atlético Tucumán",    "ARG", "ARGPD",  avgMe=51, squad=21),
        SyntheticTeam("Barracas Central",    "ARG", "ARGPD",  avgMe=46, squad=20),
        SyntheticTeam("Riestra",             "ARG", "ARGPD",  avgMe=45, squad=20),
    )

    // Brasil — Série A 2025
    val BRASIL = listOf(
        SyntheticTeam("Flamengo",            "BRA", "BRASEA", avgMe=74, squad=28),
        SyntheticTeam("Palmeiras",           "BRA", "BRASEA", avgMe=73, squad=28),
        SyntheticTeam("São Paulo FC",        "BRA", "BRASEA", avgMe=68, squad=26),
        SyntheticTeam("Fluminense",          "BRA", "BRASEA", avgMe=67, squad=25),
        SyntheticTeam("Corinthians",         "BRA", "BRASEA", avgMe=66, squad=25),
        SyntheticTeam("Atletico Mineiro",    "BRA", "BRASEA", avgMe=67, squad=25),
        SyntheticTeam("Internacional",       "BRA", "BRASEA", avgMe=65, squad=25),
        SyntheticTeam("Grêmio",              "BRA", "BRASEA", avgMe=64, squad=24),
        SyntheticTeam("Santos FC",           "BRA", "BRASEA", avgMe=62, squad=23),
        SyntheticTeam("Botafogo",            "BRA", "BRASEA", avgMe=64, squad=24),
        SyntheticTeam("Cruzeiro",            "BRA", "BRASEA", avgMe=63, squad=23),
        SyntheticTeam("Vasco da Gama",       "BRA", "BRASEA", avgMe=61, squad=23),
        SyntheticTeam("Fortaleza EC",        "BRA", "BRASEA", avgMe=60, squad=22),
        SyntheticTeam("Athletico Paranaense","BRA", "BRASEA", avgMe=60, squad=22),
        SyntheticTeam("RB Bragantino",       "BRA", "BRASEA", avgMe=59, squad=22),
        SyntheticTeam("Bahia",               "BRA", "BRASEA", avgMe=57, squad=22),
        SyntheticTeam("Ceará SC",            "BRA", "BRASEA", avgMe=55, squad=21),
        SyntheticTeam("Sport Recife",        "BRA", "BRASEA", avgMe=53, squad=21),
        SyntheticTeam("Juventude",           "BRA", "BRASEA", avgMe=51, squad=20),
        SyntheticTeam("Criciúma EC",         "BRA", "BRASEA", avgMe=50, squad=20),
    )

    // México — Liga MX 2025/26
    val MEXICO = listOf(
        SyntheticTeam("Club América",        "MEX", "LIGAMX", avgMe=71, squad=26),
        SyntheticTeam("Guadalajara",         "MEX", "LIGAMX", avgMe=70, squad=26),  // Chivas
        SyntheticTeam("Cruz Azul",           "MEX", "LIGAMX", avgMe=67, squad=25),
        SyntheticTeam("Pumas UNAM",          "MEX", "LIGAMX", avgMe=65, squad=24),
        SyntheticTeam("Tigres UANL",         "MEX", "LIGAMX", avgMe=69, squad=26),
        SyntheticTeam("Monterrey",           "MEX", "LIGAMX", avgMe=68, squad=25),
        SyntheticTeam("Toluca FC",           "MEX", "LIGAMX", avgMe=63, squad=23),
        SyntheticTeam("Atlas FC",            "MEX", "LIGAMX", avgMe=61, squad=23),
        SyntheticTeam("León FC",             "MEX", "LIGAMX", avgMe=62, squad=23),
        SyntheticTeam("Santos Laguna",       "MEX", "LIGAMX", avgMe=60, squad=22),
        SyntheticTeam("Pachuca",             "MEX", "LIGAMX", avgMe=62, squad=23),
        SyntheticTeam("Tijuana",             "MEX", "LIGAMX", avgMe=57, squad=22),
        SyntheticTeam("Necaxa",              "MEX", "LIGAMX", avgMe=55, squad=21),
        SyntheticTeam("Querétaro",           "MEX", "LIGAMX", avgMe=53, squad=21),
        SyntheticTeam("San Luis FC",         "MEX", "LIGAMX", avgMe=54, squad=21),
        SyntheticTeam("Mazatlán FC",         "MEX", "LIGAMX", avgMe=52, squad=20),
        SyntheticTeam("Puebla FC",           "MEX", "LIGAMX", avgMe=56, squad=22),
        SyntheticTeam("Juárez FC",           "MEX", "LIGAMX", avgMe=50, squad=20),
    )

    // Arabia Saudita — Saudi Pro League 2025/26
    // Jerarquía: Al-Hilal/Al-Nassr muy fuertes por fichajes galácticos
    val SAUDI = listOf(
        SyntheticTeam("Al-Hilal SFC",        "SAU", "SPL",    avgMe=80, squad=30),
        SyntheticTeam("Al-Nassr FC",         "SAU", "SPL",    avgMe=78, squad=29),
        SyntheticTeam("Al-Ittihad Club",     "SAU", "SPL",    avgMe=74, squad=27),
        SyntheticTeam("Al-Ahli SFC",         "SAU", "SPL",    avgMe=72, squad=26),
        SyntheticTeam("Al-Qadsiah",          "SAU", "SPL",    avgMe=64, squad=24),
        SyntheticTeam("Al-Shabab FC",        "SAU", "SPL",    avgMe=63, squad=23),
        SyntheticTeam("Al-Fayha FC",         "SAU", "SPL",    avgMe=58, squad=22),
        SyntheticTeam("Al-Ettifaq FC",       "SAU", "SPL",    avgMe=60, squad=22),
        SyntheticTeam("Al-Taawoun FC",       "SAU", "SPL",    avgMe=58, squad=22),
        SyntheticTeam("Al-Wehda Club",       "SAU", "SPL",    avgMe=56, squad=21),
        SyntheticTeam("Al-Hazm Club",        "SAU", "SPL",    avgMe=52, squad=20),
        SyntheticTeam("Abha Club",           "SAU", "SPL",    avgMe=51, squad=20),
        SyntheticTeam("Al-Fateh SC",         "SAU", "SPL",    avgMe=54, squad=21),
        SyntheticTeam("Damac FC",            "SAU", "SPL",    avgMe=53, squad=21),
        SyntheticTeam("Al-Riyadh SC",        "SAU", "SPL",    avgMe=50, squad=20),
        SyntheticTeam("Al-Okhdood Club",     "SAU", "SPL",    avgMe=49, squad=20),
        SyntheticTeam("Al-Khaleej SC",       "SAU", "SPL",    avgMe=51, squad=20),
        SyntheticTeam("Al-Qadisiyah",        "SAU", "SPL",    avgMe=49, squad=20),
    )
}

// Data class para equipos sintéticos
data class SyntheticTeam(
    val name: String,
    val country: String,
    val competition: String,
    val avgMe: Int,         // media global del equipo (45-80)
    val squad: Int,         // tamaño de plantilla a generar
)
```

### Generación de jugadores para equipos sintéticos
En `SeedLoader.kt`, añadir `seedSyntheticLeagues()`:
- Para cada `SyntheticTeam`, generar `squad` jugadores con atributos aleatorios (seed derivado del nombre del equipo) centrados en `avgMe` ± 8.
- Distribución de posiciones por equipo: 2 GK, 6 DEF, 5 MID, 4 ATT (escalar según squad size).
- Los nombres de jugadores deben ser realistas para cada país:
  - ARG: apellidos hispanos (García, Rodríguez, López, Fernández, Sosa, Almada...)
  - BRA: nombres brasileños (Silva, Santos, Oliveira, Pereira, Costa, Souza...)
  - MEX: apellidos hispanos mexicanos (Hernández, González, Martínez, Flores...)
  - SAU: nombres árabes (Al-Dawsari, Al-Shahrani, Al-Faraj, Salem, Firas, Omar...)
- Para jugadores estrella sintéticos (Al-Hilal/Al-Nassr), usar nombres famosos que son realistas en 2025/26:
  - Al-Hilal: "Neymar Jr" (si recuperado), "Ruben Neves", "Kalidou Koulibaly", "Sergej Milinković-Savić"
  - Al-Nassr: "Cristiano Ronaldo" (me=85, ve=70, re=75, ag=55, ca=90, remate=95, regate=85), "Sadio Mané", "Marcelo Brozovic"

---

## PARTE 3 — Integración completa

### CompetitionRepository
- `setupLeague(code)` debe funcionar para todos los códigos (no solo LIGA1/LIGA2)
- Calcular `relegationSlots` y `promotionSlots` según el tamaño de la liga

### ProManagerRepository / OfferPoolGenerator
- `generateOffers()` debe buscar candidatos en TODAS las ligas, no solo LIGA1/LIGA2
- Añadir filtro por `prestige` del manager: prestige 1-3 = ligas pequeñas/medianas, 7-10 = Premier/Bundesliga/Al-Hilal

### SeasonState
- `SeasonStateEntity.managerLeague` debe poder ser cualquier código, no solo LIGA1/LIGA2
- Actualizar referencias hardcodeadas de "LIGA1" en EndOfSeasonUseCase y MatchdayViewModel

### Pantalla de selección de liga (LigaSelectScreen / Stubs.kt)
- Añadir lista de ligas disponibles agrupadas por continente/país
- El usuario puede elegir qué liga simular en modo Liga Manager
- El ProManager recibe ofertas de cualquier liga según su prestige

---

## Reglas de implementación
- Sin commits automáticos
- Room version ya está en 2 (subida en la tarea anterior) — no cambiar
- Tests JUnit5 para la generación de equipos sintéticos y el setupLeague
- Si un método tiene >50 líneas, extráelo a una función privada
- No crear archivos .md adicionales
