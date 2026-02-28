package com.pcfutbol.economy

import com.pcfutbol.core.data.db.CompetitionDao
import com.pcfutbol.core.data.db.FixtureDao
import com.pcfutbol.core.data.db.ManagerProfileDao
import com.pcfutbol.core.data.db.NewsDao
import com.pcfutbol.core.data.db.NewsEntity
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.SeasonStateEntity
import com.pcfutbol.core.data.db.StandingDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.managerLeague
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import com.pcfutbol.matchsim.PlayerDevelopmentEngine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val CREY = "CREY"
private const val LIGA1 = "LIGA1"
private const val LIGA2 = "LIGA2"
private const val RFEF1A = "RFEF1A"
private const val RFEF1B = "RFEF1B"
private const val RFEF2A = "RFEF2A"
private const val RFEF2B = "RFEF2B"
private const val RFEF2C = "RFEF2C"
private const val RFEF2D = "RFEF2D"

data class TeamResult(
    val teamId: Int,
    val name: String,
    val position: Int,
    val points: Int,
)

data class SeasonSummary(
    val season: String,
    val champion: TeamResult?,
    val relegated: List<TeamResult>,
    val promotedToLiga1: List<TeamResult>,
    val promotedToLiga2: List<TeamResult> = emptyList(),
    val promotedToRfef1A: List<TeamResult> = emptyList(),
    val promotedToRfef1B: List<TeamResult> = emptyList(),
    val managerPosition: Int,
    val managerPoints: Int,
    val objective: String,
    val objectiveMet: Boolean,
)

/**
 * Orquesta el flujo de fin de temporada:
 *  1. Identifica campeon, ascensos y descensos.
 *  2. Evalua el objetivo de la junta directiva del manager.
 *  3. Aplica movimientos de equipos entre competiciones.
 *  4. Genera noticias y actualiza el estado de temporada.
 */
@Singleton
class EndOfSeasonUseCase @Inject constructor(
    private val standingDao: StandingDao,
    private val teamDao: TeamDao,
    private val competitionDao: CompetitionDao,
    private val seasonStateDao: SeasonStateDao,
    private val managerProfileDao: ManagerProfileDao,
    private val newsDao: NewsDao,
    private val playerDao: PlayerDao,
    private val fixtureDao: FixtureDao,
) {

    /**
     * Calcula el resumen de fin de temporada sin modificar aun la BD.
     */
    suspend fun computeSummary(): SeasonSummary {
        val state = seasonStateDao.get() ?: return emptySeasonSummary()

        val liga1Standings = standingDao.getAllSorted(LIGA1)
        val liga2Standings = standingDao.getAllSorted(LIGA2)
        val rfef1aStandings = standingDao.getAllSorted(RFEF1A)
        val rfef1bStandings = standingDao.getAllSorted(RFEF1B)
        val rfef2aStandings = standingDao.getAllSorted(RFEF2A)
        val rfef2bStandings = standingDao.getAllSorted(RFEF2B)
        val rfef2cStandings = standingDao.getAllSorted(RFEF2C)
        val rfef2dStandings = standingDao.getAllSorted(RFEF2D)
        val liga1RelegationSlots = resolveRelegationSlots(LIGA1, liga1Standings.size)
        val liga2PromotionSlots = competitionDao.byCode(LIGA2)?.promotionSlots?.coerceAtLeast(0) ?: 3

        val managerTeam = teamDao.byId(state.managerTeamId)
        val managerCompCode = managerTeam
            ?.competitionKey
            ?.takeIf { it.isNotBlank() }
            ?: state.managerLeague
        val managerStandings = standingDao.getAllSorted(managerCompCode)

        val championStanding = managerStandings.firstOrNull() ?: liga1Standings.firstOrNull()
        val champion = championStanding?.let { standing ->
            TeamResult(
                teamId = standing.teamId,
                name = teamDao.byId(standing.teamId)?.nameShort ?: "",
                position = standing.position,
                points = standing.points,
            )
        }

        val relegated = liga1Standings
            .filter { it.position > liga1Standings.size - liga1RelegationSlots }
            .map { TeamResult(it.teamId, teamDao.byId(it.teamId)?.nameShort ?: "", it.position, it.points) }

        val promotedToLiga1 = liga2Standings
            .filter { it.position <= liga2PromotionSlots }
            .map { TeamResult(it.teamId, teamDao.byId(it.teamId)?.nameShort ?: "", it.position, it.points) }
        val promotedToLiga2 = listOfNotNull(
            rfef1aStandings.firstOrNull(),
            rfef1bStandings.firstOrNull(),
        ).map { TeamResult(it.teamId, teamDao.byId(it.teamId)?.nameShort ?: "", it.position, it.points) }
        val promotedToRfef1A = (rfef2aStandings + rfef2bStandings)
            .filter { it.position <= 3 }
            .map { TeamResult(it.teamId, teamDao.byId(it.teamId)?.nameShort ?: "", it.position, it.points) }
        val promotedToRfef1B = (rfef2cStandings + rfef2dStandings)
            .filter { it.position <= 3 }
            .map { TeamResult(it.teamId, teamDao.byId(it.teamId)?.nameShort ?: "", it.position, it.points) }

        val managerStanding = managerStandings.find { it.teamId == state.managerTeamId }
        val managerPos = managerStanding?.position ?: 0
        val managerPts = managerStanding?.points ?: 0

        val objective = managerTeam?.let { BoardObjectives.assignObjective(it) }
        val objectiveStandings = managerStandings.ifEmpty {
            if (managerCompCode == LIGA2) liga2Standings else liga1Standings
        }
        val relegationSlots = resolveRelegationSlots(managerCompCode, objectiveStandings.size)
        val met = managerStanding != null && objective != null &&
            BoardObjectives.evaluate(
                objective = objective,
                standing = managerStanding,
                totalTeams = objectiveStandings.size.coerceAtLeast(1),
                relegationSlots = relegationSlots,
            )

        return SeasonSummary(
            season = state.season,
            champion = champion,
            relegated = relegated,
            promotedToLiga1 = promotedToLiga1,
            promotedToLiga2 = promotedToLiga2,
            promotedToRfef1A = promotedToRfef1A,
            promotedToRfef1B = promotedToRfef1B,
            managerPosition = managerPos,
            managerPoints = managerPts,
            objective = objective?.labelEs ?: "",
            objectiveMet = met,
        )
    }

    /**
     * Aplica el fin de temporada: actualiza competicionKey, genera noticias
     * y marca la temporada como POSTSEASON.
     */
    suspend fun applyEndOfSeason(summary: SeasonSummary) {
        val state = seasonStateDao.get() ?: return
        if (state.phase == "POSTSEASON") return

        summary.relegated.forEach { tr ->
            val team = teamDao.byId(tr.teamId) ?: return@forEach
            teamDao.update(team.copy(competitionKey = LIGA2))
        }
        summary.promotedToLiga1.forEach { tr ->
            val team = teamDao.byId(tr.teamId) ?: return@forEach
            teamDao.update(team.copy(competitionKey = LIGA1))
        }
        summary.promotedToLiga2.forEach { tr ->
            val team = teamDao.byId(tr.teamId) ?: return@forEach
            teamDao.update(team.copy(competitionKey = LIGA2))
        }
        summary.promotedToRfef1A.forEach { tr ->
            val team = teamDao.byId(tr.teamId) ?: return@forEach
            teamDao.update(team.copy(competitionKey = RFEF1A))
        }
        summary.promotedToRfef1B.forEach { tr ->
            val team = teamDao.byId(tr.teamId) ?: return@forEach
            teamDao.update(team.copy(competitionKey = RFEF1B))
        }

        val matchday = state.currentMatchday
        val date = java.time.LocalDate.now().toString()

        summary.champion?.let { ch ->
            newsDao.insert(
                NewsEntity(
                    date = date,
                    matchday = matchday,
                    category = "BOARD",
                    titleEs = "CAMPEON ${ch.name}",
                    bodyEs = "${ch.name} se proclama campeon de liga con ${ch.points} puntos.",
                )
            )
        }
        if (summary.relegated.isNotEmpty()) {
            val names = summary.relegated.joinToString(", ") { it.name }
            newsDao.insert(
                NewsEntity(
                    date = date,
                    matchday = matchday,
                    category = "BOARD",
                    titleEs = "Descenso confirmado",
                    bodyEs = "$names descienden a Segunda Division.",
                )
            )
        }
        if (summary.promotedToLiga1.isNotEmpty()) {
            val names = summary.promotedToLiga1.joinToString(", ") { it.name }
            newsDao.insert(
                NewsEntity(
                    date = date,
                    matchday = matchday,
                    category = "BOARD",
                    titleEs = "Ascenso a Primera Division",
                    bodyEs = "$names ascienden a Primera Division.",
                )
            )
        }
        if (summary.promotedToLiga2.isNotEmpty()) {
            val names = summary.promotedToLiga2.joinToString(", ") { it.name }
            newsDao.insert(
                NewsEntity(
                    date = date,
                    matchday = matchday,
                    category = "BOARD",
                    titleEs = "Ascenso a Segunda Division",
                    bodyEs = "$names ascienden desde 1a RFEF a Segunda Division.",
                )
            )
        }
        if (summary.promotedToRfef1A.isNotEmpty()) {
            val names = summary.promotedToRfef1A.joinToString(", ") { it.name }
            newsDao.insert(
                NewsEntity(
                    date = date,
                    matchday = matchday,
                    category = "BOARD",
                    titleEs = "Ascenso a 1a RFEF Grupo A",
                    bodyEs = "$names ascienden desde 2a RFEF al Grupo A de 1a RFEF.",
                )
            )
        }
        if (summary.promotedToRfef1B.isNotEmpty()) {
            val names = summary.promotedToRfef1B.joinToString(", ") { it.name }
            newsDao.insert(
                NewsEntity(
                    date = date,
                    matchday = matchday,
                    category = "BOARD",
                    titleEs = "Ascenso a 1a RFEF Grupo B",
                    bodyEs = "$names ascienden desde 2a RFEF al Grupo B de 1a RFEF.",
                )
            )
        }

        val objectiveNews = if (summary.objectiveMet) {
            "Has cumplido el objetivo de la temporada: ${summary.objective}."
        } else {
            "No has cumplido el objetivo: ${summary.objective}. Posicion final: ${summary.managerPosition}."
        }
        newsDao.insert(
            NewsEntity(
                date = date,
                matchday = matchday,
                category = "BOARD",
                titleEs = if (summary.objectiveMet) "Objetivo cumplido" else "Objetivo no cumplido",
                bodyEs = objectiveNews,
                teamId = state.managerTeamId,
            )
        )

        applySeasonDevelopment(state)

        seasonStateDao.update(
            state.copy(
                phase = "POSTSEASON",
                objectivesMet = summary.objectiveMet,
            )
        )
    }

    /**
     * Inicia la siguiente temporada.
     * Las pantallas de navegacion deben llamar a setupLeague() tras este metodo.
     */
    suspend fun advanceToNextSeason() {
        val state = seasonStateDao.get() ?: return
        val nextSeason = advanceSeasonString(state.season)

        val allPlayers = playerDao.allPlayers().first()
        allPlayers.forEach { p ->
            val isRetired = p.status == PlayerDevelopmentEngine.RETIRED_STATUS
            val nextStatus = if (isRetired) PlayerDevelopmentEngine.RETIRED_STATUS else 0
            val nextTeamSlot = if (isRetired) null else p.teamSlotId
            val nextStarter = if (isRetired) false else p.isStarter
            if (p.estadoForma != 50 ||
                p.moral != 50 ||
                p.status != nextStatus ||
                p.teamSlotId != nextTeamSlot ||
                p.isStarter != nextStarter ||
                p.injuryWeeksLeft != 0 ||
                p.sanctionMatchesLeft != 0
            ) {
                playerDao.update(
                    p.copy(
                        estadoForma = 50,
                        moral = 50,
                        status = nextStatus,
                        teamSlotId = nextTeamSlot,
                        isStarter = nextStarter,
                        injuryWeeksLeft = 0,
                        sanctionMatchesLeft = 0,
                    )
                )
            }
        }

        fixtureDao.deleteByCompetition(CREY)

        seasonStateDao.update(
            state.copy(
                season = nextSeason,
                currentMatchday = 1,
                phase = "PRESEASON",
                transferWindowOpen = true,
                objectivesMet = false,
            )
        )
    }

    // ---------------------------------------------------------------------

    private suspend fun resolveRelegationSlots(competitionCode: String, teamCount: Int): Int {
        val dbValue = competitionDao.byCode(competitionCode)
            ?.relegationSlots
            ?.coerceAtLeast(0)
        return dbValue ?: CompetitionDefinitions.defaultRelegationSlots(teamCount)
    }

    /**
     * Aplica evolucion anual de jugadores y genera canteranos para el equipo del manager.
     */
    private suspend fun applySeasonDevelopment(state: SeasonStateEntity) {
        val seasonStartYear = parseSeasonStartYear(state.season)
        val seed = state.season.hashCode().toLong()
        val allPlayers = playerDao.allPlayers().first()
        if (allPlayers.isEmpty()) return
        val managerContext = buildDevelopmentContext(state.managerTeamId)

        val source = allPlayers.map { p ->
            PlayerDevelopmentEngine.DevelopmentPlayer(
                id = p.id,
                birthYear = p.birthYear,
                status = p.status,
                ve = p.ve,
                re = p.re,
                ag = p.ag,
                ca = p.ca,
                remate = p.remate,
                regate = p.regate,
                pase = p.pase,
                tiro = p.tiro,
                entrada = p.entrada,
                portero = p.portero,
            )
        }

        val evolved = PlayerDevelopmentEngine.applySeasonGrowth(
            players = source,
            seasonStartYear = seasonStartYear,
            seed = seed,
            context = managerContext,
        ).associateBy { it.id }

        val updatedPlayers = allPlayers.map { p ->
            val evolvedPlayer = evolved[p.id] ?: return@map p
            val retired = evolvedPlayer.status == PlayerDevelopmentEngine.RETIRED_STATUS
            p.copy(
                teamSlotId = if (retired) null else p.teamSlotId,
                isStarter = if (retired) false else p.isStarter,
                status = evolvedPlayer.status,
                ve = evolvedPlayer.ve,
                re = evolvedPlayer.re,
                ag = evolvedPlayer.ag,
                ca = evolvedPlayer.ca,
                remate = evolvedPlayer.remate,
                regate = evolvedPlayer.regate,
                pase = evolvedPlayer.pase,
                tiro = evolvedPlayer.tiro,
                entrada = evolvedPlayer.entrada,
                portero = evolvedPlayer.portero,
            )
        }
        playerDao.updateAll(updatedPlayers)

        val managerTeamId = state.managerTeamId
        if (managerTeamId <= 0) return

        val managerTeam = teamDao.byId(managerTeamId) ?: return
        val nextPid = (updatedPlayers.maxOfOrNull { it.pid } ?: 0) + 1
        val youthCount = 2 + if (
            managerContext.staff.juveniles >= 80 &&
            managerContext.staff.ojeador >= 70
        ) 1 else 0
        val youthPlayers = PlayerDevelopmentEngine.generateYouthPlayers(
            teamSlotId = managerTeamId,
            count = youthCount,
            seasonStartYear = seasonStartYear,
            seed = seed xor managerTeamId.toLong(),
            context = managerContext,
        )

        val youthEntities = youthPlayers.mapIndexed { index, youth ->
            PlayerEntity(
                pid = nextPid + index,
                teamSlotId = managerTeamId,
                number = 0,
                nameShort = youth.name.take(15),
                nameFull = youth.name,
                position = youth.position,
                roles = defaultRolesForPosition(youth.position),
                citizenship = managerTeam.countryId.ifBlank { "ES" },
                status = youth.status,
                birthDay = 1,
                birthMonth = 7,
                birthYear = youth.birthYear,
                height = if (youth.position == "PO") 188 else 178,
                weight = if (youth.position == "PO") 82 else 75,
                skin = 1,
                hair = 1,
                ve = youth.ve,
                re = youth.re,
                ag = youth.ag,
                ca = youth.ca,
                remate = youth.remate,
                regate = youth.regate,
                pase = youth.pase,
                tiro = youth.tiro,
                entrada = youth.entrada,
                portero = youth.portero,
                wageK = 1,
                contractEndYear = seasonStartYear + 4,
                releaseClauseK = 1500,
                isStarter = false,
            )
        }
        if (youthEntities.isNotEmpty()) {
            playerDao.insertAll(youthEntities)
        }
    }

    private fun advanceSeasonString(season: String): String {
        return try {
            val startYear = season.substringBefore("-").toInt()
            val nextStart = startYear + 1
            val nextEnd = (nextStart % 100) + 1
            "$nextStart-${nextEnd.toString().padStart(2, '0')}"
        } catch (_: Exception) {
            season
        }
    }

    private fun parseSeasonStartYear(season: String): Int =
        season.substringBefore("-").toIntOrNull() ?: 2025

    private suspend fun buildDevelopmentContext(
        managerTeamId: Int,
    ): PlayerDevelopmentEngine.DevelopmentContext {
        val profile = managerProfileDao.byTeam(managerTeamId)
            ?: return PlayerDevelopmentEngine.DevelopmentContext()
        return PlayerDevelopmentEngine.DevelopmentContext(
            staff = PlayerDevelopmentEngine.StaffProfile(
                segundoEntrenador = profile.segundoEntrenador,
                fisio = profile.fisio,
                psicologo = profile.psicologo,
                asistente = profile.asistente,
                secretario = profile.secretario,
                ojeador = profile.ojeador,
                juveniles = profile.juveniles,
                cuidador = profile.cuidador,
            ),
            training = PlayerDevelopmentEngine.TrainingPlan(
                intensity = parseIntensity(profile.trainingIntensity),
                focus = parseFocus(profile.trainingFocus),
            ),
        )
    }

    private fun parseIntensity(value: String): PlayerDevelopmentEngine.TrainingIntensity =
        runCatching { PlayerDevelopmentEngine.TrainingIntensity.valueOf(value.uppercase()) }
            .getOrDefault(PlayerDevelopmentEngine.TrainingIntensity.MEDIUM)

    private fun parseFocus(value: String): PlayerDevelopmentEngine.TrainingFocus =
        runCatching { PlayerDevelopmentEngine.TrainingFocus.valueOf(value.uppercase()) }
            .getOrDefault(PlayerDevelopmentEngine.TrainingFocus.BALANCED)

    private fun defaultRolesForPosition(position: String): String = when (position) {
        "PO" -> "[0,0,0,0,0,0]"
        "DF" -> "[1,0,0,0,0,0]"
        "MC" -> "[2,0,0,0,0,0]"
        "DC" -> "[3,0,0,0,0,0]"
        else -> "[2,0,0,0,0,0]"
    }

    private fun emptySeasonSummary() = SeasonSummary(
        season = "2025-26",
        champion = null,
        relegated = emptyList(),
        promotedToLiga1 = emptyList(),
        managerPosition = 0,
        managerPoints = 0,
        objective = "",
        objectiveMet = false,
    )
}
