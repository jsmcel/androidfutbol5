package com.pcfutbol.competition

import com.pcfutbol.core.data.db.*
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import com.pcfutbol.matchsim.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class MatchdayResult(
    val matchday: Int,
    val competitionCode: String,
    val results: List<MatchResult>,
    val updatedStandings: List<StandingEntity>,
)

/**
 * Repositorio principal del motor de competición.
 * Orquesta FixtureGenerator, MatchSimulator y StandingsCalculator.
 */
@Singleton
class CompetitionRepository @Inject constructor(
    private val competitionDao: CompetitionDao,
    private val fixtureDao: FixtureDao,
    private val standingDao: StandingDao,
    private val teamDao: TeamDao,
    private val playerDao: PlayerDao,
    private val seasonStateDao: SeasonStateDao,
    private val managerProfileDao: ManagerProfileDao,
    private val tacticPresetDao: TacticPresetDao,
    private val newsDao: NewsDao,
) {

    fun standings(comp: String): Flow<List<StandingWithTeam>> =
        standingDao.standingWithTeam(comp)

    fun fixtures(comp: String): Flow<List<FixtureEntity>> =
        fixtureDao.byCompetition(comp)

    suspend fun pendingFixtures(comp: String): Int =
        fixtureDao.pendingCount(comp)

    suspend fun setupAllLeagues(codes: Collection<String>? = null) {
        val leagueCodes = codes?.toList()
            ?: competitionDao.all()
                .first()
                .filter { it.formatType == "LEAGUE" }
                .map { it.code }
        for (leagueCode in leagueCodes) {
            setupLeague(leagueCode)
        }
    }

    /**
     * Simula todos los partidos de una jornada concreta y actualiza la clasificación.
     * @param seed semilla maestra; cada partido usa seed XOR fixtureId para independencia
     */
    suspend fun advanceMatchday(
        competitionCode: String,
        matchday: Int,
        masterSeed: Long,
    ): MatchdayResult {
        decrementPlayerAvailabilityCounters()

        val fixtures = fixtureDao.byMatchday(competitionCode, matchday)
            .filter { !it.played }
        val seasonState = seasonStateDao.get()
        val managerTeamId = seasonState?.managerTeamId ?: -1
        var managerFixturePlayed: FixtureEntity? = null
        var managerResult: MatchResult? = null

        val results = mutableListOf<MatchResult>()
        fixtures.forEach { fixture ->
            val home = buildTeamInput(fixture.homeTeamId, isHome = true)
            val away = buildTeamInput(fixture.awayTeamId, isHome = false)
            val seed = masterSeed xor fixture.id.toLong()
            val ctx = MatchContext(fixture.id, home, away, seed, fixture.neutral)
            val result = MatchSimulator.simulate(ctx)
            fixtureDao.recordResult(
                id = fixture.id,
                hg = result.homeGoals,
                ag = result.awayGoals,
                seed = seed,
            )
            fixtureDao.updateEventsJson(fixture.id, serializeEvents(result.events))
            applyPostMatchEffects(fixture, result)
            publishMatchNews(
                fixture = fixture,
                result = result,
                matchday = matchday,
                managerTeamId = managerTeamId,
            )
            if (fixture.homeTeamId == managerTeamId || fixture.awayTeamId == managerTeamId) {
                managerFixturePlayed = fixture
                managerResult = result
            }
            results += result
        }

        // Recalcular clasificación con todos los partidos jugados hasta ahora
        val allPlayedFixtures = fixtureDao.byCompetition(competitionCode)
            .first()
            .filter { it.played }
        val teamIds = teamDao.byCompetition(competitionCode).first().map { it.slotId }
        val updatedStandings = StandingsCalculator.calculate(competitionCode, teamIds, allPlayedFixtures)
        standingDao.insertAll(updatedStandings)
        applyWeeklyFinance(
            matchday = matchday,
            managerTeamId = managerTeamId,
            managerFixture = managerFixturePlayed,
            managerResult = managerResult,
            updatedStandings = updatedStandings,
        )

        // Avanzar matchday en SeasonState y gestionar ventana de mercado
        val state = seasonStateDao.get()
        if (state != null && matchday >= state.currentMatchday) {
            val nextMd = matchday + 1
            val (windowOpen, phase) = when {
                // Fin de pretemporada: cierra ventana de verano al pasar jornada 3
                nextMd > 3 && state.transferWindowOpen && state.phase == "PRESEASON" ->
                    false to "SEASON"
                // Apertura ventana de invierno (jornada 19)
                nextMd == 19 && !state.transferWindowOpen ->
                    true to state.phase
                // Cierre ventana de invierno (tras jornada 21)
                nextMd > 21 && state.transferWindowOpen && state.phase == "SEASON" ->
                    false to state.phase
                else ->
                    state.transferWindowOpen to state.phase
            }
            seasonStateDao.update(state.copy(
                currentMatchday = nextMd,
                transferWindowOpen = windowOpen,
                phase = phase,
            ))
        }

        return MatchdayResult(
            matchday = matchday,
            competitionCode = competitionCode,
            results = results,
            updatedStandings = updatedStandings,
        )
    }

    /**
     * Inicializa el calendario de una liga.
     * Borra fixtures existentes y genera el calendar completo.
     */
    suspend fun setupLeague(competitionCode: String) {
        val teams = teamDao.byCompetition(competitionCode).first().map { it.slotId }
        fixtureDao.deleteByCompetition(competitionCode)
        standingDao.deleteByCompetition(competitionCode)

        refreshCompetitionMetadata(competitionCode, teams.size)
        if (teams.size < 2) return

        val fixtures = FixtureGenerator.generateLeague(competitionCode, teams)
        fixtureDao.insertAll(fixtures)

        val standings = teams.map { teamId ->
            StandingEntity(competitionCode = competitionCode, teamId = teamId)
        }
        standingDao.insertAll(standings)
    }

    // -------------------------------------------------------------------------

    private suspend fun applyPostMatchEffects(
        fixture: FixtureEntity,
        result: MatchResult,
    ) {
        applyMoraleAfterMatch(
            homeTeamId = fixture.homeTeamId,
            awayTeamId = fixture.awayTeamId,
            homeGoals = result.homeGoals,
            awayGoals = result.awayGoals,
        )
        applyInjuriesFromEvents(result)
    }

    private suspend fun applyMoraleAfterMatch(
        homeTeamId: Int,
        awayTeamId: Int,
        homeGoals: Int,
        awayGoals: Int,
    ) {
        val (homeDelta, awayDelta) = MatchOutcomeRules.moraleDeltas(homeGoals, awayGoals)
        if (homeDelta != 0) playerDao.shiftTeamMoral(homeTeamId, homeDelta)
        if (awayDelta != 0) playerDao.shiftTeamMoral(awayTeamId, awayDelta)
    }

    private suspend fun applyInjuriesFromEvents(result: MatchResult) {
        val injuryEvents = result.events.filter {
            it.type == EventType.INJURY && it.playerId != null && it.injuryWeeks != null
        }
        injuryEvents.forEach { injury ->
            val playerId = injury.playerId ?: return@forEach
            val weeks = injury.injuryWeeks ?: return@forEach
            val current = playerDao.byId(playerId) ?: return@forEach
            val injuryWeeksLeft = maxOf(current.injuryWeeksLeft, weeks)
            val status = when {
                injuryWeeksLeft > 0 -> 1
                current.sanctionMatchesLeft > 0 -> 2
                else -> 0
            }
            playerDao.update(current.copy(
                injuryWeeksLeft = injuryWeeksLeft,
                status = status,
            ))
        }
    }

    /**
     * Cierra una jornada reduciendo contadores de indisponibilidad.
     * Se ejecuta al comienzo de la siguiente simulación para no acortar
     * la duración de lesiones producidas en la jornada recién jugada.
     */
    private suspend fun decrementPlayerAvailabilityCounters() {
        val unavailable = (playerDao.withInjury() + playerDao.withSanction())
            .associateBy { it.id }
            .values
        if (unavailable.isEmpty()) return

        val updated = unavailable.mapNotNull { player ->
            val injuryWeeksLeft = (player.injuryWeeksLeft - 1).coerceAtLeast(0)
            val sanctionMatchesLeft = (player.sanctionMatchesLeft - 1).coerceAtLeast(0)
            val status = when {
                injuryWeeksLeft > 0 -> 1
                sanctionMatchesLeft > 0 -> 2
                else -> 0
            }
            if (
                injuryWeeksLeft == player.injuryWeeksLeft &&
                sanctionMatchesLeft == player.sanctionMatchesLeft &&
                status == player.status
            ) {
                null
            } else {
                player.copy(
                    injuryWeeksLeft = injuryWeeksLeft,
                    sanctionMatchesLeft = sanctionMatchesLeft,
                    status = status,
                )
            }
        }
        if (updated.isNotEmpty()) playerDao.updateAll(updated)
    }

    private suspend fun buildTeamInput(teamId: Int, isHome: Boolean): TeamMatchInput {
        val team = teamDao.byId(teamId)
        val players = playerDao.byTeamNow(teamId)
        val available = players.filter {
            it.status == 0 && it.injuryWeeksLeft <= 0 && it.sanctionMatchesLeft <= 0
        }
        val preferred = available.sortedWith(
            compareByDescending<PlayerEntity> { it.isStarter }
                .thenByDescending { it.ca }
                .thenBy { it.number }
        )
        val preferredIds = preferred.map { it.id }.toSet()
        val fallback = players
            .filter { it.id !in preferredIds }
            .sortedByDescending { it.ca }
        val starters = (preferred + fallback).take(11)
        val tactic = resolveTeamTactic(teamId)
        return TeamMatchInput(
            teamId   = teamId,
            teamName = team?.nameShort ?: "Equipo $teamId",
            squad    = starters.map { it.toSimAttrs() },
            tactic   = tactic,
            isHome   = isHome,
            competitionCode = team?.competitionKey ?: "",
        )
    }

    private suspend fun resolveTeamTactic(teamId: Int): TacticParams {
        val state = seasonStateDao.get() ?: return TacticParams()
        if (state.managerTeamId != teamId) return TacticParams()

        val managerProfileId = managerProfileDao.byTeam(teamId)?.id ?: teamId
        val preset = tacticPresetDao.bySlot(managerProfileId, 0) ?: return TacticParams()
        return TacticParams(
            tipoJuego = preset.tipoJuego,
            tipoMarcaje = preset.tipoMarcaje,
            tipoPresion = preset.tipoPresion,
            tipoDespejes = preset.tipoDespejes,
            faltas = preset.faltas,
            porcToque = preset.porcToque,
            porcContra = preset.porcContra,
            marcajeDefensas = preset.marcajeDefensas,
            marcajeMedios = preset.marcajeMedios,
            puntoDefensa = preset.puntoDefensa,
            puntoAtaque = preset.puntoAtaque,
            area = preset.area,
            perdidaTiempo = preset.perdidaTiempo,
        )
    }

    private suspend fun refreshCompetitionMetadata(competitionCode: String, teamCount: Int) {
        val competition = competitionDao.byCode(competitionCode) ?: return
        if (competition.formatType != "LEAGUE") return

        val relegationSlots = CompetitionDefinitions.defaultRelegationSlots(teamCount)
        val promotionSlots = promotionSlotsFor(competitionCode, teamCount)
        val matchdays = CompetitionDefinitions.roundRobinMatchdays(teamCount)

        competitionDao.update(
            competition.copy(
                teamCount = teamCount,
                matchdayCount = matchdays,
                relegationSlots = relegationSlots,
                promotionSlots = promotionSlots,
            )
        )
    }

    private fun promotionSlotsFor(competitionCode: String, teamCount: Int): Int = when (competitionCode) {
        "LIGA2" -> when {
            teamCount >= 18 -> 3
            teamCount >= 10 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        "LIGA2B", "LIGA2B2" -> when {
            teamCount >= 20 -> 4
            teamCount >= 12 -> 3
            teamCount >= 6 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        "RFEF1A", "RFEF1B" -> if (teamCount >= 2) 1 else 0
        "RFEF2A", "RFEF2B", "RFEF2C", "RFEF2D" -> when {
            teamCount >= 18 -> 3
            teamCount >= 10 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        else -> 0
    }

    private fun PlayerEntity.toSimAttrs() = PlayerSimAttrs(
        playerId = id,
        playerName = nameShort.ifBlank { nameFull },
        ve = ve, re = re, ag = ag, ca = ca,
        remate = remate, regate = regate, pase = pase,
        tiro = tiro, entrada = entrada, portero = portero,
        estadoForma = estadoForma, moral = moral,
    )

    private fun serializeEvents(events: List<MatchEvent>): String {
        val array = JSONArray()
        events.forEach { event ->
            val obj = JSONObject()
                .put("minute", event.minute)
                .put("type", event.type.name)
                .put("teamId", event.teamId)
                .put("description", event.description)
            event.playerId?.let { obj.put("playerId", it) }
            event.playerName?.let { obj.put("playerName", it) }
            event.injuryWeeks?.let { obj.put("injuryWeeks", it) }
            array.put(obj)
        }
        return array.toString()
    }

    private suspend fun applyWeeklyFinance(
        matchday: Int,
        managerTeamId: Int,
        managerFixture: FixtureEntity?,
        managerResult: MatchResult?,
        updatedStandings: List<StandingEntity>,
    ) {
        if (managerTeamId <= 0 || managerFixture == null || managerResult == null) return
        val seasonState = seasonStateDao.get() ?: return
        val team = teamDao.byId(managerTeamId) ?: return
        val squad = playerDao.byTeamNow(managerTeamId)
        val wagesCostK = squad.sumOf { it.wageK }.coerceAtLeast(500)

        val sponsorIncomeK = (team.membersCount / 20).coerceIn(300, 7000) + team.prestige * 40
        val homeGame = managerFixture.homeTeamId == managerTeamId
        val attendanceIncomeK = if (homeGame) {
            (team.membersCount / 18).coerceIn(250, 5000)
        } else {
            (team.membersCount / 35).coerceIn(120, 1800)
        }
        val resultBonusK = when {
            homeGame && managerResult.homeGoals > managerResult.awayGoals -> 220
            !homeGame && managerResult.awayGoals > managerResult.homeGoals -> 260
            managerResult.homeGoals == managerResult.awayGoals -> 90
            else -> -120
        }

        val netK = sponsorIncomeK + attendanceIncomeK + resultBonusK - wagesCostK
        var newBudgetK = (team.budgetK + netK).coerceAtLeast(0)
        var presidentEvents: List<String> = emptyList()

        if (allowsPresidentDesk(seasonState.normalizedControlMode)) {
            val managerStanding = updatedStandings.firstOrNull { it.teamId == managerTeamId }
            val managerPosition = managerStanding?.position?.takeIf { it > 0 } ?: (updatedStandings.size + 1)
            val totalTeams = updatedStandings.size.coerceAtLeast(1)
            val relegationSlots = competitionDao.byCode(managerFixture.competitionCode)
                ?.relegationSlots
                ?.coerceAtLeast(1)
                ?: 3

            val isHomeManager = managerFixture.homeTeamId == managerTeamId
            val managerGoals = if (isHomeManager) managerResult.homeGoals else managerResult.awayGoals
            val rivalGoals = if (isHomeManager) managerResult.awayGoals else managerResult.homeGoals
            val goalDiff = managerGoals - rivalGoals

            val presidentUpdate = applyPresidentWeeklyEffects(
                state = seasonState,
                teamId = managerTeamId,
                matchday = matchday,
                wageBillK = wagesCostK,
                budgetK = newBudgetK,
                managerGoalDiff = goalDiff,
                managerPosition = managerPosition,
                totalTeams = totalTeams,
                relegationSlots = relegationSlots,
            )
            seasonStateDao.update(presidentUpdate.state)
            newBudgetK = presidentUpdate.budgetK
            presidentEvents = presidentUpdate.events

            if (presidentEvents.isNotEmpty()) {
                newsDao.insert(
                    NewsEntity(
                        date = java.time.LocalDate.now().toString(),
                        matchday = matchday,
                        category = "PRESIDENT",
                        titleEs = "Despacho del presidente (J$matchday)",
                        bodyEs = presidentEvents.joinToString(" "),
                        teamId = managerTeamId,
                    )
                )
            }
        }

        teamDao.update(team.copy(budgetK = newBudgetK))

        newsDao.insert(
            NewsEntity(
                date = java.time.LocalDate.now().toString(),
                matchday = matchday,
                category = "FINANCE",
                titleEs = "Caja semanal ${if (netK >= 0) "+" else ""}${netK}K",
                bodyEs = buildString {
                    append("Sponsor ${sponsorIncomeK}K + taquilla ${attendanceIncomeK}K + bonus ${resultBonusK}K - salarios ${wagesCostK}K.")
                    if (presidentEvents.isNotEmpty()) {
                        append(" Junta: ")
                        append(presidentEvents.joinToString(" "))
                    }
                },
                teamId = managerTeamId,
            )
        )
    }

    private data class PresidentWeeklyUpdate(
        val budgetK: Int,
        val state: SeasonStateEntity,
        val events: List<String>,
    )

    private suspend fun applyPresidentWeeklyEffects(
        state: SeasonStateEntity,
        teamId: Int,
        matchday: Int,
        wageBillK: Int,
        budgetK: Int,
        managerGoalDiff: Int,
        managerPosition: Int,
        totalTeams: Int,
        relegationSlots: Int,
    ): PresidentWeeklyUpdate {
        val rngSeed = state.season.hashCode().toLong() xor
            (matchday.toLong() * 65537L) xor
            (teamId.toLong() * 131L) xor
            0x50EL
        val rng = kotlin.random.Random(rngSeed)

        var pressure = state.presidentPressure.coerceIn(0, 12)
        var ownership = state.normalizedPresidentOwnership
        var capMode = state.normalizedPresidentSalaryCapMode
        var capK = state.presidentSalaryCapK.coerceAtLeast(0)
        var nextReview = state.presidentNextReviewMatchday.coerceAtLeast(1)
        var lastReview = state.presidentLastReviewMatchday.coerceAtLeast(0)
        var lastCapPenalty = state.presidentLastCapPenaltyMatchday
        var budget = budgetK.coerceAtLeast(0)
        val events = mutableListOf<String>()

        if (capK <= 0) {
            val factor = presidentSalaryCapFactor(capMode)
            capK = maxOf((wageBillK * factor).toInt(), wageBillK + 1)
        } else {
            capK = maxOf(capK, wageBillK + 1)
        }

        pressure += when {
            managerGoalDiff >= 2 -> -1
            managerGoalDiff <= -2 -> 2
            managerGoalDiff == -1 -> 1
            else -> 0
        }
        if (managerPosition > totalTeams - relegationSlots) {
            pressure += 1
        } else if (managerPosition <= maxOf(3, totalTeams / 4)) {
            pressure -= 1
        }
        pressure = pressure.coerceIn(0, 12)

        val capCrisis = wageBillK > capK
        val relegationAlert = managerPosition > totalTeams - relegationSlots && matchday >= maxOf(7, totalTeams / 4)
        val highPressure = pressure >= 9
        val reviewDue = matchday >= maxOf(1, nextReview)
        val reviewNow = reviewDue || capCrisis || relegationAlert || highPressure

        if (reviewNow) {
            when (ownership) {
                PRESIDENT_OWNERSHIP_PRIVATE -> {
                    if (pressure >= 8 && rng.nextDouble() < 0.35) {
                        val injection = rng.nextInt(2_000, 5_001)
                        budget += injection
                        pressure = (pressure - 1).coerceAtLeast(0)
                        events += "Consejo privado aprueba ampliacion de caja (+${injection}K)."
                    } else if (pressure <= 2 && rng.nextDouble() < 0.22) {
                        val payout = rng.nextInt(800, 2_201)
                        budget = (budget - payout).coerceAtLeast(0)
                        events += "Consejo privado extrae dividendos (${payout}K)."
                    }
                }

                PRESIDENT_OWNERSHIP_STATE -> {
                    if ((matchday % 6 == 0 && rng.nextDouble() < 0.70) || rng.nextDouble() < 0.20) {
                        val injection = rng.nextInt(1_800, 4_501)
                        budget += injection
                        capK = maxOf((capK * 1.01).toInt(), wageBillK + 1)
                        events += "Aportacion institucional extraordinaria (+${injection}K)."
                    }
                }

                PRESIDENT_OWNERSHIP_LISTED -> {
                    if (managerGoalDiff > 0 && rng.nextDouble() < 0.65) {
                        val delta = rng.nextInt(300, 1_201)
                        budget += delta
                        events += "La cotizacion sube tras la victoria (+${delta}K)."
                    } else if (managerGoalDiff < 0 && rng.nextDouble() < 0.70) {
                        val delta = rng.nextInt(300, 1_401)
                        budget = (budget - delta).coerceAtLeast(0)
                        events += "La cotizacion cae tras la derrota (${delta}K)."
                    }
                }
            }
        }

        if (capCrisis && (matchday - lastCapPenalty >= 4 || reviewNow)) {
            val overflowK = (wageBillK - capK).coerceAtLeast(0)
            val penalty = minOf(
                overflowK * 18,
                maxOf(500, (budget * 12) / 100),
            )
            budget = (budget - penalty).coerceAtLeast(0)
            pressure = (pressure + 2).coerceAtMost(12)
            lastCapPenalty = matchday
            events += "Incumplimiento de tope salarial: multa financiera (${penalty}K)."
        } else if (capCrisis) {
            pressure = (pressure + 1).coerceAtMost(12)
        }

        if (pressure >= 9 && capMode != PRESIDENT_CAP_STRICT && reviewNow) {
            capMode = PRESIDENT_CAP_STRICT
            capK = maxOf((wageBillK * presidentSalaryCapFactor(capMode)).toInt(), wageBillK + 1)
            events += "La junta impone tope salarial estricto por alta presion."
        } else if (pressure <= 2 && capMode == PRESIDENT_CAP_STRICT && reviewNow && matchday >= 8) {
            capMode = PRESIDENT_CAP_BALANCED
            capK = maxOf((wageBillK * presidentSalaryCapFactor(capMode)).toInt(), wageBillK + 1)
            events += "La junta relaja el tope salarial a modo equilibrado."
        }

        if (reviewNow) {
            lastReview = matchday
            val minGap = if (pressure >= 8) 2 else 3
            val maxGap = if (pressure >= 8) 4 else 6
            nextReview = matchday + rng.nextInt(minGap, maxGap + 1)
        } else {
            nextReview = maxOf(nextReview, matchday + 1)
        }

        ownership = when (ownership) {
            PRESIDENT_OWNERSHIP_PRIVATE,
            PRESIDENT_OWNERSHIP_STATE,
            PRESIDENT_OWNERSHIP_LISTED -> ownership
            else -> PRESIDENT_OWNERSHIP_SOCIOS
        }

        return PresidentWeeklyUpdate(
            budgetK = budget.coerceAtLeast(0),
            state = state.copy(
                presidentOwnership = ownership,
                presidentSalaryCapMode = capMode,
                presidentSalaryCapK = maxOf(capK, wageBillK + 1),
                presidentPressure = pressure.coerceIn(0, 12),
                presidentLastReviewMatchday = lastReview,
                presidentNextReviewMatchday = nextReview.coerceAtLeast(matchday + 1),
                presidentLastCapPenaltyMatchday = lastCapPenalty,
            ),
            events = events,
        )
    }

    private suspend fun publishMatchNews(
        fixture: FixtureEntity,
        result: MatchResult,
        matchday: Int,
        managerTeamId: Int,
    ) {
        val homeName = teamDao.byId(fixture.homeTeamId)?.nameShort ?: "Local"
        val awayName = teamDao.byId(fixture.awayTeamId)?.nameShort ?: "Visitante"
        val redCards = result.events.count { it.type == EventType.RED_CARD }
        val injuries = result.events.count { it.type == EventType.INJURY }
        val varDisallowed = result.varDisallowedHome + result.varDisallowedAway
        val teamId = when {
            fixture.homeTeamId == managerTeamId -> managerTeamId
            fixture.awayTeamId == managerTeamId -> managerTeamId
            else -> -1
        }
        val details = buildString {
            append("$homeName ${result.homeGoals}-${result.awayGoals} $awayName.")
            if (varDisallowed > 0) append(" VAR: $varDisallowed gol(es) anulados.")
            if (redCards > 0) append(" Rojas: $redCards.")
            if (injuries > 0) append(" Lesiones: $injuries.")
            append(" +${result.addedTimeSecondHalf} de añadido final.")
        }
        newsDao.insert(
            NewsEntity(
                date = java.time.LocalDate.now().toString(),
                matchday = matchday,
                category = "RESULT",
                titleEs = "J$matchday: $homeName ${result.homeGoals}-${result.awayGoals} $awayName",
                bodyEs = details,
                teamId = teamId,
            )
        )
    }
}
