package com.pcfutbol.matchsim

import kotlin.math.exp
import kotlin.random.Random

/**
 * Simulador determinístico de partidos.
 * Modelo: distribución de Poisson para goles, calibrado con los datos
 * de engine_strength_reference_global_final_force_liga2_safe.csv
 *
 * Reproducibilidad garantizada por seed (mismo seed = mismo resultado siempre).
 */
object MatchSimulator {

    // VAR: probabilidad de revisión y anulación (mismos valores que el CLI)
    private const val VAR_REVIEW_PROB   = 0.18   // 18% de revisión tras gol
    private const val VAR_DISALLOW_PROB = 0.38   // 38% de anulación cuando hay revisión

    /**
     * Simula un partido y devuelve el resultado con eventos.
     * La función es pura: no tiene estado mutable ni efectos secundarios.
     *
     * Incluye:
     *  - VAR: 18% revisión, 38% anulación → goles reales < goles Poisson
     *  - Tiempo añadido: calculado según amarillas + VAR + goles
     *  - Tarjetas rojas: 2ª amarilla + roja directa si faltas==3
     *  - Lesiones: 8% por equipo, 2-8 semanas
     */
    fun simulate(ctx: MatchContext): MatchResult {
        val rng = Random(ctx.seed)

        val homeStrength = StrengthCalculator.calculate(ctx.home)
        val awayStrength = StrengthCalculator.calculate(ctx.away)
        val disciplineSummary = generateDisciplinaryEvents(ctx, rng)
        val pace = paceAdjustments(ctx)

        // λ de Poisson calibrado con ventaja local (+12%)
        val homeLambda = applyPaceFactor(
            applyExpulsionPenalty(
            lambda = strengthToLambda(homeStrength, isHome = !ctx.neutral),
            redCards = disciplineSummary.homeRedCards,
            ),
            pace.homeFactor,
        )
        val awayLambda = applyPaceFactor(
            applyExpulsionPenalty(
            lambda = strengthToLambda(awayStrength, isHome = false),
            redCards = disciplineSummary.awayRedCards,
            ),
            pace.awayFactor,
        )

        val homeGoalsRaw = poissonSample(homeLambda, rng)
        val awayGoalsRaw = poissonSample(awayLambda, rng)
        val injuryEvents = generateInjuryEvents(ctx, rng)
        val timeWastingSummary = generateTimeWastingEvents(ctx, rng)

        // Aplicar VAR
        val homeVarResult = applyVarToGoals(ctx.home, homeGoalsRaw, rng)
        val awayVarResult = applyVarToGoals(ctx.away, awayGoalsRaw, rng)
        val homeGoals = homeVarResult.goalsAfterVar
        val awayGoals = awayVarResult.goalsAfterVar

        val goalEvents = generateGoalEvents(ctx, homeGoals, awayGoals, rng)

        // Tiempo añadido: base + amarillas/10 + VAR reviews + goles
        val totalYellows = disciplineSummary.events.count { it.type == EventType.YELLOW_CARD }
        val totalVarReviews = homeVarResult.varReviews + awayVarResult.varReviews
        val totalGoals = homeGoals + awayGoals
        val addedBase = (totalYellows / 3) + totalVarReviews + (totalGoals / 3) + timeWastingSummary.addedTimeBias
        val addedTime1 = (addedBase / 2 + rng.nextInt(1, 4)).coerceIn(1, 6)
        val addedTime2 = (addedBase / 2 + rng.nextInt(2, 6)).coerceIn(2, 10)

        val allEvents = (disciplineSummary.events + injuryEvents + goalEvents + timeWastingSummary.events
                + homeVarResult.varEvents + awayVarResult.varEvents)
            .sortedBy { it.minute }

        return MatchResult(
            homeGoals            = homeGoals,
            awayGoals            = awayGoals,
            events               = allEvents,
            homeStrength         = homeStrength,
            awayStrength         = awayStrength,
            seed                 = ctx.seed,
            addedTimeFirstHalf   = addedTime1,
            addedTimeSecondHalf  = addedTime2,
            varDisallowedHome    = homeVarResult.disallowed,
            varDisallowedAway    = awayVarResult.disallowed,
        )
    }

    // -------------------------------------------------------------------------
    // Modelo Poisson

    /**
     * Convierte fortaleza (10..99) a lambda de Poisson (0.4..3.0).
     * Calibrado para producir ~1.4 goles/equipo promedio (media real de LaLiga).
     */
    private fun strengthToLambda(strength: Double, isHome: Boolean): Double {
        // Normalizar strength a 0..1
        val norm = (strength - 10.0) / 89.0
        // Escala logarítmica: lambda ∈ [0.4, 3.0]
        val lambda = 0.4 + norm * 2.6
        // Ventaja local: +12% en goles para el local
        return if (isHome) lambda * 1.12 else lambda
    }

    /**
     * Muestra aleatoria de distribución de Poisson usando el método de Knuth.
     */
    private fun poissonSample(lambda: Double, rng: Random): Int {
        val l = exp(-lambda)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= rng.nextDouble()
        } while (p > l)
        return (k - 1).coerceIn(0, 9)
    }

    // -------------------------------------------------------------------------
    // VAR

    private data class VarGoalResult(
        val goalsAfterVar: Int,
        val disallowed: Int,
        val varReviews: Int,
        val varEvents: List<MatchEvent>,
    )

    /**
     * Aplica el VAR a los goles de un equipo.
     * Cada gol tiene un 18% de revisión; de las revisiones, el 38% termina en anulación.
     */
    private fun applyVarToGoals(team: TeamMatchInput, goals: Int, rng: Random): VarGoalResult {
        var remaining = goals
        var disallowed = 0
        var reviews = 0
        val events = mutableListOf<MatchEvent>()

        repeat(goals) {
            if (rng.nextDouble() < VAR_REVIEW_PROB) {
                reviews++
                if (rng.nextDouble() < VAR_DISALLOW_PROB) {
                    remaining--
                    disallowed++
                    val player = pickAnyPlayer(team, rng)
                    val playerName = player?.playerName?.takeIf { it.isNotBlank() }
                    events += MatchEvent(
                        minute = rng.nextInt(1, 95),
                        type = EventType.VAR_DISALLOWED,
                        teamId = team.teamId,
                        playerId = player?.playerId?.takeIf { it > 0 },
                        playerName = playerName,
                        description = if (playerName != null)
                            "GOL ANULADO por VAR: $playerName (${team.teamName})"
                        else
                            "GOL ANULADO por VAR (${team.teamName})",
                    )
                }
            }
        }

        return VarGoalResult(
            goalsAfterVar = remaining.coerceAtLeast(0),
            disallowed    = disallowed,
            varReviews    = reviews,
            varEvents     = events,
        )
    }

    internal fun applyExpulsionPenalty(lambda: Double, redCards: Int): Double {
        if (redCards <= 0) return lambda
        return (lambda * 0.8).coerceAtLeast(0.1)
    }

    private data class PaceAdjustments(
        val homeFactor: Double,
        val awayFactor: Double,
    )

    private data class TimeWastingSummary(
        val events: List<MatchEvent>,
        val addedTimeBias: Int,
    )

    private fun paceAdjustments(ctx: MatchContext): PaceAdjustments {
        val homeWaste = ctx.home.tactic.perdidaTiempo == 1
        val awayWaste = ctx.away.tactic.perdidaTiempo == 1
        return when {
            homeWaste && awayWaste -> PaceAdjustments(homeFactor = 0.82, awayFactor = 0.82)
            homeWaste -> PaceAdjustments(homeFactor = 0.88, awayFactor = 0.92)
            awayWaste -> PaceAdjustments(homeFactor = 0.92, awayFactor = 0.88)
            else -> PaceAdjustments(homeFactor = 1.0, awayFactor = 1.0)
        }
    }

    private fun applyPaceFactor(lambda: Double, paceFactor: Double): Double =
        (lambda * paceFactor).coerceAtLeast(0.1)

    private fun generateTimeWastingEvents(
        ctx: MatchContext,
        rng: Random,
    ): TimeWastingSummary {
        val events = mutableListOf<MatchEvent>()
        var bias = 0
        if (ctx.home.tactic.perdidaTiempo == 1) {
            events += createTimeWastingEventsForTeam(ctx.home, rng)
            bias += 1 + rng.nextInt(0, 2)
        }
        if (ctx.away.tactic.perdidaTiempo == 1) {
            events += createTimeWastingEventsForTeam(ctx.away, rng)
            bias += 1 + rng.nextInt(0, 2)
        }
        return TimeWastingSummary(events.sortedBy { it.minute }, bias.coerceIn(0, 4))
    }

    private fun createTimeWastingEventsForTeam(
        team: TeamMatchInput,
        rng: Random,
    ): List<MatchEvent> {
        val count = rng.nextInt(1, 4)
        return (0 until count).map {
            MatchEvent(
                minute = rng.nextInt(70, 94),
                type = EventType.TIME_WASTING,
                teamId = team.teamId,
                description = "Perdida de tiempo de ${team.teamName}",
            )
        }
    }

    // -------------------------------------------------------------------------
    // Eventos del partido

    private data class TeamDisciplineRuntime(
        val input: TeamMatchInput,
        val yellowBySlot: MutableMap<Int, Int> = mutableMapOf(),
        val dismissedSlots: MutableSet<Int> = mutableSetOf(),
        var redCards: Int = 0,
    )

    private data class DisciplineSummary(
        val events: List<MatchEvent>,
        val homeRedCards: Int,
        val awayRedCards: Int,
    )

    private fun generateDisciplinaryEvents(
        ctx: MatchContext,
        rng: Random,
    ): DisciplineSummary {
        val events = mutableListOf<MatchEvent>()
        val home = TeamDisciplineRuntime(ctx.home)
        val away = TeamDisciplineRuntime(ctx.away)

        // Tarjetas amarillas (media ~3 por partido) con segunda amarilla = roja.
        val yellowCount = poissonSample(1.5, rng) + poissonSample(1.5, rng)
        repeat(yellowCount) {
            val teamRuntime = if (rng.nextBoolean()) home else away
            addYellowEvent(teamRuntime, rng, events)
        }

        // Roja directa: solo para juego duro (faltas=3), 5% de probabilidad por equipo.
        addDirectRedIfNeeded(home, rng, events)
        addDirectRedIfNeeded(away, rng, events)

        return DisciplineSummary(
            events = events.sortedBy { it.minute },
            homeRedCards = home.redCards,
            awayRedCards = away.redCards,
        )
    }

    private fun generateGoalEvents(
        ctx: MatchContext,
        homeGoals: Int,
        awayGoals: Int,
        rng: Random,
    ): List<MatchEvent> {
        val events = mutableListOf<MatchEvent>()
        repeat(homeGoals) { events += createGoalEvent(ctx.home, rng) }
        repeat(awayGoals) { events += createGoalEvent(ctx.away, rng) }
        return events
    }

    private fun createGoalEvent(team: TeamMatchInput, rng: Random): MatchEvent {
        val scorer = pickAnyPlayer(team, rng)
        return MatchEvent(
            minute = rng.nextInt(1, 91),
            type = EventType.GOAL,
            teamId = team.teamId,
            playerId = scorer?.playerId?.takeIf { it > 0 },
            playerName = scorer?.playerName,
            description = scorer?.playerName
                ?.takeIf { it.isNotBlank() }
                ?.let { "Gol de $it (${team.teamName})" }
                ?: "Gol de ${team.teamName}",
        )
    }

    private fun generateInjuryEvents(
        ctx: MatchContext,
        rng: Random,
    ): List<MatchEvent> {
        val events = mutableListOf<MatchEvent>()
        createInjuryEvent(ctx.home, rng)?.let { events += it }
        createInjuryEvent(ctx.away, rng)?.let { events += it }
        return events
    }

    private fun createInjuryEvent(
        team: TeamMatchInput,
        rng: Random,
    ): MatchEvent? {
        if (team.squad.isEmpty()) return null
        if (rng.nextDouble() >= 0.08) return null
        val injured = pickAnyPlayer(team, rng) ?: return null
        val weeks = rng.nextInt(2, 9)
        val playerName = injured.playerName.takeIf { it.isNotBlank() } ?: "Jugador"
        return MatchEvent(
            minute = rng.nextInt(1, 91),
            type = EventType.INJURY,
            teamId = team.teamId,
            playerId = injured.playerId.takeIf { it > 0 },
            playerName = playerName,
            injuryWeeks = weeks,
            description = "Lesión de $playerName ($weeks semanas)",
        )
    }

    private fun addYellowEvent(
        teamRuntime: TeamDisciplineRuntime,
        rng: Random,
        events: MutableList<MatchEvent>,
    ) {
        val slot = pickEligibleSlot(teamRuntime, rng) ?: return
        val player = teamRuntime.input.squad[slot]
        val minute = rng.nextInt(1, 91)
        val playerName = player.playerName.takeIf { it.isNotBlank() } ?: "Jugador"

        events += MatchEvent(
            minute = minute,
            type = EventType.YELLOW_CARD,
            teamId = teamRuntime.input.teamId,
            playerId = player.playerId.takeIf { it > 0 },
            playerName = playerName,
            description = "Tarjeta amarilla para $playerName",
        )

        val yellowCount = (teamRuntime.yellowBySlot[slot] ?: 0) + 1
        teamRuntime.yellowBySlot[slot] = yellowCount
        if (yellowCount >= 2) {
            teamRuntime.redCards += 1
            teamRuntime.dismissedSlots += slot
            events += MatchEvent(
                minute = minute,
                type = EventType.RED_CARD,
                teamId = teamRuntime.input.teamId,
                playerId = player.playerId.takeIf { it > 0 },
                playerName = playerName,
                description = "Segunda amarilla y expulsión de $playerName",
            )
        }
    }

    private fun addDirectRedIfNeeded(
        teamRuntime: TeamDisciplineRuntime,
        rng: Random,
        events: MutableList<MatchEvent>,
    ) {
        if (teamRuntime.input.tactic.faltas != 3) return
        if (rng.nextDouble() >= 0.05) return
        val slot = pickEligibleSlot(teamRuntime, rng) ?: return
        val player = teamRuntime.input.squad[slot]
        val playerName = player.playerName.takeIf { it.isNotBlank() } ?: "Jugador"
        teamRuntime.redCards += 1
        teamRuntime.dismissedSlots += slot

        events += MatchEvent(
            minute = rng.nextInt(15, 91),
            type = EventType.RED_CARD,
            teamId = teamRuntime.input.teamId,
            playerId = player.playerId.takeIf { it > 0 },
            playerName = playerName,
            description = "Roja directa para $playerName",
        )
    }

    private fun pickEligibleSlot(
        teamRuntime: TeamDisciplineRuntime,
        rng: Random,
    ): Int? {
        if (teamRuntime.input.squad.isEmpty()) return null
        val eligible = teamRuntime.input.squad.indices
            .filterNot { it in teamRuntime.dismissedSlots }
        if (eligible.isEmpty()) return null
        return eligible[rng.nextInt(eligible.size)]
    }

    private fun pickAnyPlayer(team: TeamMatchInput, rng: Random): PlayerSimAttrs? {
        if (team.squad.isEmpty()) return null
        return team.squad[rng.nextInt(team.squad.size)]
    }
}
