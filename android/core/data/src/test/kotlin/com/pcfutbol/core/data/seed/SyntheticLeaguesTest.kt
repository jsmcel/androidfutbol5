package com.pcfutbol.core.data.seed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyntheticLeaguesTest {

    @Test
    fun `generate players is deterministic and respects squad size`() {
        val team = SyntheticLeagues.ARGENTINA.first { it.name == "River Plate" }

        val first = SyntheticLeagues.generatePlayers(team)
        val second = SyntheticLeagues.generatePlayers(team)

        assertEquals(team.squad, first.size)
        assertEquals(first.map { it.name }, second.map { it.name })
        assertEquals(first.map { it.position }, second.map { it.position })
    }

    @Test
    fun `saudi leagues include requested stars`() {
        val alHilal = SyntheticLeagues.SAUDI.first { it.name == "Al-Hilal SFC" }
        val alNassr = SyntheticLeagues.SAUDI.first { it.name == "Al-Nassr FC" }

        val hilalPlayers = SyntheticLeagues.generatePlayers(alHilal)
        val nassrPlayers = SyntheticLeagues.generatePlayers(alNassr)

        assertNotNull(hilalPlayers.find { it.name == "Neymar Jr" })
        assertNotNull(hilalPlayers.find { it.name == "Ruben Neves" })
        assertNotNull(hilalPlayers.find { it.name == "Kalidou Koulibaly" })
        assertNotNull(hilalPlayers.find { it.name == "Sergej Milinkovic-Savic" })

        val cristiano = nassrPlayers.find { it.name == "Cristiano Ronaldo" }
        assertNotNull(cristiano)
        assertEquals(85, cristiano!!.regate)
        assertEquals(95, cristiano.remate)
        assertEquals(95, cristiano.tiro)
        assertEquals(90, cristiano.ca)
        assertEquals(70, cristiano.ve)
        assertEquals(75, cristiano.re)
        assertEquals(55, cristiano.ag)
    }

    @Test
    fun `competition counts match spec`() {
        val counts = SyntheticLeagues.competitionTeamCounts()

        assertEquals(28, counts["ARGPD"])
        assertEquals(20, counts["BRASEA"])
        assertEquals(18, counts["LIGAMX"])
        assertEquals(18, counts["SPL"])
        assertEquals(20, counts["RFEF1A"])
        assertEquals(20, counts["RFEF1B"])
        assertEquals(18, counts["RFEF2A"])
        assertEquals(18, counts["RFEF2B"])
        assertEquals(18, counts["RFEF2C"])
        assertEquals(18, counts["RFEF2D"])
    }

    @Test
    fun `rfef groups generate expected total teams`() {
        val counts = SyntheticLeagues.competitionTeamCounts()
        val totalRfef = (counts["RFEF1A"] ?: 0) +
            (counts["RFEF1B"] ?: 0) +
            (counts["RFEF2A"] ?: 0) +
            (counts["RFEF2B"] ?: 0) +
            (counts["RFEF2C"] ?: 0) +
            (counts["RFEF2D"] ?: 0)

        assertEquals(112, totalRfef)
    }

    @Test
    fun `each synthetic squad keeps at least one goalkeeper`() {
        val allTeams = SyntheticLeagues.allTeams

        allTeams.forEach { team ->
            val players = SyntheticLeagues.generatePlayers(team)
            val keepers = players.count { it.position == "PO" }
            assertTrue(keepers >= 1, "Equipo ${team.name} sin porteros")
        }
    }
}
