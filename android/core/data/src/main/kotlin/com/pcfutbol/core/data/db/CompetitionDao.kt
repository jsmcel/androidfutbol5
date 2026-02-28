package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CompetitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(competitions: List<CompetitionEntity>)

    @Query("SELECT * FROM competitions ORDER BY code")
    fun all(): Flow<List<CompetitionEntity>>

    @Query("SELECT * FROM competitions WHERE code = :code")
    suspend fun byCode(code: String): CompetitionEntity?

    @Query("SELECT COUNT(*) FROM competitions")
    suspend fun count(): Int

    @Update
    suspend fun update(competition: CompetitionEntity)
}
