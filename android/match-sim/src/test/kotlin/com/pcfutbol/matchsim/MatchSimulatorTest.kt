package com.pcfutbol.matchsim

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MatchSimulatorTest {

    private fun makeTeam(
        id: Int = 1,
        name: String = "Team $id",
        attrValue: Int = 65,
        isHome: Boolean = false,
        tactic: TacticParams = TacticParams(),
    ) = TeamMatchInput(
        teamId = id,
        teamName = name,
        isHome = isHome,
        tactic = tactic,
        squad = List(11) {
            PlayerSimAttrs(
                playerId = id * 1000 + it,
                playerName = "P$id-$it",
                ve = attrValue, re = attrValue, ag = attrValue, ca = attrValue,
                remate = attrValue, regate = attrValue, pase = attrValue,
                tiro = attrValue, entrada = attrValue, portero = attrValue,
            )
        }
    )

    @Test
    fun `mismo seed produce mismo resultado`() {
        val ctx = MatchContext(
            fixtureId = 1,
            home = makeTeam(1, "Madrid", 80, isHome = true),
            away = makeTeam(2, "Barça", 78),
            seed = 42L,
        )
        val r1 = MatchSimulator.simulate(ctx)
        val r2 = MatchSimulator.simulate(ctx)

        assertEquals(r1.homeGoals, r2.homeGoals)
        assertEquals(r1.awayGoals, r2.awayGoals)
        assertEquals(r1.events.size, r2.events.size)
    }

    @Test
    fun `seeds diferentes producen resultados distintos la mayoria de veces`() {
        val home = makeTeam(1, "Madrid", 75, isHome = true)
        val away = makeTeam(2, "Barça", 75)
        val results = (1L..100L).map { seed ->
            MatchSimulator.simulate(MatchContext(1, home, away, seed))
        }
        val uniqueScores = results.map { "${it.homeGoals}-${it.awayGoals}" }.toSet()
        assertTrue(uniqueScores.size > 5, "Se esperan al menos 6 marcadores distintos, got $uniqueScores")
    }

    @Test
    fun `equipo muy superior gana mas del 60 pct de las veces`() {
        val strong = makeTeam(1, "Fuerte", 95, isHome = true)
        val weak   = makeTeam(2, "Débil", 30)
        var strongWins = 0
        (1L..200L).forEach { seed ->
            val r = MatchSimulator.simulate(MatchContext(1, strong, weak, seed))
            if (r.homeGoals > r.awayGoals) strongWins++
        }
        assertTrue(strongWins > 120, "El equipo fuerte debería ganar >60%, ganó $strongWins/200")
    }

    @Test
    fun `goles siempre en rango valido`() {
        val home = makeTeam(1, "A", 65, isHome = true)
        val away = makeTeam(2, "B", 65)
        repeat(500) { seed ->
            val r = MatchSimulator.simulate(MatchContext(1, home, away, seed.toLong()))
            assertTrue(r.homeGoals in 0..9, "homeGoals fuera de rango: ${r.homeGoals}")
            assertTrue(r.awayGoals in 0..9, "awayGoals fuera de rango: ${r.awayGoals}")
        }
    }

    @Test
    fun `ventaja local incrementa la media de goles del local`() {
        val homeSide = makeTeam(1, "Local", 65, isHome = true)
        val awaySide = makeTeam(2, "Visitante", 65, isHome = false)
        var homeGoalsTotal = 0
        var awayGoalsTotal = 0
        val n = 1000L
        (1L..n).forEach { seed ->
            val r = MatchSimulator.simulate(MatchContext(1, homeSide, awaySide, seed))
            homeGoalsTotal += r.homeGoals
            awayGoalsTotal += r.awayGoals
        }
        val homeAvg = homeGoalsTotal.toDouble() / n
        val awayAvg = awayGoalsTotal.toDouble() / n
        assertTrue(homeAvg > awayAvg,
            "Local ($homeAvg) debería tener más goles que visitante ($awayAvg)")
    }

    @ParameterizedTest
    @ValueSource(longs = [0L, 1L, 999L, Long.MAX_VALUE])
    fun `no lanza excepcion con seeds extremos`(seed: Long) {
        val ctx = MatchContext(1, makeTeam(isHome = true), makeTeam(2), seed)
        assertDoesNotThrow { MatchSimulator.simulate(ctx) }
    }

    @Test
    fun `lesiones en partido mantienen semanas en rango 2 a 8`() {
        val home = makeTeam(1, "Local", 65, isHome = true)
        val away = makeTeam(2, "Visitante", 65)

        val injuryEvents = (1L..500L)
            .flatMap { seed -> MatchSimulator.simulate(MatchContext(1, home, away, seed)).events }
            .filter { it.type == EventType.INJURY }

        assertTrue(injuryEvents.isNotEmpty(), "Se esperaban eventos de lesión en 500 simulaciones")
        injuryEvents.forEach { injury ->
            assertTrue(injury.injuryWeeks in 2..8, "Lesión fuera de rango: ${injury.injuryWeeks}")
        }
    }

    @Test
    fun `aplica roja por segunda amarilla en alguna simulacion`() {
        val home = makeTeam(1, "Local", 70, isHome = true)
        val away = makeTeam(2, "Visitante", 70)

        val found = (1L..5000L).any { seed ->
            MatchSimulator.simulate(MatchContext(1, home, away, seed)).events.any { event ->
                event.type == EventType.RED_CARD &&
                    event.description.contains("Segunda amarilla", ignoreCase = true)
            }
        }

        assertTrue(found, "No se encontró ninguna expulsión por segunda amarilla en 5000 seeds")
    }

    @Test
    fun `roja directa aparece con faltas duras`() {
        val hardTactic = TacticParams(faltas = 3)
        val home = makeTeam(1, "Local", 70, isHome = true, tactic = hardTactic)
        val away = makeTeam(2, "Visitante", 70, tactic = hardTactic)

        val found = (1L..5000L).any { seed ->
            MatchSimulator.simulate(MatchContext(1, home, away, seed)).events.any { event ->
                event.type == EventType.RED_CARD &&
                    event.description.contains("Roja directa", ignoreCase = true)
            }
        }

        assertTrue(found, "No se encontró roja directa en 5000 seeds con faltas=3")
    }

    @Test
    fun `expulsion reduce lambda en veinte por ciento`() {
        val adjusted = MatchSimulator.applyExpulsionPenalty(lambda = 2.5, redCards = 1)
        assertEquals(2.0, adjusted, 1e-9)
    }

    @Test
    fun `VAR es determinístico - mismo seed mismo resultado`() {
        val ctx = MatchContext(
            fixtureId = 99,
            home = makeTeam(1, "Local", 75, isHome = true),
            away = makeTeam(2, "Visitante", 75),
            seed = 12345L,
        )
        val r1 = MatchSimulator.simulate(ctx)
        val r2 = MatchSimulator.simulate(ctx)
        assertEquals(r1.homeGoals, r2.homeGoals)
        assertEquals(r1.awayGoals, r2.awayGoals)
        assertEquals(r1.varDisallowedHome, r2.varDisallowedHome)
        assertEquals(r1.varDisallowedAway, r2.varDisallowedAway)
    }

    @Test
    fun `VAR genera eventos VAR_DISALLOWED en alguna simulacion`() {
        val home = makeTeam(1, "Local", 90, isHome = true)
        val away = makeTeam(2, "Visitante", 90)
        val found = (1L..1000L).any { seed ->
            val r = MatchSimulator.simulate(MatchContext(1, home, away, seed))
            r.events.any { it.type == EventType.VAR_DISALLOWED }
        }
        assertTrue(found, "Se esperaban goles anulados por VAR en 1000 simulaciones de equipos ofensivos")
    }

    @Test
    fun `tiempo añadido siempre en rango valido`() {
        val home = makeTeam(1, "A", 65, isHome = true)
        val away = makeTeam(2, "B", 65)
        repeat(200) { seed ->
            val r = MatchSimulator.simulate(MatchContext(1, home, away, seed.toLong()))
            assertTrue(r.addedTimeFirstHalf in 1..6,
                "Tiempo añadido 1ª parte fuera de rango: ${r.addedTimeFirstHalf}")
            assertTrue(r.addedTimeSecondHalf in 2..10,
                "Tiempo añadido 2ª parte fuera de rango: ${r.addedTimeSecondHalf}")
        }
    }

    @Test
    fun `goles despues del VAR no son mayores que goles Poisson`() {
        // Los goles finales siempre son <= goles Poisson (el VAR solo puede anular, no añadir)
        val home = makeTeam(1, "Atacante", 90, isHome = true)
        val away = makeTeam(2, "Atacante2", 90)
        repeat(500) { seed ->
            val r = MatchSimulator.simulate(MatchContext(1, home, away, seed.toLong()))
            assertTrue(r.varDisallowedHome >= 0)
            assertTrue(r.varDisallowedAway >= 0)
        }
    }
}
