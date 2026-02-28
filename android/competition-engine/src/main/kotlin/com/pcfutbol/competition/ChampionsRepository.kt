package com.pcfutbol.competition

import com.pcfutbol.core.data.db.FixtureDao
import com.pcfutbol.core.data.db.FixtureEntity
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.StandingDao
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import com.pcfutbol.matchsim.EventType
import com.pcfutbol.matchsim.MatchContext
import com.pcfutbol.matchsim.MatchEvent
import com.pcfutbol.matchsim.MatchResult
import com.pcfutbol.matchsim.MatchSimulator
import com.pcfutbol.matchsim.PlayerSimAttrs
import com.pcfutbol.matchsim.TeamMatchInput
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Champions League simplificada:
 * QF (8 equipos) -> SF (4) -> F (2).
 */
@Singleton
class ChampionsRepository @Inject constructor(
    private val fixtureDao: FixtureDao,
    private val teamDao: TeamDao,
    private val standingDao: StandingDao,
    private val seasonStateDao: SeasonStateDao,
    private val playerDao: PlayerDao,
) {
    companion object {
        private val ROUNDS = listOf("QF", "SF", "F")
        private val NEXT_ROUND = mapOf("QF" to "SF", "SF" to "F")
        private val ROUND_MATCHDAY = mapOf("QF" to 1, "SF" to 2, "F" to 3)
        private val EUROPEAN_LEAGUES = setOf(
            "PRML", "SERIA", "LIG1", "BUN1", "ERED", "PRIM",
            "BELGA", "SUPERL", "SCOT", "RPL", "DSL", "EKSTR", "ABUND",
        )
    }

    suspend fun setup() {
        fixtureDao.deleteByCompetition(CompetitionDefinitions.CEURO)

        val state = seasonStateDao.get()
        val setupSeed = (state?.season?.hashCode()?.toLong() ?: 0L) xor
            CompetitionDefinitions.CEURO.hashCode().toLong()
        val random = Random(setupSeed)

        val liga1Top = standingDao.getAllSorted(CompetitionDefinitions.LIGA1)
            .map { it.teamId }
            .take(4)
            .ifEmpty {
                teamDao.byCompetition(CompetitionDefinitions.LIGA1)
                    .first()
                    .map { it.slotId }
                    .take(4)
            }

        val allTeams = teamDao.allTeams().first()
        val european = allTeams
            .filter { it.competitionKey in EUROPEAN_LEAGUES }
            .map { it.slotId }
            .shuffled(random)

        val participants = linkedSetOf<Int>()
        participants.addAll(liga1Top)
        european.forEach { if (participants.size < 8) participants.add(it) }

        if (participants.size < 8) {
            val fallback = allTeams
                .map { it.slotId }
                .shuffled(random)
            fallback.forEach { if (participants.size < 8) participants.add(it) }
        }

        if (participants.size < 2) return

        val fixtures = FixtureGenerator.generateKnockout(
            competitionCode = CompetitionDefinitions.CEURO,
            teamIds = participants.toList().take(8).shuffled(random),
            startRound = "QF",
            startMatchday = ROUND_MATCHDAY.getValue("QF"),
        )
        fixtureDao.insertAll(fixtures)
    }

    suspend fun simulateRound(seed: Long) {
        val round = getCurrentRound()
        if (round == "DONE") return

        decrementPlayerAvailabilityCounters()
        val pending = fixtureDao.byRound(CompetitionDefinitions.CEURO, round)
            .filter { !it.played }
        pending.forEach { fixture ->
            val home = buildTeamInput(fixture.homeTeamId, isHome = !fixture.neutral)
            val away = buildTeamInput(fixture.awayTeamId, isHome = fixture.neutral)
            val fixtureSeed = seed xor fixture.id.toLong()
            val result = MatchSimulator.simulate(
                MatchContext(
                    fixtureId = fixture.id,
                    home = home,
                    away = away,
                    seed = fixtureSeed,
                    neutral = fixture.neutral,
                )
            )

            val penalties = if (result.homeGoals == result.awayGoals) {
                MatchOutcomeRules.simulatePenaltyShootout(home, away, fixtureSeed)
            } else {
                null
            }

            fixtureDao.recordResult(
                id = fixture.id,
                hg = result.homeGoals,
                ag = result.awayGoals,
                seed = fixtureSeed,
                decidedByPenalties = penalties != null,
                homePenalties = penalties?.homePenalties,
                awayPenalties = penalties?.awayPenalties,
            )
            fixtureDao.updateEventsJson(fixture.id, serializeEvents(result.events))
            applyMoraleAfterMatch(
                homeTeamId = fixture.homeTeamId,
                awayTeamId = fixture.awayTeamId,
                homeGoals = result.homeGoals,
                awayGoals = result.awayGoals,
            )
            applyInjuriesFromEvents(result)
        }

        val roundFixtures = fixtureDao.byRound(CompetitionDefinitions.CEURO, round)
        if (roundFixtures.isNotEmpty() && roundFixtures.all { it.played }) {
            createNextRound(round, seed)
        }
    }

    suspend fun getCurrentRound(): String {
        for (round in ROUNDS) {
            val fixtures = fixtureDao.byRound(CompetitionDefinitions.CEURO, round)
            if (fixtures.isEmpty()) return round
            if (fixtures.any { !it.played }) return round
        }
        return "DONE"
    }

    suspend fun fixturesForCurrentRound(): List<FixtureEntity> {
        val round = getCurrentRound()
        if (round == "DONE") return fixtureDao.byRound(CompetitionDefinitions.CEURO, "F")
        return fixtureDao.byRound(CompetitionDefinitions.CEURO, round)
    }

    suspend fun teamNamesForFixtures(fixtures: List<FixtureEntity>): Map<Int, String> {
        val ids = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        return ids.mapNotNull { id ->
            teamDao.byId(id)?.let { id to it.nameShort }
        }.toMap()
    }

    suspend fun championName(): String? {
        val final = fixtureDao.byRound(CompetitionDefinitions.CEURO, "F")
        if (final.isEmpty() || final.any { !it.played }) return null
        val winnerId = winnerFromFixture(final.first())
        return teamDao.byId(winnerId)?.nameShort
    }

    private suspend fun createNextRound(round: String, seed: Long) {
        val nextRound = NEXT_ROUND[round] ?: return
        val played = fixtureDao.byRound(CompetitionDefinitions.CEURO, round)
        if (played.isEmpty() || played.any { !it.played }) return

        val random = Random(seed xor round.hashCode().toLong())
        var fixtures = FixtureGenerator.generateKnockout(
            competitionCode = CompetitionDefinitions.CEURO,
            teamIds = played.map(::winnerFromFixture).shuffled(random),
            startRound = nextRound,
            startMatchday = ROUND_MATCHDAY[nextRound] ?: 1,
        )
        if (nextRound == "F") {
            fixtures = fixtures.map { it.copy(neutral = true) }
        }
        fixtureDao.insertAll(fixtures)
    }

    private fun winnerFromFixture(fixture: FixtureEntity): Int {
        if (fixture.decidedByPenalties) {
            val homePenalties = fixture.homePenalties ?: 0
            val awayPenalties = fixture.awayPenalties ?: 0
            return if (homePenalties > awayPenalties) fixture.homeTeamId else fixture.awayTeamId
        }
        return if (fixture.homeGoals >= fixture.awayGoals) fixture.homeTeamId else fixture.awayTeamId
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
            teamId = teamId,
            teamName = teamDao.byId(teamId)?.nameShort ?: "Equipo $teamId",
            squad = starters.map { it.toSimAttrs() },
            isHome = isHome,
        )
    }

    private fun PlayerEntity.toSimAttrs() = PlayerSimAttrs(
        playerId = id,
        playerName = nameShort.ifBlank { nameFull },
        ve = ve,
        re = re,
        ag = ag,
        ca = ca,
        remate = remate,
        regate = regate,
        pase = pase,
        tiro = tiro,
        entrada = entrada,
        portero = portero,
        estadoForma = estadoForma,
        moral = moral,
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
