package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.competition.CompetitionRepository
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.isProManagerMode
import com.pcfutbol.economy.EndOfSeasonUseCase
import com.pcfutbol.economy.SeasonSummary
import com.pcfutbol.promanager.ProManagerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EndOfSeasonUiState(
    val summary: SeasonSummary? = null,
    val loading: Boolean = true,
    val applied: Boolean = false,
    val nextSeasonReady: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class EndOfSeasonViewModel @Inject constructor(
    private val endOfSeasonUseCase: EndOfSeasonUseCase,
    private val competitionRepository: CompetitionRepository,
    private val proManagerRepository: ProManagerRepository,
    private val seasonStateDao: SeasonStateDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EndOfSeasonUiState())
    val uiState: StateFlow<EndOfSeasonUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { endOfSeasonUseCase.computeSummary() }
                .onSuccess { summary ->
                    _uiState.value = EndOfSeasonUiState(summary = summary, loading = false)
                }
                .onFailure { e ->
                    _uiState.value = EndOfSeasonUiState(loading = false, error = e.message)
                }
        }
    }

    /** Aplica descensos/ascensos y genera noticias (llamar una sola vez). */
    fun applyEndOfSeason() {
        val summary = _uiState.value.summary ?: return
        viewModelScope.launch {
            runCatching { endOfSeasonUseCase.applyEndOfSeason(summary) }
                .onSuccess { _uiState.value = _uiState.value.copy(applied = true) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    /** Avanza al siguiente año y regenera calendarios. */
    fun startNextSeason() {
        viewModelScope.launch {
            runCatching {
                // Registrar resultado de temporada en ProManager (si aplica)
                val summary = _uiState.value.summary
                val state = seasonStateDao.get()
                if (summary != null && state?.isProManagerMode() == true && state.managerTeamId > 0) {
                    proManagerRepository.recordSeasonEnd(
                        managerId      = proManagerRepository.managerIdForTeam(state.managerTeamId),
                        teamId         = state.managerTeamId,
                        objectivesMet  = summary.objectiveMet,
                        finalPosition  = summary.managerPosition,
                    )
                }

                endOfSeasonUseCase.advanceToNextSeason()
                competitionRepository.setupAllLeagues()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(nextSeasonReady = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
