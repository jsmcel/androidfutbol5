package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.NewsDao
import com.pcfutbol.core.data.db.NewsEntity
import com.pcfutbol.core.data.db.PRESIDENT_CAP_BALANCED
import com.pcfutbol.core.data.db.PRESIDENT_CAP_FLEX
import com.pcfutbol.core.data.db.PRESIDENT_CAP_STRICT
import com.pcfutbol.core.data.db.PRESIDENT_OWNERSHIP_LISTED
import com.pcfutbol.core.data.db.PRESIDENT_OWNERSHIP_PRIVATE
import com.pcfutbol.core.data.db.PRESIDENT_OWNERSHIP_SOCIOS
import com.pcfutbol.core.data.db.PRESIDENT_OWNERSHIP_STATE
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.SeasonStateEntity
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.allowsPresidentDesk
import com.pcfutbol.core.data.db.normalizedControlMode
import com.pcfutbol.core.data.db.normalizedPresidentOwnership
import com.pcfutbol.core.data.db.normalizedPresidentSalaryCapMode
import com.pcfutbol.core.data.db.presidentOwnershipLabel
import com.pcfutbol.core.data.db.presidentSalaryCapFactor
import com.pcfutbol.core.data.db.presidentSalaryCapModeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

private const val NEWS_CATEGORY_PRESIDENT = "PRESIDENT"

enum class PresidentAction {
    PRIVATE_INVESTOR,
    STATE_PROJECT,
    IPO,
    PELOTAZO,
}

data class PresidentDeskUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val season: String = "2025-26",
    val matchday: Int = 1,
    val teamId: Int = -1,
    val teamName: String = "",
    val budgetK: Int = 0,
    val wageBillK: Int = 0,
    val salaryCapK: Int = 0,
    val salaryCapMarginK: Int = 0,
    val salaryCapModeKey: String = PRESIDENT_CAP_BALANCED,
    val salaryCapModeLabel: String = "Equilibrado",
    val ownershipKey: String = PRESIDENT_OWNERSHIP_SOCIOS,
    val ownershipLabel: String = "Socios",
    val pressure: Int = 0,
    val investorRounds: Int = 0,
    val ipoDone: Boolean = false,
    val pelotazoDone: Boolean = false,
)

@HiltViewModel
class PresidentDeskViewModel @Inject constructor(
    private val seasonStateDao: SeasonStateDao,
    private val teamDao: TeamDao,
    private val playerDao: PlayerDao,
    private val newsDao: NewsDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresidentDeskUiState())
    val uiState: StateFlow<PresidentDeskUiState> = _uiState.asStateFlow()

    init {
        observeState()
    }

    fun setSalaryCapMode(mode: String) {
        viewModelScope.launch {
            runCatching {
                val state = seasonStateDao.get() ?: return@runCatching
                val managerTeamId = state.managerTeamId
                if (managerTeamId <= 0) return@runCatching
                if (!allowsPresidentDesk(state.normalizedControlMode)) {
                    _uiState.update { it.copy(message = "Necesitas nivel Total para el despacho del presidente.") }
                    return@runCatching
                }
                val normalizedMode = when (mode.uppercase()) {
                    PRESIDENT_CAP_STRICT -> PRESIDENT_CAP_STRICT
                    PRESIDENT_CAP_FLEX -> PRESIDENT_CAP_FLEX
                    else -> PRESIDENT_CAP_BALANCED
                }
                val wageBillK = playerDao.byTeamNow(managerTeamId).sumOf { it.wageK.coerceAtLeast(1) }.coerceAtLeast(120)
                val capK = maxOf((wageBillK * presidentSalaryCapFactor(normalizedMode)).toInt(), wageBillK + 1)
                seasonStateDao.update(
                    state.copy(
                        presidentSalaryCapMode = normalizedMode,
                        presidentSalaryCapK = capK,
                    )
                )
                _uiState.update { it.copy(message = "Tope salarial actualizado a ${presidentSalaryCapModeLabel(normalizedMode)}.") }
            }.onFailure { e ->
                _uiState.update { it.copy(message = e.message ?: "No se pudo actualizar el tope salarial.") }
            }
        }
    }

    fun trigger(action: PresidentAction) {
        viewModelScope.launch {
            runCatching {
                val state = seasonStateDao.get() ?: return@runCatching
                if (!allowsPresidentDesk(state.normalizedControlMode)) {
                    _uiState.update { it.copy(message = "Necesitas nivel Total para ejecutar operaciones de presidencia.") }
                    return@runCatching
                }
                val teamId = state.managerTeamId
                if (teamId <= 0) return@runCatching
                val team = teamDao.byId(teamId) ?: return@runCatching
                val wageBillK = playerDao.byTeamNow(teamId).sumOf { it.wageK.coerceAtLeast(1) }.coerceAtLeast(120)
                val profile = normalizePresidentProfile(state, wageBillK)
                val rng = actionRng(state, teamId, action)

                var budgetK = team.budgetK
                var ownership = profile.normalizedPresidentOwnership
                var capMode = profile.normalizedPresidentSalaryCapMode
                var capK = profile.presidentSalaryCapK
                var pressure = profile.presidentPressure.coerceIn(0, 12)
                var rounds = profile.presidentInvestorRounds.coerceAtLeast(0)
                var ipoDone = profile.presidentIpoDone
                var pelotazoDone = profile.presidentPelotazoDone

                val resultMessage = when (action) {
                    PresidentAction.PRIVATE_INVESTOR -> {
                        if (rounds >= 2) {
                            "Ya has agotado las rondas de inversores privadas."
                        } else {
                            val multiplier = 0.85 + rng.nextDouble() * 0.30
                            val injectionK = ((8_000 + team.prestige.coerceAtLeast(1) * 2_500) * multiplier).toInt()
                            budgetK += injectionK
                            ownership = PRESIDENT_OWNERSHIP_PRIVATE
                            rounds += 1
                            pressure = (pressure + 1).coerceAtMost(12)
                            capK = maxOf((capK * 1.08).toInt(), wageBillK + 1)
                            "Inversor privado firmado (+${injectionK}K)."
                        }
                    }

                    PresidentAction.STATE_PROJECT -> {
                        if (ownership == PRESIDENT_OWNERSHIP_STATE) {
                            "El club ya opera como proyecto de estado."
                        } else {
                            val multiplier = 0.90 + rng.nextDouble() * 0.25
                            val injectionK = (35_000 * multiplier).toInt()
                            budgetK += injectionK
                            ownership = PRESIDENT_OWNERSHIP_STATE
                            pressure = (pressure + 2).coerceAtMost(12)
                            capK = maxOf((capK * 1.35).toInt(), wageBillK + 1)
                            "Operacion club-estado cerrada (+${injectionK}K)."
                        }
                    }

                    PresidentAction.IPO -> {
                        if (ipoDone) {
                            "El club ya cotiza en bolsa."
                        } else {
                            val multiplier = 0.90 + rng.nextDouble() * 0.20
                            val injectionK = (22_000 * multiplier).toInt()
                            budgetK += injectionK
                            ipoDone = true
                            ownership = PRESIDENT_OWNERSHIP_LISTED
                            pressure = (pressure + 1).coerceAtMost(12)
                            capK = maxOf((capK * 1.15).toInt(), wageBillK + 1)
                            "Salida a bolsa completada (+${injectionK}K)."
                        }
                    }

                    PresidentAction.PELOTAZO -> {
                        if (pelotazoDone) {
                            "Ya ejecutaste un pelotazo inmobiliario en esta etapa."
                        } else {
                            val multiplier = 0.90 + rng.nextDouble() * 0.35
                            val injectionK = (16_000 * multiplier).toInt()
                            budgetK += injectionK
                            pelotazoDone = true
                            pressure = (pressure + 1).coerceAtMost(12)
                            capK = maxOf((capK * 1.05).toInt(), wageBillK + 1)
                            "Pelotazo inmobiliario aprobado (+${injectionK}K)."
                        }
                    }
                }

                val updatedState = profile.copy(
                    presidentOwnership = ownership,
                    presidentSalaryCapMode = capMode,
                    presidentSalaryCapK = maxOf(capK, wageBillK + 1),
                    presidentPressure = pressure,
                    presidentInvestorRounds = rounds,
                    presidentIpoDone = ipoDone,
                    presidentPelotazoDone = pelotazoDone,
                )
                seasonStateDao.update(updatedState)
                teamDao.update(team.copy(budgetK = budgetK.coerceAtLeast(0)))
                newsDao.insert(
                    NewsEntity(
                        date = java.time.LocalDate.now().toString(),
                        matchday = state.currentMatchday,
                        category = NEWS_CATEGORY_PRESIDENT,
                        titleEs = "Despacho del presidente",
                        bodyEs = resultMessage,
                        teamId = teamId,
                    )
                )
                _uiState.update { it.copy(message = resultMessage) }
            }.onFailure { e ->
                _uiState.update { it.copy(message = e.message ?: "No se pudo ejecutar la operacion.") }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun observeState() {
        viewModelScope.launch {
            seasonStateDao.observe().filterNotNull().collect { state ->
                runCatching {
                    val teamId = state.managerTeamId
                    if (teamId <= 0) {
                        _uiState.value = PresidentDeskUiState(
                            loading = false,
                            available = false,
                            error = "Selecciona primero un equipo manager.",
                        )
                        return@runCatching
                    }
                    val team = teamDao.byId(teamId) ?: run {
                        _uiState.value = PresidentDeskUiState(
                            loading = false,
                            available = false,
                            error = "No se encontro el equipo del manager.",
                        )
                        return@runCatching
                    }
                    val wageBillK = playerDao.byTeamNow(teamId).sumOf { it.wageK.coerceAtLeast(1) }.coerceAtLeast(120)
                    val normalized = normalizePresidentProfile(state, wageBillK)
                    val capK = normalized.presidentSalaryCapK
                    _uiState.value = PresidentDeskUiState(
                        loading = false,
                        available = allowsPresidentDesk(normalized.normalizedControlMode),
                        error = if (allowsPresidentDesk(normalized.normalizedControlMode)) null else
                            "Disponible solo en nivel de control Total.",
                        message = _uiState.value.message,
                        season = normalized.season,
                        matchday = normalized.currentMatchday,
                        teamId = teamId,
                        teamName = team.nameShort,
                        budgetK = team.budgetK,
                        wageBillK = wageBillK,
                        salaryCapK = capK,
                        salaryCapMarginK = capK - wageBillK,
                        salaryCapModeKey = normalized.normalizedPresidentSalaryCapMode,
                        salaryCapModeLabel = presidentSalaryCapModeLabel(normalized.normalizedPresidentSalaryCapMode),
                        ownershipKey = normalized.normalizedPresidentOwnership,
                        ownershipLabel = presidentOwnershipLabel(normalized.normalizedPresidentOwnership),
                        pressure = normalized.presidentPressure.coerceIn(0, 12),
                        investorRounds = normalized.presidentInvestorRounds.coerceAtLeast(0),
                        ipoDone = normalized.presidentIpoDone,
                        pelotazoDone = normalized.presidentPelotazoDone,
                    )
                }.onFailure { e ->
                    _uiState.value = PresidentDeskUiState(
                        loading = false,
                        available = false,
                        error = e.message ?: "No se pudo cargar el despacho del presidente.",
                    )
                }
            }
        }
    }

    private fun normalizePresidentProfile(state: SeasonStateEntity, wageBillK: Int): SeasonStateEntity {
        val normalizedOwnership = state.normalizedPresidentOwnership
        val normalizedCapMode = state.normalizedPresidentSalaryCapMode
        val cap = if (state.presidentSalaryCapK > 0) {
            state.presidentSalaryCapK
        } else {
            (wageBillK * presidentSalaryCapFactor(normalizedCapMode)).toInt()
        }
        return state.copy(
            presidentOwnership = normalizedOwnership,
            presidentSalaryCapMode = normalizedCapMode,
            presidentSalaryCapK = maxOf(cap, wageBillK + 1),
            presidentPressure = state.presidentPressure.coerceIn(0, 12),
            presidentInvestorRounds = state.presidentInvestorRounds.coerceAtLeast(0),
            presidentLastReviewMatchday = state.presidentLastReviewMatchday.coerceAtLeast(0),
            presidentNextReviewMatchday = state.presidentNextReviewMatchday.coerceAtLeast(1),
        )
    }

    private fun actionRng(
        state: SeasonStateEntity,
        teamId: Int,
        action: PresidentAction,
    ): Random {
        val seed = (state.season.hashCode().toLong() shl 16) xor
            (state.currentMatchday.toLong() * 257L) xor
            (teamId.toLong() * 131L) xor
            action.name.hashCode().toLong()
        return Random(seed)
    }
}
