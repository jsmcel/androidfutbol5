package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.economy.TransferMarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketUiState(
    val freeAgents: List<PlayerEntity> = emptyList(),
    val searchQuery: String = "",
    val filterPosition: String = "ALL",
    val windowOpen: Boolean = false,
    val windowName: String = "Mercado cerrado",
    val message: String? = null,
    val loading: Boolean = false,
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
        viewModelScope.launch {
            seasonStateDao.observe().filterNotNull().collect { ss ->
                val team = teamDao.byId(ss.managerTeamId)
                _state.update { it.copy(
                    managerTeamId = ss.managerTeamId,
                    windowOpen = ss.transferWindowOpen,
                    windowName = if (ss.transferWindowOpen) "Ventana abierta" else "Mercado cerrado",
                    budgetK = team?.budgetK ?: 0,
                )}
            }
        }
        viewModelScope.launch {
            repo.freeAgents().collect { agents ->
                _state.update { it.copy(freeAgents = agents) }
            }
        }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setPositionFilter(pos: String) {
        _state.update { it.copy(filterPosition = pos) }
    }

    /** Lista filtrada de agentes libres según búsqueda y posición */
    val filteredAgents: StateFlow<List<PlayerEntity>> = state.map { s ->
        s.freeAgents
            .filter { p ->
                (s.filterPosition == "ALL" || p.position == s.filterPosition) &&
                (s.searchQuery.isBlank() || p.nameShort.contains(s.searchQuery, ignoreCase = true))
            }
            .sortedByDescending { it.ca }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun makeOffer(playerPid: Int, amountK: Int) {
        val managerTeamId = _state.value.managerTeamId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val result = repo.makeOffer(playerPid, managerTeamId, amountK)
            _state.update { it.copy(
                loading = false,
                message = result.getOrElse { it.message },
            )}
        }
    }

    fun sell(playerPid: Int, priceK: Int) {
        val managerTeamId = _state.value.managerTeamId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val result = repo.sellPlayer(playerPid, managerTeamId, priceK)
            _state.update { it.copy(
                loading = false,
                message = result.getOrElse { it.message },
            )}
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
