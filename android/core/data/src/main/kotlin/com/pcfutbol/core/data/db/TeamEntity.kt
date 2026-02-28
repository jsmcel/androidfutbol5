package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Equipo de fútbol.
 * slotId: 1..260 (ID interno del PKF original, preservado para compatibilidad)
 * competitionKey: LIGA1 | LIGA2 | LIGA2B | (vacío = sin competición asignada)
 */
@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val slotId: Int,
    val nameShort: String,
    val nameFull: String,
    val stadiumName: String,
    val countryId: String,          // "ES", "DE", "FR", ...
    val competitionKey: String,     // "LIGA1", "LIGA2", "LIGA2B", "CEURO", ...
    val budgetK: Int,               // presupuesto en miles de euros
    val membersCount: Int,
    val sponsor: String,
    val prestige: Int,              // 1..10, calculado de atributos medios
    val shieldAssetName: String,    // nombre del archivo de escudo (para Coil)
    val kitAssetName: String,
)
