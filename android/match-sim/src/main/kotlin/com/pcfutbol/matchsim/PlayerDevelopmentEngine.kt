package com.pcfutbol.matchsim

import kotlin.random.Random

/**
 * Motor de evolucion anual de jugadores.
 * Mantiene reglas deterministas por seed para poder reproducir temporadas.
 */
object PlayerDevelopmentEngine {

    const val ACTIVE_STATUS = 0
    const val RETIRED_STATUS = 9

    enum class TrainingIntensity { LOW, MEDIUM, HIGH }
    enum class TrainingFocus { BALANCED, PHYSICAL, DEFENSIVE, TECHNICAL, ATTACKING }

    data class StaffProfile(
        val segundoEntrenador: Int = 50,
        val fisio: Int = 50,
        val psicologo: Int = 50,
        val asistente: Int = 50,
        val secretario: Int = 50,
        val ojeador: Int = 50,
        val juveniles: Int = 50,
        val cuidador: Int = 50,
    )

    data class TrainingPlan(
        val intensity: TrainingIntensity = TrainingIntensity.MEDIUM,
        val focus: TrainingFocus = TrainingFocus.BALANCED,
    )

    data class DevelopmentContext(
        val staff: StaffProfile = StaffProfile(),
        val training: TrainingPlan = TrainingPlan(),
    )

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
        context: DevelopmentContext = DevelopmentContext(),
    ): List<DevelopmentPlayer> {
        val normalizedContext = normalizeContext(context)
        return players.map { player ->
            val age = (seasonStartYear - player.birthYear).coerceAtLeast(0)
            val rng = Random(seed xor (player.id.toLong() * 31L))

            if (player.status == RETIRED_STATUS) {
                player
            } else if (mustRetire(player, age)) {
                player.copy(status = RETIRED_STATUS)
            } else {
                when {
                    age < 24 -> evolveYoung(player, age, rng, normalizedContext)
                    age >= 31 -> degradeVeteran(player, age, rng, normalizedContext)
                    else -> evolvePrime(player, rng, normalizedContext)
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
        context: DevelopmentContext = DevelopmentContext(),
    ): List<YouthPlayer> {
        if (teamSlotId <= 0 || count <= 0) return emptyList()

        val normalizedContext = normalizeContext(context)
        val staff = normalizedContext.staff
        val floorBonus = (staff.juveniles / 20).coerceIn(0, 5)
        val scoutBonus = (staff.ojeador / 25).coerceIn(0, 4)
        val consistency = (staff.segundoEntrenador / 30).coerceIn(0, 3)
        val rng = Random(seed xor (teamSlotId.toLong() * 13L))
        return (0 until count).map { index ->
            val position = pickYouthPosition(rng, staff.ojeador)
            val age = rng.nextInt(16, 19)
            val nameSuffix = 100 + ((index * 37 + rng.nextInt(900)) % 900)
            val isGoalkeeper = position == "PO"

            YouthPlayer(
                teamSlotId = teamSlotId,
                name = "Cantera $nameSuffix",
                position = position,
                birthYear = seasonStartYear - age,
                ve = sampledAttr(rng, 30, 50, floorBonus + scoutBonus, consistency),
                re = sampledAttr(rng, 30, 50, floorBonus + scoutBonus, consistency),
                ag = sampledAttr(rng, 30, 50, floorBonus, consistency),
                ca = sampledAttr(rng, 30, 50, floorBonus + scoutBonus, consistency),
                remate = sampledAttr(
                    rng = rng,
                    min = if (isGoalkeeper) 5 else 25,
                    max = if (isGoalkeeper) 25 else 50,
                    floorBonus = if (isGoalkeeper) floorBonus / 2 else floorBonus + scoutBonus,
                    consistency = consistency,
                ),
                regate = sampledAttr(
                    rng = rng,
                    min = if (isGoalkeeper) 5 else 25,
                    max = if (isGoalkeeper) 25 else 50,
                    floorBonus = if (isGoalkeeper) floorBonus / 2 else floorBonus + scoutBonus,
                    consistency = consistency,
                ),
                pase = sampledAttr(
                    rng = rng,
                    min = if (isGoalkeeper) 10 else 25,
                    max = if (isGoalkeeper) 30 else 50,
                    floorBonus = if (isGoalkeeper) floorBonus / 2 else floorBonus + scoutBonus,
                    consistency = consistency,
                ),
                tiro = sampledAttr(
                    rng = rng,
                    min = if (isGoalkeeper) 5 else 20,
                    max = if (isGoalkeeper) 20 else 45,
                    floorBonus = if (isGoalkeeper) floorBonus / 2 else floorBonus + scoutBonus,
                    consistency = consistency,
                ),
                entrada = sampledAttr(
                    rng = rng,
                    min = if (isGoalkeeper) 10 else 25,
                    max = if (isGoalkeeper) 30 else 50,
                    floorBonus = if (isGoalkeeper) floorBonus / 2 else floorBonus + scoutBonus,
                    consistency = consistency,
                ),
                portero = clampAttr(if (isGoalkeeper) sampledAttr(rng, 35, 55, floorBonus + scoutBonus, consistency) else 0),
            )
        }
    }

    private fun mustRetire(player: DevelopmentPlayer, age: Int): Boolean =
        age >= 37 || (age >= 35 && player.ve <= 30)

    private fun evolveYoung(
        player: DevelopmentPlayer,
        age: Int,
        rng: Random,
        context: DevelopmentContext,
    ): DevelopmentPlayer {
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

        val baseImproveCount = rng.nextInt(1, 4)
        val intensityAdjust = when (context.training.intensity) {
            TrainingIntensity.LOW -> if (baseImproveCount > 1 && rng.nextInt(100) < 40) -1 else 0
            TrainingIntensity.MEDIUM -> 0
            TrainingIntensity.HIGH -> 1
        }
        val coachAdjust = if (age <= 21 && context.staff.segundoEntrenador >= 70) 1 else 0
        val improveCount = (baseImproveCount + intensityAdjust + coachAdjust).coerceIn(1, 5)
        val weakest = attributes.sortedBy { it.second }.map { it.first }
        val focused = pickDistinctAttributes(
            rng = rng,
            weightedPool = focusWeightedAttrs(context.training.focus, player.portero >= 35),
            count = improveCount,
        )
        val targets = (weakest.take(improveCount) + focused).distinct().take(improveCount)
        targets.forEach { key ->
            val intensityBonus = when (context.training.intensity) {
                TrainingIntensity.LOW -> 0
                TrainingIntensity.MEDIUM -> if (rng.nextInt(100) < 20) 1 else 0
                TrainingIntensity.HIGH -> if (rng.nextInt(100) < 55) 1 else 0
            }
            val coachBonus = if (context.staff.segundoEntrenador >= 75 && rng.nextInt(100) < 35) 1 else 0
            val delta = (rng.nextInt(1, 4) + intensityBonus + coachBonus).coerceAtMost(4)
            evolved = applyAttributeDelta(evolved, key, delta)
        }
        return evolved
    }

    private fun evolvePrime(
        player: DevelopmentPlayer,
        rng: Random,
        context: DevelopmentContext,
    ): DevelopmentPlayer {
        val baseAdjustmentCount = rng.nextInt(0, 3)
        val adjustmentCount = when (context.training.intensity) {
            TrainingIntensity.LOW -> if (baseAdjustmentCount > 0 && rng.nextInt(100) < 35) baseAdjustmentCount - 1 else baseAdjustmentCount
            TrainingIntensity.MEDIUM -> baseAdjustmentCount
            TrainingIntensity.HIGH -> if (rng.nextInt(100) < 35) baseAdjustmentCount + 1 else baseAdjustmentCount
        }.coerceIn(0, 3)
        if (adjustmentCount == 0) return player

        var evolved = player
        val attrs = pickDistinctAttributes(
            rng = rng,
            weightedPool = focusWeightedAttrs(context.training.focus, player.portero >= 35),
            count = adjustmentCount,
        )
        val focusAttrs = focusedAttrsOnly(context.training.focus)

        attrs.forEach { key ->
            var delta = rng.nextInt(-1, 2)
            if (delta > 0 && key in focusAttrs && rng.nextInt(100) < 45) delta += 1
            if (delta < 0 && context.staff.segundoEntrenador >= 75 && rng.nextInt(100) < 40) delta += 1
            evolved = applyAttributeDelta(evolved, key, delta)
        }
        return evolved
    }

    private fun degradeVeteran(
        player: DevelopmentPlayer,
        age: Int,
        rng: Random,
        context: DevelopmentContext,
    ): DevelopmentPlayer {
        val baseDecline = if (age >= 34) 2 else 1
        val intensityPenalty = if (context.training.intensity == TrainingIntensity.HIGH && age >= 33) 1 else 0
        val fisioRelief = when {
            context.staff.fisio >= 85 -> 1
            context.staff.fisio >= 65 && age >= 34 -> 1
            else -> 0
        }
        val decline = (baseDecline + intensityPenalty - fisioRelief).coerceAtLeast(0)
        var evolved = player
        if (decline > 0) {
            evolved = evolved.copy(
                ve = clampAttr(player.ve - decline),
                re = clampAttr(player.re - decline),
            )
        }
        if (decline > 0 &&
            context.training.intensity == TrainingIntensity.HIGH &&
            context.staff.fisio < 40 &&
            age >= 34 &&
            rng.nextInt(100) < 35
        ) {
            evolved = evolved.copy(ag = clampAttr(evolved.ag - 1))
        }
        return evolved
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

    private fun pickYouthPosition(rng: Random, scout: Int): String {
        val roll = rng.nextInt(100)
        return when {
            scout >= 80 && roll < 9 -> "PO"
            scout >= 80 && roll < 35 -> "DF"
            scout >= 80 && roll < 70 -> "MC"
            scout in 60..79 && roll < 10 -> "PO"
            scout in 60..79 && roll < 36 -> "DF"
            scout in 60..79 && roll < 72 -> "MC"
            roll < 12 -> "PO"
            roll < 40 -> "DF"
            roll < 73 -> "MC"
            else -> "DC"
        }
    }

    private fun focusWeightedAttrs(focus: TrainingFocus, isGoalkeeper: Boolean): List<String> {
        if (isGoalkeeper) {
            val base = listOf("portero", "re", "ag", "ca", "pase")
            return when (focus) {
                TrainingFocus.PHYSICAL -> base + listOf("re", "ag", "ve", "re")
                TrainingFocus.DEFENSIVE -> base + listOf("entrada", "re", "ag")
                TrainingFocus.TECHNICAL -> base + listOf("pase", "ca", "portero")
                TrainingFocus.ATTACKING -> base + listOf("ca", "pase")
                TrainingFocus.BALANCED -> base
            }
        }

        val base = listOf("ve", "re", "ag", "ca", "remate", "regate", "pase", "tiro", "entrada")
        return when (focus) {
            TrainingFocus.PHYSICAL -> base + listOf("ve", "re", "ag", "ve", "re")
            TrainingFocus.DEFENSIVE -> base + listOf("entrada", "re", "ag", "entrada")
            TrainingFocus.TECHNICAL -> base + listOf("pase", "regate", "ca", "pase")
            TrainingFocus.ATTACKING -> base + listOf("remate", "tiro", "regate", "remate")
            TrainingFocus.BALANCED -> base
        }
    }

    private fun focusedAttrsOnly(focus: TrainingFocus): Set<String> = when (focus) {
        TrainingFocus.PHYSICAL -> setOf("ve", "re", "ag")
        TrainingFocus.DEFENSIVE -> setOf("entrada", "re", "ag")
        TrainingFocus.TECHNICAL -> setOf("pase", "regate", "ca")
        TrainingFocus.ATTACKING -> setOf("remate", "tiro", "regate")
        TrainingFocus.BALANCED -> emptySet()
    }

    private fun pickDistinctAttributes(
        rng: Random,
        weightedPool: List<String>,
        count: Int,
    ): List<String> {
        if (weightedPool.isEmpty() || count <= 0) return emptyList()
        val selected = linkedSetOf<String>()
        var attempts = 0
        val maxAttempts = (count * 8).coerceAtLeast(8)
        while (selected.size < count && attempts < maxAttempts) {
            selected += weightedPool[rng.nextInt(weightedPool.size)]
            attempts += 1
        }
        if (selected.size < count) {
            weightedPool.distinct().shuffled(rng).forEach { attr ->
                if (selected.size < count) selected += attr
            }
        }
        return selected.toList().take(count)
    }

    private fun sampledAttr(
        rng: Random,
        min: Int,
        max: Int,
        floorBonus: Int,
        consistency: Int,
    ): Int {
        val adjustedMin = (min + floorBonus).coerceAtMost(max)
        val adjustedMax = (max + floorBonus).coerceAtLeast(adjustedMin)
        var value = rng.nextInt(adjustedMin, adjustedMax + 1)
        repeat(consistency) {
            value = (value + rng.nextInt(adjustedMin, adjustedMax + 1)) / 2
        }
        return clampAttr(value)
    }

    private fun normalizeContext(context: DevelopmentContext): DevelopmentContext {
        fun clampStaff(value: Int): Int = value.coerceIn(0, 100)
        val raw = context.staff
        return context.copy(
            staff = raw.copy(
                segundoEntrenador = clampStaff(raw.segundoEntrenador),
                fisio = clampStaff(raw.fisio),
                psicologo = clampStaff(raw.psicologo),
                asistente = clampStaff(raw.asistente),
                secretario = clampStaff(raw.secretario),
                ojeador = clampStaff(raw.ojeador),
                juveniles = clampStaff(raw.juveniles),
                cuidador = clampStaff(raw.cuidador),
            ),
        )
    }

    private fun clampAttr(value: Int): Int = value.coerceIn(0, 99)
}
