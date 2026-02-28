package com.pcfutbol.seasonstate

import com.pcfutbol.core.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio central del estado de la temporada activa.
 * Equivalente al dominio ACTLIGA del juego original.
 *
 * Gestiona: fase de temporada, jornada actual, lesiones/sanciones, noticias.
 */
@Singleton
class SeasonStateRepository @Inject constructor(
    private val seasonStateDao: SeasonStateDao,
    private val newsDao: NewsDao,
    private val playerDao: PlayerDao,
) {
    // -------------------------------------------------------------------------
    // Estado de temporada

    fun observeState(): Flow<SeasonStateEntity?> = seasonStateDao.observe()

    suspend fun getState(): SeasonStateEntity =
        seasonStateDao.get() ?: SeasonStateEntity().also { seasonStateDao.insert(it) }

    suspend fun initSeason(managerTeamId: Int, mode: String) {
        val baseState = SeasonStateEntity(
            managerTeamId = managerTeamId,
            phase = "PRESEASON",
            currentMatchday = 0,
        )
        seasonStateDao.insert(
            baseState.withManagerMode(mode)
        )
    }

    suspend fun advanceMatchday() {
        val state = getState()
        seasonStateDao.update(
            state.copy(currentMatchday = state.currentMatchday + 1)
        )
    }

    suspend fun openTransferWindow(open: Boolean) {
        val state = getState()
        seasonStateDao.update(state.copy(transferWindowOpen = open))
    }

    suspend fun setPhase(phase: String) {
        val state = getState()
        seasonStateDao.update(state.copy(phase = phase))
    }

    suspend fun setObjectivesMet(met: Boolean) {
        val state = getState()
        seasonStateDao.update(state.copy(objectivesMet = met))
    }

    // -------------------------------------------------------------------------
    // Lesiones y sanciones

    /**
     * Procesa el fin de jornada: reduce contadores de lesión/sanción de todos los jugadores.
     * Llama al fin de cada jornada jugada.
     */
    suspend fun processMatchdayEnd(teamId: Int) {
        val affectedPlayers = (playerDao.withInjury() + playerDao.withSanction())
            .filter { teamId <= 0 || it.teamSlotId == teamId }
            .associateBy { it.id }
            .values
        if (affectedPlayers.isEmpty()) return

        val updated = affectedPlayers.map { player ->
            val injuryWeeksLeft = (player.injuryWeeksLeft - 1).coerceAtLeast(0)
            val sanctionMatchesLeft = (player.sanctionMatchesLeft - 1).coerceAtLeast(0)
            val status = when {
                injuryWeeksLeft > 0 -> 1
                sanctionMatchesLeft > 0 -> 2
                else -> 0
            }
            player.copy(
                injuryWeeksLeft = injuryWeeksLeft,
                sanctionMatchesLeft = sanctionMatchesLeft,
                status = status,
            )
        }
        playerDao.updateAll(updated)
    }

    /**
     * Lesiona a un jugador por N semanas.
     */
    suspend fun injurePlayer(playerId: Int, weeks: Int) {
        val player = playerDao.byId(playerId) ?: return
        playerDao.update(player.copy(
            status = 1,
            injuryWeeksLeft = weeks,
        ))
        // Noticia
        newsDao.insert(NewsEntity(
            date = currentDate(),
            matchday = getState().currentMatchday,
            category = "INJURY",
            titleEs = "Lesión de ${player.nameShort}",
            bodyEs  = "${player.nameFull} se perderá aproximadamente $weeks semanas.",
            teamId  = player.teamSlotId ?: -1,
        ))
    }

    /**
     * Sanciona a un jugador por N partidos (tarjeta roja o acumulación de amarillas).
     */
    suspend fun sanctionPlayer(playerId: Int, matches: Int) {
        val player = playerDao.byId(playerId) ?: return
        playerDao.update(player.copy(
            status = 2,
            sanctionMatchesLeft = matches,
        ))
    }

    // -------------------------------------------------------------------------
    // Noticias

    fun observeNews(): Flow<List<NewsEntity>> = newsDao.recent()

    fun observeUnreadCount(): Flow<Int> = newsDao.unreadCount()

    suspend fun addNews(
        category: String,
        title: String,
        body: String,
        teamId: Int = -1,
    ) {
        val state = getState()
        newsDao.insert(NewsEntity(
            date     = currentDate(),
            matchday = state.currentMatchday,
            category = category,
            titleEs  = title,
            bodyEs   = body,
            teamId   = teamId,
        ))
    }

    suspend fun markNewsRead(id: Int) = newsDao.markRead(id)

    // -------------------------------------------------------------------------

    private fun currentDate(): String {
        // En producción usar LocalDate o similar
        return java.time.LocalDate.now().toString()
    }
}
