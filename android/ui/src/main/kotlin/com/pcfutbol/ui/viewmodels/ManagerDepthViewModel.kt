package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.ManagerProfileDao
import com.pcfutbol.core.data.db.ManagerProfileEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.db.isProManagerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagerDepthUiState(
    val available: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val managerName: String = "",
    val teamName: String = "",
    val profileId: Int = -1,
    val intensity: String = "MEDIUM",
    val focus: String = "BALANCED",
    val staff: Map<String, Int> = DEFAULT_STAFF,
    val saved: Boolean = false,
) {
    companion object {
        val DEFAULT_STAFF: Map<String, Int> = mapOf(
            "segundoEntrenador" to 50,
            "fisio" to 50,
            "psicologo" to 50,
            "asistente" to 50,
            "secretario" to 50,
            "ojeador" to 50,
            "juveniles" to 50,
            "cuidador" to 50,
        )
    }
}

@HiltViewModel
class ManagerDepthViewModel @Inject constructor(
    private val seasonStateDao: SeasonStateDao,
    private val managerProfileDao: ManagerProfileDao,
    private val teamDao: TeamDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManagerDepthUiState())
    val uiState: StateFlow<ManagerDepthUiState> = _uiState.asStateFlow()

    init {
        observeProfile()
    }

    fun setIntensity(value: String) {
        if (value !in INTENSITIES) return
        _uiState.value = _uiState.value.copy(intensity = value, saved = false)
    }

    fun setFocus(value: String) {
        if (value !in FOCUSES) return
        _uiState.value = _uiState.value.copy(focus = value, saved = false)
    }

    fun setStaffValue(key: String, value: Int) {
        if (key !in ManagerDepthUiState.DEFAULT_STAFF.keys) return
        val normalized = value.coerceIn(0, 100)
        _uiState.value = _uiState.value.copy(
            staff = _uiState.value.staff.toMutableMap().apply { put(key, normalized) },
            saved = false,
        )
    }

    fun save() {
        val snapshot = _uiState.value
        if (!snapshot.available || snapshot.profileId <= 0) return
        viewModelScope.launch {
            val profile = managerProfileDao.byId(snapshot.profileId) ?: return@launch
            managerProfileDao.update(
                profile.copy(
                    trainingIntensity = snapshot.intensity,
                    trainingFocus = snapshot.focus,
                    segundoEntrenador = snapshot.staff["segundoEntrenador"] ?: 50,
                    fisio = snapshot.staff["fisio"] ?: 50,
                    psicologo = snapshot.staff["psicologo"] ?: 50,
                    asistente = snapshot.staff["asistente"] ?: 50,
                    secretario = snapshot.staff["secretario"] ?: 50,
                    ojeador = snapshot.staff["ojeador"] ?: 50,
                    juveniles = snapshot.staff["juveniles"] ?: 50,
                    cuidador = snapshot.staff["cuidador"] ?: 50,
                )
            )
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    private fun observeProfile() {
        viewModelScope.launch {
            seasonStateDao.observe().filterNotNull().collect { season ->
                runCatching {
                    val teamId = season.managerTeamId
                    if (teamId <= 0) {
                        _uiState.value = ManagerDepthUiState(
                            available = false,
                            loading = false,
                            error = "Selecciona un equipo para gestionar entrenamiento/staff.",
                        )
                        return@runCatching
                    }

                    var profile = managerProfileDao.byTeam(teamId)
                    if (profile == null && season.isProManagerMode()) {
                        val generated = ManagerProfileEntity(
                            name = "Manager $teamId",
                            passwordHash = "local",
                            currentTeamId = teamId,
                            prestige = 1,
                        )
                        val id = managerProfileDao.insert(generated).toInt()
                        profile = managerProfileDao.byId(id)
                    }

                    val teamName = teamDao.byId(teamId)?.nameShort ?: "Equipo $teamId"
                    if (profile == null) {
                        _uiState.value = ManagerDepthUiState(
                            available = false,
                            loading = false,
                            teamName = teamName,
                            error = "Disponible en partida ProManager con manager activo.",
                        )
                        return@runCatching
                    }

                    _uiState.value = ManagerDepthUiState(
                        available = true,
                        loading = false,
                        managerName = profile.name,
                        teamName = teamName,
                        profileId = profile.id,
                        intensity = profile.trainingIntensity.takeIf { it in INTENSITIES } ?: "MEDIUM",
                        focus = profile.trainingFocus.takeIf { it in FOCUSES } ?: "BALANCED",
                        staff = mapOf(
                            "segundoEntrenador" to profile.segundoEntrenador,
                            "fisio" to profile.fisio,
                            "psicologo" to profile.psicologo,
                            "asistente" to profile.asistente,
                            "secretario" to profile.secretario,
                            "ojeador" to profile.ojeador,
                            "juveniles" to profile.juveniles,
                            "cuidador" to profile.cuidador,
                        ),
                        saved = true,
                    )
                }.onFailure { e ->
                    _uiState.value = ManagerDepthUiState(
                        available = false,
                        loading = false,
                        error = e.message ?: "No se pudo cargar staff/entrenamiento.",
                    )
                }
            }
        }
    }

    companion object {
        val INTENSITIES = listOf("LOW", "MEDIUM", "HIGH")
        val FOCUSES = listOf("BALANCED", "PHYSICAL", "DEFENSIVE", "TECHNICAL", "ATTACKING")
    }
}
