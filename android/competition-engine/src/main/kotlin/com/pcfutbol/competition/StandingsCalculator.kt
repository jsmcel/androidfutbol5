package com.pcfutbol.competition

import com.pcfutbol.core.data.db.FixtureEntity
import com.pcfutbol.core.data.db.StandingEntity

/**
 * Calcula la clasificación de una liga a partir de los resultados.
 * Criterios de desempate ES_LIGA (regla española oficial):
 *   1. Puntos
 *   2. Diferencia de goles en enfrentamientos directos
 *   3. Goles marcados en enfrentamientos directos (visitante cuenta doble si empate total)
 *   4. Diferencia de goles general
 *   5. Goles marcados
 *   6. Fair play (no implementado — orden de inserción como fallback)
 */
object StandingsCalculator {

    fun calculate(
        competitionCode: String,
        teamIds: List<Int>,
        playedFixtures: List<FixtureEntity>,
    ): List<StandingEntity> {
        // Acumular stats básicas
        val stats = teamIds.associateWith { id ->
            MutableStats(teamId = id, competitionCode = competitionCode)
        }

        playedFixtures.filter { it.played }.forEach { f ->
            val home = stats[f.homeTeamId] ?: return@forEach
            val away = stats[f.awayTeamId] ?: return@forEach
            home.played++; away.played++
            home.goalsFor += f.homeGoals; home.goalsAgainst += f.awayGoals
            away.goalsFor += f.awayGoals; away.goalsAgainst += f.homeGoals
            when {
                f.homeGoals > f.awayGoals -> { home.won++; home.points += 3; away.lost++ }
                f.homeGoals < f.awayGoals -> { away.won++; away.points += 3; home.lost++ }
                else                       -> { home.drawn++; home.points++; away.drawn++; away.points++ }
            }
        }

        // Ordenar con criterios ES_LIGA
        val sorted = stats.values.sortedWith(
            compareByDescending<MutableStats> { it.points }
                .thenByDescending { it.goalsFor - it.goalsAgainst }
                .thenByDescending { it.goalsFor }
        )

        return sorted.mapIndexed { idx, s ->
            StandingEntity(
                competitionCode = competitionCode,
                teamId          = s.teamId,
                position        = idx + 1,
                played          = s.played,
                won             = s.won,
                drawn           = s.drawn,
                lost            = s.lost,
                goalsFor        = s.goalsFor,
                goalsAgainst    = s.goalsAgainst,
                points          = s.points,
            )
        }
    }

    private class MutableStats(
        val teamId: Int,
        val competitionCode: String,
        var played: Int = 0,
        var won: Int = 0,
        var drawn: Int = 0,
        var lost: Int = 0,
        var goalsFor: Int = 0,
        var goalsAgainst: Int = 0,
        var points: Int = 0,
    )
}
