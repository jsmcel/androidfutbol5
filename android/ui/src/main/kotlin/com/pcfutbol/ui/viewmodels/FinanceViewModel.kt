package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.NewsDao
import com.pcfutbol.core.data.db.NewsEntity
import com.pcfutbol.core.data.db.PlayerDao
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.economy.WageCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FinanceUiState(
    val loading: Boolean = true,
    val teamName: String = "",
    val season: String = "2025-26",
    val matchday: Int = 1,
    val budgetK: Int = 0,
    val payrollWeeklyK: Int = 0,
    val squadMarketValueK: Int = 0,
    val projectedSponsorK: Int = 0,
    val projectedTicketK: Int = 0,
    val projectedNetWeeklyK: Int = 0,
    val financeNews: List<NewsEntity> = emptyList(),
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val seasonStateDao: SeasonStateDao,
    private val teamDao: TeamDao,
    private val playerDao: PlayerDao,
    private val newsDao: NewsDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            seasonStateDao.observe().filterNotNull().collect { state ->
                val teamId = state.managerTeamId
                if (teamId <= 0) {
                    _uiState.value = FinanceUiState(loading = false)
                    return@collect
                }
                val team = teamDao.byId(teamId)
                val players = playerDao.byTeamNow(teamId)
                val payroll = players.sumOf { it.wageK }
                val marketValue = players.sumOf {
                    WageCalculator.marketValueK(it, team?.competitionKey ?: "LIGA1")
                }
                val sponsor = team?.let { (it.membersCount / 20).coerceIn(300, 7000) + it.prestige * 40 } ?: 0
                val ticket = team?.let { (it.membersCount / 18).coerceIn(250, 5000) } ?: 0
                val projectedNet = sponsor + ticket - payroll
                val financeNews = newsDao.recentByCategory("FINANCE", 15).first()
                _uiState.value = FinanceUiState(
                    loading = false,
                    teamName = team?.nameShort ?: "Equipo",
                    season = state.season,
                    matchday = state.currentMatchday,
                    budgetK = team?.budgetK ?: 0,
                    payrollWeeklyK = payroll,
                    squadMarketValueK = marketValue,
                    projectedSponsorK = sponsor,
                    projectedTicketK = ticket,
                    projectedNetWeeklyK = projectedNet,
                    financeNews = financeNews,
                )
            }
        }
    }
}
