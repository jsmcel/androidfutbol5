package com.pcfutbol.economy

import com.pcfutbol.core.data.db.TeamEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BoardObjectivesTest {

    @Test
    fun `assignObjective uses ranking rules for LIGA1`() {
        val liga1Teams = (1..20).map { idx ->
            team(
                slotId = idx,
                competition = "LIGA1",
                prestige = 21 - idx,
            )
        }

        assertEquals(
            BoardObjectives.Objective.CAMPEON,
            BoardObjectives.assignObjective(liga1Teams.first(), liga1Teams),
        )
        assertEquals(
            BoardObjectives.Objective.TOP_4,
            BoardObjectives.assignObjective(liga1Teams[3], liga1Teams),
        )
        assertEquals(
            BoardObjectives.Objective.TOP_6,
            BoardObjectives.assignObjective(liga1Teams[5], liga1Teams),
        )
        assertEquals(
            BoardObjectives.Objective.TOP_10,
            BoardObjectives.assignObjective(liga1Teams[8], liga1Teams),
        )
        assertEquals(
            BoardObjectives.Objective.TOP_HALF,
            BoardObjectives.assignObjective(liga1Teams[11], liga1Teams),
        )
        assertEquals(
            BoardObjectives.Objective.SALVARSE,
            BoardObjectives.assignObjective(liga1Teams[18], liga1Teams),
        )
    }

    @Test
    fun `assignObjective uses ranking rules for LIGA2 and fallback without peers`() {
        val liga2Teams = (1..22).map { idx ->
            team(
                slotId = idx,
                competition = "LIGA2",
                prestige = 23 - idx,
            )
        }

        assertEquals(
            BoardObjectives.Objective.TOP_10,
            BoardObjectives.assignObjective(liga2Teams[7], liga2Teams),
        )
        assertEquals(
            BoardObjectives.Objective.SALVARSE,
            BoardObjectives.assignObjective(liga2Teams[20], liga2Teams),
        )

        val fallback = team(slotId = 999, competition = "PRML", prestige = 7)
        assertEquals(
            BoardObjectives.Objective.TOP_6,
            BoardObjectives.assignObjective(fallback),
        )
    }

    private fun team(
        slotId: Int,
        competition: String,
        prestige: Int,
    ): TeamEntity = TeamEntity(
        slotId = slotId,
        nameShort = "T$slotId",
        nameFull = "Team $slotId",
        stadiumName = "Stadium",
        countryId = "ES",
        competitionKey = competition,
        budgetK = 1000,
        membersCount = 10000,
        sponsor = "Sponsor",
        prestige = prestige,
        shieldAssetName = "",
        kitAssetName = "",
    )
}
