package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Partido del calendario.
 * homeGoals / awayGoals = -1 si aún no se ha jugado.
 */
@Entity(
    tableName = "fixtures",
    foreignKeys = [
        ForeignKey(CompetitionEntity::class, ["code"], ["competitionCode"]),
        ForeignKey(TeamEntity::class, ["slotId"], ["homeTeamId"]),
        ForeignKey(TeamEntity::class, ["slotId"], ["awayTeamId"]),
    ],
    indices = [
        Index("competitionCode"),
        Index("homeTeamId"),
        Index("awayTeamId"),
        Index(value = ["competitionCode", "matchday"])
    ]
)
data class FixtureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val competitionCode: String,
    val matchday: Int,
    val round: String,              // "MD1", "QF", "SF", "F", etc.
    val homeTeamId: Int,
    val awayTeamId: Int,
    val homeGoals: Int = -1,
    val awayGoals: Int = -1,
    val homePenalties: Int? = null,
    val awayPenalties: Int? = null,
    val decidedByPenalties: Boolean = false,
    val played: Boolean = false,
    val neutral: Boolean = false,   // campo neutral (finales)
    val seed: Long = 0L,
    val eventsJson: String = "",    // JSON array de MatchEvent serializado
)
