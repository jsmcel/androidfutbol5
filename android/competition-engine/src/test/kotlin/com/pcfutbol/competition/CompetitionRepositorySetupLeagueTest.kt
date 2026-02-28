package com.pcfutbol.competition

import com.pcfutbol.core.data.db.CompetitionDao
import com.pcfutbol.core.data.db.CompetitionEntity
import com.pcfutbol.core.data.db.FixtureDao
import com.pcfutbol.core.data.db.FixtureEntity
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.StandingDao
import com.pcfutbol.core.data.db.StandingEntity
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.TeamEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompetitionRepositorySetupLeagueTest {

    @Test
    fun `setupLeague creates fixtures standings and refreshes competition metadata`() = runTest {
        val competitionDao = mockk<CompetitionDao>()
        val fixtureDao = mockk<FixtureDao>()
        val standingDao = mockk<StandingDao>()
        val teamDao = mockk<TeamDao>()
        val playerDao = mockk<PlayerDao>(relaxed = true)
        val seasonStateDao = mockk<SeasonStateDao>(relaxed = true)

        val teamCount = 20
        val teams = (1..teamCount).map { makeTeam(it, "PRML") }

        val fixtureSlot = slot<List<FixtureEntity>>()
        val standingSlot = slot<List<StandingEntity>>()
        val compUpdateSlot = slot<CompetitionEntity>()

        coEvery { competitionDao.byCode("PRML") } returns makeCompetition("PRML")
        coEvery { competitionDao.update(capture(compUpdateSlot)) } returns Unit
        coEvery { fixtureDao.deleteByCompetition("PRML") } returns Unit
        coEvery { standingDao.deleteByCompetition("PRML") } returns Unit
        coEvery { fixtureDao.insertAll(capture(fixtureSlot)) } returns Unit
        coEvery { standingDao.insertAll(capture(standingSlot)) } returns Unit
        every { teamDao.byCompetition("PRML") } returns flowOf(teams)

        val repository = CompetitionRepository(
            competitionDao = competitionDao,
            fixtureDao = fixtureDao,
            standingDao = standingDao,
            teamDao = teamDao,
            playerDao = playerDao,
            seasonStateDao = seasonStateDao,
        )

        repository.setupLeague("PRML")

        assertEquals(teamCount * (teamCount - 1), fixtureSlot.captured.size)
        assertEquals(teamCount, standingSlot.captured.size)
        assertEquals(teamCount, compUpdateSlot.captured.teamCount)
        assertEquals(38, compUpdateSlot.captured.matchdayCount)
        assertEquals(3, compUpdateSlot.captured.relegationSlots)
        assertEquals(0, compUpdateSlot.captured.promotionSlots)

        coVerify(exactly = 1) { fixtureDao.deleteByCompetition("PRML") }
        coVerify(exactly = 1) { standingDao.deleteByCompetition("PRML") }
    }

    @Test
    fun `setupLeague with reduced second tier adjusts slots by size`() = runTest {
        val competitionDao = mockk<CompetitionDao>()
        val fixtureDao = mockk<FixtureDao>()
        val standingDao = mockk<StandingDao>()
        val teamDao = mockk<TeamDao>()
        val playerDao = mockk<PlayerDao>(relaxed = true)
        val seasonStateDao = mockk<SeasonStateDao>(relaxed = true)

        val teams = (1..5).map { makeTeam(it, "LIGA2") }
        val fixtureSlot = slot<List<FixtureEntity>>()
        val compUpdateSlot = slot<CompetitionEntity>()

        coEvery { competitionDao.byCode("LIGA2") } returns makeCompetition("LIGA2")
        coEvery { competitionDao.update(capture(compUpdateSlot)) } returns Unit
        coEvery { fixtureDao.deleteByCompetition("LIGA2") } returns Unit
        coEvery { standingDao.deleteByCompetition("LIGA2") } returns Unit
        coEvery { fixtureDao.insertAll(capture(fixtureSlot)) } returns Unit
        coEvery { standingDao.insertAll(any()) } returns Unit
        every { teamDao.byCompetition("LIGA2") } returns flowOf(teams)

        val repository = CompetitionRepository(
            competitionDao = competitionDao,
            fixtureDao = fixtureDao,
            standingDao = standingDao,
            teamDao = teamDao,
            playerDao = playerDao,
            seasonStateDao = seasonStateDao,
        )

        repository.setupLeague("LIGA2")

        assertEquals(20, fixtureSlot.captured.size)
        assertEquals(10, compUpdateSlot.captured.matchdayCount)
        assertEquals(1, compUpdateSlot.captured.promotionSlots)
    }

    private fun makeTeam(slotId: Int, comp: String): TeamEntity = TeamEntity(
        slotId = slotId,
        nameShort = "Team $slotId",
        nameFull = "Team $slotId",
        stadiumName = "Stadium",
        countryId = "ES",
        competitionKey = comp,
        budgetK = 1000,
        membersCount = 0,
        sponsor = "",
        prestige = 5,
        shieldAssetName = "shield_1",
        kitAssetName = "kit_1",
    )

    private fun makeCompetition(code: String): CompetitionEntity = CompetitionEntity(
        code = code,
        name = code,
        formatType = "LEAGUE",
        teamCount = 0,
        matchdayCount = 0,
        promotionSlots = 0,
        relegationSlots = 0,
        europeanSlots = 0,
        playoffSlots = 0,
        tiebreakRule = "STANDARD",
        season = "2025-26",
    )
}
