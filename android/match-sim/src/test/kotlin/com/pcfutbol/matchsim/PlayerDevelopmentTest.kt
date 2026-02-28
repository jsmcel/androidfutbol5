package com.pcfutbol.matchsim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerDevelopmentTest {

    @Test
    fun `young player improves at least one attribute`() {
        val player = basePlayer(id = 1, birthYear = 2004)

        val evolved = PlayerDevelopmentEngine.applySeasonGrowth(
            players = listOf(player),
            seasonStartYear = 2025,
            seed = 1234L,
        ).first()

        val improved = evolved.ve > player.ve ||
            evolved.re > player.re ||
            evolved.ag > player.ag ||
            evolved.ca > player.ca ||
            evolved.remate > player.remate ||
            evolved.regate > player.regate ||
            evolved.pase > player.pase ||
            evolved.tiro > player.tiro ||
            evolved.entrada > player.entrada ||
            evolved.portero > player.portero

        assertTrue(improved, "Un jugador joven debe mejorar al menos un atributo")
    }

    @Test
    fun `veteran player worsens in speed or stamina`() {
        val player = basePlayer(id = 2, birthYear = 1990, ve = 70, re = 68)

        val evolved = PlayerDevelopmentEngine.applySeasonGrowth(
            players = listOf(player),
            seasonStartYear = 2025,
            seed = 1234L,
        ).first()

        assertTrue(
            evolved.ve < player.ve || evolved.re < player.re,
            "Un veterano debe empeorar en VE o RE",
        )
    }

    @Test
    fun `player aged 37 retires`() {
        val player = basePlayer(id = 3, birthYear = 1988)

        val evolved = PlayerDevelopmentEngine.applySeasonGrowth(
            players = listOf(player),
            seasonStartYear = 2025,
            seed = 55L,
        ).first()

        assertEquals(PlayerDevelopmentEngine.RETIRED_STATUS, evolved.status)
    }

    @Test
    fun `same seed produces same results`() {
        val players = listOf(
            basePlayer(id = 10, birthYear = 2004, ve = 45, re = 44),
            basePlayer(id = 11, birthYear = 1997, ve = 70, re = 72),
            basePlayer(id = 12, birthYear = 1990, ve = 40, re = 43),
        )

        val first = PlayerDevelopmentEngine.applySeasonGrowth(
            players = players,
            seasonStartYear = 2025,
            seed = 98765L,
        )
        val second = PlayerDevelopmentEngine.applySeasonGrowth(
            players = players,
            seasonStartYear = 2025,
            seed = 98765L,
        )

        assertEquals(first, second)
    }

    @Test
    fun `high physio softens veteran decline`() {
        val player = basePlayer(id = 40, birthYear = 1990, ve = 60, re = 60)
        val lowPhysio = PlayerDevelopmentEngine.DevelopmentContext(
            staff = PlayerDevelopmentEngine.StaffProfile(fisio = 20),
            training = PlayerDevelopmentEngine.TrainingPlan(
                intensity = PlayerDevelopmentEngine.TrainingIntensity.HIGH,
            ),
        )
        val highPhysio = PlayerDevelopmentEngine.DevelopmentContext(
            staff = PlayerDevelopmentEngine.StaffProfile(fisio = 95),
            training = PlayerDevelopmentEngine.TrainingPlan(
                intensity = PlayerDevelopmentEngine.TrainingIntensity.HIGH,
            ),
        )

        val lowEvolved = PlayerDevelopmentEngine.applySeasonGrowth(
            players = listOf(player),
            seasonStartYear = 2025,
            seed = 1010L,
            context = lowPhysio,
        ).first()
        val highEvolved = PlayerDevelopmentEngine.applySeasonGrowth(
            players = listOf(player),
            seasonStartYear = 2025,
            seed = 1010L,
            context = highPhysio,
        ).first()

        assertTrue(highEvolved.ve >= lowEvolved.ve)
        assertTrue(highEvolved.re >= lowEvolved.re)
    }

    @Test
    fun `youth intake quality improves with scouting and juveniles staff`() {
        val lowContext = PlayerDevelopmentEngine.DevelopmentContext(
            staff = PlayerDevelopmentEngine.StaffProfile(
                segundoEntrenador = 20,
                ojeador = 20,
                juveniles = 20,
            ),
        )
        val highContext = PlayerDevelopmentEngine.DevelopmentContext(
            staff = PlayerDevelopmentEngine.StaffProfile(
                segundoEntrenador = 90,
                ojeador = 90,
                juveniles = 90,
            ),
        )

        val lowYouth = PlayerDevelopmentEngine.generateYouthPlayers(
            teamSlotId = 12,
            count = 20,
            seasonStartYear = 2025,
            seed = 2026L,
            context = lowContext,
        )
        val highYouth = PlayerDevelopmentEngine.generateYouthPlayers(
            teamSlotId = 12,
            count = 20,
            seasonStartYear = 2025,
            seed = 2026L,
            context = highContext,
        )

        val lowQuality = lowYouth.map { it.ca + it.pase + it.regate }.average()
        val highQuality = highYouth.map { it.ca + it.pase + it.regate }.average()

        assertTrue(highQuality > lowQuality)
    }

    private fun basePlayer(
        id: Int,
        birthYear: Int,
        ve: Int = 50,
        re: Int = 50,
        ag: Int = 50,
        ca: Int = 50,
        remate: Int = 50,
        regate: Int = 50,
        pase: Int = 50,
        tiro: Int = 50,
        entrada: Int = 50,
        portero: Int = 0,
    ) = PlayerDevelopmentEngine.DevelopmentPlayer(
        id = id,
        birthYear = birthYear,
        status = PlayerDevelopmentEngine.ACTIVE_STATUS,
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
    )
}
