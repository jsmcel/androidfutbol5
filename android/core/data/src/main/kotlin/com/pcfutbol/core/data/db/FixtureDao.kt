package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FixtureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fixtures: List<FixtureEntity>)

    @Query("SELECT * FROM fixtures WHERE id = :id")
    suspend fun byId(id: Int): FixtureEntity?

    @Query("SELECT * FROM fixtures WHERE competitionCode = :comp AND round = :round ORDER BY id")
    suspend fun byRound(comp: String, round: String): List<FixtureEntity>

    @Query("SELECT * FROM fixtures WHERE competitionCode = :comp AND matchday = :md ORDER BY id")
    suspend fun byMatchday(comp: String, md: Int): List<FixtureEntity>

    @Query("SELECT * FROM fixtures WHERE competitionCode = :comp ORDER BY matchday, id")
    fun byCompetition(comp: String): Flow<List<FixtureEntity>>

    @Query("SELECT * FROM fixtures WHERE competitionCode = :comp AND homeGoals >= 0")
    suspend fun byCompetitionPlayed(comp: String): List<FixtureEntity>

    @Query("""
        SELECT * FROM fixtures
        WHERE (homeTeamId = :teamId OR awayTeamId = :teamId) AND played = 0
        ORDER BY matchday, id LIMIT 1
    """)
    suspend fun nextFixture(teamId: Int): FixtureEntity?

    @Query("""
        UPDATE fixtures
        SET homeGoals=:hg,
            awayGoals=:ag,
            homePenalties=:homePenalties,
            awayPenalties=:awayPenalties,
            decidedByPenalties=:decidedByPenalties,
            played=1,
            seed=:seed
        WHERE id=:id
    """)
    suspend fun recordResult(
        id: Int,
        hg: Int,
        ag: Int,
        seed: Long,
        decidedByPenalties: Boolean = false,
        homePenalties: Int? = null,
        awayPenalties: Int? = null,
    )

    @Query("UPDATE fixtures SET eventsJson = :json WHERE id = :id")
    suspend fun updateEventsJson(id: Int, json: String)

    @Query("DELETE FROM fixtures WHERE competitionCode = :comp")
    suspend fun deleteByCompetition(comp: String)

    @Query("SELECT COUNT(*) FROM fixtures WHERE competitionCode = :comp AND played = 0")
    suspend fun pendingCount(comp: String): Int
}
