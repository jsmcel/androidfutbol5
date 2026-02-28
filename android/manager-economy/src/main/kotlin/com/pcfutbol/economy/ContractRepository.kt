package com.pcfutbol.economy

import com.pcfutbol.core.data.db.NewsDao
import com.pcfutbol.core.data.db.NewsEntity
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContractRepository @Inject constructor(
    private val playerDao: PlayerDao,
    private val teamDao: TeamDao,
    private val seasonStateDao: SeasonStateDao,
    private val newsDao: NewsDao,
) {

    suspend fun squadContracts(managerTeamId: Int): List<PlayerEntity> =
        playerDao.byTeamNow(managerTeamId)

    suspend fun renewContract(
        playerId: Int,
        managerTeamId: Int,
        newContractEndYear: Int,
        newWageK: Int,
        newClauseK: Int,
    ): Result<String> {
        val state = seasonStateDao.get() ?: return Result.failure(Exception("Sin temporada activa"))
        val team = teamDao.byId(managerTeamId) ?: return Result.failure(Exception("Equipo no encontrado"))
        val player = playerDao.byId(playerId) ?: return Result.failure(Exception("Jugador no encontrado"))
        if (player.teamSlotId != managerTeamId) {
            return Result.failure(Exception("El jugador no pertenece a tu equipo"))
        }

        val seasonYear = parseSeasonStartYear(state.season)
        if (newContractEndYear <= seasonYear) {
            return Result.failure(Exception("El contrato debe acabar despues de $seasonYear"))
        }
        if (newContractEndYear > seasonYear + 7) {
            return Result.failure(Exception("Duracion de contrato excesiva"))
        }
        if (newWageK <= 0) {
            return Result.failure(Exception("Salario invalido"))
        }
        if (newClauseK < 200) {
            return Result.failure(Exception("La clausula minima es 200K"))
        }

        val yearsDelta = (newContractEndYear - player.contractEndYear).coerceAtLeast(0)
        val wageJump = (newWageK - player.wageK).coerceAtLeast(0)
        val negotiationFeeK = (newWageK * 5 + yearsDelta * 40 + wageJump * 3).coerceAtLeast(60)
        if (team.budgetK < negotiationFeeK) {
            return Result.failure(Exception("Presupuesto insuficiente para renovar (${negotiationFeeK}K)"))
        }

        playerDao.update(
            player.copy(
                wageK = newWageK,
                contractEndYear = newContractEndYear,
                releaseClauseK = newClauseK,
            )
        )
        teamDao.update(team.copy(budgetK = team.budgetK - negotiationFeeK))
        newsDao.insert(
            NewsEntity(
                date = java.time.LocalDate.now().toString(),
                matchday = state.currentMatchday,
                category = "BOARD",
                titleEs = "RENOVACION: ${player.nameShort}",
                bodyEs = "${player.nameFull} renueva hasta $newContractEndYear (${newWageK}K/sem, clausula ${newClauseK}K).",
                teamId = managerTeamId,
            )
        )

        return Result.success("Contrato renovado (${negotiationFeeK}K)")
    }

    suspend fun rescindContract(
        playerId: Int,
        managerTeamId: Int,
    ): Result<String> {
        val state = seasonStateDao.get() ?: return Result.failure(Exception("Sin temporada activa"))
        val team = teamDao.byId(managerTeamId) ?: return Result.failure(Exception("Equipo no encontrado"))
        val player = playerDao.byId(playerId) ?: return Result.failure(Exception("Jugador no encontrado"))
        if (player.teamSlotId != managerTeamId) {
            return Result.failure(Exception("El jugador no pertenece a tu equipo"))
        }
        val compensationK = (player.wageK * 12).coerceAtLeast(80)
        if (team.budgetK < compensationK) {
            return Result.failure(Exception("No puedes rescindir: faltan ${compensationK - team.budgetK}K"))
        }

        playerDao.update(
            player.copy(
                teamSlotId = null,
                isStarter = false,
                status = 0,
            )
        )
        teamDao.update(team.copy(budgetK = team.budgetK - compensationK))
        newsDao.insert(
            NewsEntity(
                date = java.time.LocalDate.now().toString(),
                matchday = state.currentMatchday,
                category = "BOARD",
                titleEs = "RESCISION: ${player.nameShort}",
                bodyEs = "${player.nameFull} queda libre. Coste de rescision: ${compensationK}K.",
                teamId = managerTeamId,
            )
        )

        return Result.success("Contrato rescindido (${compensationK}K)")
    }

    private fun parseSeasonStartYear(season: String): Int =
        season.substringBefore("-").toIntOrNull() ?: 2025
}
