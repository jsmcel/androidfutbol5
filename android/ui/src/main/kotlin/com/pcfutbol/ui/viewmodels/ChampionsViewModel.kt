package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.competition.ChampionsRepository
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import com.pcfutbol.core.data.db.FixtureEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChampionsUiState(
    val selectedCompetition: String = CompetitionDefinitions.CEURO,
    val currentRound: String = "QF",
    val fixtures: List<FixtureEntity> = emptyList(),
    val teamNames: Map<Int, String> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val tournamentComplete: Boolean = false,
    val winnerName: String = "",
)

@HiltViewModel
class ChampionsViewModel @Inject constructor(
    private val championsRepository: ChampionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChampionsUiState())
    val uiState: StateFlow<ChampionsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun selectCompetition(code: String) {
        if (_uiState.value.selectedCompetition == code) return
        _uiState.value = _uiState.value.copy(selectedCompetition = code)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val competitionCode = _uiState.value.selectedCompetition
            runCatching {
                val round = championsRepository.getCurrentRound(competitionCode)
                val fixtures = championsRepository.fixturesForCurrentRound(competitionCode)
                val names = championsRepository.teamNamesForFixtures(fixtures)
                val winner = championsRepository.championName(competitionCode)
                Quad(round, fixtures, names, winner)
            }.onSuccess { (round, fixtures, names, winner) ->
                _uiState.value = ChampionsUiState(
                    selectedCompetition = competitionCode,
                    currentRound = round,
                    fixtures = fixtures,
                    teamNames = names,
                    loading = false,
                    tournamentComplete = round == "DONE" || !winner.isNullOrBlank(),
                    winnerName = winner ?: "",
                )
            }.onFailure { throwable ->
                _uiState.value = ChampionsUiState(
                    selectedCompetition = competitionCode,
                    loading = false,
                    error = throwable.message ?: "Error cargando Champions",
                )
            }
        }
    }

    fun setup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val competitionCode = _uiState.value.selectedCompetition
            runCatching { championsRepository.setup(competitionCode) }
                .onSuccess { load() }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = throwable.message ?: "Error iniciando Champions",
                    )
                }
        }
    }

    fun simulateRound(seed: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val competitionCode = _uiState.value.selectedCompetition
            runCatching { championsRepository.simulateRound(seed, competitionCode) }
                .onSuccess { load() }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = throwable.message ?: "Error simulando Champions",
                    )
                }
        }
    }

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )
}
