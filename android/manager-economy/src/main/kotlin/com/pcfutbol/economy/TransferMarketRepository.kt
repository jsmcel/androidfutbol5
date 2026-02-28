package com.pcfutbol.economy

import com.pcfutbol.core.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class TransferOffer(
    val playerId: Int,
    val playerName: String,
    val fromTeamId: Int?,
    val toTeamId: Int,
    val amountK: Int,
    val wageK: Int,
    val status: OfferStatus,
)

enum class OfferStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }

/**
 * Gestión del mercado de fichajes.
 * Ventana de verano: jornadas 0-3 (pre-temporada)
 * Ventana de invierno: jornadas 19-20 (pausa invernal)
 */
@Singleton
class TransferMarketRepository @Inject constructor(
    private val playerDao: PlayerDao,
    private val teamDao: TeamDao,
    private val seasonStateDao: SeasonStateDao,
    private val newsDao: NewsDao,
) {
    fun freeAgents(): Flow<List<PlayerEntity>> = playerDao.freeAgents()

    fun squadOf(teamId: Int): Flow<List<PlayerEntity>> = playerDao.byTeam(teamId)

    /**
     * El manager intenta fichar a un jugador de otro equipo.
     * Calcula oferta automática basada en el valor de mercado.
     */
    suspend fun makeOffer(
        playerPid: Int,
        managerTeamId: Int,
        offeredAmountK: Int,
    ): Result<String> {
        val state = seasonStateDao.get() ?: return Result.failure(Exception("Sin temporada activa"))
        if (!state.transferWindowOpen) {
            return Result.failure(Exception("La ventana de mercado está cerrada"))
        }
        val player = playerDao.byPid(playerPid)
            ?: return Result.failure(Exception("Jugador no encontrado"))

        val media = WageCalculator.playerMedia(player)
        val team  = teamDao.byId(managerTeamId)
            ?: return Result.failure(Exception("Equipo no encontrado"))

        val marketValueK = WageCalculator.marketValueK(player, team.competitionKey)
        if (offeredAmountK < marketValueK * 0.8) {
            return Result.failure(Exception("Oferta demasiado baja (mín. ${(marketValueK * 0.8).toInt()}K€)"))
        }

        // Verificar que el manager tenga presupuesto suficiente
        if (team.budgetK < offeredAmountK) {
            return Result.failure(Exception("Presupuesto insuficiente (tienes ${team.budgetK}K€, necesitas ${offeredAmountK}K€)"))
        }

        // Aceptación automática si oferta ≥ 90% del valor
        val accepted = offeredAmountK >= marketValueK * 0.9
        if (accepted) {
            playerDao.update(player.copy(teamSlotId = managerTeamId))
            teamDao.update(team.copy(budgetK = team.budgetK - offeredAmountK))
            val news = "Fichaje completado: ${player.nameShort} por ${offeredAmountK}K€"
            newsDao.insert(NewsEntity(
                date     = java.time.LocalDate.now().toString(),
                matchday = state.currentMatchday,
                category = "TRANSFER",
                titleEs  = "FICHAJE: ${player.nameShort}",
                bodyEs   = "${player.nameFull} se une a ${team.nameShort} por ${offeredAmountK}K€.",
                teamId   = managerTeamId,
            ))
            return Result.success(news)
        } else {
            return Result.failure(Exception("El equipo ha rechazado la oferta"))
        }
    }

    /**
     * Vender un jugador del equipo del manager (desvincular del equipo).
     */
    suspend fun sellPlayer(
        playerPid: Int,
        managerTeamId: Int,
        askingPriceK: Int,
    ): Result<String> {
        val state = seasonStateDao.get() ?: return Result.failure(Exception("Sin temporada activa"))
        if (!state.transferWindowOpen) {
            return Result.failure(Exception("Ventana de mercado cerrada"))
        }
        val player = playerDao.byPid(playerPid) ?: return Result.failure(Exception("No encontrado"))
        if (player.teamSlotId != managerTeamId) {
            return Result.failure(Exception("El jugador no pertenece a tu equipo"))
        }

        // Cesión o venta: quitar del equipo y actualizar presupuesto
        playerDao.update(player.copy(teamSlotId = null))
        val team = teamDao.byId(managerTeamId)
        if (team != null) {
            teamDao.update(team.copy(budgetK = team.budgetK + askingPriceK))
        }
        newsDao.insert(NewsEntity(
            date     = java.time.LocalDate.now().toString(),
            matchday = state.currentMatchday,
            category = "TRANSFER",
            titleEs  = "VENTA: ${player.nameShort}",
            bodyEs   = "${player.nameFull} abandona ${team?.nameShort ?: "tu equipo"} por ${askingPriceK}K€.",
            teamId   = managerTeamId,
        ))
        return Result.success("${player.nameShort} vendido por ${askingPriceK}K€")
    }

    /**
     * Genera ofertas AI automáticas para los jugadores del equipo del manager.
     * Se ejecuta al comienzo de cada ventana de mercado.
     * Equipos de mayor prestige ofrecen por jugadores con media alta.
     */
    suspend fun generateAiOffers(managerTeamId: Int): List<TransferOffer> {
        val state = seasonStateDao.get() ?: return emptyList()
        if (!state.transferWindowOpen) return emptyList()

        val squad = mutableListOf<PlayerEntity>()
        // TODO: collect desde el Flow en un scope externo
        return emptyList()
    }
}
