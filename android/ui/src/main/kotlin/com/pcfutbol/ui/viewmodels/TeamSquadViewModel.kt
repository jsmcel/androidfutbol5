package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeamSquadUiState(
    val players: List<PlayerEntity> = emptyList(),
    val filter: String = "ALL",
    val loading: Boolean = true,
    val teamName: String = "",
    val budgetK: Int = 0,
)

@HiltViewModel
class TeamSquadViewModel @Inject constructor(
    private val playerDao: PlayerDao,
    private val seasonStateDao: SeasonStateDao,
    private val teamDao: TeamDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamSquadUiState())
    val uiState: StateFlow<TeamSquadUiState> = _uiState.asStateFlow()

    private var allPlayers: List<PlayerEntity> = emptyList()

    init {
        viewModelScope.launch {
            val teamId = seasonStateDao.observe().filterNotNull().first().managerTeamId
            val team = teamDao.byId(teamId)
            val resolvedName = team?.nameShort ?: "Mi Equipo"
            val resolvedBudget = team?.budgetK ?: 0
            playerDao.byTeam(teamId).collectLatest { players ->
                allPlayers = players
                val activeFilter = _uiState.value.filter
                _uiState.value = _uiState.value.copy(
                    players = applyFilter(players, activeFilter),
                    loading = false,
                    teamName = resolvedName,
                    budgetK = resolvedBudget,
                )
            }
        }
    }

    fun setFilter(pos: String) {
        val normalized = pos.uppercase().takeIf { it in SUPPORTED_FILTERS } ?: "ALL"
        _uiState.value = _uiState.value.copy(
            filter = normalized,
            players = applyFilter(allPlayers, normalized),
        )
    }

    private fun applyFilter(players: List<PlayerEntity>, filter: String): List<PlayerEntity> {
        if (filter == "ALL") return players
        return players.filter { it.position == filter }
    }

    private companion object {
        val SUPPORTED_FILTERS = setOf("ALL", "PO", "DF", "MC", "DC")
    }
}
