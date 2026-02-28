package com.pcfutbol.matchsim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StrengthCalculatorTest {

    @Test
    fun `moral aplica bonus con formula oficial`() {
        val neutralMoral = buildTeam(moral = 50)
        val highMoral = buildTeam(moral = 80)

        val neutralStrength = StrengthCalculator.calculate(neutralMoral)
        val highStrength = StrengthCalculator.calculate(highMoral)

        assertEquals(0.6, highStrength - neutralStrength, 1e-9)
    }

    private fun buildTeam(moral: Int): TeamMatchInput =
        TeamMatchInput(
            teamId = 1,
            teamName = "Equipo",
            isHome = false,
            squad = List(11) { idx ->
                PlayerSimAttrs(
                    playerId = idx + 1,
                    playerName = "Jugador ${idx + 1}",
                    ve = 70,
                    re = 70,
                    ag = 70,
                    ca = 70,
                    remate = 70,
                    regate = 70,
                    pase = 70,
                    tiro = 70,
                    entrada = 70,
                    portero = 70,
                    estadoForma = 50,
                    moral = moral,
                )
            },
        )
}
