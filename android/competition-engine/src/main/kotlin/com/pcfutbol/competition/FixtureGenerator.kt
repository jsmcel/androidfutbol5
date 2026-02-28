package com.pcfutbol.competition

import com.pcfutbol.core.data.db.FixtureEntity

/**
 * Genera el calendario de una liga de doble vuelta (round-robin) usando el algoritmo de rotación.
 * Garantiza que cada equipo juega 1 vez de local y 1 de visitante contra cada rival.
 */
object FixtureGenerator {

    /**
     * Genera todos los partidos de una liga.
     * @param competitionCode clave interna ("LIGA1", "LIGA2", ...)
     * @param teamIds lista de IDs de equipos (tamaño par)
     * @param baseMatchday primer número de jornada (por defecto 1)
     * @return lista de FixtureEntity sin ID (Room lo asigna al insertar)
     */
    fun generateLeague(
        competitionCode: String,
        teamIds: List<Int>,
        baseMatchday: Int = 1,
    ): List<FixtureEntity> {
        require(teamIds.size >= 2) { "Se necesitan al menos 2 equipos" }
        val teams = if (teamIds.size % 2 == 0) teamIds.toMutableList()
                    else (teamIds + listOf(-1)).toMutableList()  // -1 = bye

        val n = teams.size
        val rounds = n - 1
        val matchesPerRound = n / 2
        val fixtures = mutableListOf<FixtureEntity>()

        for (round in 0 until rounds) {
            val matchday = baseMatchday + round
            for (match in 0 until matchesPerRound) {
                val home = teams[match]
                val away = teams[n - 1 - match]
                if (home != -1 && away != -1) {
                    fixtures += FixtureEntity(
                        competitionCode = competitionCode,
                        matchday = matchday,
                        round = "MD$matchday",
                        homeTeamId = home,
                        awayTeamId = away,
                    )
                }
            }
            // Rotar: el primero es fijo, el resto rotan
            val last = teams.removeAt(n - 1)
            teams.add(1, last)
        }

        // Segunda vuelta: invertir local/visitante
        val firstLeg = fixtures.toList()
        firstLeg.forEach { f ->
            fixtures += f.copy(
                matchday = f.matchday + rounds,
                round = "MD${f.matchday + rounds}",
                homeTeamId = f.awayTeamId,
                awayTeamId = f.homeTeamId,
            )
        }

        return fixtures
    }

    /**
     * Genera el cuadro de eliminatoria directa (Copa del Rey, etc.).
     * @param teamIds equipos en orden de emparejamiento (par = local, impar = visitante)
     * @param startRound nombre de la ronda ("QF", "SF", "F", ...)
     */
    fun generateKnockout(
        competitionCode: String,
        teamIds: List<Int>,
        startRound: String = "R1",
        startMatchday: Int = 1,
    ): List<FixtureEntity> {
        val fixtures = mutableListOf<FixtureEntity>()
        teamIds.chunked(2).forEachIndexed { idx, pair ->
            if (pair.size == 2) {
                fixtures += FixtureEntity(
                    competitionCode = competitionCode,
                    matchday = startMatchday + idx,
                    round = startRound,
                    homeTeamId = pair[0],
                    awayTeamId = pair[1],
                )
            }
        }
        return fixtures
    }
}
