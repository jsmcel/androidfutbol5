package com.pcfutbol.matchsim

import kotlin.random.Random

/**
 * Motor de evolucion anual de jugadores.
 * Mantiene reglas deterministas por seed para poder reproducir temporadas.
 */
object PlayerDevelopmentEngine {

    const val ACTIVE_STATUS = 0
    const val RETIRED_STATUS = 9

    /**
     * Vista minima del jugador necesaria para aplicar evolucion sin depender de Room.
     */
    data class DevelopmentPlayer(
        val id: Int,
        val birthYear: Int,
        val status: Int = ACTIVE_STATUS,
        val ve: Int,
        val re: Int,
        val ag: Int,
        val ca: Int,
        val remate: Int,
        val regate: Int,
        val pase: Int,
        val tiro: Int,
        val entrada: Int,
        val portero: Int,
    )

    data class YouthPlayer(
        val teamSlotId: Int,
        val name: String,
        val position: String,
        val birthYear: Int,
        val status: Int = ACTIVE_STATUS,
        val ve: Int,
        val re: Int,
        val ag: Int,
        val ca: Int,
        val remate: Int,
        val regate: Int,
        val pase: Int,
        val tiro: Int,
        val entrada: Int,
        val portero: Int,
    )

    /**
     * Aplica evolucion de fin de temporada.
     * - <24: mejora 1..3 atributos mas debiles (+1..+3)
     * - 24..30: pequena oscilacion aleatoria (0..2 atributos con -1..+1)
     * - >=31: degradacion en VE/RE (-1 o -2 desde 34)
     * - retiro: >=37 o >=35 con VE <= 30
     */
    fun applySeasonGrowth(
        players: List<DevelopmentPlayer>,
        seasonStartYear: Int,
        seed: Long,
    ): List<DevelopmentPlayer> {
        return players.map { player ->
            val age = (seasonStartYear - player.birthYear).coerceAtLeast(0)
            val rng = Random(seed xor (player.id.toLong() * 31L))

            if (player.status == RETIRED_STATUS) {
                player
            } else if (mustRetire(player, age)) {
                player.copy(status = RETIRED_STATUS)
            } else {
                when {
                    age < 24 -> evolveYoung(player, rng)
                    age >= 31 -> degradeVeteran(player, age)
                    else -> evolvePrime(player, rng)
                }
            }
        }
    }

    /**
     * Genera canteranos de 16..18 anios.
     */
    fun generateYouthPlayers(
        teamSlotId: Int,
        count: Int = 3,
        seasonStartYear: Int,
        seed: Long,
    ): List<YouthPlayer> {
        if (teamSlotId <= 0 || count <= 0) return emptyList()

        val rng = Random(seed xor (teamSlotId.toLong() * 13L))
        return (0 until count).map { index ->
            val position = pickYouthPosition(rng)
            val age = rng.nextInt(16, 19)
            val nameSuffix = 100 + ((index * 37 + rng.nextInt(900)) % 900)
            val isGoalkeeper = position == "PO"

            YouthPlayer(
                teamSlotId = teamSlotId,
                name = "Cantera $nameSuffix",
                position = position,
                birthYear = seasonStartYear - age,
                ve = clampAttr(rng.nextInt(30, 51)),
                re = clampAttr(rng.nextInt(30, 51)),
                ag = clampAttr(rng.nextInt(30, 51)),
                ca = clampAttr(rng.nextInt(30, 51)),
                remate = clampAttr(if (isGoalkeeper) rng.nextInt(5, 26) else rng.nextInt(25, 51)),
                regate = clampAttr(if (isGoalkeeper) rng.nextInt(5, 26) else rng.nextInt(25, 51)),
                pase = clampAttr(if (isGoalkeeper) rng.nextInt(10, 31) else rng.nextInt(25, 51)),
                tiro = clampAttr(if (isGoalkeeper) rng.nextInt(5, 21) else rng.nextInt(20, 46)),
                entrada = clampAttr(if (isGoalkeeper) rng.nextInt(10, 31) else rng.nextInt(25, 51)),
                portero = clampAttr(if (isGoalkeeper) rng.nextInt(35, 56) else 0),
            )
        }
    }

    private fun mustRetire(player: DevelopmentPlayer, age: Int): Boolean =
        age >= 37 || (age >= 35 && player.ve <= 30)

    private fun evolveYoung(player: DevelopmentPlayer, rng: Random): DevelopmentPlayer {
        var evolved = player
        val attributes = listOf(
            "ve" to player.ve,
            "re" to player.re,
            "ag" to player.ag,
            "ca" to player.ca,
            "remate" to player.remate,
            "regate" to player.regate,
            "pase" to player.pase,
            "tiro" to player.tiro,
            "entrada" to player.entrada,
            "portero" to player.portero,
        )

        val improveCount = rng.nextInt(1, 4)
        val weakest = attributes.sortedBy { it.second }.take(improveCount)
        weakest.forEach { (key, _) ->
            evolved = applyAttributeDelta(evolved, key, rng.nextInt(1, 4))
        }
        return evolved
    }

    private fun evolvePrime(player: DevelopmentPlayer, rng: Random): DevelopmentPlayer {
        val adjustmentCount = rng.nextInt(0, 3)
        if (adjustmentCount == 0) return player

        var evolved = player
        val attrs = listOf("ve", "re", "ag", "ca", "remate", "regate", "pase", "tiro", "entrada", "portero")
            .shuffled(rng)
            .take(adjustmentCount)

        attrs.forEach { key ->
            evolved = applyAttributeDelta(evolved, key, rng.nextInt(-1, 2))
        }
        return evolved
    }

    private fun degradeVeteran(player: DevelopmentPlayer, age: Int): DevelopmentPlayer {
        val delta = if (age >= 34) -2 else -1
        return player.copy(
            ve = clampAttr(player.ve + delta),
            re = clampAttr(player.re + delta),
        )
    }

    private fun applyAttributeDelta(
        player: DevelopmentPlayer,
        attribute: String,
        delta: Int,
    ): DevelopmentPlayer {
        if (delta == 0) return player
        return when (attribute) {
            "ve" -> player.copy(ve = clampAttr(player.ve + delta))
            "re" -> player.copy(re = clampAttr(player.re + delta))
            "ag" -> player.copy(ag = clampAttr(player.ag + delta))
            "ca" -> player.copy(ca = clampAttr(player.ca + delta))
            "remate" -> player.copy(remate = clampAttr(player.remate + delta))
            "regate" -> player.copy(regate = clampAttr(player.regate + delta))
            "pase" -> player.copy(pase = clampAttr(player.pase + delta))
            "tiro" -> player.copy(tiro = clampAttr(player.tiro + delta))
            "entrada" -> player.copy(entrada = clampAttr(player.entrada + delta))
            "portero" -> player.copy(portero = clampAttr(player.portero + delta))
            else -> player
        }
    }

    private fun pickYouthPosition(rng: Random): String {
        val roll = rng.nextInt(100)
        return when {
            roll < 10 -> "PO"
            roll < 38 -> "DF"
            roll < 74 -> "MC"
            else -> "DC"
        }
    }

    private fun clampAttr(value: Int): Int = value.coerceIn(0, 99)
}
