package com.pcfutbol.economy

import com.pcfutbol.core.data.db.StandingEntity
import com.pcfutbol.core.data.db.TeamEntity

/**
 * Board objective rules for career mode.
 *
 * Objectives are assigned using relative ranking inside the competition when
 * peers are available, with a prestige-based fallback for compatibility.
 */
object BoardObjectives {

    enum class Objective(val labelEs: String) {
        SALVARSE("Salvarse del descenso"),
        TOP_HALF("Quedar en la primera mitad"),
        TOP_10("Quedar entre los 10 primeros"),
        TOP_6("Clasificarse para Europa"),
        TOP_4("Clasificarse para Champions"),
        CAMPEON("Ganar el campeonato"),
    }

    /**
     * Assign a board objective for a team.
     *
     * When [competitionTeams] is provided, the objective is derived from the
     * team rank by prestige inside its competition (CLI parity).
     */
    fun assignObjective(
        team: TeamEntity,
        competitionTeams: List<TeamEntity> = emptyList(),
    ): Objective {
        val ranked = rankPosition(team, competitionTeams)
        if (ranked != null) {
            return objectiveByRank(
                competitionKey = team.competitionKey,
                position = ranked.first,
                totalTeams = ranked.second,
            )
        }

        return when {
            team.competitionKey == "LIGA2" -> Objective.SALVARSE
            team.prestige <= 3 -> Objective.SALVARSE
            team.prestige in 4..5 -> Objective.TOP_HALF
            team.prestige == 6 -> Objective.TOP_10
            team.prestige == 7 -> Objective.TOP_6
            team.prestige == 8 -> Objective.TOP_4
            else -> Objective.CAMPEON
        }
    }

    /**
     * Evaluate objective completion from final standings.
     */
    fun evaluate(
        objective: Objective,
        standing: StandingEntity,
        totalTeams: Int,
        relegationSlots: Int,
    ): Boolean {
        val safeZone = totalTeams - relegationSlots
        return when (objective) {
            Objective.SALVARSE -> standing.position <= safeZone
            Objective.TOP_HALF -> standing.position <= totalTeams / 2
            Objective.TOP_10 -> standing.position <= 10
            Objective.TOP_6 -> standing.position <= 6
            Objective.TOP_4 -> standing.position <= 4
            Objective.CAMPEON -> standing.position == 1
        }
    }

    private fun rankPosition(team: TeamEntity, competitionTeams: List<TeamEntity>): Pair<Int, Int>? {
        if (competitionTeams.isEmpty()) return null
        val peers = competitionTeams
            .filter { it.competitionKey == team.competitionKey }
            .ifEmpty { competitionTeams }
        if (peers.isEmpty()) return null

        val ranked = peers.sortedWith(
            compareByDescending<TeamEntity> { it.prestige }
                .thenBy { it.slotId },
        )
        val idx = ranked.indexOfFirst { it.slotId == team.slotId }
        if (idx < 0) return null
        return (idx + 1) to ranked.size
    }

    private fun objectiveByRank(
        competitionKey: String,
        position: Int,
        totalTeams: Int,
    ): Objective {
        if (position <= 3) return Objective.CAMPEON
        if (competitionKey == "LIGA1" && position <= 4) return Objective.TOP_4
        if (competitionKey == "LIGA1" && position <= 6) return Objective.TOP_6
        if (position <= 10) return Objective.TOP_10

        val directBottom = if (totalTeams >= 22) 6 else 4
        val directStart = (totalTeams - directBottom + 1).coerceAtLeast(1)
        if (position >= directStart) return Objective.SALVARSE
        return Objective.TOP_HALF
    }
}
