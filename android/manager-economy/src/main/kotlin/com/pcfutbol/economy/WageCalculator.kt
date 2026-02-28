package com.pcfutbol.economy

import com.pcfutbol.core.data.db.PlayerEntity

/**
 * Calcula salarios, cláusulas y valoraciones económicas de jugadores.
 * Basado en atributos del jugador y nivel de competición del equipo.
 */
object WageCalculator {

    /**
     * Calcula la media global de un jugador (0..99) ponderando por posición.
     * Compatible con el campo ME del simulador original.
     */
    fun playerMedia(p: PlayerEntity): Int {
        val baseMedia = when (p.position) {
            "PO" -> p.portero * 0.6 + p.ca * 0.2 + p.re * 0.2
            "DF" -> p.entrada * 0.35 + p.ca * 0.30 + p.ve * 0.20 + p.re * 0.15
            "MC" -> p.pase * 0.35 + p.ca * 0.30 + p.re * 0.20 + p.tiro * 0.15
            "DC" -> p.remate * 0.40 + p.regate * 0.25 + p.ca * 0.20 + p.tiro * 0.15
            else -> (p.ve + p.re + p.ag + p.ca + p.pase) / 5.0
        }
        return baseMedia.toInt().coerceIn(0, 99)
    }

    /**
     * Salario semanal estimado en K€ basado en media del jugador y liga.
     * Escala logarítmica: jugadores de élite (media 85+) ganan >100K/sem.
     */
    fun weeklyWageK(media: Int, competitionKey: String): Int {
        val baseK = when {
            media >= 90 -> 200
            media >= 85 -> 100
            media >= 80 -> 50
            media >= 75 -> 25
            media >= 70 -> 12
            media >= 65 -> 6
            media >= 60 -> 3
            else        -> 1
        }
        // Multiplicador por liga
        val ligaMult = when (competitionKey) {
            "LIGA1" -> 1.0
            "LIGA2" -> 0.5
            "LIGA2B" -> 0.25
            else    -> 0.8
        }
        return (baseK * ligaMult).toInt().coerceAtLeast(1)
    }

    /**
     * Cláusula de rescisión estimada en K€.
     * ~30x el salario semanal para jugadores normales, más para estrellas.
     */
    fun releaseClauseK(wageK: Int, media: Int): Int {
        val multiplier = when {
            media >= 85 -> 60
            media >= 75 -> 40
            else        -> 25
        }
        return wageK * multiplier
    }

    /**
     * Valor de mercado estimado en K€.
     * Base: cláusula × factor de edad.
     */
    fun marketValueK(player: PlayerEntity, competitionKey: String): Int {
        val media = playerMedia(player)
        val wage = weeklyWageK(media, competitionKey)
        val clause = releaseClauseK(wage, media)
        val age = 2026 - player.birthYear
        val ageFactor = when {
            age <= 22 -> 1.5   // prima por juventud
            age <= 26 -> 1.2
            age <= 30 -> 1.0
            age <= 33 -> 0.7
            else      -> 0.4
        }
        return (clause * ageFactor).toInt().coerceAtLeast(1)
    }
}
