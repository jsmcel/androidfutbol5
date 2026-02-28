package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SeasonStateEntity)

    @Query("SELECT * FROM season_state WHERE id = 1")
    fun observe(): Flow<SeasonStateEntity?>

    @Query("SELECT * FROM season_state WHERE id = 1")
    suspend fun get(): SeasonStateEntity?

    @Update
    suspend fun update(state: SeasonStateEntity)
}
