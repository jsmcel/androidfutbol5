package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.FixtureDao
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.managerLeague
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

enum class StatsView {
    TOP_SCORERS,
    TOP_PLAYERS,
    TEAM_STRENGTH,
}

data class GoalScorer(
    val playerName: String,
    val teamName: String,
    val goals: Int,
)

data class TopPlayer(
    val playerName: String,
    val teamName: String,
    val position: String,
    val score: Double,
)

data class TeamStrengthRow(
    val teamName: String,
    val competitionCode: String,
    val score: Double,
)

data class StatsUiState(
    val competitionCode: String = CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE,
    val selectedView: StatsView = StatsView.TOP_SCORERS,
    val topScorers: List<GoalScorer> = emptyList(),
    val topPlayers: List<TopPlayer> = emptyList(),
    val teamStrength: List<TeamStrengthRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val fixtureDao: FixtureDao,
    private val teamDao: TeamDao,
    private val seasonStateDao: SeasonStateDao,
    private val playerDao: PlayerDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun selectView(view: StatsView) {
        if (_uiState.value.selectedView == view) return
        _uiState.value = _uiState.value.copy(selectedView = view)
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                val competitionCode = resolveCompetition()
                val fixtures = fixtureDao.byCompetitionPlayed(competitionCode)
                val goalsByPlayer = linkedMapOf<Pair<String, Int>, Int>()

                fixtures.forEach { fixture ->
                    if (fixture.eventsJson.isBlank()) return@forEach
                    parseGoalEvents(fixture.eventsJson).forEach goalEventLoop@{ event ->
                        if (event.playerName.isBlank() || event.teamId <= 0) return@goalEventLoop
                        val key = event.playerName to event.teamId
                        goalsByPlayer[key] = (goalsByPlayer[key] ?: 0) + 1
                    }
                }

                val teamNameCache = goalsByPlayer.keys
                    .map { it.second }
                    .distinct()
                    .associateWith { teamId ->
                        teamDao.byId(teamId)?.nameShort ?: "Equipo $teamId"
                    }

                val top = goalsByPlayer.entries
                    .map { (key, goals) ->
                        GoalScorer(
                            playerName = key.first,
                            teamName = teamNameCache[key.second] ?: "Equipo ${key.second}",
                            goals = goals,
                        )
                    }
                    .sortedWith(
                        compareByDescending<GoalScorer> { it.goals }
                            .thenBy { it.playerName }
                    )
                    .take(20)

                val allPlayers = playerDao.allPlayers().first()
                val allTeams = teamDao.allTeams().first()

                val teamNameById = allTeams.associate { team ->
                    team.slotId to team.nameShort
                }
                val activePlayers = allPlayers.filter { player ->
                    player.teamSlotId != null && player.status == 0
                }

                val topPlayers = activePlayers
                    .map { player ->
                        TopPlayer(
                            playerName = player.nameShort.ifBlank { player.nameFull },
                            teamName = teamNameById[player.teamSlotId] ?: "Equipo ${player.teamSlotId}",
                            position = player.position,
                            score = qualityScore(player),
                        )
                    }
                    .sortedWith(
                        compareByDescending<TopPlayer> { it.score }
                            .thenBy { it.playerName }
                    )
                    .take(50)

                val playersByTeam = activePlayers.groupBy { it.teamSlotId!! }
                val strengthRows = allTeams
                    .mapNotNull { team ->
                        val teamPlayers = playersByTeam[team.slotId] ?: return@mapNotNull null
                        if (teamPlayers.isEmpty()) return@mapNotNull null
                        TeamStrengthRow(
                            teamName = team.nameShort,
                            competitionCode = team.competitionKey,
                            score = teamStrengthScore(teamPlayers),
                        )
                    }
                    .sortedWith(
                        compareByDescending<TeamStrengthRow> { it.score }
                            .thenBy { it.teamName }
                    )
                    .take(60)

                Triple(top, topPlayers, strengthRows) to competitionCode
            }.onSuccess { (stats, comp) ->
                val (scorers, topPlayers, teamStrength) = stats
                _uiState.value = StatsUiState(
                    competitionCode = comp,
                    topScorers = scorers,
                    topPlayers = topPlayers,
                    teamStrength = teamStrength,
                    selectedView = _uiState.value.selectedView,
                    loading = false,
                )
            }.onFailure { throwable ->
                _uiState.value = StatsUiState(
                    selectedView = _uiState.value.selectedView,
                    loading = false,
                    error = throwable.message ?: "No se pudieron cargar los goleadores",
                )
            }
        }
    }

    private suspend fun resolveCompetition(): String {
        val fallback = CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE
        val state = seasonStateDao.get() ?: return fallback
        if (state.managerTeamId > 0) {
            return teamDao.byId(state.managerTeamId)
                ?.competitionKey
                ?.takeIf { it.isNotBlank() }
                ?: fallback
        }
        return state.managerLeague.ifBlank { fallback }
    }

    private fun parseGoalEvents(eventsJson: String): List<GoalEvent> =
        runCatching {
            val array = JSONArray(eventsJson)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    if (item.optString("type") != "GOAL") continue
                    add(
                        GoalEvent(
                            playerName = item.optString("playerName"),
                            teamId = item.optInt("teamId", -1),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

    private data class GoalEvent(
        val playerName: String,
        val teamId: Int,
    )

    private fun qualityScore(player: PlayerEntity): Double {
        val media = (
            player.ve +
                player.re +
                player.ag +
                player.remate +
                player.regate +
                player.pase +
                player.tiro +
                player.entrada +
                player.portero
            ) / 9.0
        return player.ca + media
    }

    private fun teamStrengthScore(teamPlayers: List<PlayerEntity>): Double {
        val lineup = teamPlayers
            .sortedByDescending { it.ca }
            .take(11)
        if (lineup.isEmpty()) return 50.0

        val gk = lineup[0]
        val defenders = lineup.drop(1).take(4)
        val midfielders = lineup.drop(5).take(4)
        val forwards = lineup.drop(9).take(2)

        val gkScore = gkStrength(gk)
        val defScore = defenders.map { defStrength(it) }.average().takeIf { !it.isNaN() } ?: 50.0
        val midScore = midfielders.map { midStrength(it) }.average().takeIf { !it.isNaN() } ?: 50.0
        val fwdScore = forwards.map { fwdStrength(it) }.average().takeIf { !it.isNaN() } ?: 50.0
        val runtime = lineup.map { runtimeBonus(it) }.average().takeIf { !it.isNaN() } ?: 0.0

        val base = gkScore * 0.15 + defScore * 0.30 + midScore * 0.30 + fwdScore * 0.25
        return (base + runtime).coerceIn(10.0, 99.0)
    }

    private fun gkStrength(p: PlayerEntity): Double =
        p.portero * 0.6 + p.re * 0.2 + p.ca * 0.2 + formFactor(p)

    private fun defStrength(p: PlayerEntity): Double =
        p.entrada * 0.4 + p.ca * 0.3 + p.ve * 0.2 + p.re * 0.1 + formFactor(p)

    private fun midStrength(p: PlayerEntity): Double =
        p.pase * 0.35 + p.ca * 0.30 + p.re * 0.20 + p.tiro * 0.15 + formFactor(p)

    private fun fwdStrength(p: PlayerEntity): Double =
        p.remate * 0.40 + p.regate * 0.25 + p.ca * 0.20 + p.tiro * 0.15 + formFactor(p)

    private fun formFactor(p: PlayerEntity): Double =
        (p.estadoForma - 50) * 0.05

    private fun runtimeBonus(p: PlayerEntity): Double =
        (p.estadoForma - 50) * 0.02 + ((p.moral - 50) / 100.0) * 2.0
}
