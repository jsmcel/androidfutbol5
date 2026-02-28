package com.pcfutbol.competition

import com.pcfutbol.core.data.db.*
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import com.pcfutbol.matchsim.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class MatchdayResult(
    val matchday: Int,
    val competitionCode: String,
    val results: List<MatchResult>,
    val updatedStandings: List<StandingEntity>,
)

/**
 * Repositorio principal del motor de competición.
 * Orquesta FixtureGenerator, MatchSimulator y StandingsCalculator.
 */
@Singleton
class CompetitionRepository @Inject constructor(
    private val competitionDao: CompetitionDao,
    private val fixtureDao: FixtureDao,
    private val standingDao: StandingDao,
    private val teamDao: TeamDao,
    private val playerDao: PlayerDao,
    private val seasonStateDao: SeasonStateDao,
) {

    fun standings(comp: String): Flow<List<StandingWithTeam>> =
        standingDao.standingWithTeam(comp)

    fun fixtures(comp: String): Flow<List<FixtureEntity>> =
        fixtureDao.byCompetition(comp)

    suspend fun pendingFixtures(comp: String): Int =
        fixtureDao.pendingCount(comp)

    suspend fun setupAllLeagues(codes: Collection<String>? = null) {
        val leagueCodes = codes?.toList()
            ?: competitionDao.all()
                .first()
                .filter { it.formatType == "LEAGUE" }
                .map { it.code }
        for (leagueCode in leagueCodes) {
            setupLeague(leagueCode)
        }
    }

    /**
     * Simula todos los partidos de una jornada concreta y actualiza la clasificación.
     * @param seed semilla maestra; cada partido usa seed XOR fixtureId para independencia
     */
    suspend fun advanceMatchday(
        competitionCode: String,
        matchday: Int,
        masterSeed: Long,
    ): MatchdayResult {
        decrementPlayerAvailabilityCounters()

        val fixtures = fixtureDao.byMatchday(competitionCode, matchday)
            .filter { !it.played }

        val results = mutableListOf<MatchResult>()
        fixtures.forEach { fixture ->
            val home = buildTeamInput(fixture.homeTeamId, isHome = true)
            val away = buildTeamInput(fixture.awayTeamId, isHome = false)
            val seed = masterSeed xor fixture.id.toLong()
            val ctx = MatchContext(fixture.id, home, away, seed, fixture.neutral)
            val result = MatchSimulator.simulate(ctx)
            fixtureDao.recordResult(
                id = fixture.id,
                hg = result.homeGoals,
                ag = result.awayGoals,
                seed = seed,
            )
            fixtureDao.updateEventsJson(fixture.id, serializeEvents(result.events))
            applyPostMatchEffects(fixture, result)
            results += result
        }

        // Recalcular clasificación con todos los partidos jugados hasta ahora
        val allPlayedFixtures = fixtureDao.byCompetition(competitionCode)
            .first()
            .filter { it.played }
        val teamIds = teamDao.byCompetition(competitionCode).first().map { it.slotId }
        val updatedStandings = StandingsCalculator.calculate(competitionCode, teamIds, allPlayedFixtures)
        standingDao.insertAll(updatedStandings)

        // Avanzar matchday en SeasonState y gestionar ventana de mercado
        val state = seasonStateDao.get()
        if (state != null && matchday >= state.currentMatchday) {
            val nextMd = matchday + 1
            val (windowOpen, phase) = when {
                // Fin de pretemporada: cierra ventana de verano al pasar jornada 3
                nextMd > 3 && state.transferWindowOpen && state.phase == "PRESEASON" ->
                    false to "SEASON"
                // Apertura ventana de invierno (jornada 19)
                nextMd == 19 && !state.transferWindowOpen ->
                    true to state.phase
                // Cierre ventana de invierno (tras jornada 21)
                nextMd > 21 && state.transferWindowOpen && state.phase == "SEASON" ->
                    false to state.phase
                else ->
                    state.transferWindowOpen to state.phase
            }
            seasonStateDao.update(state.copy(
                currentMatchday = nextMd,
                transferWindowOpen = windowOpen,
                phase = phase,
            ))
        }

        return MatchdayResult(
            matchday = matchday,
            competitionCode = competitionCode,
            results = results,
            updatedStandings = updatedStandings,
        )
    }

    /**
     * Inicializa el calendario de una liga.
     * Borra fixtures existentes y genera el calendar completo.
     */
    suspend fun setupLeague(competitionCode: String) {
        val teams = teamDao.byCompetition(competitionCode).first().map { it.slotId }
        fixtureDao.deleteByCompetition(competitionCode)
        standingDao.deleteByCompetition(competitionCode)

        refreshCompetitionMetadata(competitionCode, teams.size)
        if (teams.size < 2) return

        val fixtures = FixtureGenerator.generateLeague(competitionCode, teams)
        fixtureDao.insertAll(fixtures)

        val standings = teams.map { teamId ->
            StandingEntity(competitionCode = competitionCode, teamId = teamId)
        }
        standingDao.insertAll(standings)
    }

    // -------------------------------------------------------------------------

    private suspend fun applyPostMatchEffects(
        fixture: FixtureEntity,
        result: MatchResult,
    ) {
        applyMoraleAfterMatch(
            homeTeamId = fixture.homeTeamId,
            awayTeamId = fixture.awayTeamId,
            homeGoals = result.homeGoals,
            awayGoals = result.awayGoals,
        )
        applyInjuriesFromEvents(result)
    }

    private suspend fun applyMoraleAfterMatch(
        homeTeamId: Int,
        awayTeamId: Int,
        homeGoals: Int,
        awayGoals: Int,
    ) {
        val (homeDelta, awayDelta) = MatchOutcomeRules.moraleDeltas(homeGoals, awayGoals)
        if (homeDelta != 0) playerDao.shiftTeamMoral(homeTeamId, homeDelta)
        if (awayDelta != 0) playerDao.shiftTeamMoral(awayTeamId, awayDelta)
    }

    private suspend fun applyInjuriesFromEvents(result: MatchResult) {
        val injuryEvents = result.events.filter {
            it.type == EventType.INJURY && it.playerId != null && it.injuryWeeks != null
        }
        injuryEvents.forEach { injury ->
            val playerId = injury.playerId ?: return@forEach
            val weeks = injury.injuryWeeks ?: return@forEach
            val current = playerDao.byId(playerId) ?: return@forEach
            val injuryWeeksLeft = maxOf(current.injuryWeeksLeft, weeks)
            val status = when {
                injuryWeeksLeft > 0 -> 1
                current.sanctionMatchesLeft > 0 -> 2
                else -> 0
            }
            playerDao.update(current.copy(
                injuryWeeksLeft = injuryWeeksLeft,
                status = status,
            ))
        }
    }

    /**
     * Cierra una jornada reduciendo contadores de indisponibilidad.
     * Se ejecuta al comienzo de la siguiente simulación para no acortar
     * la duración de lesiones producidas en la jornada recién jugada.
     */
    private suspend fun decrementPlayerAvailabilityCounters() {
        val unavailable = (playerDao.withInjury() + playerDao.withSanction())
            .associateBy { it.id }
            .values
        if (unavailable.isEmpty()) return

        val updated = unavailable.mapNotNull { player ->
            val injuryWeeksLeft = (player.injuryWeeksLeft - 1).coerceAtLeast(0)
            val sanctionMatchesLeft = (player.sanctionMatchesLeft - 1).coerceAtLeast(0)
            val status = when {
                injuryWeeksLeft > 0 -> 1
                sanctionMatchesLeft > 0 -> 2
                else -> 0
            }
            if (
                injuryWeeksLeft == player.injuryWeeksLeft &&
                sanctionMatchesLeft == player.sanctionMatchesLeft &&
                status == player.status
            ) {
                null
            } else {
                player.copy(
                    injuryWeeksLeft = injuryWeeksLeft,
                    sanctionMatchesLeft = sanctionMatchesLeft,
                    status = status,
                )
            }
        }
        if (updated.isNotEmpty()) playerDao.updateAll(updated)
    }

    private suspend fun buildTeamInput(teamId: Int, isHome: Boolean): TeamMatchInput {
        val players = playerDao.byTeamNow(teamId)
        val available = players.filter {
            it.status == 0 && it.injuryWeeksLeft <= 0 && it.sanctionMatchesLeft <= 0
        }
        val preferred = available.sortedWith(
            compareByDescending<PlayerEntity> { it.isStarter }
                .thenByDescending { it.ca }
                .thenBy { it.number }
        )
        val preferredIds = preferred.map { it.id }.toSet()
        val fallback = players
            .filter { it.id !in preferredIds }
            .sortedByDescending { it.ca }
        val starters = (preferred + fallback).take(11)
        return TeamMatchInput(
            teamId   = teamId,
            teamName = teamDao.byId(teamId)?.nameShort ?: "Equipo $teamId",
            squad    = starters.map { it.toSimAttrs() },
            isHome   = isHome,
        )
    }

    private suspend fun refreshCompetitionMetadata(competitionCode: String, teamCount: Int) {
        val competition = competitionDao.byCode(competitionCode) ?: return
        if (competition.formatType != "LEAGUE") return

        val relegationSlots = CompetitionDefinitions.defaultRelegationSlots(teamCount)
        val promotionSlots = promotionSlotsFor(competitionCode, teamCount)
        val matchdays = CompetitionDefinitions.roundRobinMatchdays(teamCount)

        competitionDao.update(
            competition.copy(
                teamCount = teamCount,
                matchdayCount = matchdays,
                relegationSlots = relegationSlots,
                promotionSlots = promotionSlots,
            )
        )
    }

    private fun promotionSlotsFor(competitionCode: String, teamCount: Int): Int = when (competitionCode) {
        "LIGA2" -> when {
            teamCount >= 18 -> 3
            teamCount >= 10 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        "LIGA2B", "LIGA2B2" -> when {
            teamCount >= 20 -> 4
            teamCount >= 12 -> 3
            teamCount >= 6 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        "RFEF1A", "RFEF1B" -> if (teamCount >= 2) 1 else 0
        "RFEF2A", "RFEF2B", "RFEF2C", "RFEF2D" -> when {
            teamCount >= 18 -> 3
            teamCount >= 10 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        else -> 0
    }

    private fun PlayerEntity.toSimAttrs() = PlayerSimAttrs(
        playerId = id,
        playerName = nameShort.ifBlank { nameFull },
        ve = ve, re = re, ag = ag, ca = ca,
        remate = remate, regate = regate, pase = pase,
        tiro = tiro, entrada = entrada, portero = portero,
        estadoForma = estadoForma, moral = moral,
    )

    private fun serializeEvents(events: List<MatchEvent>): String {
        val array = JSONArray()
        events.forEach { event ->
            val obj = JSONObject()
                .put("minute", event.minute)
                .put("type", event.type.name)
                .put("teamId", event.teamId)
                .put("description", event.description)
            event.playerId?.let { obj.put("playerId", it) }
            event.playerName?.let { obj.put("playerName", it) }
            event.injuryWeeks?.let { obj.put("injuryWeeks", it) }
            array.put(obj)
        }
        return array.toString()
    }
}
