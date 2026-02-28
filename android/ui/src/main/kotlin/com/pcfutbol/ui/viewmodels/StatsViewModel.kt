package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.FixtureDao
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.managerLeague
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

data class GoalScorer(
    val playerName: String,
    val teamName: String,
    val goals: Int,
)

data class StatsUiState(
    val competitionCode: String = CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE,
    val topScorers: List<GoalScorer> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val fixtureDao: FixtureDao,
    private val teamDao: TeamDao,
    private val seasonStateDao: SeasonStateDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        load()
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
                    parseGoalEvents(fixture.eventsJson).forEach { event ->
                        if (event.playerName.isBlank() || event.teamId <= 0) return@forEach
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

                competitionCode to top
            }.onSuccess { (comp, scorers) ->
                _uiState.value = StatsUiState(
                    competitionCode = comp,
                    topScorers = scorers,
                    loading = false,
                )
            }.onFailure { throwable ->
                _uiState.value = StatsUiState(
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
}
