package com.pcfutbol.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NationalSquadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<NationalSquadEntity>)

    @Query("DELETE FROM national_squad WHERE season = :season")
    suspend fun clearSeason(season: String)

    @Transaction
    suspend fun replaceSeasonSquad(season: String, entries: List<NationalSquadEntity>) {
        clearSeason(season)
        if (entries.isNotEmpty()) insertAll(entries)
    }

    @Query(
        """
        SELECT p.* FROM players p
        INNER JOIN national_squad ns ON ns.playerId = p.id
        WHERE ns.season = :season
        ORDER BY ns.score DESC, p.ca DESC, p.nameShort ASC
        LIMIT :limit
        """
    )
    suspend fun squadPlayers(season: String, limit: Int = 23): List<PlayerEntity>

    @Query("SELECT MAX(updatedAtMatchday) FROM national_squad WHERE season = :season")
    suspend fun lastUpdateMatchday(season: String): Int?
}

