package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(teams: List<TeamEntity>)

    @Query("SELECT * FROM teams ORDER BY slotId")
    fun allTeams(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE slotId = :slotId")
    suspend fun byId(slotId: Int): TeamEntity?

    @Query("SELECT * FROM teams WHERE competitionKey = :key ORDER BY slotId")
    fun byCompetition(key: String): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE slotId IN (:ids)")
    suspend fun byIds(ids: List<Int>): List<TeamEntity>

    @Query("SELECT COUNT(*) FROM teams")
    suspend fun count(): Int

    @Update
    suspend fun update(team: TeamEntity)
}
