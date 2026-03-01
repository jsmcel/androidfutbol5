package com.pcfutbol.economy

import com.pcfutbol.core.data.db.NewsDao
import com.pcfutbol.core.data.db.NewsEntity
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.presidentSalaryCapFactor
import com.pcfutbol.core.data.db.presidentSalaryCapModeLabel
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.SeasonStateEntity
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.TeamEntity
import com.pcfutbol.core.data.db.normalizedPresidentSalaryCapMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.random.Random
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

private enum class SalaryCapMode(val factor: Double, val labelEs: String) {
    STRICT(1.05, "Estricto"),
    BALANCED(1.20, "Equilibrado"),
    FLEX(1.45, "Flexible"),
}

data class SalaryCapSnapshot(
    val wageBillK: Int,
    val salaryCapK: Int,
    val modeKey: String,
    val modeLabel: String,
    val marginK: Int,
)

private data class ContractTerms(
    val wageK: Int,
    val releaseClauseK: Int,
    val contractEndYear: Int,
)

/**
 * Gestion del mercado de fichajes.
 * Ventana de verano: jornadas 0-3 (pretemporada).
 * Ventana de invierno: jornadas 19-21.
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

    suspend fun freeAgentsNow(limit: Int = 400): List<PlayerEntity> =
        playerDao.freeAgentsNow(limit)

    suspend fun transferTargetsNow(managerTeamId: Int, limit: Int = 800): List<PlayerEntity> =
        playerDao.transferTargetsNow(managerTeamId, limit)

    suspend fun squadNow(teamId: Int): List<PlayerEntity> =
        playerDao.byTeamNow(teamId)

    suspend fun managerSalaryCapSnapshot(managerTeamId: Int): SalaryCapSnapshot {
        val seasonState = seasonStateDao.get()
        val team = teamDao.byId(managerTeamId)
            ?: return SalaryCapSnapshot(
                wageBillK = 0,
                salaryCapK = 0,
                modeKey = SalaryCapMode.BALANCED.name,
                modeLabel = SalaryCapMode.BALANCED.labelEs,
                marginK = 0,
            )
        val squad = playerDao.byTeamNow(managerTeamId)
        return buildSalaryCapSnapshot(
            team = team,
            squad = squad,
            seasonState = seasonState,
            isManagerTeam = seasonState?.managerTeamId == managerTeamId,
        )
    }

    /**
     * El manager intenta fichar a un jugador (agente libre o de otro club).
     */
    suspend fun makeOffer(
        playerPid: Int,
        managerTeamId: Int,
        offeredAmountK: Int,
    ): Result<String> {
        val state = seasonStateDao.get() ?: return Result.failure(Exception("Sin temporada activa"))
        if (!state.transferWindowOpen) {
            return Result.failure(Exception("La ventana de mercado esta cerrada"))
        }
        val player = playerDao.byPid(playerPid)
            ?: return Result.failure(Exception("Jugador no encontrado"))
        if (player.teamSlotId == managerTeamId) {
            return Result.failure(Exception("Ese jugador ya pertenece a tu club"))
        }

        val buyerTeam = teamDao.byId(managerTeamId)
            ?: return Result.failure(Exception("Equipo no encontrado"))
        val sellerTeam = player.teamSlotId?.let { teamDao.byId(it) }
        val marketComp = sellerTeam?.competitionKey ?: buyerTeam.competitionKey
        val marketBaseValueK = WageCalculator.marketValueK(player, marketComp)
        val marketValueK = (marketBaseValueK * marketDynamicsMultiplier(state))
            .toInt()
            .coerceAtLeast(50)
        val minOffer = (marketValueK * 0.75).toInt()
        if (offeredAmountK < minOffer) {
            return Result.failure(Exception("Oferta demasiado baja (min. ${minOffer}K)"))
        }
        if (buyerTeam.budgetK < offeredAmountK) {
            return Result.failure(Exception("Presupuesto insuficiente"))
        }
        val incomingTerms = projectedContractTerms(
            player = player,
            destinationCompetition = buyerTeam.competitionKey,
            season = state.season,
        )
        val capSnapshot = managerSalaryCapSnapshot(managerTeamId)
        val projectedBillK = capSnapshot.wageBillK + incomingTerms.wageK
        if (projectedBillK > capSnapshot.salaryCapK) {
            return Result.failure(
                Exception(
                    "Tope salarial excedido: masa ${projectedBillK}K > cap ${capSnapshot.salaryCapK}K " +
                        "(${capSnapshot.modeLabel})"
                )
            )
        }

        val accepted = evaluateOfferAcceptance(
            state = state,
            player = player,
            offeredAmountK = offeredAmountK,
            marketValueK = marketValueK,
        )
        if (!accepted) {
            return Result.failure(Exception("El club rechaza la oferta"))
        }

        playerDao.update(
            player.copy(
                teamSlotId = managerTeamId,
                isStarter = false,
                wageK = incomingTerms.wageK,
                releaseClauseK = incomingTerms.releaseClauseK,
                contractEndYear = incomingTerms.contractEndYear,
            )
        )
        teamDao.update(buyerTeam.copy(budgetK = buyerTeam.budgetK - offeredAmountK))
        if (sellerTeam != null) {
            teamDao.update(sellerTeam.copy(budgetK = sellerTeam.budgetK + offeredAmountK))
        }

        val date = java.time.LocalDate.now().toString()
        newsDao.insert(
            NewsEntity(
                date = date,
                matchday = state.currentMatchday,
                category = "TRANSFER",
                titleEs = "FICHAJE: ${player.nameShort}",
                bodyEs = "${player.nameFull} se une a ${buyerTeam.nameShort} por ${offeredAmountK}K. " +
                    "Ficha ${incomingTerms.wageK}K/sem.",
                teamId = managerTeamId,
            )
        )
        if (sellerTeam != null) {
            newsDao.insert(
                NewsEntity(
                    date = date,
                    matchday = state.currentMatchday,
                    category = "TRANSFER",
                    titleEs = "VENTA: ${player.nameShort}",
                    bodyEs = "${sellerTeam.nameShort} traspasa a ${player.nameFull} por ${offeredAmountK}K.",
                    teamId = sellerTeam.slotId,
                )
            )
        }

        return Result.success("Fichaje cerrado: ${player.nameShort} por ${offeredAmountK}K")
    }

    /**
     * Vender un jugador de tu equipo a un club comprador.
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
        val player = playerDao.byPid(playerPid) ?: return Result.failure(Exception("Jugador no encontrado"))
        if (player.teamSlotId != managerTeamId) {
            return Result.failure(Exception("El jugador no pertenece a tu equipo"))
        }
        val sellerTeam = teamDao.byId(managerTeamId) ?: return Result.failure(Exception("Equipo no encontrado"))

        val marketBaseValueK = WageCalculator.marketValueK(player, sellerTeam.competitionKey)
        val marketValueK = (marketBaseValueK * marketDynamicsMultiplier(state))
            .toInt()
            .coerceAtLeast(50)
        val minSale = (marketValueK * 0.55).toInt().coerceAtLeast(50)
        if (askingPriceK < minSale) {
            return Result.failure(Exception("Precio demasiado bajo (min. ${minSale}K)"))
        }

        val buyer = pickBuyerTeam(managerTeamId, sellerTeam.competitionKey)
            ?: return Result.failure(Exception("No hay compradores disponibles"))
        if (buyer.budgetK < askingPriceK) {
            return Result.failure(Exception("${buyer.nameShort} no tiene presupuesto"))
        }

        val accepted = evaluateSaleAcceptance(
            state = state,
            player = player,
            askingPriceK = askingPriceK,
            marketValueK = marketValueK,
        )
        if (!accepted) {
            return Result.failure(Exception("Ningun club acepta ese precio"))
        }

        val buyerTerms = projectedContractTerms(
            player = player,
            destinationCompetition = buyer.competitionKey,
            season = state.season,
        )
        playerDao.update(
            player.copy(
                teamSlotId = buyer.slotId,
                isStarter = false,
                wageK = buyerTerms.wageK,
                releaseClauseK = buyerTerms.releaseClauseK,
                contractEndYear = buyerTerms.contractEndYear,
            )
        )
        teamDao.update(sellerTeam.copy(budgetK = sellerTeam.budgetK + askingPriceK))
        teamDao.update(buyer.copy(budgetK = buyer.budgetK - askingPriceK))

        val date = java.time.LocalDate.now().toString()
        newsDao.insert(
            NewsEntity(
                date = date,
                matchday = state.currentMatchday,
                category = "TRANSFER",
                titleEs = "VENTA: ${player.nameShort}",
                bodyEs = "${player.nameFull} deja ${sellerTeam.nameShort} y firma por ${buyer.nameShort} por ${askingPriceK}K.",
                teamId = managerTeamId,
            )
        )
        newsDao.insert(
            NewsEntity(
                date = date,
                matchday = state.currentMatchday,
                category = "TRANSFER",
                titleEs = "FICHAJE: ${player.nameShort}",
                bodyEs = "${buyer.nameShort} incorpora a ${player.nameFull} por ${askingPriceK}K.",
                teamId = buyer.slotId,
            )
        )

        return Result.success("${player.nameShort} vendido a ${buyer.nameShort} por ${askingPriceK}K")
    }

    /**
     * Placeholder para futuras ofertas IA activas.
     */
    suspend fun generateAiOffers(managerTeamId: Int): List<TransferOffer> {
        val state = seasonStateDao.get() ?: return emptyList()
        if (!state.transferWindowOpen) return emptyList()
        val squad = playerDao.byTeamNow(managerTeamId)
        if (squad.isEmpty()) return emptyList()
        return emptyList()
    }

    private fun evaluateOfferAcceptance(
        state: SeasonStateEntity,
        player: PlayerEntity,
        offeredAmountK: Int,
        marketValueK: Int,
    ): Boolean {
        val ratio = offeredAmountK.toDouble() / marketValueK.coerceAtLeast(1)
        if (ratio >= 1.0) return true
        if (ratio < 0.75) return false
        val base = when {
            ratio >= 0.95 -> 85
            ratio >= 0.90 -> 72
            ratio >= 0.85 -> 56
            else -> 40
        }
        val qualityPenalty = ((player.ca - 70).coerceAtLeast(0) / 2)
        val acceptance = (base - qualityPenalty).coerceIn(10, 95)
        val seed = (state.season.hashCode() * 31L) xor (player.id.toLong() * 17L) xor offeredAmountK.toLong()
        return Random(seed).nextInt(100) < acceptance
    }

    private fun evaluateSaleAcceptance(
        state: SeasonStateEntity,
        player: PlayerEntity,
        askingPriceK: Int,
        marketValueK: Int,
    ): Boolean {
        val ratio = askingPriceK.toDouble() / marketValueK.coerceAtLeast(1)
        if (ratio <= 0.9) return true
        val acceptance = when {
            ratio <= 1.0 -> 72
            ratio <= 1.10 -> 48
            ratio <= 1.20 -> 30
            else -> 15
        }
        val seed = (state.currentMatchday.toLong() shl 8) xor player.id.toLong() xor askingPriceK.toLong()
        return Random(seed).nextInt(100) < acceptance
    }

    private suspend fun pickBuyerTeam(
        managerTeamId: Int,
        managerCompetition: String,
    ): TeamEntity? {
        val allTeams = teamDao.allTeams().first()
            .filter { it.slotId != managerTeamId }
        if (allTeams.isEmpty()) return null

        val preferred = allTeams
            .filter { it.competitionKey == managerCompetition }
            .sortedByDescending { it.budgetK }
        return (preferred + allTeams.sortedByDescending { it.budgetK })
            .firstOrNull { it.budgetK > 0 }
    }

    private fun buildSalaryCapSnapshot(
        team: TeamEntity,
        squad: List<PlayerEntity>,
        seasonState: SeasonStateEntity?,
        isManagerTeam: Boolean,
    ): SalaryCapSnapshot {
        val wageBillK = squad.sumOf { it.wageK.coerceAtLeast(1) }.coerceAtLeast(120)
        val managerMode = seasonState?.normalizedPresidentSalaryCapMode
        val modeKey = if (isManagerTeam && managerMode != null) {
            managerMode
        } else {
            deriveSalaryCapMode(team, wageBillK).name
        }
        val modeFactor = presidentSalaryCapFactor(modeKey)
        val baseCap = if (isManagerTeam && seasonState != null && seasonState.presidentSalaryCapK > 0) {
            seasonState.presidentSalaryCapK
        } else {
            (wageBillK * modeFactor).toInt()
        }
        val salaryCapK = maxOf(baseCap, wageBillK + 1)
        return SalaryCapSnapshot(
            wageBillK = wageBillK,
            salaryCapK = salaryCapK,
            modeKey = modeKey,
            modeLabel = presidentSalaryCapModeLabel(modeKey),
            marginK = salaryCapK - wageBillK,
        )
    }

    private fun deriveSalaryCapMode(team: TeamEntity, wageBillK: Int): SalaryCapMode {
        val liquidityWeeks = team.budgetK.toDouble() / wageBillK.coerceAtLeast(1).toDouble()
        return when {
            liquidityWeeks < 10.0 || team.prestige <= 3 -> SalaryCapMode.STRICT
            liquidityWeeks > 26.0 && team.prestige >= 7 -> SalaryCapMode.FLEX
            else -> SalaryCapMode.BALANCED
        }
    }

    private fun projectedContractTerms(
        player: PlayerEntity,
        destinationCompetition: String,
        season: String,
    ): ContractTerms {
        val media = WageCalculator.playerMedia(player)
        val wageK = WageCalculator.weeklyWageK(media, destinationCompetition)
        val clauseK = WageCalculator.releaseClauseK(wageK, media)
        val seasonStart = season.substringBefore("-").toIntOrNull() ?: 2025
        val durationYears = when {
            media >= 85 -> 5
            media >= 75 -> 4
            else -> 3
        }
        return ContractTerms(
            wageK = wageK,
            releaseClauseK = clauseK,
            contractEndYear = seasonStart + durationYears,
        )
    }

    private fun marketDynamicsMultiplier(state: SeasonStateEntity): Double {
        val trend = state.marketTrend.coerceIn(-20, 20)
        val mood = state.marketFanMood.coerceIn(0, 100)
        val press = state.marketPressRating.coerceIn(0, 100)
        val climate = state.refereeClimate.coerceIn(-20, 20)
        val base = 1.0 +
            trend * 0.005 +
            (mood - 50) * 0.0015 +
            (press - 50) * 0.0010 +
            climate * 0.0008
        return base.coerceIn(0.85, 1.18)
    }
}
