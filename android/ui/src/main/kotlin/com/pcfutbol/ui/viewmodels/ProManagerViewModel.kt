package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.PROMANAGER_MODE
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.withManagerMode
import com.pcfutbol.promanager.OfferPoolGenerator
import com.pcfutbol.promanager.ProManagerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OfferUiItem(
    val teamId: Int,
    val teamName: String,
    val competitionKey: String,
    val salaryK: Int,
    val objectiveLabel: String,
)

data class ProManagerUiState(
    val noManager: Boolean = true,
    val pendingLogin: Boolean = false,
    val managerId: Int = -1,
    val managerName: String? = null,
    val managerNames: List<String> = emptyList(),
    val offers: List<OfferUiItem> = emptyList(),
    val selectedOfferId: Int = -1,
    val error: String? = null,
    val offerAccepted: Boolean = false,
)

@HiltViewModel
class ProManagerViewModel @Inject constructor(
    private val repo: ProManagerRepository,
    private val seasonStateDao: SeasonStateDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProManagerUiState())
    val uiState: StateFlow<ProManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.allManagers().collect { managers ->
                if (managers.isEmpty()) {
                    _uiState.value = ProManagerUiState(noManager = true)
                } else {
                    _uiState.value = ProManagerUiState(
                        noManager    = false,
                        pendingLogin = true,
                        managerNames = managers.map { it.name },
                    )
                }
            }
        }
    }

    fun createManager(name: String, password: String) {
        viewModelScope.launch {
            val profile = repo.createManager(name, password)
            loadOffers(profile.id, name)
        }
    }

    fun login(name: String, password: String) {
        viewModelScope.launch {
            val profile = repo.authenticate(name, password)
            if (profile != null) {
                loadOffers(profile.id, profile.name)
            } else {
                _uiState.value = _uiState.value.copy(error = "Contraseña incorrecta")
            }
        }
    }

    private suspend fun loadOffers(managerId: Int, managerName: String) {
        val offers = repo.generateOffers(managerId).map { it.toUiItem() }
        _uiState.value = ProManagerUiState(
            noManager    = false,
            pendingLogin = false,
            managerId    = managerId,
            managerName  = managerName,
            offers       = offers,
        )
    }

    fun selectOffer(teamId: Int) {
        _uiState.value = _uiState.value.copy(selectedOfferId = teamId)
    }

    fun acceptSelected() {
        val teamId    = _uiState.value.selectedOfferId
        val managerId = _uiState.value.managerId
        if (teamId == -1 || managerId == -1) return
        viewModelScope.launch {
            repo.acceptOffer(managerId, teamId)
            val state = seasonStateDao.get()
            if (state != null) {
                seasonStateDao.update(
                    state.copy(managerTeamId = teamId)
                        .withManagerMode(PROMANAGER_MODE)
                )
            }
            // Signal navigation AFTER DB is committed
            _uiState.value = _uiState.value.copy(offerAccepted = true)
        }
    }

    fun consumeOfferAccepted() {
        _uiState.value = _uiState.value.copy(offerAccepted = false)
    }

    private fun OfferPoolGenerator.ManagerOffer.toUiItem() = OfferUiItem(
        teamId         = team.slotId,
        teamName       = team.nameFull,
        competitionKey = team.competitionKey,
        salaryK        = salary,
        objectiveLabel = when (objectiveLevel) {
            1 -> "Salvarse"
            2 -> "Top 10"
            3 -> "Título"
            else -> "Competir"
        },
    )
}
