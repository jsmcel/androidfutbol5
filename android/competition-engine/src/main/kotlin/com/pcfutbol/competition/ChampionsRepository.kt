package com.pcfutbol.competition

import com.pcfutbol.core.data.db.FixtureDao
import com.pcfutbol.core.data.db.FixtureEntity
import com.pcfutbol.core.data.db.NewsDao
import com.pcfutbol.core.data.db.NewsEntity
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.StandingDao
import com.pcfutbol.core.data.db.TeamDao
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
 * Competiciones UEFA modernas:
 * - Champions (CEURO), Europa League (RECOPA), Conference (CUEFA)
 * - Fase liga (8 jornadas) + playoff 9-24 + KO (octavos, cuartos, semis, final)
 */
@Singleton
class ChampionsRepository @Inject constructor(
    private val fixtureDao: FixtureDao,
    private val teamDao: TeamDao,
    private val standingDao: StandingDao,
    private val seasonStateDao: SeasonStateDao,
    private val playerDao: PlayerDao,
    private val newsDao: NewsDao,
) {
    companion object {
        private val EURO_COMPETITIONS = listOf(
            CompetitionDefinitions.CEURO,
            CompetitionDefinitions.RECOPA,
            CompetitionDefinitions.CUEFA,
        )
        private val LEAGUE_PHASE_ROUNDS = (1..8).map { "LP$it" }
        private val ROUND_ORDER = LEAGUE_PHASE_ROUNDS + listOf(
            "POF1", "POF2",
            "R16_1", "R16_2",
            "QF1", "QF2",
            "SF1", "SF2",
            "F",
        )
        private val ROUND_MATCHDAY = ROUND_ORDER.withIndex()
            .associate { (index, round) -> round to index + 1 }
        private val LEAGUE_SIZE = 36
        private val PLAYOFF_START = 9
        private val PLAYOFF_END = 24

        private val EUROPEAN_LEAGUE_PRIORITY = listOf(
            CompetitionDefinitions.LIGA1,
            "PRML", "SERIA", "BUN1", "LIG1",
            "ERED", "PRIM", "BELGA", "SUPERL", "SCOT",
            "DSL", "EKSTR", "ABUND", "RPL",
            CompetitionDefinitions.LIGA2,
            "ARGPD", "BRASEA", "LIGAMX", "SPL",
        )
    }

    suspend fun setup(competitionCode: String = CompetitionDefinitions.CEURO) {
        require(competitionCode in EURO_COMPETITIONS) {
            "Competicion europea no soportada: $competitionCode"
        }
        fixtureDao.deleteByCompetition(competitionCode)

        val season = seasonStateDao.get()?.season ?: "2025-26"
        val seed = season.hashCode().toLong() xor competitionCode.hashCode().toLong()
        val participants = participantsForCompetition(competitionCode)
        if (participants.size < 2) return

        val fixtures = generateLeaguePhaseFixtures(
            competitionCode = competitionCode,
            participants = participants.take(LEAGUE_SIZE),
            seed = seed,
        )
        fixtureDao.insertAll(fixtures)
    }

    suspend fun setupAll() {
        for (code in EURO_COMPETITIONS) setup(code)
    }

    suspend fun simulateRound(seed: Long, competitionCode: String = CompetitionDefinitions.CEURO) {
        require(competitionCode in EURO_COMPETITIONS) {
            "Competicion europea no soportada: $competitionCode"
        }
        val round = getCurrentRound(competitionCode)
        if (round == "DONE") return

        decrementPlayerAvailabilityCounters()
        val pending = fixtureDao.byRound(competitionCode, round).filter { !it.played }
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

            val penalties = if (round == "F" && result.homeGoals == result.awayGoals) {
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

        val roundFixtures = fixtureDao.byRound(competitionCode, round)
        if (roundFixtures.isNotEmpty() && roundFixtures.all { it.played }) {
            createNextRoundIfNeeded(competitionCode, round, seed)
        }
    }

    suspend fun getCurrentRound(competitionCode: String = CompetitionDefinitions.CEURO): String {
        require(competitionCode in EURO_COMPETITIONS) {
            "Competicion europea no soportada: $competitionCode"
        }
        for (round in ROUND_ORDER) {
            val fixtures = fixtureDao.byRound(competitionCode, round)
            if (fixtures.isEmpty()) return round
            if (fixtures.any { !it.played }) return round
        }
        return "DONE"
    }

    suspend fun fixturesForCurrentRound(
        competitionCode: String = CompetitionDefinitions.CEURO,
    ): List<FixtureEntity> {
        val round = getCurrentRound(competitionCode)
        return if (round == "DONE") fixtureDao.byRound(competitionCode, "F")
        else fixtureDao.byRound(competitionCode, round)
    }

    suspend fun teamNamesForFixtures(fixtures: List<FixtureEntity>): Map<Int, String> {
        val ids = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        return ids.mapNotNull { id -> teamDao.byId(id)?.let { id to it.nameShort } }.toMap()
    }

    suspend fun championName(
        competitionCode: String = CompetitionDefinitions.CEURO,
    ): String? {
        val finals = fixtureDao.byRound(competitionCode, "F")
        if (finals.isEmpty() || finals.any { !it.played }) return null
        val winnerId = winnerFromFixture(finals.first())
        return teamDao.byId(winnerId)?.nameShort
    }

    // -------------------------------------------------------------------------
    // Progresion de rondas

    private suspend fun createNextRoundIfNeeded(
        competitionCode: String,
        completedRound: String,
        seed: Long,
    ) {
        when (completedRound) {
            "LP8" -> createPlayoffRound(competitionCode)
            "POF2" -> createRoundOf16(competitionCode, seed)
            "R16_2" -> createTwoLegRound(competitionCode, "R16_1", "R16_2", "QF1", "QF2", seed)
            "QF2" -> createTwoLegRound(competitionCode, "QF1", "QF2", "SF1", "SF2", seed)
            "SF2" -> createFinal(competitionCode, seed)
            "F" -> publishChampionNews(competitionCode)
        }
    }

    private suspend fun createPlayoffRound(competitionCode: String) {
        if (fixtureDao.byRound(competitionCode, "POF1").isNotEmpty()) return

        val ranking = leaguePhaseRanking(competitionCode)
        val playoff = ranking.drop(PLAYOFF_START - 1).take(PLAYOFF_END - PLAYOFF_START + 1)
        if (playoff.size < 16) return

        val fixtures = mutableListOf<FixtureEntity>()
        for (i in 0 until 8) {
            val betterSeeded = playoff[i]             // 9..16
            val lowerSeeded = playoff[playoff.lastIndex - i] // 24..17
            fixtures += fixture(
                competitionCode = competitionCode,
                round = "POF1",
                home = lowerSeeded,
                away = betterSeeded,
            )
            fixtures += fixture(
                competitionCode = competitionCode,
                round = "POF2",
                home = betterSeeded,
                away = lowerSeeded,
            )
        }
        fixtureDao.insertAll(fixtures)
    }

    private suspend fun createRoundOf16(competitionCode: String, seed: Long) {
        if (fixtureDao.byRound(competitionCode, "R16_1").isNotEmpty()) return

        val ranking = leaguePhaseRanking(competitionCode)
        val top8 = ranking.take(8)
        if (top8.size < 8) return

        val playoffWinners = resolveTwoLegWinners(competitionCode, "POF1", "POF2", seed xor 0x120F12L)
            .shuffled(Random(seed xor competitionCode.hashCode().toLong() xor 0x55AAL))
            .take(8)
        if (playoffWinners.size < 8) return

        val fixtures = mutableListOf<FixtureEntity>()
        top8.zip(playoffWinners).forEach { (seeded, challenger) ->
            fixtures += fixture(
                competitionCode = competitionCode,
                round = "R16_1",
                home = challenger,
                away = seeded,
            )
            fixtures += fixture(
                competitionCode = competitionCode,
                round = "R16_2",
                home = seeded,
                away = challenger,
            )
        }
        fixtureDao.insertAll(fixtures)
    }

    private suspend fun createTwoLegRound(
        competitionCode: String,
        fromRound1: String,
        fromRound2: String,
        toRound1: String,
        toRound2: String,
        seed: Long,
    ) {
        if (fixtureDao.byRound(competitionCode, toRound1).isNotEmpty()) return

        val winners = resolveTwoLegWinners(
            competitionCode = competitionCode,
            firstLegRound = fromRound1,
            secondLegRound = fromRound2,
            seed = seed xor toRound1.hashCode().toLong(),
        ).shuffled(Random(seed xor toRound2.hashCode().toLong()))

        if (winners.size < 2) return
        val fixtures = mutableListOf<FixtureEntity>()
        winners.chunked(2).forEach { pair ->
            if (pair.size < 2) return@forEach
            val a = pair[0]
            val b = pair[1]
            val firstHome = if (Random(seed xor a.toLong() xor b.toLong()).nextBoolean()) a else b
            val firstAway = if (firstHome == a) b else a
            fixtures += fixture(
                competitionCode = competitionCode,
                round = toRound1,
                home = firstHome,
                away = firstAway,
            )
            fixtures += fixture(
                competitionCode = competitionCode,
                round = toRound2,
                home = firstAway,
                away = firstHome,
            )
        }
        fixtureDao.insertAll(fixtures)
    }

    private suspend fun createFinal(competitionCode: String, seed: Long) {
        if (fixtureDao.byRound(competitionCode, "F").isNotEmpty()) return

        val finalists = resolveTwoLegWinners(
            competitionCode = competitionCode,
            firstLegRound = "SF1",
            secondLegRound = "SF2",
            seed = seed xor 0x7F7F7FL,
        )
        if (finalists.size < 2) return

        val home = finalists[0]
        val away = finalists[1]
        fixtureDao.insertAll(
            listOf(
                fixture(
                    competitionCode = competitionCode,
                    round = "F",
                    home = home,
                    away = away,
                    neutral = true,
                )
            )
        )
    }

    private suspend fun resolveTwoLegWinners(
        competitionCode: String,
        firstLegRound: String,
        secondLegRound: String,
        seed: Long,
    ): List<Int> {
        val firstLeg = fixtureDao.byRound(competitionCode, firstLegRound).sortedBy { it.id }
        val secondLeg = fixtureDao.byRound(competitionCode, secondLegRound)
            .associateBy { tieKey(it.homeTeamId, it.awayTeamId) }
        val winners = mutableListOf<Int>()

        firstLeg.forEachIndexed { index, first ->
            val second = secondLeg[tieKey(first.homeTeamId, first.awayTeamId)] ?: return@forEachIndexed
            val teamA = first.homeTeamId
            val teamB = first.awayTeamId

            val teamAGoals = goalsForTeam(first, teamA) + goalsForTeam(second, teamA)
            val teamBGoals = goalsForTeam(first, teamB) + goalsForTeam(second, teamB)

            val winner = when {
                teamAGoals > teamBGoals -> teamA
                teamBGoals > teamAGoals -> teamB
                else -> {
                    if (!second.decidedByPenalties) {
                        val home = buildTeamInput(second.homeTeamId, isHome = !second.neutral)
                        val away = buildTeamInput(second.awayTeamId, isHome = second.neutral)
                        val shootout = MatchOutcomeRules.simulatePenaltyShootout(
                            home = home,
                            away = away,
                            seed = seed xor second.id.toLong() xor index.toLong(),
                        )
                        fixtureDao.recordResult(
                            id = second.id,
                            hg = second.homeGoals,
                            ag = second.awayGoals,
                            seed = second.seed,
                            decidedByPenalties = true,
                            homePenalties = shootout.homePenalties,
                            awayPenalties = shootout.awayPenalties,
                        )
                    }
                    val refreshedSecond = fixtureDao.byId(second.id) ?: second
                    winnerFromFixture(refreshedSecond)
                }
            }
            winners += winner
        }

        return winners
    }

    // -------------------------------------------------------------------------
    // Construccion de participantes y calendario fase liga

    private suspend fun participantsForCompetition(competitionCode: String): List<Int> {
        val rankedPool = rankedEuropeanPool()
        val competitionIndex = EURO_COMPETITIONS.indexOf(competitionCode).coerceAtLeast(0)
        val start = competitionIndex * LEAGUE_SIZE
        val selected = rankedPool.drop(start).take(LEAGUE_SIZE).toMutableList()
        if (selected.size >= LEAGUE_SIZE) return selected

        rankedPool.forEach { candidate ->
            if (selected.size >= LEAGUE_SIZE) return@forEach
            if (candidate !in selected) selected += candidate
        }
        return selected
    }

    private suspend fun rankedEuropeanPool(): List<Int> {
        val allTeams = teamDao.allTeams().first()
        val teamsByLeague = allTeams.groupBy { it.competitionKey }
        val rankingByLeague = EUROPEAN_LEAGUE_PRIORITY.associateWith { code ->
            val fromStandings = standingDao.getAllSorted(code).map { it.teamId }
            if (fromStandings.isNotEmpty()) {
                fromStandings
            } else {
                teamsByLeague[code]
                    .orEmpty()
                    .sortedByDescending { it.prestige }
                    .map { it.slotId }
            }
        }
        val maxDepth = rankingByLeague.values.maxOfOrNull { it.size } ?: 0

        val pool = mutableListOf<Int>()
        for (position in 0 until maxDepth) {
            EUROPEAN_LEAGUE_PRIORITY.forEach { league ->
                val teamId = rankingByLeague[league]?.getOrNull(position) ?: return@forEach
                if (teamId !in pool) pool += teamId
            }
        }

        allTeams.sortedByDescending { it.prestige }.forEach { team ->
            if (team.slotId !in pool) pool += team.slotId
        }
        return pool
    }

    private fun generateLeaguePhaseFixtures(
        competitionCode: String,
        participants: List<Int>,
        seed: Long,
    ): List<FixtureEntity> {
        val rng = Random(seed xor competitionCode.hashCode().toLong())
        val opponents = participants.associateWith { mutableSetOf<Int>() }.toMutableMap()
        val homeCounts = participants.associateWith { 0 }.toMutableMap()
        val awayCounts = participants.associateWith { 0 }.toMutableMap()
        val fixtures = mutableListOf<FixtureEntity>()

        LEAGUE_PHASE_ROUNDS.forEach { round ->
            val unpaired = participants.shuffled(rng).toMutableList()
            while (unpaired.size >= 2) {
                val a = unpaired.removeAt(0)
                val candidates = unpaired.filter { it !in (opponents[a] ?: emptySet()) }
                val b = when {
                    candidates.isNotEmpty() -> candidates.minByOrNull { candidate ->
                        pairingCost(a, candidate, homeCounts, awayCounts, opponents)
                    } ?: unpaired.first()
                    else -> unpaired.minByOrNull { candidate ->
                        pairingCost(a, candidate, homeCounts, awayCounts, opponents, allowRepeat = true)
                    } ?: unpaired.first()
                }
                unpaired.remove(b)

                val aNeedsHome = (homeCounts[a] ?: 0) <= (awayCounts[a] ?: 0)
                val bNeedsHome = (homeCounts[b] ?: 0) <= (awayCounts[b] ?: 0)
                val home = when {
                    aNeedsHome && !bNeedsHome -> a
                    bNeedsHome && !aNeedsHome -> b
                    else -> if (rng.nextBoolean()) a else b
                }
                val away = if (home == a) b else a

                opponents.getValue(a).add(b)
                opponents.getValue(b).add(a)
                homeCounts[home] = (homeCounts[home] ?: 0) + 1
                awayCounts[away] = (awayCounts[away] ?: 0) + 1

                fixtures += FixtureEntity(
                    competitionCode = competitionCode,
                    matchday = ROUND_MATCHDAY.getValue(round),
                    round = round,
                    homeTeamId = home,
                    awayTeamId = away,
                )
            }
        }

        return fixtures
    }

    private fun pairingCost(
        a: Int,
        b: Int,
        homeCounts: Map<Int, Int>,
        awayCounts: Map<Int, Int>,
        opponents: Map<Int, Set<Int>>,
        allowRepeat: Boolean = false,
    ): Int {
        val repeatedPenalty = if (!allowRepeat && b in (opponents[a] ?: emptySet())) 10_000 else 0
        val balanceA = kotlin.math.abs((homeCounts[a] ?: 0) - (awayCounts[a] ?: 0))
        val balanceB = kotlin.math.abs((homeCounts[b] ?: 0) - (awayCounts[b] ?: 0))
        return repeatedPenalty + balanceA + balanceB
    }

    private suspend fun leaguePhaseRanking(competitionCode: String): List<Int> {
        data class Stats(
            var points: Int = 0,
            var gf: Int = 0,
            var ga: Int = 0,
        ) {
            val gd: Int get() = gf - ga
        }

        val fixtures = LEAGUE_PHASE_ROUNDS.flatMap { fixtureDao.byRound(competitionCode, it) }
        if (fixtures.isEmpty()) return emptyList()

        val stats = linkedMapOf<Int, Stats>()
        fixtures.forEach { fixture ->
            stats.putIfAbsent(fixture.homeTeamId, Stats())
            stats.putIfAbsent(fixture.awayTeamId, Stats())
            if (!fixture.played) return@forEach

            val home = stats.getValue(fixture.homeTeamId)
            val away = stats.getValue(fixture.awayTeamId)
            home.gf += fixture.homeGoals
            home.ga += fixture.awayGoals
            away.gf += fixture.awayGoals
            away.ga += fixture.homeGoals
            when {
                fixture.homeGoals > fixture.awayGoals -> home.points += 3
                fixture.awayGoals > fixture.homeGoals -> away.points += 3
                else -> {
                    home.points += 1
                    away.points += 1
                }
            }
        }

        return stats.entries
            .sortedWith(
                compareByDescending<Map.Entry<Int, Stats>> { it.value.points }
                    .thenByDescending { it.value.gd }
                    .thenByDescending { it.value.gf }
                    .thenBy { it.key }
            )
            .map { it.key }
    }

    // -------------------------------------------------------------------------
    // Helpers de partido y persistencia

    private suspend fun publishChampionNews(competitionCode: String) {
        val winner = championName(competitionCode) ?: return
        val state = seasonStateDao.get()
        val title = when (competitionCode) {
            CompetitionDefinitions.CEURO -> "CAMPEON DE LA CHAMPIONS"
            CompetitionDefinitions.RECOPA -> "CAMPEON DE LA EUROPA LEAGUE"
            else -> "CAMPEON DE LA CONFERENCE LEAGUE"
        }
        newsDao.insert(
            NewsEntity(
                date = java.time.LocalDate.now().toString(),
                matchday = state?.currentMatchday ?: 0,
                category = "BOARD",
                titleEs = title,
                bodyEs = "$winner gana ${CompetitionDefinitions.displayName(competitionCode)}.",
            )
        )
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

    private fun winnerFromFixture(fixture: FixtureEntity): Int {
        if (fixture.decidedByPenalties) {
            val homePenalties = fixture.homePenalties ?: 0
            val awayPenalties = fixture.awayPenalties ?: 0
            return if (homePenalties > awayPenalties) fixture.homeTeamId else fixture.awayTeamId
        }
        return if (fixture.homeGoals >= fixture.awayGoals) fixture.homeTeamId else fixture.awayTeamId
    }

    private fun goalsForTeam(fixture: FixtureEntity, teamId: Int): Int = when (teamId) {
        fixture.homeTeamId -> fixture.homeGoals
        fixture.awayTeamId -> fixture.awayGoals
        else -> 0
    }

    private fun tieKey(teamA: Int, teamB: Int): Pair<Int, Int> =
        if (teamA <= teamB) teamA to teamB else teamB to teamA

    private fun fixture(
        competitionCode: String,
        round: String,
        home: Int,
        away: Int,
        neutral: Boolean = false,
    ): FixtureEntity = FixtureEntity(
        competitionCode = competitionCode,
        matchday = ROUND_MATCHDAY.getValue(round),
        round = round,
        homeTeamId = home,
        awayTeamId = away,
        neutral = neutral,
    )
}
