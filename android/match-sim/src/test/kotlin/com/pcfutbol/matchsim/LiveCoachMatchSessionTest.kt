package com.pcfutbol.matchsim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveCoachMatchSessionTest {

    private fun makeTeam(
        id: Int,
        name: String,
        attr: Int,
        isHome: Boolean,
    ) = TeamMatchInput(
        teamId = id,
        teamName = name,
        isHome = isHome,
        tactic = TacticParams(),
        squad = List(11) { idx ->
            PlayerSimAttrs(
                playerId = id * 1000 + idx,
                playerName = "P$id-$idx",
                ve = attr,
                re = attr,
                ag = attr,
                ca = attr,
                remate = attr,
                regate = attr,
                pase = attr,
                tiro = attr,
                entrada = attr,
                portero = attr,
            )
        }
    )

    private fun playFull(
        seed: Long,
        commandByMinute: (Int) -> LiveCoachCommand,
    ): MatchResult {
        val ctx = MatchContext(
            fixtureId = 1,
            home = makeTeam(id = 10, name = "Local", attr = 75, isHome = true),
            away = makeTeam(id = 20, name = "Visitante", attr = 74, isHome = false),
            seed = seed,
        )
        val session = LiveCoachMatchSession.create(ctx, managerTeamId = ctx.home.teamId)
        var queryMinute = 1
        while (true) {
            val step = session.step(commandByMinute(queryMinute))
            queryMinute = step.minute + 1
            if (step.finished) break
        }
        return session.toMatchResult()
    }

    @Test
    fun `live session is deterministic with same seed and commands`() {
        val commandPlan: (Int) -> LiveCoachCommand = { minute ->
            when {
                minute < 30 -> LiveCoachCommand.BALANCED
                minute < 60 -> LiveCoachCommand.HIGH_PRESS
                else -> LiveCoachCommand.WASTE_TIME
            }
        }
        val r1 = playFull(seed = 20260302L, commandByMinute = commandPlan)
        val r2 = playFull(seed = 20260302L, commandByMinute = commandPlan)

        assertEquals(r1.homeGoals, r2.homeGoals)
        assertEquals(r1.awayGoals, r2.awayGoals)
        assertEquals(r1.events.size, r2.events.size)
        assertEquals(r1.addedTimeFirstHalf, r2.addedTimeFirstHalf)
        assertEquals(r1.addedTimeSecondHalf, r2.addedTimeSecondHalf)
    }

    @Test
    fun `live session reaches full time and added time in valid range`() {
        val result = playFull(seed = 42L) { LiveCoachCommand.BALANCED }
        assertTrue(result.addedTimeFirstHalf in 1..6)
        assertTrue(result.addedTimeSecondHalf in 2..10)
        assertTrue(result.events.isNotEmpty())
    }

    @Test
    fun `all in command increases goals on average against low block`() {
        val attackGoals = (1L..120L).sumOf { seed ->
            playFull(seed) { LiveCoachCommand.ATTACK_ALL_IN }.homeGoals
        } / 120.0
        val lowBlockGoals = (1L..120L).sumOf { seed ->
            playFull(seed) { LiveCoachCommand.LOW_BLOCK }.homeGoals
        } / 120.0

        assertTrue(
            attackGoals > lowBlockGoals,
            "ATTACK_ALL_IN debe elevar goles esperados (allIn=$attackGoals lowBlock=$lowBlockGoals)",
        )
    }
}
