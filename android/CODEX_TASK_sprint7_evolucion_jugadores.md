# CODEX TASK — Sprint 7: Evolución de Jugadores + Alineación CLI↔Android

## Contexto

Proyecto: PC Fútbol 5 Android clone (Kotlin + Jetpack Compose)
Módulos: ver `AGENTS.md` para estructura completa.

El usuario quiere que:
1. **CLI y Android app evolucionen en paralelo** — lo que está en uno debe estar en el otro.
2. **Sistema de evolución de jugadores** — los jugadores mejoran/empeoran según edad, entrenamientos, partidos.
3. **Multi-jugador por turnos** — varios managers pueden compartir la misma temporada.

---

## TAREA 1: Evolución dinámica de jugadores

### Qué implementar en `:match-sim` + `:core:data`

**PlayerDevelopmentEngine.kt** (en `:match-sim`)

```kotlin
object PlayerDevelopmentEngine {
    /**
     * Aplica evolución de fin de temporada a todos los jugadores.
     * - Jóvenes (< 23): +1..+3 en atributos más débiles
     * - Prime (23..29): ±0..±1 aleatorio
     * - Veteranos (30+): -1..-2 en VE y RE por temporada
     * - Retirados (37+): marcar status = "RETIRED"
     */
    fun applySeasonGrowth(players: List<PlayerEntity>, seed: Long): List<PlayerEntity>

    /**
     * Jugadores de cantera: 16-18 años generados aleatoriamente
     * con atributos bajos (30..50) pero potencial implícito por edad.
     */
    fun generateYouthPlayers(teamSlotId: Int, count: Int = 3, seed: Long): List<PlayerEntity>
}
```

**Criterios de evolución** (basados en PC Fútbol 5 original):
- `age = currentYear - birthYear` (usar `SeasonStateEntity.season` p.e. "2025-26" → year=2025)
- Atributos afectados: VE, RE, AG, CA, REMATE, REGATE, PASE, TIRO, ENTRADA, PORTERO
- Mejora: max +3 por atributo por temporada, solo jugadores < 24
- Degradación: -1 por temporada a partir de 31, -2 a partir de 34
- Retiro: status = "RETIRED" cuando age >= 37 OR (age >= 35 AND VE <= 30)
- Los cambios se aplican en `EndOfSeasonUseCase.applySeasonDevelopment()`

### Integrar en EndOfSeasonUseCase (`:manager-economy`)

```kotlin
// En applySeasonEnd():
val allPlayers = playerDao.getAllPlayers()
val evolved = PlayerDevelopmentEngine.applySeasonGrowth(allPlayers, seed = season.hashCode().toLong())
playerDao.updateAll(evolved)

// Generar canteras
val managerTeamId = ss.managerTeamId
val youth = PlayerDevelopmentEngine.generateYouthPlayers(managerTeamId, count = 2, seed)
playerDao.insertAll(youth)
```

### Tests requeridos

- `PlayerDevelopmentTest.kt` en `:match-sim/src/test`:
  - Joven 21 años: debe mejorar en al menos 1 atributo
  - Veterano 35 años: debe empeorar en VE o RE
  - Mayor 37 años: status == "RETIRED"
  - Mismo seed → mismos resultados (reproducibilidad)

---

## TAREA 2: Alineación CLI ↔ Android

### El CLI (`cli/pcfutbol_cli.py`) debe tener la misma lógica que el Android

Busca en el CLI las funciones que **existen en Android** pero no en CLI (o viceversa) y añádelas.

**Funciones pendientes de implementar en CLI:**

### 2a. Evolución de jugadores al fin de temporada

En `menu_promanager` y `menu_liga`, cuando llega fin de temporada (jornada 38 completada), aplicar:

```python
def _apply_season_development(data: dict) -> None:
    """Aplica evolución/retiro de jugadores tras fin de temporada."""
    import random
    players = data.get("players", [])
    season_str = data.get("season", "2025-26")
    year = int(season_str.split("-")[0])

    for p in players:
        age = year - p.get("birth_year", 2000)
        attrs = ["ve", "re", "ag", "ca", "remate", "regate", "pase", "tiro", "entrada", "portero"]

        if age >= 37 or (age >= 35 and p.get("ve", 50) <= 30):
            p["status"] = "RETIRED"
        elif age < 24:
            # Mejora: 1-3 atributos
            for attr in random.sample(attrs, k=random.randint(1, 3)):
                p[attr] = min(99, p.get(attr, 50) + random.randint(1, 3))
        elif age >= 31:
            # Degradación
            delta = 2 if age >= 34 else 1
            for attr in ["ve", "re"]:
                p[attr] = max(1, p.get(attr, 50) - delta)

    # Cantera: 2 jugadores nuevos para el equipo del manager
    manager_team = data.get("manager_team")
    if manager_team:
        for _ in range(2):
            youth = {
                "name": f"Cantera {random.randint(100,999)}",
                "position": random.choice(["DF", "MC", "DC"]),
                "birth_year": year - random.randint(16, 18),
                "ve": random.randint(35, 50), "re": random.randint(35, 50),
                "ag": random.randint(30, 55), "ca": random.randint(35, 55),
                "remate": random.randint(20, 45), "regate": random.randint(20, 45),
                "pase": random.randint(25, 50), "tiro": random.randint(20, 45),
                "entrada": random.randint(25, 50), "portero": 0,
                "team_slot_id": manager_team.get("slot_id"),
                "status": "OK",
            }
            players.append(youth)

    data["players"] = [p for p in players if p.get("status") != "RETIRED"]
    data["retired_count"] = data.get("retired_count", 0) + sum(1 for p in players if p.get("status") == "RETIRED")
```

Llamar `_apply_season_development(data)` en `_season_end(data)`.

### 2b. Multi-jugador por turnos (mismo save)

En el CLI, el save file (`~/.pcfutbol_career.json`) debe soportar múltiples managers:

```python
# Estructura del save con multi-manager:
{
  "season": "2025-26",
  "current_player_turn": 0,  # índice del manager activo
  "managers": [
    {
      "name": "Jose",
      "team_slot_id": 42,
      "mode": "PROMANAGER",
      "matchday": 18,
      ...
    },
    {
      "name": "Marta",
      "team_slot_id": 7,
      "mode": "LIGA",
      "matchday": 18,
      ...
    }
  ],
  "shared": {
    # Fixtures, standings, players — compartidos entre todos
    "fixtures": [...],
    "standings": {...},
    "players": [...],
  }
}
```

**Implementar:**
- `menu_multiplayer(data)` — muestra lista de managers, permite añadir nuevo o seleccionar turno
- En el menú principal (opción 7 nueva), acceder a multijugador
- Al terminar la jornada del manager activo, avanzar el turno (`current_player_turn`)
- La jornada real solo avanza cuando TODOS los managers han jugado su turno de esa jornada

---

## TAREA 3: Fix CSV parsing (Bug MEDIO del QA)

En `SeedLoader.kt`, línea donde parsea `age`:

```kotlin
// ANTES (lanza NumberFormatException si age es ""):
val age = line[5].toInt()

// DESPUÉS:
val age = line[5].toIntOrNull() ?: 0
```

Busca todos los campos numéricos del CSV que puedan ser vacíos y aplica `.toIntOrNull() ?: 0` o `.toDoubleOrNull() ?: 0.0`.

---

## Archivos clave a modificar

| Archivo | Tarea |
|---|---|
| `match-sim/src/main/kotlin/.../PlayerDevelopmentEngine.kt` | NUEVO — Tarea 1 |
| `match-sim/src/test/.../PlayerDevelopmentTest.kt` | NUEVO — Tarea 1 tests |
| `manager-economy/.../EndOfSeasonUseCase.kt` | Integrar PlayerDevelopmentEngine |
| `core/data/.../SeedLoader.kt` | Fix CSV parsing — Tarea 3 |
| `cli/pcfutbol_cli.py` | Añadir _apply_season_development + multi-manager — Tarea 2 |

---

## Restricciones

- NO modificar entidades Room ni esquemas de DB sin añadir migración.
- `PlayerEntity` ya tiene campo `status: String` — usarlo para "RETIRED".
- El CLI debe mantener compatibilidad con saves existentes (añadir campos con defaults).
- Todos los tests deben pasar: `./gradlew :match-sim:test :competition-engine:test`.

## Verificación final

```bash
# Android:
./gradlew :match-sim:test :competition-engine:test assembleDebug

# CLI:
python cli/pcfutbol_cli.py  # no debe dar error en import
```
