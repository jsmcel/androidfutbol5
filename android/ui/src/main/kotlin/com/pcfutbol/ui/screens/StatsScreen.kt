package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGreen
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosRed
import com.pcfutbol.ui.theme.DosWhite
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.StatsView
import com.pcfutbol.ui.viewmodels.StatsViewModel

@Composable
fun StatsScreen(
    onNavigateUp: () -> Unit,
    vm: StatsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Text(
                text = screenTitle(state.selectedView, state.competitionCode),
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(8.dp))
        StatsTabs(
            selected = state.selectedView,
            onSelect = vm::selectView,
        )
        Spacer(Modifier.height(8.dp))

        DosPanel(
            title = panelTitle(state.selectedView),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = DosCyan)
                    }
                }

                state.error != null -> {
                    Text(
                        text = state.error ?: "Error desconocido",
                        color = DosRed,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }

                state.selectedView == StatsView.TOP_SCORERS && state.topScorers.isEmpty() -> {
                    EmptyStats("Aun no hay goles registrados.")
                }

                state.selectedView == StatsView.TOP_PLAYERS && state.topPlayers.isEmpty() -> {
                    EmptyStats("Aun no hay ranking de jugadores.")
                }

                state.selectedView == StatsView.TEAM_STRENGTH && state.teamStrength.isEmpty() -> {
                    EmptyStats("Aun no hay ranking de fortaleza.")
                }

                else -> when (state.selectedView) {
                    StatsView.TOP_SCORERS -> TopScorersList(state)
                    StatsView.TOP_PLAYERS -> TopPlayersList(state)
                    StatsView.TEAM_STRENGTH -> TeamStrengthList(state)
                }
            }
        }
    }
}

@Composable
private fun StatsTabs(
    selected: StatsView,
    onSelect: (StatsView) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DosButton(
            text = "GOLEADORES",
            onClick = { onSelect(StatsView.TOP_SCORERS) },
            color = if (selected == StatsView.TOP_SCORERS) DosYellow else DosGray,
            modifier = Modifier.weight(1f),
        )
        DosButton(
            text = "JUGADORES",
            onClick = { onSelect(StatsView.TOP_PLAYERS) },
            color = if (selected == StatsView.TOP_PLAYERS) DosCyan else DosGray,
            modifier = Modifier.weight(1f),
        )
        DosButton(
            text = "EQUIPOS",
            onClick = { onSelect(StatsView.TEAM_STRENGTH) },
            color = if (selected == StatsView.TEAM_STRENGTH) DosGreen else DosGray,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EmptyStats(message: String) {
    Text(
        text = message,
        color = DosGray,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
    )
}

@Composable
private fun TopScorersList(state: com.pcfutbol.ui.viewmodels.StatsUiState) {
    HeaderRowScorers()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        itemsIndexed(state.topScorers) { index, scorer ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}".padStart(2),
                    color = DosCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(0.7f),
                )
                Text(
                    text = scorer.playerName,
                    color = DosWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(3.4f),
                    maxLines = 1,
                )
                Text(
                    text = scorer.teamName,
                    color = DosGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(2.6f),
                    maxLines = 1,
                )
                Text(
                    text = scorer.goals.toString(),
                    color = DosYellow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TopPlayersList(state: com.pcfutbol.ui.viewmodels.StatsUiState) {
    HeaderRowPlayers()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        itemsIndexed(state.topPlayers) { index, player ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}".padStart(2),
                    color = DosCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(0.7f),
                )
                Text(
                    text = player.playerName,
                    color = DosWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(3.0f),
                    maxLines = 1,
                )
                Text(
                    text = player.teamName,
                    color = DosGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(2.3f),
                    maxLines = 1,
                )
                Text(
                    text = player.position,
                    color = DosGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.0f),
                )
                Text(
                    text = String.format("%.1f", player.score),
                    color = DosYellow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.0f),
                )
            }
        }
    }
}

@Composable
private fun TeamStrengthList(state: com.pcfutbol.ui.viewmodels.StatsUiState) {
    HeaderRowTeams()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        itemsIndexed(state.teamStrength) { index, team ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}".padStart(2),
                    color = DosCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(0.7f),
                )
                Text(
                    text = team.teamName,
                    color = DosWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(3.5f),
                    maxLines = 1,
                )
                Text(
                    text = team.competitionCode,
                    color = DosGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1.8f),
                    maxLines = 1,
                )
                Text(
                    text = String.format("%.1f", team.score),
                    color = DosGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.0f),
                )
            }
        }
    }
}

@Composable
private fun HeaderRowScorers() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(0.7f),
        )
        Text(
            text = "JUGADOR",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(3.4f),
        )
        Text(
            text = "EQUIPO",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(2.6f),
        )
        Text(
            text = "GOLES",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeaderRowPlayers() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(0.7f),
        )
        Text(
            text = "JUGADOR",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(3.0f),
        )
        Text(
            text = "EQUIPO",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(2.3f),
        )
        Text(
            text = "POS",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = "ME",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.0f),
        )
    }
}

@Composable
private fun HeaderRowTeams() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(0.7f),
        )
        Text(
            text = "EQUIPO",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(3.5f),
        )
        Text(
            text = "LIGA",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(1.8f),
        )
        Text(
            text = "FZA",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.0f),
        )
    }
}

private fun screenTitle(view: StatsView, competitionCode: String): String = when (view) {
    StatsView.TOP_SCORERS -> "GOLEADORES - $competitionCode"
    StatsView.TOP_PLAYERS -> "TOP JUGADORES"
    StatsView.TEAM_STRENGTH -> "FORTALEZA EQUIPOS"
}

private fun panelTitle(view: StatsView): String = when (view) {
    StatsView.TOP_SCORERS -> "TOP GOLEADORES"
    StatsView.TOP_PLAYERS -> "TOP JUGADORES"
    StatsView.TEAM_STRENGTH -> "RANKING DE FORTALEZA"
}
