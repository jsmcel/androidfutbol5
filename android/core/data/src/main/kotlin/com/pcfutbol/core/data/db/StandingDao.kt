package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StandingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(standings: List<StandingEntity>)

    @Query("""
        SELECT s.*, t.nameShort, t.shieldAssetName
        FROM standings s
        JOIN teams t ON t.slotId = s.teamId
        WHERE s.competitionCode = :comp
        ORDER BY s.points DESC, (s.goalsFor - s.goalsAgainst) DESC, s.goalsFor DESC
    """)
    fun standingWithTeam(comp: String): Flow<List<StandingWithTeam>>

    @Query("SELECT * FROM standings WHERE competitionCode = :comp ORDER BY points DESC, (goalsFor - goalsAgainst) DESC, goalsFor DESC")
    suspend fun getAllSorted(comp: String): List<StandingEntity>

    @Query("SELECT * FROM standings WHERE competitionCode = :comp AND teamId = :teamId")
    suspend fun byTeam(comp: String, teamId: Int): StandingEntity?

    @Update
    suspend fun update(standing: StandingEntity)

    @Query("DELETE FROM standings WHERE competitionCode = :comp")
    suspend fun deleteByCompetition(comp: String)
}

data class StandingWithTeam(
    val competitionCode: String,
    val teamId: Int,
    val position: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int,
    val nameShort: String,
    val shieldAssetName: String,
)
