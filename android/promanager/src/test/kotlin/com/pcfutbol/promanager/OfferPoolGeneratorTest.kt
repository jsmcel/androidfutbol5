package com.pcfutbol.promanager

import com.pcfutbol.core.data.db.ManagerProfileEntity
import com.pcfutbol.core.data.db.TeamEntity
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfferPoolGeneratorTest {

    private fun makeManager(prestige: Int) = ManagerProfileEntity(
        id = 1,
        name = "Test",
        passwordHash = "",
        prestige = prestige,
    )

    private fun makeTeam(slotId: Int, prestige: Int, comp: String = "LIGA1") = TeamEntity(
        slotId = slotId,
        nameShort = "Team $slotId",
        nameFull = "Team $slotId",
        stadiumName = "Stadium",
        countryId = "ES",
        competitionKey = comp,
        budgetK = 1000,
        membersCount = 0,
        sponsor = "",
        prestige = prestige,
        shieldAssetName = "shield_$slotId",
        kitAssetName = "kit_$slotId",
    )

    @Test
    fun `nuevo manager siempre recibe al menos 3 ofertas`() {
        val manager = makeManager(prestige = 1)
        val teams = (1..20).map { makeTeam(it, prestige = it % 10 + 1) }

        val offers = OfferPoolGenerator.generate(manager, teams, emptySet())

        assertTrue(offers.size >= 3, "Se esperan >=3 ofertas, got ${offers.size}")
    }

    @Test
    fun `manager nuevo arranca en rfef2 con rangos de prestige validos`() {
        val manager = makeManager(prestige = 1)
        val teams = listOf(
            makeTeam(1, prestige = 1, comp = "RFEF2A"),
            makeTeam(2, prestige = 2, comp = "RFEF2A"),
            makeTeam(3, prestige = 1, comp = "RFEF2B"),
            makeTeam(4, prestige = 2, comp = "RFEF2B"),
            makeTeam(5, prestige = 2, comp = "RFEF1A"),
            makeTeam(6, prestige = 3, comp = "RFEF1B"),
            makeTeam(7, prestige = 4, comp = "LIGA2"),
            makeTeam(8, prestige = 6, comp = "LIGA2"),
            makeTeam(9, prestige = 7, comp = "LIGA1"),
            makeTeam(10, prestige = 10, comp = "LIGA1"),
        )

        val offers = OfferPoolGenerator.generate(manager, teams, emptySet())

        assertTrue(
            offers.any { it.team.competitionKey.startsWith("RFEF2") },
            "Manager rookie debe recibir al menos una oferta de 2a RFEF"
        )
        offers.forEach { offer ->
            when (offer.team.competitionKey) {
                "RFEF2A", "RFEF2B", "RFEF2C", "RFEF2D" ->
                    assertTrue(offer.team.prestige in 1..2)
                "RFEF1A", "RFEF1B", "LIGA2B", "LIGA2B2" ->
                    assertTrue(offer.team.prestige in 2..3)
                "LIGA2" ->
                    assertTrue(offer.team.prestige in 4..6)
                "LIGA1" ->
                    assertTrue(offer.team.prestige in 7..10)
            }
        }
    }

    @Test
    fun `manager prestige uno recibe ofertas rfef2`() {
        val manager = makeManager(prestige = 1)
        val teams = listOf(
            makeTeam(1, prestige = 1, comp = "RFEF2A"),
            makeTeam(2, prestige = 2, comp = "RFEF2B"),
            makeTeam(3, prestige = 2, comp = "RFEF2C"),
            makeTeam(4, prestige = 1, comp = "RFEF2D"),
            makeTeam(5, prestige = 3, comp = "RFEF1A"),
        )

        val offers = OfferPoolGenerator.generate(manager, teams, emptySet())

        assertTrue(offers.any { it.team.competitionKey.startsWith("RFEF2") })
    }

    @Test
    fun `equipos con manager no aparecen en las ofertas`() {
        val manager = makeManager(prestige = 5)
        val teams = (1..20).map { makeTeam(it, prestige = 5) }
        val occupied = (1..15).toSet()

        val offers = OfferPoolGenerator.generate(manager, teams, occupied)

        offers.forEach { offer ->
            assertFalse(
                offer.team.slotId in occupied,
                "Equipo ${offer.team.slotId} ya tiene manager"
            )
        }
    }

    @Test
    fun `manager veterano recibe equipos de mayor prestige`() {
        val rookie = makeManager(prestige = 1)
        val veteran = makeManager(prestige = 8)
        val teams = (1..30).map { makeTeam(it, prestige = (it % 10) + 1, comp = if (it % 2 == 0) "ERED" else "LIGA2") }

        val rookieOffers = OfferPoolGenerator.generate(rookie, teams, emptySet())
        val veteranOffers = OfferPoolGenerator.generate(veteran, teams, emptySet())

        val rookieAvg = rookieOffers.map { it.team.prestige }.average()
        val veteranAvg = veteranOffers.map { it.team.prestige }.average()

        assertTrue(
            veteranAvg > rookieAvg,
            "Veterano ($veteranAvg) deberia tener ofertas de mayor prestige que rookie ($rookieAvg)"
        )
    }

    @Test
    fun `salario es proporcional al prestige del equipo`() {
        val manager = makeManager(prestige = 3)
        val weakTeam = makeTeam(1, prestige = 2, comp = "ERED")
        val strongTeam = makeTeam(2, prestige = 9, comp = "ERED")

        val weakOffers = OfferPoolGenerator.generate(manager, listOf(weakTeam), emptySet())
        val strongOffers = OfferPoolGenerator.generate(manager, listOf(strongTeam), emptySet())

        if (weakOffers.isNotEmpty() && strongOffers.isNotEmpty()) {
            assertTrue(strongOffers[0].salary > weakOffers[0].salary, "Equipo fuerte debe ofrecer mas salario")
        }
    }

    @Test
    fun `filtro de ligas por prestige del manager`() {
        val rookie = makeManager(prestige = 2)
        val veteran = makeManager(prestige = 9)
        val teams = listOf(
            makeTeam(1, prestige = 9, comp = "PRML"),
            makeTeam(2, prestige = 8, comp = "BUN1"),
            makeTeam(3, prestige = 8, comp = "SPL"),
            makeTeam(4, prestige = 5, comp = "ERED"),
            makeTeam(5, prestige = 4, comp = "LIGA2"),
        )

        val rookieOffers = OfferPoolGenerator.generate(rookie, teams, emptySet())
        val veteranOffers = OfferPoolGenerator.generate(veteran, teams, emptySet())

        assertTrue(
            rookieOffers.none { it.team.competitionKey in setOf("PRML", "BUN1") },
            "Rookie no deberia recibir Premier/Bundesliga"
        )
        assertTrue(
            veteranOffers.any { it.team.competitionKey in setOf("PRML", "BUN1", "SPL") },
            "Veterano deberia recibir ofertas de ligas elite"
        )
    }
}
