package com.pcfutbol.promanager

import com.pcfutbol.core.data.db.ManagerProfileEntity
import com.pcfutbol.core.data.db.TeamEntity
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import kotlin.math.abs

/**
 * Genera el pool de ofertas para ProManager con cobertura de todas las ligas activas.
 */
object OfferPoolGenerator {

    data class ManagerOffer(
        val team: TeamEntity,
        val salary: Int,
        val objectiveLevel: Int, // 1=salvarse, 2=top-10, 3=titulo
    )

    private val eliteLeagues = setOf("PRML", "BUN1", "SERIA", "LIG1", CompetitionDefinitions.LIGA1, "SPL")
    private val rfef1Leagues = setOf(
        CompetitionDefinitions.LIGA2B,
        CompetitionDefinitions.LIGA2B2,
        CompetitionDefinitions.RFEF1A,
        CompetitionDefinitions.RFEF1B,
    )
    private val rfef2Leagues = setOf(
        CompetitionDefinitions.RFEF2A,
        CompetitionDefinitions.RFEF2B,
        CompetitionDefinitions.RFEF2C,
        CompetitionDefinitions.RFEF2D,
    )
    private val nonLeagueCompetitions = setOf("CREY", "CEURO", "RECOPA", "CUEFA", "SCESP", "SCEUR", "FOREIGN")

    fun generate(
        manager: ManagerProfileEntity,
        allTeams: List<TeamEntity>,
        teamsWithManager: Set<Int>,
        isNewSeason: Boolean = false,
    ): List<ManagerOffer> {
        val rookieRfef2Group = preferredRookieRfef2Group(manager)
        val eligibleTeams = allTeams.filter { team ->
            team.slotId !in teamsWithManager &&
                isLeagueTeam(team.competitionKey) &&
                isLeagueEligible(manager.prestige, team.competitionKey, rookieRfef2Group) &&
                isPrestigeCompatible(manager.prestige, team.prestige, team.competitionKey)
        }

        val ranked = eligibleTeams.sortedWith(
            compareBy<TeamEntity> { leagueDistance(manager.prestige, it.competitionKey, rookieRfef2Group) }
                .thenBy { abs(it.prestige - manager.prestige) }
                .thenByDescending { it.prestige }
        )

        val candidates = if (ranked.size >= 3) {
            ranked.take(5)
        } else {
            relaxedCandidates(manager, allTeams, teamsWithManager).take(5)
        }

        return candidates
            .distinctBy { it.slotId }
            .map { team ->
                ManagerOffer(
                    team = team,
                    salary = calcSalary(team, manager.prestige),
                    objectiveLevel = calcObjective(team.competitionKey, team.prestige),
                )
            }
    }

    // ---------------------------------------------------------------------

    private fun relaxedCandidates(
        manager: ManagerProfileEntity,
        allTeams: List<TeamEntity>,
        teamsWithManager: Set<Int>,
    ): List<TeamEntity> =
        allTeams
            .filter { it.slotId !in teamsWithManager && isLeagueTeam(it.competitionKey) }
            .sortedBy { abs(it.prestige - manager.prestige) }

    private fun isLeagueTeam(competitionKey: String): Boolean =
        competitionKey.isNotBlank() && competitionKey !in nonLeagueCompetitions

    private fun isLeagueEligible(
        managerPrestige: Int,
        competitionKey: String,
        rookieRfef2Group: String?,
    ): Boolean {
        val tier = leagueTier(competitionKey)
        return when {
            managerPrestige <= 2 -> when {
                competitionKey in rfef2Leagues -> competitionKey == rookieRfef2Group
                competitionKey in rfef1Leagues -> true
                competitionKey == CompetitionDefinitions.LIGA2 -> true
                competitionKey == CompetitionDefinitions.LIGA1 -> true
                else -> false
            }
            managerPrestige <= 3 -> competitionKey in rfef2Leagues ||
                competitionKey in rfef1Leagues ||
                competitionKey == CompetitionDefinitions.LIGA2 ||
                competitionKey == CompetitionDefinitions.LIGA1
            managerPrestige <= 6 -> tier <= 3 && competitionKey !in setOf("PRML", "BUN1")
            else -> tier <= 2 || competitionKey in eliteLeagues
        }
    }

    private fun leagueTier(competitionKey: String): Int = CompetitionDefinitions.competitionTier(competitionKey)

    private fun leagueDistance(
        managerPrestige: Int,
        competitionKey: String,
        rookieRfef2Group: String?,
    ): Int {
        if (managerPrestige <= 2 && competitionKey in rfef2Leagues && competitionKey == rookieRfef2Group) {
            return 0
        }
        val preferredTier = when {
            managerPrestige <= 2 -> 4
            managerPrestige <= 3 -> 3
            managerPrestige <= 6 -> 2
            else -> 1
        }
        return abs(leagueTier(competitionKey) - preferredTier)
    }

    private fun isPrestigeCompatible(
        managerPrestige: Int,
        teamPrestige: Int,
        competitionKey: String,
    ): Boolean {
        if (managerPrestige <= 3) {
            val expectedRange = when {
                competitionKey in rfef2Leagues -> 1..2
                competitionKey in rfef1Leagues -> 2..3
                competitionKey == CompetitionDefinitions.LIGA2 -> 4..6
                competitionKey == CompetitionDefinitions.LIGA1 -> 7..10
                else -> null
            }
            if (expectedRange != null) {
                return teamPrestige in expectedRange
            }
        }

        val gap = teamPrestige - managerPrestige
        return when {
            managerPrestige <= 3 -> gap in -2..3
            managerPrestige <= 6 -> gap in -3..4
            else -> gap in -4..5
        }
    }

    private fun calcSalary(team: TeamEntity, managerPrestige: Int): Int {
        val leagueBonus = when (leagueTier(team.competitionKey)) {
            1 -> 8
            2 -> 4
            3 -> 2
            else -> 0
        }
        return team.prestige * 5 + managerPrestige * 2 + leagueBonus
    }

    private fun calcObjective(compKey: String, teamPrestige: Int): Int = when {
        compKey in rfef2Leagues || compKey in rfef1Leagues || compKey == CompetitionDefinitions.LIGA2 -> 1
        teamPrestige <= 3 -> 1
        teamPrestige in 4..6 -> 2
        else -> 3
    }

    private fun preferredRookieRfef2Group(manager: ManagerProfileEntity): String? {
        if (manager.prestige > 2) return null
        val sortedGroups = rfef2Leagues.toList().sorted()
        if (sortedGroups.isEmpty()) return null
        val idx = abs(manager.id).mod(sortedGroups.size)
        return sortedGroups[idx]
    }
}
