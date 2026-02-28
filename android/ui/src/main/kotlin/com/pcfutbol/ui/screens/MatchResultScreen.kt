package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.*
import com.pcfutbol.ui.viewmodels.MatchResultViewModel

@Composable
fun MatchResultScreen(
    fixtureId: Int,
    onNavigateUp: () -> Unit,
    vm: MatchResultViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(fixtureId) { vm.load(fixtureId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Text(
                text = "RESULTADO",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando...", color = DosGray, fontFamily = FontFamily.Monospace)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error!!, color = DosRed, fontFamily = FontFamily.Monospace)
            }
            state.fixture != null -> {
                val f = state.fixture!!

                // Scoreboard
                DosPanel(title = "JORNADA ${f.matchday}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // Home team
                        Text(
                            text = state.homeName,
                            color = DosWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )

                        // Score
                        if (f.played) {
                            Text(
                                text = "${f.homeGoals}  -  ${f.awayGoals}",
                                color = DosYellow,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        } else {
                            Text(
                                text = "?  -  ?",
                                color = DosGray,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }

                        // Away team
                        Text(
                            text = state.awayName,
                            color = DosWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Result label
                    if (f.played) {
                        val label = when {
                            f.homeGoals > f.awayGoals -> "VICTORIA LOCAL"
                            f.homeGoals < f.awayGoals -> "VICTORIA VISITANTE"
                            else                      -> "EMPATE"
                        }
                        Text(
                            text = label,
                            color = DosCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Competition info
                DosPanel(title = "INFO") {
                    InfoLine("Competición", f.competitionCode)
                    InfoLine("Jornada", f.matchday.toString())
                    InfoLine("Estado", if (f.played) "Jugado" else "Pendiente")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label.padEnd(14),
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            color = DosWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}
