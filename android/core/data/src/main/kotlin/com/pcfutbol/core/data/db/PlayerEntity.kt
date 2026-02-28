package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Jugador.
 * Atributos (0..99) según contrato RE de EQUIPOS.PKF:
 *   VE=velocidad, RE=resistencia, AG=agresividad, CA=calidad
 *   REMATE, REGATE, PASE, TIRO, ENTRADA, PORTERO
 *
 * estadoForma y moral son runtime y se inicializan en 50.
 */
@Entity(
    tableName = "players",
    foreignKeys = [ForeignKey(
        entity = TeamEntity::class,
        parentColumns = ["slotId"],
        childColumns = ["teamSlotId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("teamSlotId"), Index("pid")]
)
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pid: Int,                   // ID interno del juego original (u16)
    val teamSlotId: Int?,           // null = agente libre
    val number: Int,
    val nameShort: String,
    val nameFull: String,
    // Categorización
    val position: String,           // "PO", "DF", "MC", "DC", "EX", "PT"
    val roles: String,              // JSON array de roles ej. "[1,2,0,0,0,0]"
    val citizenship: String,        // código país ISO-2
    val status: Int,                // 0=activo, 1=lesionado, 2=sancionado, 3=cedido
    // Físico
    val birthDay: Int,
    val birthMonth: Int,
    val birthYear: Int,
    val height: Int,                // cm
    val weight: Int,                // kg
    val skin: Int,
    val hair: Int,
    // Atributos técnicos (0..99)
    val ve: Int,                    // velocidad
    val re: Int,                    // resistencia
    val ag: Int,                    // agresividad
    val ca: Int,                    // calidad
    val remate: Int,
    val regate: Int,
    val pase: Int,
    val tiro: Int,
    val entrada: Int,
    val portero: Int,
    // Estado de temporada (runtime)
    val estadoForma: Int = 50,      // 0..100
    val moral: Int = 50,            // 0..100
    val injuryWeeksLeft: Int = 0,
    val sanctionMatchesLeft: Int = 0,
    // Contrato
    val wageK: Int = 0,             // salario semanal en miles
    val contractEndYear: Int = 2026,
    val releaseClauseK: Int = 0,
    val isStarter: Boolean = false,
)
