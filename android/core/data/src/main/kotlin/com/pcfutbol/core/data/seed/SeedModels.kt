package com.pcfutbol.core.data.seed

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Modelos de deserialización para los archivos JSON de seed de datos 2025/26.
 * Generados a partir de pcf55_teams_extracted.json y pcf55_derived_attributes_global.csv
 */

/** Entrada de pcf55_teams_extracted.json (260 equipos) */
@JsonClass(generateAdapter = true)
data class SeedTeam(
    val index: Int,
    @Json(name = "team_name") val teamName: String,
    @Json(name = "stadium_name") val stadiumName: String,
    @Json(name = "full_name") val fullName: String?,
    val confidence: Double,
)

/** Entrada de pcf55_transfermarkt_mapping_global.json */
@JsonClass(generateAdapter = true)
data class SeedTmMapping(
    @Json(name = "pcf_index") val pcfIndex: Int,
    @Json(name = "pcf_team") val pcfTeam: String,
    @Json(name = "pcf_stadium") val pcfStadium: String,
    @Json(name = "tm_team") val tmTeam: String,
    @Json(name = "tm_competition") val tmCompetition: String,   // "ES1", "ES2", "A1", etc.
    @Json(name = "tm_stadium") val tmStadium: String,
    @Json(name = "tm_team_market_value_eur") val tmMarketValueEur: Double,
    @Json(name = "tm_squad_total_value_eur") val tmSquadValueEur: Double,
    val score: Double,
    val accepted: Boolean,
)

/** Jugador del seed (derivado de pcf55_derived_attributes_global.csv) */
data class SeedPlayer(
    val teamSlotId: Int,
    val teamName: String,
    val competition: String,
    val playerName: String,
    val position: String,       // "Goalkeeper", "Defender", "Midfielder", "Forward"
    val age: Int,
    val marketValueEur: Long,
    // Atributos
    val ve: Int,    // velocidad
    val re: Int,    // resistencia
    val ag: Int,    // agresividad
    val ca: Int,    // calidad
    val me: Int,    // media
    val portero: Int,
    val entrada: Int,
    val regate: Int,
    val remate: Int,
    val pase: Int,
    val tiro: Int,
    val source: String,
)

/** Definición estática de competición para el seed */
data class SeedCompetition(
    val code: String,
    val name: String,
    val formatType: String,
    val teamCount: Int,
    val matchdayCount: Int,
    val promotionSlots: Int,
    val relegationSlots: Int,
    val europeanSlots: Int,
    val playoffSlots: Int,
    val tiebreakRule: String,
)
