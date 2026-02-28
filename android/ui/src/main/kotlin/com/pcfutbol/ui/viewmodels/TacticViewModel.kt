package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.ManagerProfileDao
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TacticPresetDao
import com.pcfutbol.core.data.db.TacticPresetEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TacticUiState(
    val preset: TacticPresetEntity? = null,
    val managerId: Int = -1,
    val saved: Boolean = false,
)

@HiltViewModel
class TacticViewModel @Inject constructor(
    private val tacticPresetDao: TacticPresetDao,
    private val seasonStateDao: SeasonStateDao,
    private val managerProfileDao: ManagerProfileDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TacticUiState())
    val uiState: StateFlow<TacticUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val seasonState = seasonStateDao.observe().filterNotNull().first()
            val managerTeamId = seasonState.managerTeamId
            // Resolve the real manager profile id from the team id
            val managerProfileId = managerProfileDao.byTeam(managerTeamId)?.id ?: managerTeamId
            val preset = tacticPresetDao.bySlot(managerProfileId, 0)
                ?: TacticPresetEntity(
                    managerProfileId = managerProfileId,
                    slotIndex = 0,
                )
            _uiState.value = TacticUiState(
                preset = preset,
                managerId = managerProfileId,
                saved = false,
            )
        }
    }

    fun updateParam(field: String, value: Int) {
        val current = _uiState.value.preset ?: return
        val updated = when (field) {
            "tipoJuego" -> current.copy(tipoJuego = value)
            "tipoMarcaje" -> current.copy(tipoMarcaje = value)
            "tipoPresion" -> current.copy(tipoPresion = value)
            "tipoDespejes" -> current.copy(tipoDespejes = value)
            "faltas" -> current.copy(faltas = value)
            "porcToque" -> current.copy(porcToque = value)
            "porcContra" -> current.copy(porcContra = value)
            "perdidaTiempo" -> current.copy(perdidaTiempo = value.coerceIn(0, 1))
            else -> return
        }
        _uiState.value = _uiState.value.copy(
            preset = updated,
            saved = false,
        )
    }

    fun save() {
        viewModelScope.launch {
            val preset = _uiState.value.preset ?: return@launch
            if (preset.id == 0) {
                val newId = tacticPresetDao.insert(preset).toInt()
                _uiState.value = _uiState.value.copy(
                    preset = preset.copy(id = newId),
                    saved = true,
                )
            } else {
                tacticPresetDao.update(preset)
                _uiState.value = _uiState.value.copy(saved = true)
            }
        }
    }
}
