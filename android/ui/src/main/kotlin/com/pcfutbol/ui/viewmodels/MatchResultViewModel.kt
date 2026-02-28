package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.FixtureDao
import com.pcfutbol.core.data.db.FixtureEntity
import com.pcfutbol.core.data.db.TeamDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchResultUiState(
    val fixture: FixtureEntity? = null,
    val homeName: String = "",
    val awayName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class MatchResultViewModel @Inject constructor(
    private val fixtureDao: FixtureDao,
    private val teamDao: TeamDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchResultUiState())
    val uiState: StateFlow<MatchResultUiState> = _uiState.asStateFlow()

    fun load(fixtureId: Int) {
        viewModelScope.launch {
            runCatching {
                val fixture = fixtureDao.byId(fixtureId)
                    ?: error("Partido no encontrado")
                val home = teamDao.byId(fixture.homeTeamId)?.nameShort ?: "Equipo ${fixture.homeTeamId}"
                val away = teamDao.byId(fixture.awayTeamId)?.nameShort ?: "Equipo ${fixture.awayTeamId}"
                Triple(fixture, home, away)
            }.onSuccess { (fixture, home, away) ->
                _uiState.value = MatchResultUiState(
                    fixture = fixture,
                    homeName = home,
                    awayName = away,
                    loading = false,
                )
            }.onFailure { e ->
                _uiState.value = MatchResultUiState(
                    loading = false,
                    error = e.message ?: "Error cargando partido",
                )
            }
        }
    }
}
