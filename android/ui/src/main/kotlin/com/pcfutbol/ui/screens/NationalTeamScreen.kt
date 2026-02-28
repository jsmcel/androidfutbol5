package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosGreen
import com.pcfutbol.ui.theme.DosWhite
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.NationalSquadPlayerUi
import com.pcfutbol.ui.viewmodels.NationalTeamViewModel

@Composable
fun NationalTeamScreen(
    onNavigateUp: () -> Unit,
    onTactic: () -> Unit,
    vm: NationalTeamViewModel = hiltViewModel(),
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
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "SELECCION ESPANOLA",
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "FIFA Ranking: #${state.fifaRanking}",
                    color = DosCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(8.dp))

        DosPanel(
            title = "CONVOCADOS (${state.squad.size})",
            modifier = Modifier.weight(1f),
        ) {
            if (state.loading) {
                Text(
                    text = "Cargando convocatoria...",
                    color = DosGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.squad, key = { it.playerId }) { player ->
                        NationalSquadRow(player = player)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        DosPanel(title = "VENTANA INTERNACIONAL") {
            Text(
                text = "${state.tournamentName} - ${state.tournamentStage}",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Proximo rival: ${state.nextOpponent}",
                color = DosWhite,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = if (state.windowOpen) {
                    "Ventana activa en jornada ${state.currentMatchday}"
                } else {
                    "Sin ventana activa (jornadas 13-14 y 26-27)"
                },
                color = if (state.windowOpen) DosGreen else DosGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )

            state.lastResult?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    color = DosCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            state.infoMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    color = DosYellow,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DosButton(
                    text = "TACTICA",
                    onClick = onTactic,
                    modifier = Modifier.weight(1f),
                    color = DosCyan,
                )
                DosButton(
                    text = if (state.managerIsCoach) "SIMULAR PARTIDO" else "SIMULACION IA",
                    onClick = vm::simulateNationalMatch,
                    modifier = Modifier.weight(1f),
                    enabled = state.canSimulate,
                    color = DosYellow,
                )
            }
        }
    }
}

@Composable
private fun NationalSquadRow(player: NationalSquadPlayerUi) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${player.position.toString().padStart(2)}.",
                color = DosGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(28.dp),
            )
            Text(
                text = "[${player.role}] ${player.name}",
                color = DosWhite,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = player.teamName,
                color = DosCyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(80.dp),
            )
            Text(
                text = "CA:${player.ca}",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(48.dp),
            )
        }
        HorizontalDivider(color = DosGray.copy(alpha = 0.35f))
    }
}

