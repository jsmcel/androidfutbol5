package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.competition.CopaRepository
import com.pcfutbol.core.data.db.FixtureEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CopaUiState(
    val currentRound: String = "",
    val fixtures: List<FixtureEntity> = emptyList(),
    val teamNames: Map<Int, String> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val roundComplete: Boolean = false,
    val tournamentComplete: Boolean = false,
    val winnerName: String = "",
)

@HiltViewModel
class CopaViewModel @Inject constructor(
    private val copaRepository: CopaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CopaUiState())
    val uiState: StateFlow<CopaUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching {
                val round = copaRepository.currentRound()
                val fixtures = copaRepository.fixturesForRound(round)
                val names = copaRepository.teamNamesForFixtures(fixtures)
                Triple(round, fixtures, names)
            }.onSuccess { (round, fixtures, names) ->
                val allPlayed = fixtures.isNotEmpty() && fixtures.all { it.played }
                _uiState.value = CopaUiState(
                    currentRound = round,
                    fixtures = fixtures,
                    teamNames = names,
                    loading = false,
                    roundComplete = allPlayed,
                )
            }.onFailure { e ->
                _uiState.value = CopaUiState(loading = false, error = e.message)
            }
        }
    }

    fun simulateRound(seed: Long) {
        val round = _uiState.value.currentRound
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            runCatching { copaRepository.simulateRound(round, seed) }
                .onSuccess { load() }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = e.message,
                    )
                }
        }
    }

    fun setup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            runCatching { copaRepository.setup() }
                .onSuccess { load() }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun advanceToNextRound() {
        val round = _uiState.value.currentRound
        viewModelScope.launch {
            runCatching {
                copaRepository.advanceToNextRound(round)
            }.onSuccess { (_, winner) ->
                if (winner != null) {
                    _uiState.value = _uiState.value.copy(
                        tournamentComplete = true,
                        winnerName = winner,
                        roundComplete = false,
                    )
                } else {
                    load()
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
