package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.economy.TransferMarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MarketTab { FREE_AGENTS, CLUB_PLAYERS, MY_SQUAD }

data class MarketUiState(
    val freeAgents: List<PlayerEntity> = emptyList(),
    val clubPlayers: List<PlayerEntity> = emptyList(),
    val squadPlayers: List<PlayerEntity> = emptyList(),
    val teamNamesById: Map<Int, String> = emptyMap(),
    val searchQuery: String = "",
    val filterPosition: String = "ALL",
    val tab: MarketTab = MarketTab.FREE_AGENTS,
    val windowOpen: Boolean = false,
    val windowName: String = "Mercado cerrado",
    val message: String? = null,
    val loading: Boolean = true,
    val managerTeamId: Int = -1,
    val budgetK: Int = 0,
)

@HiltViewModel
class TransferMarketViewModel @Inject constructor(
    private val repo: TransferMarketRepository,
    private val seasonStateDao: SeasonStateDao,
    private val teamDao: TeamDao,
) : ViewModel() {

    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    init {
        observeSeasonState()
    }

    fun setSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setPositionFilter(pos: String) {
        _state.update { it.copy(filterPosition = pos) }
    }

    fun setTab(tab: MarketTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun visiblePlayers(): List<PlayerEntity> {
        val s = _state.value
        val base = when (s.tab) {
            MarketTab.FREE_AGENTS -> s.freeAgents
            MarketTab.CLUB_PLAYERS -> s.clubPlayers
            MarketTab.MY_SQUAD -> s.squadPlayers
        }
        return base.filter { player ->
            (s.filterPosition == "ALL" || player.position == s.filterPosition) &&
                (s.searchQuery.isBlank() || player.nameShort.contains(s.searchQuery, ignoreCase = true))
        }
    }

    fun originTeamName(player: PlayerEntity): String {
        val teamId = player.teamSlotId ?: return "Libre"
        return _state.value.teamNamesById[teamId] ?: "Equipo $teamId"
    }

    fun makeOffer(playerPid: Int, amountK: Int) {
        val managerTeamId = _state.value.managerTeamId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val result = repo.makeOffer(playerPid, managerTeamId, amountK)
            _state.update { it.copy(loading = false, message = result.getOrElse { e -> e.message }) }
            refreshPools()
        }
    }

    fun sell(playerPid: Int, priceK: Int) {
        val managerTeamId = _state.value.managerTeamId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val result = repo.sellPlayer(playerPid, managerTeamId, priceK)
            _state.update { it.copy(loading = false, message = result.getOrElse { e -> e.message }) }
            refreshPools()
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun observeSeasonState() {
        viewModelScope.launch {
            seasonStateDao.observe().filterNotNull().collect { ss ->
                val team = teamDao.byId(ss.managerTeamId)
                _state.update {
                    it.copy(
                        managerTeamId = ss.managerTeamId,
                        windowOpen = ss.transferWindowOpen,
                        windowName = if (ss.transferWindowOpen) "Ventana abierta" else "Mercado cerrado",
                        budgetK = team?.budgetK ?: 0,
                    )
                }
                refreshPools()
            }
        }
    }

    private suspend fun refreshPools() {
        val managerTeamId = _state.value.managerTeamId
        if (managerTeamId <= 0) {
            _state.update { it.copy(freeAgents = emptyList(), clubPlayers = emptyList(), squadPlayers = emptyList(), loading = false) }
            return
        }
        val freeAgents = repo.freeAgentsNow()
        val clubPlayers = repo.transferTargetsNow(managerTeamId)
        val squad = repo.squadNow(managerTeamId)
        val teamIds = (clubPlayers.mapNotNull { it.teamSlotId } + squad.mapNotNull { it.teamSlotId }).toSet().toList()
        val teamNames = if (teamIds.isEmpty()) {
            emptyMap()
        } else {
            teamDao.byIds(teamIds).associate { it.slotId to it.nameShort }
        }
        val budget = teamDao.byId(managerTeamId)?.budgetK ?: 0
        _state.update {
            it.copy(
                freeAgents = freeAgents,
                clubPlayers = clubPlayers,
                squadPlayers = squad,
                teamNamesById = teamNames,
                budgetK = budget,
                loading = false,
            )
        }
    }
}
