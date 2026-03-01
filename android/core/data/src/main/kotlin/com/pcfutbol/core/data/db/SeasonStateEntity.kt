package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

const val MANAGER_MODE = "MANAGER"
const val PROMANAGER_MODE = "PROMANAGER"
const val CONTROL_MODE_BASIC = "BASIC"
const val CONTROL_MODE_STANDARD = "STANDARD"
const val CONTROL_MODE_TOTAL = "TOTAL"
const val PRESIDENT_OWNERSHIP_SOCIOS = "SOCIOS"
const val PRESIDENT_OWNERSHIP_PRIVATE = "PRIVATE"
const val PRESIDENT_OWNERSHIP_STATE = "STATE"
const val PRESIDENT_OWNERSHIP_LISTED = "LISTED"
const val PRESIDENT_CAP_STRICT = "STRICT"
const val PRESIDENT_CAP_BALANCED = "BALANCED"
const val PRESIDENT_CAP_FLEX = "FLEX"
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
    val managerControlMode: String = CONTROL_MODE_STANDARD, // BASIC | STANDARD | TOTAL
    val transferWindowOpen: Boolean = false,
    val objectivesMet: Boolean = false,
    val presidentOwnership: String = PRESIDENT_OWNERSHIP_SOCIOS, // SOCIOS | PRIVATE | STATE | LISTED
    val presidentSalaryCapMode: String = PRESIDENT_CAP_BALANCED, // STRICT | BALANCED | FLEX
    val presidentSalaryCapK: Int = 0, // miles de euros / semana
    val presidentPressure: Int = 0, // 0..12
    val presidentInvestorRounds: Int = 0,
    val presidentIpoDone: Boolean = false,
    val presidentPelotazoDone: Boolean = false,
    val presidentLastReviewMatchday: Int = 0,
    val presidentNextReviewMatchday: Int = 1,
    val presidentLastCapPenaltyMatchday: Int = -99,
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

val SeasonStateEntity.normalizedControlMode: String
    get() = when (managerControlMode.uppercase()) {
        CONTROL_MODE_BASIC -> CONTROL_MODE_BASIC
        CONTROL_MODE_TOTAL -> CONTROL_MODE_TOTAL
        else -> CONTROL_MODE_STANDARD
    }

fun SeasonStateEntity.withControlMode(mode: String): SeasonStateEntity {
    val normalized = when (mode.uppercase()) {
        CONTROL_MODE_BASIC -> CONTROL_MODE_BASIC
        CONTROL_MODE_TOTAL -> CONTROL_MODE_TOTAL
        else -> CONTROL_MODE_STANDARD
    }
    return copy(managerControlMode = normalized)
}

fun controlModeLabel(mode: String): String = when (mode.uppercase()) {
    CONTROL_MODE_BASIC -> "Basico"
    CONTROL_MODE_TOTAL -> "Total"
    else -> "Estandar"
}

fun allowsCoachMode(mode: String): Boolean =
    mode.uppercase() != CONTROL_MODE_BASIC

fun allowsManagerDepth(mode: String): Boolean =
    mode.uppercase() != CONTROL_MODE_BASIC

fun allowsPresidentDesk(mode: String): Boolean =
    mode.uppercase() == CONTROL_MODE_TOTAL

val SeasonStateEntity.normalizedPresidentOwnership: String
    get() = when (presidentOwnership.uppercase()) {
        PRESIDENT_OWNERSHIP_PRIVATE -> PRESIDENT_OWNERSHIP_PRIVATE
        PRESIDENT_OWNERSHIP_STATE -> PRESIDENT_OWNERSHIP_STATE
        PRESIDENT_OWNERSHIP_LISTED -> PRESIDENT_OWNERSHIP_LISTED
        else -> PRESIDENT_OWNERSHIP_SOCIOS
    }

val SeasonStateEntity.normalizedPresidentSalaryCapMode: String
    get() = when (presidentSalaryCapMode.uppercase()) {
        PRESIDENT_CAP_STRICT -> PRESIDENT_CAP_STRICT
        PRESIDENT_CAP_FLEX -> PRESIDENT_CAP_FLEX
        else -> PRESIDENT_CAP_BALANCED
    }

fun presidentSalaryCapFactor(mode: String): Double = when (mode.uppercase()) {
    PRESIDENT_CAP_STRICT -> 1.05
    PRESIDENT_CAP_FLEX -> 1.45
    else -> 1.20
}

fun presidentSalaryCapModeLabel(mode: String): String = when (mode.uppercase()) {
    PRESIDENT_CAP_STRICT -> "Estricto"
    PRESIDENT_CAP_FLEX -> "Flexible"
    else -> "Equilibrado"
}

fun presidentOwnershipLabel(ownership: String): String = when (ownership.uppercase()) {
    PRESIDENT_OWNERSHIP_PRIVATE -> "Inversor privado"
    PRESIDENT_OWNERSHIP_STATE -> "Club-estado"
    PRESIDENT_OWNERSHIP_LISTED -> "Cotizada"
    else -> "Socios"
}
