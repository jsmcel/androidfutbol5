package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TacticPresetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tactic: TacticPresetEntity): Long

    @Query("SELECT * FROM tactic_presets WHERE managerProfileId = :managerId ORDER BY slotIndex")
    fun byManager(managerId: Int): Flow<List<TacticPresetEntity>>

    @Query("SELECT * FROM tactic_presets WHERE managerProfileId = :managerId AND slotIndex = :slot")
    suspend fun bySlot(managerId: Int, slot: Int): TacticPresetEntity?

    @Update
    suspend fun update(tactic: TacticPresetEntity)

    @Delete
    suspend fun delete(tactic: TacticPresetEntity)
}
