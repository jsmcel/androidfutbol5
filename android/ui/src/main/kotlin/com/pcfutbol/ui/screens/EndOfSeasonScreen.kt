package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.economy.SeasonSummary
import com.pcfutbol.economy.TeamResult
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.*
import com.pcfutbol.ui.viewmodels.EndOfSeasonViewModel

@Composable
fun EndOfSeasonScreen(
    onNextSeason: (Boolean) -> Unit,
    vm: EndOfSeasonViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()

    // Auto-apply end of season on first load
    LaunchedEffect(state.summary, state.applied) {
        if (state.summary != null && !state.applied) {
            vm.applyEndOfSeason()
        }
    }

    // Navigate when next season is ready
    LaunchedEffect(state.nextSeasonReady, state.nextRouteOffers) {
        if (state.nextSeasonReady) onNextSeason(state.nextRouteOffers)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        // Title banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DosNavy)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "FIN DE TEMPORADA",
                color = DosYellow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 2.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DosCyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Calculando resultados...", color = DosGray,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error!!, color = DosRed, fontFamily = FontFamily.Monospace)
            }
            state.summary != null -> {
                val s = state.summary!!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Season label
                    Text(
                        text = "Temporada ${s.season}",
                        color = DosGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )

                    // Champion
                    s.champion?.let { ch ->
                        DosPanel(title = "CAMPEÓN DE LIGA") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("★", color = DosYellow, fontSize = 22.sp)
                                Column {
                                    Text(
                                        text = ch.name,
                                        color = DosYellow,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                    )
                                    Text(
                                        text = "${ch.points} puntos",
                                        color = DosGray,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }

                    // Manager objective
                    DosPanel(title = "TU TEMPORADA") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SummaryLine("Posición final", "${s.managerPosition}º")
                            SummaryLine("Puntos", "${s.managerPoints} pts")
                            SummaryLine("Objetivo", s.objective)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Resultado",
                                    color = DosGray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                )
                                val (resultText, resultColor) = if (s.objectiveMet)
                                    "CUMPLIDO ✓" to DosGreen
                                else
                                    "NO CUMPLIDO ✗" to DosRed
                                Text(
                                    text = resultText,
                                    color = resultColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    // Relegated
                    if (s.relegated.isNotEmpty()) {
                        DosPanel(title = "DESCENSO A SEGUNDA") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                s.relegated.forEach { team ->
                                    TeamResultRow(team, DosRed)
                                }
                            }
                        }
                    }

                    // Promoted
                    if (s.promotedToLiga1.isNotEmpty()) {
                        DosPanel(title = "ASCENSO A PRIMERA") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                s.promotedToLiga1.forEach { team ->
                                    TeamResultRow(team, DosGreen)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                DosButton(
                    text = "NUEVA TEMPORADA →",
                    onClick = vm::startNextSeason,
                    modifier = Modifier.fillMaxWidth(),
                    color = DosCyan,
                    enabled = state.applied && !state.nextSeasonReady,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text(value, color = DosWhite, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TeamResultRow(team: TeamResult, accent: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${team.position}. ${team.name}",
            color = accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${team.points} pts",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}
