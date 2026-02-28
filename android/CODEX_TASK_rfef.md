# Tarea Codex: 1ª RFEF y 2ª RFEF

## Objetivo
Añadir 1ª RFEF (dos grupos) y 2ª RFEF (cuatro grupos) como competiciones jugables
en las que el ProManager puede empezar su carrera. Son el nivel de entrada para managers
sin historial.

## Contexto de ligas actuales
Ya existen: LIGA1 (20 eq), LIGA2 (22 eq), LIGA2B (primer grupo 1ª RFEF).
Las claves internas que vamos a usar:
- `RFEF1A` — 1ª RFEF Grupo A (20 equipos)
- `RFEF1B` — 1ª RFEF Grupo B (20 equipos)
- `RFEF2A` — 2ª RFEF Grupo A (18 equipos)
- `RFEF2B` — 2ª RFEF Grupo B (18 equipos)
- `RFEF2C` — 2ª RFEF Grupo C (18 equipos)
- `RFEF2D` — 2ª RFEF Grupo D (18 equipos)

## Equipos (sintéticos, con nombres reales de la temporada 2025/26)

### 1ª RFEF Grupo A (20 equipos, zona norte/noroeste)
avgMe=54 ± 6:
Racing de Ferrol, Arenteiro, Pontevedra, SD Compostela, Real Avila, Zamora CF,
Burgos Promesas, Numancia, Calahorra, Cayón, Laredo, Rayo Majadahonda, Alcorcón B,
Getafe B, Real Madrid Castilla B, Atletico de Madrid B, Villarreal B, Levante B,
Deportivo Aragones, Jacetano

### 1ª RFEF Grupo B (20 equipos, zona sur/este)
avgMe=54 ± 6:
Castellon B, Hércules, Intercity, UCAM Murcia, Yeclano, Lorca Deportiva,
Melilla, Ceuta FC, Algeciras CF, Antequera CF, Betis Deportivo, Sevilla Atletico,
Malaga B, Granada B, Recreativo de Huelva, Linense, Marbella FC,
Atletico Sanluqueno, Talavera de la Reina, Cordoba B

### 2ª RFEF Grupo A (18 equipos, gallego-asturiano)
avgMe=46 ± 6:
Compostela B, Ferrol B, Lugo B, Viveiro, Bergantiños, Somozas, Arenteiro B,
Fabril, Deportivo B, Ourense CF, Pontevedra B, Arousa, Lalin,
Racing Vilalbés, Valladares, Coruxo B, Astorga, Eibar B

### 2ª RFEF Grupo B (18 equipos, vasco-navarro-riojano)
avgMe=46 ± 6:
Bilbao Athletic B, Baskonia, Amurrio, SD Lagunak, Athletic Club Femenino B,
Tolosa CF, Izarra, Osasuna B, Espanol B Promesas, Nastic B, Lleida Esportiu,
Barcelona B, Girona B, Figueres, Peralada, Badalona, Prat, Vilafranca

### 2ª RFEF Grupo C (18 equipos, centro)
avgMe=46 ± 6:
Real Madrid C, Atletico B, Getafe C, Leganes B, Rayo B, Alcala,
Mostoles, Navalcarnero, Majadahonda, Parla Escuela, Toledo, Talavera B,
Torrijos, Illescas, Pozuelo, Alcobendas, San Fernando, Arganda

### 2ª RFEF Grupo D (18 equipos, sur)
avgMe=46 ± 6:
Betis C, Sevilla C, Malaga C, Granada C, Almeria B, Cadiz B,
Jerez Industrial, Sanlucar de Barrameda, Jaen, Linares, Motril, Marbella B,
Ecija Balompie, Cabecense, El Palo, Cadiz Femenino B, Antequera B, Pulpileno

## Parámetros de generación de jugadores
- avgMe para 1ª RFEF: 54, stdDev: 6, min: 35, max: 68
- avgMe para 2ª RFEF: 46, stdDev: 6, min: 28, max: 60
- Plantilla: 18 jugadores por equipo (igual que SyntheticLeagues para otras ligas)

## Cambios a implementar

### 1. CompetitionDefinitions.kt
Añadir las 6 nuevas claves a:
- `ALL_LEAGUE_CODES` lista
- `COMPETITION_NAMES` mapa (nombres en español)
- `COMPETITION_MATCHDAYS` (jornadas: 1ª RFEF = 38 jornadas, 2ª RFEF = 34 jornadas)
- `COMPETITION_TIERS` mapa (tier 3 para 1ª RFEF, tier 4 para 2ª RFEF)
- Constantes individuales: `const val RFEF1A = "RFEF1A"` etc.

### 2. SyntheticLeagues.kt
Añadir método `rfef1aTeams()`, `rfef1bTeams()`, y los 4 grupos de 2ª RFEF.
Usar el mismo patrón que las ligas existentes:
```kotlin
fun rfef1aTeams(): List<SyntheticTeam> = listOf(
    SyntheticTeam("Racing de Ferrol", "RFEF1A", avgMe = 54),
    SyntheticTeam("Arenteiro",        "RFEF1A", avgMe = 52),
    // ...20 equipos
)
```

### 3. SeedLoader.kt
En la función que inserta equipos sintéticos, añadir los 6 grupos RFEF igual que
se hace para ARGPD/BRASEA/LIGAMX/SPL.

### 4. AppInitializer.kt
En `setupAllLeagues()`, añadir setup de los 6 grupos RFEF con sus tamaños correctos:
- `setupLeague("RFEF1A", 20)` — doble vuelta = 38 jornadas
- `setupLeague("RFEF1B", 20)`
- `setupLeague("RFEF2A", 18)` — doble vuelta = 34 jornadas
- `setupLeague("RFEF2B", 18)`
- `setupLeague("RFEF2C", 18)`
- `setupLeague("RFEF2D", 18)`

### 5. OfferPoolGenerator.kt
El ProManager nuevo (prestige=1) debe recibir ofertas de equipos en:
- 2ª RFEF (grupo aleatorio): prestige 1-2
- 1ª RFEF: prestige 2-3
- LIGA2: prestige 4-6
- LIGA1: prestige 7-10

Añadir RFEF1A, RFEF1B, RFEF2A, RFEF2B, RFEF2C, RFEF2D al pool de ofertas
para managers de prestige bajo (1-3).

### 6. LigaSelectViewModel.kt
En `leagueGroups` añadir un grupo "ESPAÑA - RFEF" con las 6 ligas para que
el usuario pueda verlas en la pantalla de selección de liga.

### 7. EndOfSeasonUseCase.kt (promoción/descenso RFEF)
Al fin de temporada:
- Campeón de RFEF1A y RFEF1B ascienden a LIGA2
- Top-3 de cada grupo RFEF2 ascienden a la 1ª RFEF correspondiente
  (RFEF2A/B → RFEF1A, RFEF2C/D → RFEF1B)
- Implementar como reglas adicionales en el mapa de promoción/descenso

## Notas técnicas
- Slot IDs para equipos RFEF: continuar desde el último slot usado por ligas existentes
  (las ligas sintéticas ARGPD/BRASEA/LIGAMX/SPL ya asignan slots; RFEF parte del siguiente)
- Usar el mismo `SeedLoader.generateSyntheticSquad(avgMe, stdDev)` que ya existe
- El código de competición en TeamEntity.competitionKey debe ser "RFEF1A", "RFEF1B", etc.
- Sin commits al finalizar

## Tests a añadir
- `SyntheticLeaguesTest`: verificar 20+20+18+18+18+18 = 112 equipos RFEF generados
- `OfferPoolGeneratorTest`: manager prestige=1 recibe ofertas de RFEF2x
