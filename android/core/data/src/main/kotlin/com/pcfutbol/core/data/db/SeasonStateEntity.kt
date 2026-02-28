package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

const val MANAGER_MODE = "MANAGER"
const val PROMANAGER_MODE = "PROMANAGER"
private const val MANAGER_LEAGUE_PREFIX = "MANAGER:"

/**
 * Estado global de la temporada activa.
 * Equivalente a ACTLIGA\*.CPT del original.
 * Solo existe una fila (id=1) por partida.
 */
@Entity(tableName = "season_state")
data class SeasonStateEntity(
    @PrimaryKey val id: Int = 1,
    val season: String = "2025-26",
    val currentMatchday: Int = 0,
    val currentDate: String = "2025-08-15",     // ISO-8601
    val phase: String = "PRESEASON",             // PRESEASON | SEASON | POSTSEASON | OFFSEASON
    val managerTeamId: Int = -1,                 // -1 = sin equipo (ProManager sin asignar)
    val managerMode: String = MANAGER_MODE,      // MANAGER[:CODE] | PROMANAGER
    val transferWindowOpen: Boolean = false,
    val objectivesMet: Boolean = false,
)

val SeasonStateEntity.managerLeague: String
    get() = when {
        managerMode.startsWith(MANAGER_LEAGUE_PREFIX) ->
            managerMode.removePrefix(MANAGER_LEAGUE_PREFIX)
                .ifBlank { "LIGA1" }
        managerMode.equals(MANAGER_MODE, ignoreCase = true) ->
            "LIGA1"
        else -> "LIGA1"
    }

fun SeasonStateEntity.withManagerLeague(code: String): SeasonStateEntity {
    val normalized = code.trim().ifBlank { "LIGA1" }
    return if (isProManagerMode()) this else copy(managerMode = "$MANAGER_LEAGUE_PREFIX$normalized")
}

fun SeasonStateEntity.withManagerMode(mode: String): SeasonStateEntity = when {
    mode.equals(PROMANAGER_MODE, ignoreCase = true) -> copy(managerMode = PROMANAGER_MODE)
    else -> copy(managerMode = "$MANAGER_LEAGUE_PREFIX${managerLeague}")
}

fun SeasonStateEntity.isProManagerMode(): Boolean =
    managerMode.equals(PROMANAGER_MODE, ignoreCase = true)
