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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.components.MatchResultRow
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosGreen
import com.pcfutbol.ui.theme.DosRed
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.ChampionsViewModel

@Composable
fun ChampionsScreen(
    onNavigateUp: () -> Unit,
    vm: ChampionsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val hasPending = state.fixtures.any { !it.played }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Text(
                text = "CHAMPIONS LEAGUE",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "RONDA: ${roundLabel(state.currentRound)}",
            color = DosCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(Modifier.height(8.dp))

        DosPanel(title = "PARTIDOS", modifier = Modifier.weight(1f)) {
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

                state.fixtures.isEmpty() && !state.tournamentComplete -> {
                    Text(
                        text = "Champions no iniciada esta temporada.",
                        color = DosGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    DosButton(
                        text = "INICIAR CHAMPIONS",
                        onClick = vm::setup,
                        color = DosYellow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.fixtures) { fixture ->
                            MatchResultRow(
                                homeTeam = state.teamNames[fixture.homeTeamId]
                                    ?: "Equipo ${fixture.homeTeamId}",
                                awayTeam = state.teamNames[fixture.awayTeamId]
                                    ?: "Equipo ${fixture.awayTeamId}",
                                homeGoals = if (fixture.played) fixture.homeGoals else -1,
                                awayGoals = if (fixture.played) fixture.awayGoals else -1,
                            )
                        }
                    }

                    if (hasPending && !state.tournamentComplete) {
                        Spacer(Modifier.height(12.dp))
                        DosButton(
                            text = "SIMULAR RONDA",
                            onClick = { vm.simulateRound(System.currentTimeMillis()) },
                            color = DosGreen,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (state.tournamentComplete) {
            Spacer(Modifier.height(8.dp))
            DosPanel(title = "CAMPEON DE EUROPA") {
                Text(
                    text = state.winnerName.ifBlank { "Desconocido" },
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Error: $error",
                color = DosRed,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun roundLabel(round: String): String = when (round) {
    "QF" -> "CUARTOS"
    "SF" -> "SEMIFINALES"
    "F" -> "FINAL"
    "DONE" -> "FINALIZADA"
    else -> round
}
