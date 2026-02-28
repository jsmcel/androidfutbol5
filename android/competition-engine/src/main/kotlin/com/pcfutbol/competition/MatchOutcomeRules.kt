package com.pcfutbol.competition

import com.pcfutbol.matchsim.TeamMatchInput
import kotlin.random.Random

/**
 * Reglas de resultado modernas compartidas por liga y copa.
 */
data class PenaltyShootoutResult(
    val homePenalties: Int,
    val awayPenalties: Int,
) {
    fun winnerTeamId(homeTeamId: Int, awayTeamId: Int): Int =
        if (homePenalties > awayPenalties) homeTeamId else awayTeamId
}

object MatchOutcomeRules {

    /**
     * Variación de moral post-partido.
     * - Victoria normal: +3 / -2
     * - Goleada (diferencia >=3): +5 / -5
     * - Empate: 0 / 0
     */
    fun moraleDeltas(homeGoals: Int, awayGoals: Int): Pair<Int, Int> {
        if (homeGoals == awayGoals) return 0 to 0
        val diff = kotlin.math.abs(homeGoals - awayGoals)
        return when {
            diff >= 3 && homeGoals > awayGoals -> 5 to -5
            diff >= 3 && awayGoals > homeGoals -> -5 to 5
            homeGoals > awayGoals -> 3 to -2
            else -> -2 to 3
        }
    }

    /**
     * Tanda de penaltis determinística: 5 lanzamientos por equipo y muerte súbita.
     */
    fun simulatePenaltyShootout(
        home: TeamMatchInput,
        away: TeamMatchInput,
        seed: Long,
    ): PenaltyShootoutResult {
        val rng = Random(seed xor 0x7A4B3C2D1E0F1234L)
        var homeScore = 0
        var awayScore = 0

        repeat(5) {
            if (takesPenalty(home, rng)) homeScore += 1
            if (takesPenalty(away, rng)) awayScore += 1
        }

        var suddenDeathRounds = 0
        while (homeScore == awayScore && suddenDeathRounds < 20) {
            if (takesPenalty(home, rng)) homeScore += 1
            if (takesPenalty(away, rng)) awayScore += 1
            suddenDeathRounds += 1
        }

        if (homeScore == awayScore) {
            if (rng.nextBoolean()) homeScore += 1 else awayScore += 1
        }

        return PenaltyShootoutResult(homePenalties = homeScore, awayPenalties = awayScore)
    }

    private fun takesPenalty(team: TeamMatchInput, rng: Random): Boolean {
        val finishing = team.squad
            .map { (it.remate + it.tiro + it.ca) / 3.0 }
            .average()
            .takeIf { !it.isNaN() } ?: 50.0
        val chance = (0.72 + (finishing - 50.0) / 250.0).coerceIn(0.55, 0.90)
        return rng.nextDouble() < chance
    }
}
