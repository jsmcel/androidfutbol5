package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Insert
    suspend fun insert(news: NewsEntity)

    @Query("SELECT * FROM news ORDER BY matchday DESC, id DESC")
    fun all(): Flow<List<NewsEntity>>

    @Query("SELECT * FROM news ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<NewsEntity>>

    @Query("SELECT COUNT(*) FROM news WHERE read = 0")
    fun unreadCount(): Flow<Int>

    @Query("UPDATE news SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Int)

    @Query("DELETE FROM news WHERE matchday < :beforeMatchday")
    suspend fun cleanup(beforeMatchday: Int)
}
