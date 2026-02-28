package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Definición estática de una competición.
 * Claves internas heredadas del original: LIGA1, LIGA2, LIGA2B, CREY, CEURO, RECOPA, CUEFA, SCESP, SCEUR
 */
@Entity(tableName = "competitions")
data class CompetitionEntity(
    @PrimaryKey val code: String,   // "LIGA1", "LIGA2", etc.
    val name: String,               // "Primera División", "Copa del Rey", ...
    val formatType: String,         // "LEAGUE" | "KNOCKOUT" | "GROUP_KNOCKOUT"
    val teamCount: Int,
    val matchdayCount: Int,         // 38 para LIGA1, 0 para KNOCKOUT
    val promotionSlots: Int,        // equipos que suben
    val relegationSlots: Int,       // equipos que bajan
    val europeanSlots: Int,         // plazas europeas directas
    val playoffSlots: Int,          // plazas de playoffs
    val tiebreakRule: String,       // "ES_LIGA" | "STANDARD"
    val season: String,             // "2025-26"
)
