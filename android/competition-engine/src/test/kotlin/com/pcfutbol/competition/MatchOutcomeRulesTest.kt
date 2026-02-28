package com.pcfutbol.competition

import com.pcfutbol.matchsim.PlayerSimAttrs
import com.pcfutbol.matchsim.TeamMatchInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MatchOutcomeRulesTest {

    @Test
    fun `moral normal reparte mas tres y menos dos`() {
        val deltas = MatchOutcomeRules.moraleDeltas(homeGoals = 2, awayGoals = 1)
        assertEquals(3 to -2, deltas)
    }

    @Test
    fun `moral por goleada aplica mas cinco y menos cinco`() {
        val deltas = MatchOutcomeRules.moraleDeltas(homeGoals = 4, awayGoals = 0)
        assertEquals(5 to -5, deltas)
    }

    @Test
    fun `empate no cambia moral`() {
        val deltas = MatchOutcomeRules.moraleDeltas(homeGoals = 1, awayGoals = 1)
        assertEquals(0 to 0, deltas)
    }

    @Test
    fun `tanda de penaltis es determinista y sin empate final`() {
        val home = makeTeam(id = 1, finishing = 80)
        val away = makeTeam(id = 2, finishing = 75)

        val r1 = MatchOutcomeRules.simulatePenaltyShootout(home, away, seed = 99L)
        val r2 = MatchOutcomeRules.simulatePenaltyShootout(home, away, seed = 99L)

        assertEquals(r1, r2)
        assertNotEquals(r1.homePenalties, r1.awayPenalties)
    }

    private fun makeTeam(id: Int, finishing: Int): TeamMatchInput =
        TeamMatchInput(
            teamId = id,
            teamName = "Team $id",
            isHome = id == 1,
            squad = List(11) { idx ->
                PlayerSimAttrs(
                    playerId = id * 100 + idx,
                    playerName = "P$id-$idx",
                    ve = 70,
                    re = 70,
                    ag = 70,
                    ca = finishing,
                    remate = finishing,
                    regate = 70,
                    pase = 70,
                    tiro = finishing,
                    entrada = 60,
                    portero = 50,
                )
            },
        )
}
