package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LineupUiState(
    val allPlayers: List<PlayerEntity> = emptyList(),
    val starters: List<PlayerEntity> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class LineupViewModel @Inject constructor(
    private val playerDao: PlayerDao,
    private val seasonStateDao: SeasonStateDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LineupUiState())
    val uiState: StateFlow<LineupUiState> = _uiState.asStateFlow()

    private var managerTeamId: Int = -1
    private var allPlayersCache: List<PlayerEntity> = emptyList()
    private var pendingStarterIds: Set<Int>? = null

    init {
        viewModelScope.launch {
            managerTeamId = seasonStateDao.observe()
                .filterNotNull()
                .first()
                .managerTeamId

            if (managerTeamId <= 0) {
                _uiState.value = LineupUiState(loading = false)
                return@launch
            }

            playerDao.byTeamWithStarters(managerTeamId).collectLatest { players ->
                allPlayersCache = players
                val starters = pendingStarterIds ?: players.filter { it.isStarter }.map { it.id }.toSet()
                _uiState.value = buildUiState(players, starters)
            }
        }
    }

    fun toggleStarter(playerId: Int) {
        val player = allPlayersCache.firstOrNull { it.id == playerId } ?: return
        if (!player.isAvailable()) return

        val starters = currentStarterIds().toMutableSet()
        if (playerId in starters) {
            starters.remove(playerId)
        } else if (starters.size < MAX_STARTERS) {
            starters.add(playerId)
        } else {
            return
        }

        pendingStarterIds = starters
        _uiState.value = buildUiState(allPlayersCache, starters)
    }

    fun autoSelect() {
        val available = allPlayersCache.filter { it.isAvailable() }
        val selected = mutableListOf<Int>()

        fun pick(position: String, count: Int) {
            available
                .asSequence()
                .filter { it.position == position && it.id !in selected }
                .sortedByDescending { it.ca }
                .take(count)
                .forEach { selected += it.id }
        }

        pick("PO", 1)
        pick("DF", 4)
        pick("MC", 3)
        pick("DC", 3)

        if (selected.size < MAX_STARTERS) {
            available
                .asSequence()
                .filter { it.id !in selected }
                .sortedByDescending { it.ca }
                .take(MAX_STARTERS - selected.size)
                .forEach { selected += it.id }
        }

        val starters = selected.take(MAX_STARTERS).toSet()
        pendingStarterIds = starters
        _uiState.value = buildUiState(allPlayersCache, starters)
    }

    fun saveLineup() {
        if (managerTeamId <= 0) return
        val starters = currentStarterIds().take(MAX_STARTERS).toSet()

        viewModelScope.launch {
            playerDao.clearStarters(managerTeamId)
            starters.forEach { playerId ->
                playerDao.setStarter(playerId, true)
            }
            pendingStarterIds = null
        }
    }

    private fun currentStarterIds(): Set<Int> =
        pendingStarterIds ?: allPlayersCache.filter { it.isStarter }.map { it.id }.toSet()

    private fun buildUiState(
        players: List<PlayerEntity>,
        starterIds: Set<Int>,
    ): LineupUiState {
        val sortedPlayers = players.sortedWith(
            compareByDescending<PlayerEntity> { it.id in starterIds }
                .thenBy { positionOrder(it.position) }
                .thenByDescending { it.ca }
                .thenBy { it.number }
        )

        val starters = sortedPlayers
            .filter { it.id in starterIds }
            .take(MAX_STARTERS)
            .sortedWith(
                compareBy<PlayerEntity> { positionOrder(it.position) }
                    .thenByDescending { it.ca }
                    .thenBy { it.number }
            )

        return LineupUiState(
            allPlayers = sortedPlayers,
            starters = starters,
            loading = false,
        )
    }

    private fun positionOrder(position: String): Int = when (position) {
        "PO" -> 0
        "DF" -> 1
        "MC" -> 2
        "DC" -> 3
        else -> 4
    }

    private fun PlayerEntity.isAvailable(): Boolean =
        status == 0 && injuryWeeksLeft <= 0 && sanctionMatchesLeft <= 0

    private companion object {
        const val MAX_STARTERS = 11
    }
}
