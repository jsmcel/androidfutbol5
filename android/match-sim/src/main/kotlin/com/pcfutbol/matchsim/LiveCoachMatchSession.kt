package com.pcfutbol.matchsim

import kotlin.math.pow
import kotlin.random.Random

enum class LiveCoachCommand {
    BALANCED,
    ATTACK_ALL_IN,
    LOW_BLOCK,
    HIGH_PRESS,
    CALM_GAME,
    WASTE_TIME,
}

data class LiveCoachStep(
    val minute: Int,
    val homeGoals: Int,
    val awayGoals: Int,
    val events: List<MatchEvent>,
    val finished: Boolean,
)

/**
 * Sesion de partido en vivo para modo entrenador.
 * Avanza minuto a minuto y permite cambiar comando durante el partido.
 */
class LiveCoachMatchSession private constructor(
    private val ctx: MatchContext,
    private val managerTeamId: Int,
) {
    private val rng = Random(ctx.seed)
    private val homeStrength = StrengthCalculator.calculate(ctx.home)
    private val awayStrength = StrengthCalculator.calculate(ctx.away)

    private val homeBaseExpectedGoals = strengthToLambda(homeStrength, isHome = !ctx.neutral) *
        competitionGoalFactor(ctx.home.competitionCode, ctx.away.competitionCode)
    private val awayBaseExpectedGoals = strengthToLambda(awayStrength, isHome = false) *
        competitionGoalFactor(ctx.home.competitionCode, ctx.away.competitionCode)

    private var minute = 0
    private var homeGoals = 0
    private var awayGoals = 0
    private var homeReds = 0
    private var awayReds = 0
    private var finished = false
    private var addedTimeFirstHalf = 0
    private var addedTimeSecondHalf = 0
    private var currentCommand = LiveCoachCommand.BALANCED

    private var yellowCards = 0
    private var varReviews = 0
    private var totalGoalsScored = 0
    private var timeWastingEvents = 0

    private val events = mutableListOf<MatchEvent>()

    fun step(command: LiveCoachCommand): LiveCoachStep {
        if (finished) {
            return snapshot(emptyList())
        }

        currentCommand = command
        minute += 1

        val minuteEvents = mutableListOf<MatchEvent>()
        if (minute == 1) {
            minuteEvents += narrationEvent("NARRADOR: Arranca el partido y rueda el balon.", -1)
        }

        maybeAddTacticalStop(minuteEvents)
        maybeAddTimeWasting(minuteEvents)
        maybeAddDisciplinaryEvents(minuteEvents)
        maybeAddInjuries(minuteEvents)
        maybeAddGoalEvents(minuteEvents)
        maybeAddNarration(minuteEvents)

        if (minute == 45) {
            addedTimeFirstHalf = computeAddedTimeFirstHalf()
        }
        if (minute == 90) {
            addedTimeSecondHalf = computeAddedTimeSecondHalf()
        }

        events += minuteEvents.sortedBy { it.minute }
        if (minute >= 90 + addedTimeSecondHalf) {
            finished = true
        }

        return snapshot(minuteEvents)
    }

    fun toMatchResult(): MatchResult {
        while (!finished) {
            step(currentCommand)
        }
        return MatchResult(
            homeGoals = homeGoals,
            awayGoals = awayGoals,
            events = events.sortedBy { it.minute },
            homeStrength = homeStrength,
            awayStrength = awayStrength,
            seed = ctx.seed,
            addedTimeFirstHalf = addedTimeFirstHalf,
            addedTimeSecondHalf = addedTimeSecondHalf,
            varDisallowedHome = events.count { it.type == EventType.VAR_DISALLOWED && it.teamId == ctx.home.teamId },
            varDisallowedAway = events.count { it.type == EventType.VAR_DISALLOWED && it.teamId == ctx.away.teamId },
        )
    }

    private fun snapshot(stepEvents: List<MatchEvent>): LiveCoachStep = LiveCoachStep(
        minute = minute.coerceAtLeast(1),
        homeGoals = homeGoals,
        awayGoals = awayGoals,
        events = stepEvents,
        finished = finished,
    )

    private fun maybeAddTacticalStop(out: MutableList<MatchEvent>) {
        if (minute in TACTICAL_STOP_MINUTES) {
            val homeTeam = rng.nextBoolean()
            val team = if (homeTeam) ctx.home else ctx.away
            out += MatchEvent(
                minute = minute,
                type = EventType.TACTICAL_STOP,
                teamId = team.teamId,
                description = "Parada tactica: ${team.teamName} ajusta el plan desde el banquillo.",
            )
        }
    }

    private fun maybeAddTimeWasting(out: MutableList<MatchEvent>) {
        val profile = commandProfile(currentCommand)
        if (!profile.wasteTime || minute < 70) return
        if (rng.nextDouble() >= 0.22) return
        timeWastingEvents += 1
        val managerIsHome = managerTeamId == ctx.home.teamId
        val team = if (managerIsHome) ctx.home else ctx.away
        out += MatchEvent(
            minute = minute,
            type = EventType.TIME_WASTING,
            teamId = team.teamId,
            description = "Perdida de tiempo de ${team.teamName} para enfriar el partido.",
        )
    }

    private fun maybeAddDisciplinaryEvents(out: MutableList<MatchEvent>) {
        addTeamDisciplineEvent(team = ctx.home, isHome = true, out = out)
        addTeamDisciplineEvent(team = ctx.away, isHome = false, out = out)
    }

    private fun addTeamDisciplineEvent(
        team: TeamMatchInput,
        isHome: Boolean,
        out: MutableList<MatchEvent>,
    ) {
        val managerSide = managerTeamId == team.teamId
        val profile = commandProfile(currentCommand)
        val aggressionBoost = if (managerSide) profile.aggressionBoost else 1.0
        val baseYellow = 0.015
        val yellowProb = (baseYellow * aggressionBoost).coerceIn(0.002, 0.050)
        if (rng.nextDouble() < yellowProb) {
            yellowCards += 1
            val player = pickAnyPlayer(team)
            out += MatchEvent(
                minute = minute,
                type = EventType.YELLOW_CARD,
                teamId = team.teamId,
                playerId = player?.playerId?.takeIf { it > 0 },
                playerName = player?.playerName,
                description = player?.playerName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "Amarilla para $it (${team.teamName})." }
                    ?: "Amarilla para ${team.teamName}.",
            )
        }

        val baseRed = 0.0015
        val redProb = (baseRed * aggressionBoost).coerceIn(0.0003, 0.010)
        if (rng.nextDouble() < redProb) {
            if (isHome) homeReds += 1 else awayReds += 1
            val player = pickAnyPlayer(team)
            out += MatchEvent(
                minute = minute,
                type = EventType.RED_CARD,
                teamId = team.teamId,
                playerId = player?.playerId?.takeIf { it > 0 },
                playerName = player?.playerName,
                description = player?.playerName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "Roja directa para $it (${team.teamName})." }
                    ?: "Roja directa para ${team.teamName}.",
            )
        }
    }

    private fun maybeAddInjuries(out: MutableList<MatchEvent>) {
        addTeamInjuryEvent(ctx.home, out)
        addTeamInjuryEvent(ctx.away, out)
    }

    private fun addTeamInjuryEvent(team: TeamMatchInput, out: MutableList<MatchEvent>) {
        if (rng.nextDouble() >= 0.0018) return
        val player = pickAnyPlayer(team)
        val weeks = rng.nextInt(2, 9)
        out += MatchEvent(
            minute = minute,
            type = EventType.INJURY,
            teamId = team.teamId,
            playerId = player?.playerId?.takeIf { it > 0 },
            playerName = player?.playerName,
            injuryWeeks = weeks,
            description = player?.playerName
                ?.takeIf { it.isNotBlank() }
                ?.let { "Lesion de $it (${team.teamName}) - baja $weeks semanas." }
                ?: "Lesion en ${team.teamName} - baja $weeks semanas.",
        )
    }

    private fun maybeAddGoalEvents(out: MutableList<MatchEvent>) {
        tryGoalForTeam(isHome = true, out = out)
        tryGoalForTeam(isHome = false, out = out)
    }

    private fun tryGoalForTeam(
        isHome: Boolean,
        out: MutableList<MatchEvent>,
    ) {
        val team = if (isHome) ctx.home else ctx.away
        val pGoal = goalProbability(isHome)
        if (rng.nextDouble() >= pGoal) return

        val scorer = pickAnyPlayer(team)
        val review = rng.nextDouble() < VAR_REVIEW_PROB
        if (review) {
            varReviews += 1
            out += MatchEvent(
                minute = minute,
                type = EventType.VAR_REVIEW,
                teamId = team.teamId,
                playerId = scorer?.playerId?.takeIf { it > 0 },
                playerName = scorer?.playerName,
                description = scorer?.playerName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "VAR revisando accion de $it (${team.teamName})." }
                    ?: "VAR revisando accion de ${team.teamName}.",
            )
            if (rng.nextDouble() < VAR_DISALLOW_PROB) {
                out += MatchEvent(
                    minute = minute,
                    type = EventType.VAR_DISALLOWED,
                    teamId = team.teamId,
                    playerId = scorer?.playerId?.takeIf { it > 0 },
                    playerName = scorer?.playerName,
                    description = scorer?.playerName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "GOL ANULADO por VAR: $it (${team.teamName})." }
                        ?: "GOL ANULADO por VAR (${team.teamName}).",
                )
                return
            }
        }

        totalGoalsScored += 1
        if (isHome) homeGoals += 1 else awayGoals += 1
        out += MatchEvent(
            minute = minute,
            type = EventType.GOAL,
            teamId = team.teamId,
            playerId = scorer?.playerId?.takeIf { it > 0 },
            playerName = scorer?.playerName,
            description = scorer?.playerName
                ?.takeIf { it.isNotBlank() }
                ?.let { "Gol de $it (${team.teamName})." }
                ?: "Gol de ${team.teamName}.",
        )
    }

    private fun maybeAddNarration(out: MutableList<MatchEvent>) {
        val mustNarrate = minute % 2 == 0
        if (!mustNarrate && rng.nextDouble() >= 0.60) return
        val managerIsHome = managerTeamId == ctx.home.teamId
        val profile = commandProfile(currentCommand)
        val text = when {
            profile.wasteTime && minute >= 70 ->
                "NARRADOR: El ritmo baja y el cronometro pesa en este tramo final."
            profile.attackBoost >= 1.18 ->
                "NARRADOR: El banquillo pide verticalidad total y el partido se rompe."
            profile.defenseBoost >= 1.12 ->
                "NARRADOR: Bloque compacto y pocos espacios en la frontal."
            managerIsHome && homeGoals > awayGoals ->
                "NARRADOR: El local intenta gestionar la ventaja con cabeza."
            !managerIsHome && awayGoals > homeGoals ->
                "NARRADOR: El visitante administra la renta sin renunciar al contragolpe."
            else ->
                MID_NARRATIONS[rng.nextInt(MID_NARRATIONS.size)]
        }
        out += narrationEvent(text, -1)
    }

    private fun narrationEvent(text: String, teamId: Int): MatchEvent = MatchEvent(
        minute = minute,
        type = EventType.NARRATION,
        teamId = teamId,
        description = text,
    )

    private fun goalProbability(isHome: Boolean): Double {
        val managerIsHome = managerTeamId == ctx.home.teamId
        val profile = commandProfile(currentCommand)

        val attackFactor = when {
            isHome && managerIsHome -> profile.attackBoost
            !isHome && !managerIsHome -> profile.attackBoost
            else -> 1.0
        }
        val defenseFactor = when {
            isHome && !managerIsHome -> profile.defenseBoost
            !isHome && managerIsHome -> profile.defenseBoost
            else -> 1.0
        }

        val redsForTeam = if (isHome) homeReds else awayReds
        val redsForOpponent = if (isHome) awayReds else homeReds
        val ownRedPenalty = 0.82.pow(redsForTeam.toDouble())
        val oppRedBonus = 1.07.pow(redsForOpponent.toDouble())

        val expectedGoals = (if (isHome) homeBaseExpectedGoals else awayBaseExpectedGoals) *
            attackFactor *
            (1.0 / defenseFactor) *
            ownRedPenalty *
            oppRedBonus

        var p = (expectedGoals / 96.0).coerceIn(0.0008, 0.17)
        if (currentCommand == LiveCoachCommand.WASTE_TIME && minute >= 70) p *= 0.85
        if (minute > 90) p *= 0.65
        return p.coerceIn(0.0006, 0.18)
    }

    private fun pickAnyPlayer(team: TeamMatchInput): PlayerSimAttrs? =
        team.squad.takeIf { it.isNotEmpty() }?.let { squad ->
            squad[rng.nextInt(squad.size)]
        }

    private fun computeAddedTimeFirstHalf(): Int =
        ((yellowCards / 3) + varReviews + (totalGoalsScored / 3) + (timeWastingEvents / 2) + 1).coerceIn(1, 6)

    private fun computeAddedTimeSecondHalf(): Int =
        ((yellowCards / 3) + varReviews + (totalGoalsScored / 2) + timeWastingEvents + 2).coerceIn(2, 10)

    private data class CommandProfile(
        val attackBoost: Double,
        val defenseBoost: Double,
        val aggressionBoost: Double,
        val wasteTime: Boolean,
    )

    private fun commandProfile(command: LiveCoachCommand): CommandProfile = when (command) {
        LiveCoachCommand.BALANCED -> CommandProfile(attackBoost = 1.0, defenseBoost = 1.0, aggressionBoost = 1.0, wasteTime = false)
        LiveCoachCommand.ATTACK_ALL_IN -> CommandProfile(attackBoost = 1.24, defenseBoost = 0.88, aggressionBoost = 1.08, wasteTime = false)
        LiveCoachCommand.LOW_BLOCK -> CommandProfile(attackBoost = 0.82, defenseBoost = 1.16, aggressionBoost = 0.94, wasteTime = false)
        LiveCoachCommand.HIGH_PRESS -> CommandProfile(attackBoost = 1.12, defenseBoost = 0.96, aggressionBoost = 1.35, wasteTime = false)
        LiveCoachCommand.CALM_GAME -> CommandProfile(attackBoost = 0.90, defenseBoost = 1.05, aggressionBoost = 0.82, wasteTime = false)
        LiveCoachCommand.WASTE_TIME -> CommandProfile(attackBoost = 0.84, defenseBoost = 1.08, aggressionBoost = 0.88, wasteTime = true)
    }

    companion object {
        private const val VAR_REVIEW_PROB = 0.18
        private const val VAR_DISALLOW_PROB = 0.38
        private const val LAMBDA_BASE = 0.29
        private const val LAMBDA_SPAN = 1.78
        private const val HOME_GOAL_BONUS = 1.08
        private val TACTICAL_STOP_MINUTES = setOf(10, 20, 30, 40, 55, 65, 75, 85)
        private val LEAGUE_GOAL_FACTOR = mapOf(
            "ES1" to 0.93,
            "ES2" to 0.81,
            "GB1" to 1.08,
            "IT1" to 1.02,
            "L1" to 1.15,
            "FR1" to 1.02,
            "NL1" to 1.12,
            "PO1" to 0.96,
            "BE1" to 1.08,
            "TR1" to 1.04,
        )
        private val MID_NARRATIONS = listOf(
            "NARRADOR: Mucho ida y vuelta, nadie consigue imponer el guion.",
            "NARRADOR: Tramo de presion alta y segundas jugadas.",
            "NARRADOR: El choque tactico manda en la medular.",
            "NARRADOR: El partido entra en fase de detalle y nervio.",
        )

        fun create(ctx: MatchContext, managerTeamId: Int): LiveCoachMatchSession =
            LiveCoachMatchSession(ctx = ctx, managerTeamId = managerTeamId)

        private fun strengthToLambda(strength: Double, isHome: Boolean): Double {
            val norm = (strength - 10.0) / 89.0
            val lambda = LAMBDA_BASE + norm * LAMBDA_SPAN
            return if (isHome) lambda * HOME_GOAL_BONUS else lambda
        }

        private fun competitionGoalFactor(homeComp: String, awayComp: String): Double {
            val hf = LEAGUE_GOAL_FACTOR[homeComp] ?: 1.0
            val af = LEAGUE_GOAL_FACTOR[awayComp] ?: 1.0
            return ((hf + af) * 0.5).coerceIn(0.75, 1.20)
        }
    }
}
