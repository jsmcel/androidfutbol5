package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.CompetitionDao
import com.pcfutbol.core.data.db.MANAGER_MODE
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.TeamEntity
import com.pcfutbol.core.data.db.managerLeague
import com.pcfutbol.core.data.db.withManagerLeague
import com.pcfutbol.core.data.db.withManagerMode
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeagueOptionUi(
    val code: String,
    val name: String,
)

data class LeagueGroupUi(
    val title: String,
    val leagues: List<LeagueOptionUi>,
)

data class LigaSelectUiState(
    val currentMatchday: Int = 1,
    val managerTeamId: Int = -1,
    val managerTeamName: String = "",
    val managerBudgetK: Int = 0,
    val selectedLeague: String = CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE,
    val leagueGroups: List<LeagueGroupUi> = emptyList(),
    val phase: String = "SEASON",
    val transferWindowOpen: Boolean = false,
    val teamsForLeague: List<TeamEntity> = emptyList(),
)

@HiltViewModel
class LigaSelectViewModel @Inject constructor(
    private val seasonStateDao: SeasonStateDao,
    private val teamDao: TeamDao,
    private val competitionDao: CompetitionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LigaSelectUiState())
    val uiState: StateFlow<LigaSelectUiState> = _uiState.asStateFlow()

    init {
        observeSeasonState()
        observeCompetitions()
        observeTeamsForLeague()
    }

    fun selectLeague(code: String) {
        val normalized = code.trim().ifBlank { CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE }
        _uiState.value = _uiState.value.copy(selectedLeague = normalized)

        viewModelScope.launch {
            val state = seasonStateDao.get() ?: return@launch
            if (state.managerTeamId > 0) return@launch
            seasonStateDao.update(
                state
                    .withManagerMode(MANAGER_MODE)
                    .withManagerLeague(normalized)
            )
        }
    }

    fun selectTeam(teamId: Int) {
        viewModelScope.launch {
            val state = seasonStateDao.get() ?: return@launch
            seasonStateDao.update(
                state
                    .withManagerMode(MANAGER_MODE)
                    .copy(managerTeamId = teamId)
            )
        }
    }

    // ------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTeamsForLeague() {
        viewModelScope.launch {
            _uiState
                .map { it.selectedLeague to (it.managerTeamId <= 0) }
                .distinctUntilChanged()
                .flatMapLatest { (league, needsPicker) ->
                    if (needsPicker) teamDao.byCompetition(league)
                    else kotlinx.coroutines.flow.flowOf(emptyList())
                }
                .collect { teams ->
                    _uiState.value = _uiState.value.copy(teamsForLeague = teams)
                }
        }
    }

    private fun observeSeasonState() {
        viewModelScope.launch {
            seasonStateDao.observe().filterNotNull().collect { ss ->
                val managerTeam = teamDao.byId(ss.managerTeamId)
                val selectedLeague = managerTeam
                    ?.competitionKey
                    ?.takeIf { it.isNotBlank() }
                    ?: ss.managerLeague
                _uiState.value = _uiState.value.copy(
                    currentMatchday = ss.currentMatchday.coerceAtLeast(1),
                    managerTeamId = ss.managerTeamId,
                    managerTeamName = managerTeam?.nameShort ?: "",
                    managerBudgetK = managerTeam?.budgetK ?: 0,
                    selectedLeague = selectedLeague,
                    phase = ss.phase,
                    transferWindowOpen = ss.transferWindowOpen,
                )
            }
        }
    }

    private fun observeCompetitions() {
        viewModelScope.launch {
            competitionDao.all().collect { competitions ->
                val leagues = competitions
                    .filter { it.formatType == "LEAGUE" && it.teamCount > 1 }
                    .associateBy { it.code }
                if (leagues.isEmpty()) return@collect

                val groups = buildLeagueGroups(leagues)
                val selectedLeague = _uiState.value.selectedLeague
                    .takeIf { it in leagues.keys }
                    ?: groups.firstOrNull()?.leagues?.firstOrNull()?.code
                    ?: CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE
                _uiState.value = _uiState.value.copy(
                    selectedLeague = selectedLeague,
                    leagueGroups = groups,
                )
            }
        }
    }

    private fun buildLeagueGroups(
        leagueMap: Map<String, com.pcfutbol.core.data.db.CompetitionEntity>,
    ): List<LeagueGroupUi> =
        leagueGroupDefs.mapNotNull { group ->
            val leagues = group.codes.mapNotNull { code ->
                leagueMap[code]?.let { LeagueOptionUi(code = it.code, name = it.name) }
            }
            if (leagues.isEmpty()) null else LeagueGroupUi(group.title, leagues)
        }

    private data class LeagueGroupDef(val title: String, val codes: List<String>)

    private val leagueGroupDefs = listOf(
        LeagueGroupDef("EUROPA - ESPANA", listOf("LIGA1", "LIGA2", "LIGA2B", "LIGA2B2")),
        LeagueGroupDef("ESPANA - RFEF", listOf("RFEF1A", "RFEF1B", "RFEF2A", "RFEF2B", "RFEF2C", "RFEF2D")),
        LeagueGroupDef("EUROPA - INGLATERRA", listOf("PRML")),
        LeagueGroupDef("EUROPA - ITALIA", listOf("SERIA")),
        LeagueGroupDef("EUROPA - FRANCIA", listOf("LIG1")),
        LeagueGroupDef("EUROPA - ALEMANIA", listOf("BUN1")),
        LeagueGroupDef("EUROPA - PAISES BAJOS", listOf("ERED")),
        LeagueGroupDef("EUROPA - PORTUGAL", listOf("PRIM")),
        LeagueGroupDef("EUROPA - BELGICA", listOf("BELGA")),
        LeagueGroupDef("EUROPA - TURQUIA", listOf("SUPERL")),
        LeagueGroupDef("EUROPA - ESCOCIA", listOf("SCOT")),
        LeagueGroupDef("EUROPA - RUSIA", listOf("RPL")),
        LeagueGroupDef("EUROPA - DINAMARCA", listOf("DSL")),
        LeagueGroupDef("EUROPA - POLONIA", listOf("EKSTR")),
        LeagueGroupDef("EUROPA - AUSTRIA", listOf("ABUND")),
        LeagueGroupDef("AMERICA - ARGENTINA", listOf("ARGPD")),
        LeagueGroupDef("AMERICA - BRASIL", listOf("BRASEA")),
        LeagueGroupDef("AMERICA - MEXICO", listOf("LIGAMX")),
        LeagueGroupDef("ASIA - ARABIA SAUDITA", listOf("SPL")),
    )
}
