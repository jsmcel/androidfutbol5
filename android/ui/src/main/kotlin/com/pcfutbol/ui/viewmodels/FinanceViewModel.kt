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
    val projectedMerchK: Int = 0,
    val projectedCommunicationCostK: Int = 0,
    val projectedNetWeeklyK: Int = 0,
    val shirtPriceEur: Int = 70,
    val pressRating: Int = 50,
    val channelLevel: Int = 45,
    val fanMood: Int = 55,
    val socialMassK: Int = 0,
    val environment: Int = 50,
    val marketTrend: Int = 0,
    val refereeVerdictLabel: String = "Neutro",
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
                val press = state.marketPressRating.coerceIn(0, 100)
                val channel = state.marketChannelLevel.coerceIn(0, 100)
                val mood = state.marketFanMood.coerceIn(0, 100)
                val socialMassK = if (state.marketSocialMassK > 0) state.marketSocialMassK else
                    ((team?.membersCount ?: 0) / 35).coerceIn(40, 2600)
                val environment = state.marketEnvironment.coerceIn(0, 100)
                val trend = state.marketTrend.coerceIn(-20, 20)
                val shirtPrice = state.marketShirtPriceEur.coerceIn(25, 180)
                val sponsorBase = team?.let { (it.membersCount / 20).coerceIn(300, 7000) + it.prestige * 40 } ?: 0
                val sponsor = (sponsorBase + ((press + channel + mood - 150) / 2).coerceIn(-180, 420)).coerceAtLeast(200)
                val attendanceRate = (
                    0.30 +
                        mood * 0.0030 +
                        environment * 0.0018 +
                        press * 0.0012 +
                        channel * 0.0010 +
                        trend * 0.0025 +
                        0.14
                    ).coerceIn(0.10, 0.96)
                val attendance = (socialMassK * 1000.0 * attendanceRate).toInt().coerceAtLeast(1200)
                val ticketPriceEur = (16.0 + (team?.prestige ?: 1) * 1.4 + environment * 0.07).coerceAtLeast(10.0)
                val ticket = ((attendance * ticketPriceEur) / 1000.0).toInt().coerceAtLeast(80)
                val demandUnits = (
                    socialMassK * (
                        0.40 +
                            mood * 0.0020 +
                            channel * 0.0022 +
                            press * 0.0015 +
                            trend.coerceAtLeast(0) * 0.0030
                        )
                    ).toInt().coerceAtLeast(15)
                val priceElasticity = when {
                    shirtPrice <= 55 -> 1.10
                    shirtPrice <= 75 -> 1.00
                    shirtPrice <= 95 -> 0.92
                    shirtPrice <= 120 -> 0.82
                    else -> 0.70
                }
                val merch = ((demandUnits * shirtPrice * priceElasticity) / 1000.0).toInt().coerceAtLeast(50)
                val communication = (120 + channel * 3 + press * 2).coerceIn(160, 1600)
                val projectedNet = sponsor + ticket + merch - communication - payroll
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
                    projectedMerchK = merch,
                    projectedCommunicationCostK = communication,
                    projectedNetWeeklyK = projectedNet,
                    shirtPriceEur = shirtPrice,
                    pressRating = press,
                    channelLevel = channel,
                    fanMood = mood,
                    socialMassK = socialMassK,
                    environment = environment,
                    marketTrend = trend,
                    refereeVerdictLabel = when (state.refereeLastVerdict.uppercase()) {
                        "FAVORED" -> "Beneficiado"
                        "HARMED" -> "Perjudicado"
                        else -> "Neutro"
                    },
                    financeNews = financeNews,
                )
            }
        }
    }
}
