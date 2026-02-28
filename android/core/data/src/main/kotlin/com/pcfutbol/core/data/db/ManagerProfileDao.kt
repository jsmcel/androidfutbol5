package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ManagerProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ManagerProfileEntity): Long

    @Query("SELECT * FROM manager_profiles WHERE isActive = 1 ORDER BY name")
    fun allActive(): Flow<List<ManagerProfileEntity>>

    @Query("SELECT * FROM manager_profiles WHERE id = :id")
    suspend fun byId(id: Int): ManagerProfileEntity?

    @Query("SELECT * FROM manager_profiles WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): ManagerProfileEntity?

    @Query("SELECT * FROM manager_profiles WHERE currentTeamId = :teamId LIMIT 1")
    suspend fun byTeam(teamId: Int): ManagerProfileEntity?

    @Update
    suspend fun update(profile: ManagerProfileEntity)

    @Query("UPDATE manager_profiles SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Int)
}
