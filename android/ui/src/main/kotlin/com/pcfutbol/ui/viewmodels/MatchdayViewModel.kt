package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.competition.CompetitionRepository
import com.pcfutbol.core.data.db.FixtureEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.managerLeague
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchdayUiState(
    val competitionCode: String = CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE,
    val fixtures: List<FixtureEntity> = emptyList(),
    val teamNames: Map<Int, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val seasonComplete: Boolean = false,
)

@HiltViewModel
class MatchdayViewModel @Inject constructor(
    private val competitionRepository: CompetitionRepository,
    private val teamDao: TeamDao,
    private val seasonStateDao: SeasonStateDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchdayUiState())
    val uiState: StateFlow<MatchdayUiState> = _uiState.asStateFlow()

    /** Resuelve la competicion activa (equipo del manager o liga seleccionada). */
    private suspend fun resolveComp(): String {
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

    fun loadMatchday(matchday: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                val comp = resolveComp()
                val fixtures = competitionRepository.fixtures(comp)
                    .first()
                    .filter { it.matchday == matchday }
                val teamIds = fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
                val names = teamIds.mapNotNull { id ->
                    teamDao.byId(id)?.let { id to it.nameShort }
                }.toMap()
                Triple(comp, fixtures, names)
            }.onSuccess { (comp, fixtures, names) ->
                _uiState.value = MatchdayUiState(
                    competitionCode = comp,
                    fixtures = fixtures,
                    teamNames = names,
                    loading = false,
                )
            }.onFailure { throwable ->
                _uiState.value = MatchdayUiState(
                    loading = false,
                    error = throwable.message ?: "Error cargando jornada",
                )
            }
        }
    }

    fun simulateMatchday(matchday: Int, seed: Long) {
        val comp = _uiState.value.competitionCode
        viewModelScope.launch {
            runCatching {
                competitionRepository.advanceMatchday(comp, matchday, seed)
            }.onSuccess {
                loadMatchday(matchday)
                val pending = competitionRepository.pendingFixtures(comp)
                if (pending == 0) {
                    _uiState.value = _uiState.value.copy(seasonComplete = true)
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    error = throwable.message ?: "Error simulando jornada",
                )
            }
        }
    }
}
