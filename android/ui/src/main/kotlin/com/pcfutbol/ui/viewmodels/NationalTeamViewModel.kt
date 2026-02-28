package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.NationalSquadDao
import com.pcfutbol.core.data.db.NationalSquadEntity
import com.pcfutbol.core.data.db.NewsDao
import com.pcfutbol.core.data.db.NewsEntity
import com.pcfutbol.core.data.db.PROMANAGER_MODE
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.SeasonStateEntity
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.exp
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

data class NationalSquadPlayerUi(
    val position: Int,
    val playerId: Int,
    val name: String,
    val role: String,
    val teamName: String,
    val ca: Int,
)

data class NationalTeamUiState(
    val season: String = "2025-26",
    val currentMatchday: Int = 1,
    val fifaRanking: Int = 8,
    val loading: Boolean = true,
    val windowOpen: Boolean = false,
    val managerIsCoach: Boolean = true,
    val squad: List<NationalSquadPlayerUi> = emptyList(),
    val tournamentName: String = "MUNDIAL",
    val tournamentStage: String = "CUARTOS",
    val nextOpponent: String = "-",
    val lastResult: String? = null,
    val infoMessage: String? = null,
    val eliminated: Boolean = false,
    val champion: Boolean = false,
    val canSimulate: Boolean = false,
)

@HiltViewModel
class NationalTeamViewModel @Inject constructor(
    private val seasonStateDao: SeasonStateDao,
    private val playerDao: PlayerDao,
    private val teamDao: TeamDao,
    private val nationalSquadDao: NationalSquadDao,
    private val newsDao: NewsDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NationalTeamUiState())
    val uiState: StateFlow<NationalTeamUiState> = _uiState.asStateFlow()

    private var currentSeason: String = ""
    private var currentWindow: Int = 0
    private var tournamentName: String = "MUNDIAL"
    private var tournamentOpponents: List<String> = emptyList()
    private var opponentStrength: Map<String, Double> = emptyMap()
    private var roundIndex: Int = 0
    private var autoSimulatedWindowKey: String? = null

    init {
        observeSeasonState()
    }

    fun simulateNationalMatch() {
        viewModelScope.launch {
            simulateNationalMatchInternal(autoSimulated = false)
        }
    }

    private fun observeSeasonState() {
        viewModelScope.launch {
            seasonStateDao.observe().filterNotNull().collect { seasonState ->
                syncFromSeasonState(seasonState)
            }
        }
    }

    private suspend fun syncFromSeasonState(seasonState: SeasonStateEntity) {
        val matchday = seasonState.currentMatchday.coerceAtLeast(1)
        val window = windowIndex(matchday)
        val managerIsCoach = !seasonState.managerMode.equals(PROMANAGER_MODE, ignoreCase = true)
        val seasonChanged = seasonState.season != currentSeason
        val enteringWindow = (matchday == 13 || matchday == 26) && window != currentWindow

        if (seasonChanged || enteringWindow) {
            currentSeason = seasonState.season
            currentWindow = window
            initializeTournament(seasonState.season, window)
            refreshNationalSquad(seasonState.season, matchday)
        } else {
            currentSeason = seasonState.season
            currentWindow = window
            ensureSquadExists(seasonState.season, matchday)
        }

        val squadPlayers = nationalSquadDao.squadPlayers(seasonState.season)
        val squadUi = mapSquadToUi(squadPlayers)
        val nextOpponent = tournamentOpponents.getOrNull(roundIndex) ?: "-"
        val stage = if (_uiState.value.champion || _uiState.value.eliminated) {
            "FINALIZADO"
        } else {
            stageLabel(roundIndex)
        }

        _uiState.value = _uiState.value.copy(
            season = seasonState.season,
            currentMatchday = matchday,
            loading = false,
            windowOpen = window > 0,
            managerIsCoach = managerIsCoach,
            squad = squadUi,
            tournamentName = tournamentName,
            tournamentStage = stage,
            nextOpponent = nextOpponent,
            canSimulate = canSimulate(windowOpen = window > 0, managerIsCoach = managerIsCoach, hasSquad = squadUi.isNotEmpty()),
        )

        if (!managerIsCoach && window > 0 && squadUi.isNotEmpty()) {
            val key = "${seasonState.season}:$window"
            if (autoSimulatedWindowKey != key) {
                autoSimulatedWindowKey = key
                simulateNationalMatchInternal(autoSimulated = true)
            }
        }
    }

    private suspend fun simulateNationalMatchInternal(autoSimulated: Boolean) {
        val state = _uiState.value
        if (roundIndex >= KNOCKOUT_STAGES.size) return
        if (state.eliminated || state.champion) return
        if (!autoSimulated && !state.canSimulate) return

        val opponent = tournamentOpponents.getOrNull(roundIndex) ?: return
        val squadPlayers = nationalSquadDao.squadPlayers(state.season)
        if (squadPlayers.isEmpty()) return

        val random = Random(System.currentTimeMillis())
        val spainStrength = squadPlayers.map { rankingScore(it) }.average().coerceAtLeast(1.0)
        val rivalStrength = opponentStrength[opponent] ?: 76.0

        val goalsEsp = poissonGoals(lambda = (spainStrength / 100.0) * 2.15, random = random)
        val goalsRival = poissonGoals(lambda = (rivalStrength / 100.0) * 1.95, random = random)

        var spainWins = goalsEsp > goalsRival
        var penaltiesInfo = ""
        if (goalsEsp == goalsRival) {
            var penEsp = random.nextInt(3, 6) + if (spainStrength >= rivalStrength) 1 else 0
            var penRival = random.nextInt(3, 6) + if (rivalStrength > spainStrength) 1 else 0
            if (penEsp == penRival) {
                if (random.nextBoolean()) penEsp++ else penRival++
            }
            spainWins = penEsp > penRival
            penaltiesInfo = " (pen $penEsp-$penRival)"
        }

        val stage = stageLabel(roundIndex)
        val resultLine = "Espana $goalsEsp-$goalsRival $opponent$penaltiesInfo [$stage]"

        if (spainWins) {
            roundIndex += 1
            val champion = roundIndex >= KNOCKOUT_STAGES.size
            val message = if (champion) {
                "Espana campeona de $tournamentName."
            } else {
                "Espana avanza a ${stageLabel(roundIndex)}."
            }
            publishNationalNews(title = "Seleccion Espanola", body = resultLine)
            _uiState.value = _uiState.value.copy(
                lastResult = resultLine,
                infoMessage = message,
                champion = champion,
                eliminated = false,
                tournamentStage = if (champion) "CAMPEON" else stageLabel(roundIndex),
                nextOpponent = if (champion) "-" else tournamentOpponents.getOrNull(roundIndex) ?: "-",
                canSimulate = canSimulate(
                    windowOpen = _uiState.value.windowOpen,
                    managerIsCoach = _uiState.value.managerIsCoach,
                    hasSquad = _uiState.value.squad.isNotEmpty(),
                ),
            )
            return
        }

        publishNationalNews(title = "Seleccion Espanola", body = resultLine)
        _uiState.value = _uiState.value.copy(
            lastResult = resultLine,
            infoMessage = "Espana eliminada en $stage.",
            eliminated = true,
            champion = false,
            tournamentStage = "ELIMINADO",
            canSimulate = false,
        )
    }

    private suspend fun refreshNationalSquad(season: String, matchday: Int) {
        val topPlayers = playerDao.topNationalPlayers(
            countryCode = "ES",
            leagueCode = CompetitionDefinitions.LIGA1,
            limit = MAX_SQUAD_SIZE,
        )
        val entries = topPlayers.map { player ->
            NationalSquadEntity(
                season = season,
                playerId = player.id,
                score = rankingScore(player),
                updatedAtMatchday = matchday,
            )
        }
        nationalSquadDao.replaceSeasonSquad(season, entries)
    }

    private suspend fun ensureSquadExists(season: String, matchday: Int) {
        val current = nationalSquadDao.squadPlayers(season, MAX_SQUAD_SIZE)
        if (current.isEmpty()) refreshNationalSquad(season, matchday)
    }

    private suspend fun mapSquadToUi(players: List<PlayerEntity>): List<NationalSquadPlayerUi> {
        val teamNames = players
            .mapNotNull { player ->
                val teamId = player.teamSlotId ?: return@mapNotNull null
                teamId to (teamDao.byId(teamId)?.nameShort ?: "Club $teamId")
            }
            .toMap()

        return players.mapIndexed { index, player ->
            NationalSquadPlayerUi(
                position = index + 1,
                playerId = player.id,
                name = player.nameShort.ifBlank { player.nameFull },
                role = player.position,
                teamName = player.teamSlotId?.let { teamNames[it] } ?: "Sin club",
                ca = player.ca,
            )
        }
    }

    private suspend fun publishNationalNews(title: String, body: String) {
        val matchday = seasonStateDao.get()?.currentMatchday ?: 1
        newsDao.insert(
            NewsEntity(
                date = LocalDate.now().toString(),
                matchday = matchday,
                category = "RESULT",
                titleEs = title,
                bodyEs = body,
                teamId = -1,
            )
        )
    }

    private fun initializeTournament(season: String, window: Int) {
        val setup = tournamentSetup(season, window)
        tournamentName = setup.name
        tournamentOpponents = setup.opponents
        opponentStrength = setup.strength
        roundIndex = 0
        autoSimulatedWindowKey = null
        _uiState.value = _uiState.value.copy(
            champion = false,
            eliminated = false,
            lastResult = null,
            infoMessage = "Convocatoria actualizada para ventana internacional.",
            tournamentName = setup.name,
            tournamentStage = stageLabel(0),
            nextOpponent = setup.opponents.firstOrNull() ?: "-",
        )
    }

    private fun tournamentSetup(season: String, window: Int): TournamentSetup {
        val seasonYear = season.substringBefore("-").toIntOrNull() ?: 2025
        val euro = seasonYear % 2 == 0
        val name = if (euro) "EUROCOPA" else "MUNDIAL"
        val rivals = if (euro) EURO_RIVALS else WORLD_RIVALS
        val random = Random((seasonYear * 31) + window)
        val opponents = rivals.shuffled(random).take(3)
        val strength = rivals.associateWith { rival ->
            when (rival) {
                "Brasil", "Francia", "Alemania" -> 86.0
                "Argentina", "Inglaterra" -> 84.0
                "Italia", "Portugal" -> 82.0
                "Paises Bajos", "Croacia" -> 80.0
                else -> 78.0
            }
        }
        return TournamentSetup(name = name, opponents = opponents, strength = strength)
    }

    private fun rankingScore(player: PlayerEntity): Double {
        val media = (
            player.ve + player.re + player.ag + player.remate + player.regate +
                player.pase + player.tiro + player.entrada + player.portero
            ) / 9.0
        return player.ca + media
    }

    private fun stageLabel(stageIndex: Int): String =
        KNOCKOUT_STAGES.getOrElse(stageIndex) { "FINALIZADO" }

    private fun canSimulate(windowOpen: Boolean, managerIsCoach: Boolean, hasSquad: Boolean): Boolean =
        windowOpen && managerIsCoach && hasSquad && !uiState.value.eliminated && !uiState.value.champion && roundIndex < KNOCKOUT_STAGES.size

    private fun windowIndex(matchday: Int): Int = when (matchday) {
        in 13..14 -> 1
        in 26..27 -> 2
        else -> 0
    }

    private fun poissonGoals(lambda: Double, random: Random): Int {
        val floor = exp(-lambda.coerceAtLeast(0.1))
        var p = 1.0
        var goals = -1
        while (p > floor) {
            p *= random.nextDouble()
            goals += 1
        }
        return goals.coerceIn(0, 6)
    }

    private data class TournamentSetup(
        val name: String,
        val opponents: List<String>,
        val strength: Map<String, Double>,
    )

    private companion object {
        const val MAX_SQUAD_SIZE = 23
        val KNOCKOUT_STAGES = listOf("CUARTOS", "SEMIFINAL", "FINAL")
        val EURO_RIVALS = listOf(
            "Alemania",
            "Francia",
            "Inglaterra",
            "Italia",
            "Portugal",
            "Paises Bajos",
            "Croacia",
        )
        val WORLD_RIVALS = listOf(
            "Alemania",
            "Francia",
            "Brasil",
            "Argentina",
            "Inglaterra",
            "Italia",
            "Portugal",
        )
    }
}
