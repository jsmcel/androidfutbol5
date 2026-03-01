package com.pcfutbol.competition

import com.pcfutbol.core.data.db.*
import com.pcfutbol.matchsim.EventType
import com.pcfutbol.matchsim.MatchContext
import com.pcfutbol.matchsim.MatchResult
import com.pcfutbol.matchsim.MatchSimulator
import com.pcfutbol.matchsim.PlayerSimAttrs
import com.pcfutbol.matchsim.TeamMatchInput
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Gestion de la Copa del Rey (CREY).
 *
 * Formato simplificado (una sola vuelta por ronda):
 *   R32  - 32 equipos -> 16 partidos  (ronda inicial)
 *   R16  - 16 equipos ->  8 partidos
 *   QF   -  8 equipos ->  4 partidos  (cuartos)
 *   SF   -  4 equipos ->  2 partidos  (semis)
 *   F    -  2 equipos ->  1 partido   (final, campo neutral)
 */
@Singleton
class CopaRepository @Inject constructor(
    private val fixtureDao: FixtureDao,
    private val teamDao: TeamDao,
    private val standingDao: StandingDao,
    private val newsDao: NewsDao,
    private val seasonStateDao: SeasonStateDao,
    private val playerDao: PlayerDao,
) {
    companion object {
        const val CREY = "CREY"
        val ROUNDS = listOf("R32", "R16", "QF", "SF", "F")
        val ROUND_NAMES = mapOf(
            "R32" to "DIECISEISAVOS",
            "R16" to "OCTAVOS DE FINAL",
            "QF"  to "CUARTOS DE FINAL",
            "SF"  to "SEMIFINALES",
            "F"   to "FINAL",
        )
        val NEXT_ROUND = mapOf(
            "R32" to "R16",
            "R16" to "QF",
            "QF"  to "SF",
            "SF"  to "F",
        )
        // matchday dentro de CREY para cada ronda
        val ROUND_MATCHDAY = mapOf("R32" to 1, "R16" to 2, "QF" to 3, "SF" to 4, "F" to 5)
    }

    /**
     * Inicializa la Copa del Rey con los mejores equipos de LIGA1 y LIGA2.
     * 24 de LIGA1 + 8 de LIGA2 = 32 (ajustado a disponibilidad).
     */
    suspend fun setup() {
        fixtureDao.deleteByCompetition(CREY)

        val liga1 = standingDao.getAllSorted("LIGA1")
        val liga2 = standingDao.getAllSorted("LIGA2")

        // Seleccionar participantes: todos LIGA1 + primeros LIGA2 hasta 32
        val liga1Ids = liga1.map { it.teamId }
        val liga2Ids = liga2.map { it.teamId }.take((32 - liga1Ids.size).coerceAtLeast(0))
        val season = seasonStateDao.get()?.season ?: "2025-26"
        val setupSeed = season.hashCode().toLong() xor CREY.hashCode().toLong()
        val teamIds = (liga1Ids + liga2Ids).take(32).shuffled(Random(setupSeed))

        if (teamIds.size < 2) return

        val fixtures = FixtureGenerator.generateKnockout(CREY, teamIds, "R32", startMatchday = 1)
        fixtureDao.insertAll(fixtures)
    }

    /**
     * Devuelve la ronda activa (la primera con partidos sin jugar o la ultima jugada).
     */
    suspend fun currentRound(): String {
        for (round in ROUNDS) {
            val fixtures = fixtureDao.byRound(CREY, round)
            if (fixtures.isNotEmpty() && fixtures.any { !it.played }) return round
            if (fixtures.isNotEmpty() && fixtures.all { it.played }) {
                val next = NEXT_ROUND[round] ?: return round   // es la final
                val nextFixtures = fixtureDao.byRound(CREY, next)
                if (nextFixtures.isEmpty()) return round        // siguiente ronda no generada aun
            }
        }
        return ROUNDS.first()
    }

    /** Fixtures de una ronda concreta. */
    suspend fun fixturesForRound(round: String): List<FixtureEntity> =
        fixtureDao.byRound(CREY, round)

    /** Nombres de equipo para un conjunto de fixtures. */
    suspend fun teamNamesForFixtures(fixtures: List<FixtureEntity>): Map<Int, String> {
        val ids = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        return ids.mapNotNull { id -> teamDao.byId(id)?.let { id to it.nameShort } }.toMap()
    }

    /**
     * Simula todos los partidos pendientes de la ronda.
     * En caso de empate se decide por penaltis (5 + muerte subita).
     */
    suspend fun simulateRound(round: String, masterSeed: Long) {
        decrementPlayerAvailabilityCounters()
        val pending = fixtureDao.byRound(CREY, round).filter { !it.played }
        pending.forEach { fixture ->
            val home = buildTeamInput(fixture.homeTeamId, isHome = !fixture.neutral)
            val away = buildTeamInput(fixture.awayTeamId, isHome = fixture.neutral)
            val seed = masterSeed xor fixture.id.toLong()
            val ctx = MatchContext(fixture.id, home, away, seed, fixture.neutral)
            val result = MatchSimulator.simulate(ctx)
            val penalties = if (result.homeGoals == result.awayGoals) {
                MatchOutcomeRules.simulatePenaltyShootout(home, away, seed)
            } else {
                null
            }

            fixtureDao.recordResult(
                id = fixture.id,
                hg = result.homeGoals,
                ag = result.awayGoals,
                seed = seed,
                decidedByPenalties = penalties != null,
                homePenalties = penalties?.homePenalties,
                awayPenalties = penalties?.awayPenalties,
            )
            applyMoraleAfterMatch(
                homeTeamId = fixture.homeTeamId,
                awayTeamId = fixture.awayTeamId,
                homeGoals = result.homeGoals,
                awayGoals = result.awayGoals,
            )
            applyInjuriesFromEvents(result)
        }
    }

    /**
     * Genera la siguiente ronda con los ganadores de la ronda actual.
     * @return (nextRound, winnerName) - winnerName != null si la Copa termino.
     */
    suspend fun advanceToNextRound(completedRound: String): Pair<String, String?> {
        val played = fixtureDao.byRound(CREY, completedRound)
        if (played.any { !it.played }) error("Hay partidos sin simular en $completedRound")

        // Ganadores: goles en tiempo reglamentario o tanda de penaltis.
        val state = seasonStateDao.get()
        val advanceSeed = (state?.season?.hashCode()?.toLong() ?: 0L) xor
            (completedRound.hashCode().toLong() * 31L)
        val winners = played.map(::winnerFromFixture)
            .shuffled(Random(advanceSeed))   // mezclar para evitar emparejamientos repetitivos

        if (completedRound == "F") {
            // Copa finalizada
            val winnerId = winners.firstOrNull() ?: return "F" to null
            val winnerName = teamDao.byId(winnerId)?.nameShort ?: "Desconocido"
            newsDao.insert(NewsEntity(
                date = java.time.LocalDate.now().toString(),
                matchday = state?.currentMatchday ?: 0,
                category = "BOARD",
                titleEs = "CAMPEON DE LA COPA",
                bodyEs = "$winnerName gana la Copa del Rey.",
            ))
            return "F" to winnerName
        }

        val nextRound = NEXT_ROUND[completedRound] ?: return completedRound to null
        val nextMatchday = ROUND_MATCHDAY[nextRound] ?: (ROUND_MATCHDAY[completedRound]!! + 1)
        val fixtures = FixtureGenerator.generateKnockout(CREY, winners, nextRound, nextMatchday)
        fixtureDao.insertAll(fixtures)

        return nextRound to null
    }

    // -----------------------------------------------------------------------

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
        val team = teamDao.byId(teamId)
        val players = playerDao.byTeamNow(teamId)
        val available = players.filter {
            it.status == 0 && it.injuryWeeksLeft <= 0 && it.sanctionMatchesLeft <= 0
        }
        val availableIds = available.map { it.id }.toSet()
        val starters = (available + players.filter { it.id !in availableIds })
            .take(11)

        return TeamMatchInput(
            teamId = teamId,
            teamName = team?.nameShort ?: "Equipo $teamId",
            squad = starters.map { it.toSimAttrs() },
            isHome = isHome,
            competitionCode = team?.competitionKey ?: "",
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
}
