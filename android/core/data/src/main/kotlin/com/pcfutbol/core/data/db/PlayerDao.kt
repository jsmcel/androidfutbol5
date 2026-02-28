package com.pcfutbol.core.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(players: List<PlayerEntity>)

    @Query("SELECT * FROM players WHERE teamSlotId = :teamId ORDER BY number")
    fun byTeam(teamId: Int): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE teamSlotId = :teamId ORDER BY isStarter DESC, number")
    fun byTeamWithStarters(teamId: Int): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE teamSlotId = :teamId ORDER BY number")
    suspend fun byTeamNow(teamId: Int): List<PlayerEntity>

    @Query(
        """
        SELECT p.* FROM players p
        INNER JOIN teams t ON t.slotId = p.teamSlotId
        WHERE t.competitionKey = :leagueCode
          AND p.citizenship = :countryCode
        ORDER BY (
            p.ca +
            (
                p.ve + p.re + p.ag + p.remate + p.regate +
                p.pase + p.tiro + p.entrada + p.portero
            ) / 9.0
        ) DESC,
        p.ca DESC,
        p.nameShort ASC
        LIMIT :limit
        """
    )
    suspend fun topNationalPlayers(
        countryCode: String,
        leagueCode: String = "LIGA1",
        limit: Int = 23,
    ): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE teamSlotId IS NULL ORDER BY ca DESC")
    fun freeAgents(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE teamSlotId IS NULL ORDER BY ca DESC LIMIT :limit")
    suspend fun freeAgentsNow(limit: Int = 400): List<PlayerEntity>

    @Query(
        """
        SELECT * FROM players
        WHERE teamSlotId IS NOT NULL
          AND teamSlotId != :managerTeamId
          AND status = 0
        ORDER BY ca DESC, nameShort ASC
        LIMIT :limit
        """
    )
    suspend fun transferTargetsNow(managerTeamId: Int, limit: Int = 800): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun byId(id: Int): PlayerEntity?

    @Query("SELECT * FROM players WHERE pid = :pid")
    suspend fun byPid(pid: Int): PlayerEntity?

    @Query("SELECT * FROM players ORDER BY ca DESC")
    fun allPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT COUNT(*) FROM players WHERE teamSlotId = :teamId")
    suspend fun countByTeam(teamId: Int): Int

    @Query("SELECT COUNT(*) FROM players")
    suspend fun count(): Int

    @Update
    suspend fun update(player: PlayerEntity)

    @Query("UPDATE players SET estadoForma = :forma, moral = :moral WHERE id = :id")
    suspend fun updateRuntime(id: Int, forma: Int, moral: Int)

    @Query("UPDATE players SET isStarter = :starter WHERE id = :id")
    suspend fun setStarter(id: Int, starter: Boolean)

    @Query("UPDATE players SET isStarter = 0 WHERE teamSlotId = :teamId")
    suspend fun clearStarters(teamId: Int)

    @Query("UPDATE players SET moral = MAX(0, MIN(99, moral + :delta)) WHERE teamSlotId = :teamId")
    suspend fun shiftTeamMoral(teamId: Int, delta: Int)

    /** Jugadores con al menos una semana de lesión pendiente. */
    @Query("SELECT * FROM players WHERE injuryWeeksLeft > 0")
    suspend fun withInjury(): List<PlayerEntity>

    /** Jugadores con al menos un partido de sanción pendiente. */
    @Query("SELECT * FROM players WHERE sanctionMatchesLeft > 0")
    suspend fun withSanction(): List<PlayerEntity>

    @Update
    suspend fun updateAll(players: List<PlayerEntity>)
}
