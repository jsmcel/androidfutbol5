package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.competition.CompetitionRepository
import com.pcfutbol.core.data.db.FixtureEntity
import com.pcfutbol.core.data.db.ManagerProfileDao
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TacticPresetDao
import com.pcfutbol.core.data.db.TacticPresetEntity
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

enum class CoachCommand {
    BALANCED,
    ATTACK_ALL_IN,
    LOW_BLOCK,
    HIGH_PRESS,
    CALM_GAME,
    WASTE_TIME,
}

data class MatchdayUiState(
    val competitionCode: String = CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE,
    val fixtures: List<FixtureEntity> = emptyList(),
    val teamNames: Map<Int, String> = emptyMap(),
    val managerTeamId: Int = -1,
    val coachCommand: CoachCommand = CoachCommand.BALANCED,
    val loading: Boolean = false,
    val error: String? = null,
    val seasonComplete: Boolean = false,
)

@HiltViewModel
class MatchdayViewModel @Inject constructor(
    private val competitionRepository: CompetitionRepository,
    private val teamDao: TeamDao,
    private val seasonStateDao: SeasonStateDao,
    private val managerProfileDao: ManagerProfileDao,
    private val tacticPresetDao: TacticPresetDao,
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
                val managerTeamId = seasonStateDao.get()?.managerTeamId ?: -1
                Quad(comp, fixtures, names, managerTeamId)
            }.onSuccess { (comp, fixtures, names, managerTeamId) ->
                _uiState.value = MatchdayUiState(
                    competitionCode = comp,
                    fixtures = fixtures,
                    teamNames = names,
                    managerTeamId = managerTeamId,
                    coachCommand = _uiState.value.coachCommand,
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

    fun setCoachCommand(command: CoachCommand) {
        _uiState.value = _uiState.value.copy(coachCommand = command)
    }

    fun simulateMatchday(matchday: Int, seed: Long, command: CoachCommand = _uiState.value.coachCommand) {
        val comp = _uiState.value.competitionCode
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                applyCoachCommand(command)
                competitionRepository.advanceMatchday(comp, matchday, seed)
            }.onSuccess {
                loadMatchday(matchday)
                val pending = competitionRepository.pendingFixtures(comp)
                if (pending == 0) {
                    _uiState.value = _uiState.value.copy(seasonComplete = true)
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = throwable.message ?: "Error simulando jornada",
                )
            }
        }
    }

    private suspend fun applyCoachCommand(command: CoachCommand) {
        val managerTeamId = seasonStateDao.get()?.managerTeamId ?: return
        if (managerTeamId <= 0) return

        val managerProfileId = managerProfileDao.byTeam(managerTeamId)?.id ?: managerTeamId
        val current = tacticPresetDao.bySlot(managerProfileId, 0)
            ?: TacticPresetEntity(
                managerProfileId = managerProfileId,
                slotIndex = 0,
            )
        val updated = when (command) {
            CoachCommand.BALANCED -> current.copy(
                tipoJuego = 2,
                tipoPresion = 2,
                porcToque = 50,
                porcContra = 30,
                perdidaTiempo = 0,
            )
            CoachCommand.ATTACK_ALL_IN -> current.copy(
                tipoJuego = 3,
                tipoPresion = 3,
                porcToque = 35,
                porcContra = 75,
                perdidaTiempo = 0,
            )
            CoachCommand.LOW_BLOCK -> current.copy(
                tipoJuego = 1,
                tipoPresion = 1,
                porcToque = 55,
                porcContra = 45,
                perdidaTiempo = 0,
            )
            CoachCommand.HIGH_PRESS -> current.copy(
                tipoJuego = 2,
                tipoPresion = 3,
                faltas = 3,
                porcToque = 45,
                porcContra = 50,
                perdidaTiempo = 0,
            )
            CoachCommand.CALM_GAME -> current.copy(
                tipoJuego = 2,
                tipoPresion = 1,
                faltas = 1,
                porcToque = 70,
                porcContra = 20,
                perdidaTiempo = 0,
            )
            CoachCommand.WASTE_TIME -> current.copy(
                tipoJuego = 1,
                tipoPresion = 1,
                porcToque = 72,
                porcContra = 18,
                perdidaTiempo = 1,
            )
        }
        if (current.id == 0) {
            tacticPresetDao.insert(updated)
        } else {
            tacticPresetDao.update(updated)
        }
    }

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )
}
