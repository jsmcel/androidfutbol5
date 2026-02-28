package com.pcfutbol.economy

import com.pcfutbol.core.data.db.StandingEntity
import com.pcfutbol.core.data.db.TeamEntity

/**
 * Gestión de objetivos de la junta directiva.
 * Al inicio de temporada la junta fija un objetivo según el prestige del equipo.
 * Al final evalúa si se cumplió.
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
     * Devuelve el objetivo de la junta para el equipo en función de su prestige
     * y la competición en la que juega.
     */
    fun assignObjective(team: TeamEntity): Objective = when {
        team.competitionKey == "LIGA2"    -> Objective.SALVARSE
        team.prestige <= 3                -> Objective.SALVARSE
        team.prestige in 4..5             -> Objective.TOP_HALF
        team.prestige == 6                -> Objective.TOP_10
        team.prestige == 7                -> Objective.TOP_6
        team.prestige == 8                -> Objective.TOP_4
        else                              -> Objective.CAMPEON
    }

    /**
     * Evalúa si el objetivo fue cumplido al final de la temporada.
     * @param standing clasificación final del equipo
     * @param totalTeams número de equipos en la competición
     * @param relegationSlots equipos que descienden
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
            Objective.TOP_10   -> standing.position <= 10
            Objective.TOP_6    -> standing.position <= 6
            Objective.TOP_4    -> standing.position <= 4
            Objective.CAMPEON  -> standing.position == 1
        }
    }
}
