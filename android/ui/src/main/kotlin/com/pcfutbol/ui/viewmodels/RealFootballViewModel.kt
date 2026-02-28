package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class RealStandingRow(
    val position: Int,
    val teamName: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int,
)

data class RealMatchResult(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val date: String,
)

data class RealFootballUiState(
    val selectedLeagueCode: String = "LIGA1",
    val standings: List<RealStandingRow> = emptyList(),
    val results: List<RealMatchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val activeTab: Int = 0,
)

val LEAGUE_MAP = mapOf(
    // Espana
    "LIGA1" to Pair("Spanish La Liga", 4335),
    "LIGA2" to Pair("Spanish La Liga 2", 4400),
    "LIGA2B" to Pair("Primera RFEF Grupo 1", 5086),
    "LIGA2B2" to Pair("Primera RFEF Grupo 2", 5088),
    // Europa
    "PRML" to Pair("English Premier League", 4328),
    "SERIA" to Pair("Italian Serie A", 4332),
    "LIG1" to Pair("French Ligue 1", 4334),
    "BUN1" to Pair("German Bundesliga", 4331),
    "ERED" to Pair("Dutch Eredivisie", 4337),
    "PRIM" to Pair("Portuguese Primeira Liga", 4344),
    "BELGA" to Pair("Belgian Pro League", 4338),
    "SUPERL" to Pair("Turkish Super Lig", 4339),
    "SCOT" to Pair("Scottish Premiership", 4330),
    "RPL" to Pair("Russian Premier League", 4355),
    "DSL" to Pair("Danish Superliga", 4340),
    "EKSTR" to Pair("Polish Ekstraklasa", 4422),
    "ABUND" to Pair("Austrian Bundesliga", 4621),
    // America
    "ARGPD" to Pair("Argentine Primera Division", 4406),
    "BRASEA" to Pair("Brazilian Serie A", 4351),
    "LIGAMX" to Pair("Mexican Liga MX", 4350),
    // Asia
    "SPL" to Pair("Saudi Pro League", 4668),
)

@HiltViewModel
class RealFootballViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RealFootballUiState())
    val uiState: StateFlow<RealFootballUiState> = _uiState.asStateFlow()

    init {
        fetchData()
    }

    fun selectLeague(code: String) {
        if (code !in LEAGUE_MAP || code == _uiState.value.selectedLeagueCode) return
        _uiState.update {
            it.copy(
                selectedLeagueCode = code,
                standings = emptyList(),
                results = emptyList(),
                error = null,
            )
        }
        fetchData()
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(activeTab = if (tab == 1) 1 else 0) }
    }

    private fun fetchData() {
        val leagueCode = _uiState.value.selectedLeagueCode
        val leagueId = LEAGUE_MAP[leagueCode]?.second ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loading = true, error = null) }

            val standingsResult = runCatching { fetchStandings(leagueId) }
            val resultsResult = runCatching { fetchResults(leagueId) }

            _uiState.update { current ->
                if (current.selectedLeagueCode != leagueCode) {
                    current
                } else {
                    current.copy(
                        standings = standingsResult.getOrElse { emptyList() },
                        results = resultsResult.getOrElse { emptyList() },
                        loading = false,
                        error = if (standingsResult.isFailure && resultsResult.isFailure) "Sin conexión" else null,
                    )
                }
            }
        }
    }

    private fun fetchStandings(leagueId: Int): List<RealStandingRow> {
        val raw = httpGet("$BASE_URL/lookuptable.php?l=$leagueId&s=2024-2025")
        val root = JSONObject(raw)
        val table = root.optJSONArray("table") ?: return emptyList()
        val rows = mutableListOf<RealStandingRow>()

        for (i in 0 until table.length()) {
            val row = table.optJSONObject(i) ?: continue
            rows += RealStandingRow(
                position = row.optString("intRank").toIntOrNull() ?: (i + 1),
                teamName = row.optString("name", "Equipo"),
                played = row.optString("played").toIntOrNull() ?: row.optInt("played", 0),
                won = row.optString("win").toIntOrNull() ?: row.optInt("win", 0),
                drawn = row.optString("draw").toIntOrNull() ?: row.optInt("draw", 0),
                lost = row.optString("loss").toIntOrNull() ?: row.optInt("loss", 0),
                goalsFor = row.optString("goalsfor").toIntOrNull() ?: row.optInt("goalsfor", 0),
                goalsAgainst = row.optString("goalsagainst").toIntOrNull() ?: row.optInt("goalsagainst", 0),
                points = row.optString("total").toIntOrNull() ?: row.optInt("total", 0),
            )
        }

        return rows.sortedBy { it.position }
    }

    private fun fetchResults(leagueId: Int): List<RealMatchResult> {
        val raw = httpGet("$BASE_URL/eventspastleague.php?id=$leagueId")
        val root = JSONObject(raw)
        val events = root.optJSONArray("events") ?: return emptyList()
        val results = mutableListOf<RealMatchResult>()

        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            results += RealMatchResult(
                homeTeam = ev.optString("strHomeTeam", "Local"),
                awayTeam = ev.optString("strAwayTeam", "Visitante"),
                homeScore = ev.optString("intHomeScore").ifBlank { "-" },
                awayScore = ev.optString("intAwayScore").ifBlank { "-" },
                date = ev.optString("dateEvent", "").ifBlank { "--" },
            )
        }

        return results
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true

            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("HTTP $status")
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://www.thesportsdb.com/api/v1/json/3"
    }
}
