package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Clasificación acumulada de un equipo en una competición.
 */
@Entity(
    tableName = "standings",
    primaryKeys = ["competitionCode", "teamId"],
    foreignKeys = [
        ForeignKey(CompetitionEntity::class, ["code"], ["competitionCode"]),
        ForeignKey(TeamEntity::class, ["slotId"], ["teamId"]),
    ],
    indices = [Index("teamId")]
)
data class StandingEntity(
    val competitionCode: String,
    val teamId: Int,
    val position: Int = 0,
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val points: Int = 0,
)
