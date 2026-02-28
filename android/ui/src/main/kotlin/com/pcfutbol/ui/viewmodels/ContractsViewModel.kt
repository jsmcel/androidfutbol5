package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.economy.ContractRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContractsUiState(
    val loading: Boolean = true,
    val managerTeamId: Int = -1,
    val teamName: String = "",
    val seasonYear: Int = 2025,
    val budgetK: Int = 0,
    val contracts: List<PlayerEntity> = emptyList(),
    val expiringOnly: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ContractsViewModel @Inject constructor(
    private val seasonStateDao: SeasonStateDao,
    private val teamDao: TeamDao,
    private val contractRepository: ContractRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContractsUiState())
    val uiState: StateFlow<ContractsUiState> = _uiState.asStateFlow()

    init {
        observeTeamContext()
    }

    fun setExpiringOnly(enabled: Boolean) {
        _uiState.update { it.copy(expiringOnly = enabled) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun visibleContracts(): List<PlayerEntity> {
        val s = _uiState.value
        val ordered = s.contracts.sortedWith(
            compareBy<PlayerEntity> { it.contractEndYear }
                .thenByDescending { it.ca }
                .thenBy { it.nameShort }
        )
        if (!s.expiringOnly) return ordered
        return ordered.filter { it.contractEndYear <= s.seasonYear + 1 }
    }

    fun renewContract(
        playerId: Int,
        newEndYear: Int,
        newWageK: Int,
        newClauseK: Int,
    ) {
        val teamId = _uiState.value.managerTeamId
        if (teamId <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val result = contractRepository.renewContract(
                playerId = playerId,
                managerTeamId = teamId,
                newContractEndYear = newEndYear,
                newWageK = newWageK,
                newClauseK = newClauseK,
            )
            _uiState.update {
                it.copy(
                    loading = false,
                    message = result.getOrElse { e -> e.message },
                )
            }
            refreshContracts()
        }
    }

    fun rescindContract(playerId: Int) {
        val teamId = _uiState.value.managerTeamId
        if (teamId <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val result = contractRepository.rescindContract(
                playerId = playerId,
                managerTeamId = teamId,
            )
            _uiState.update {
                it.copy(
                    loading = false,
                    message = result.getOrElse { e -> e.message },
                )
            }
            refreshContracts()
        }
    }

    private fun observeTeamContext() {
        viewModelScope.launch {
            seasonStateDao.observe().filterNotNull().collect { state ->
                val teamId = state.managerTeamId
                val team = teamDao.byId(teamId)
                _uiState.update {
                    it.copy(
                        managerTeamId = teamId,
                        teamName = team?.nameShort ?: "Equipo",
                        seasonYear = parseSeasonStartYear(state.season),
                        budgetK = team?.budgetK ?: 0,
                    )
                }
                refreshContracts()
            }
        }
    }

    private suspend fun refreshContracts() {
        val teamId = _uiState.value.managerTeamId
        if (teamId <= 0) {
            _uiState.update { it.copy(contracts = emptyList(), loading = false) }
            return
        }
        val team = teamDao.byId(teamId)
        val players = contractRepository.squadContracts(teamId)
        _uiState.update {
            it.copy(
                contracts = players,
                budgetK = team?.budgetK ?: it.budgetK,
                loading = false,
            )
        }
    }

    private fun parseSeasonStartYear(season: String): Int =
        season.substringBefore("-").toIntOrNull() ?: 2025
}
