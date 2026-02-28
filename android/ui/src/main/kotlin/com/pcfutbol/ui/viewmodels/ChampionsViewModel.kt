package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.competition.ChampionsRepository
import com.pcfutbol.core.data.db.FixtureEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChampionsUiState(
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

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                val round = championsRepository.getCurrentRound()
                val fixtures = championsRepository.fixturesForCurrentRound()
                val names = championsRepository.teamNamesForFixtures(fixtures)
                val winner = championsRepository.championName()
                Quad(round, fixtures, names, winner)
            }.onSuccess { (round, fixtures, names, winner) ->
                _uiState.value = ChampionsUiState(
                    currentRound = round,
                    fixtures = fixtures,
                    teamNames = names,
                    loading = false,
                    tournamentComplete = round == "DONE",
                    winnerName = winner ?: "",
                )
            }.onFailure { throwable ->
                _uiState.value = ChampionsUiState(
                    loading = false,
                    error = throwable.message ?: "Error cargando Champions",
                )
            }
        }
    }

    fun setup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { championsRepository.setup() }
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
            runCatching { championsRepository.simulateRound(seed) }
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
