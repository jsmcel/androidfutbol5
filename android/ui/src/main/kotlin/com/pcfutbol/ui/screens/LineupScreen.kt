package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosGreen
import com.pcfutbol.ui.theme.DosRed
import com.pcfutbol.ui.theme.DosWhite
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.LineupViewModel

@Composable
fun LineupScreen(
    onNavigateUp: () -> Unit,
    vm: LineupViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val starterIds = state.starters.map { it.id }.toSet()

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
                }
                Text(
                    text = "ALINEACION",
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            DosButton(
                text = "SELECCION AUTO",
                onClick = vm::autoSelect,
                color = DosCyan,
            )
        }

        Spacer(Modifier.height(8.dp))

        DosPanel(
            title = "TITULARES (${state.starters.size}/11)",
            modifier = Modifier.weight(1f),
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

                state.allPlayers.isEmpty() -> {
                    Text(
                        text = "No hay jugadores disponibles.",
                        color = DosGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }

                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(state.allPlayers) { player ->
                            LineupPlayerRow(
                                player = player,
                                isStarter = player.id in starterIds,
                                onClick = { vm.toggleStarter(player.id) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        DosButton(
            text = "GUARDAR ALINEACION",
            onClick = vm::saveLineup,
            modifier = Modifier.fillMaxWidth(),
            color = DosGreen,
        )
    }
}

@Composable
private fun LineupPlayerRow(
    player: PlayerEntity,
    isStarter: Boolean,
    onClick: () -> Unit,
) {
    val selectable = player.status == 0 && player.injuryWeeksLeft <= 0 && player.sanctionMatchesLeft <= 0
    val nameColor = when {
        !selectable -> DosGray
        isStarter -> DosGreen
        else -> DosWhite
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectable, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isStarter) "X" else " ",
            color = if (isStarter) DosGreen else DosGray,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = "[${player.position}]",
            color = positionColor(player.position),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = player.nameShort,
            color = nameColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = "CA:${player.ca}",
            color = if (selectable) DosYellow else DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
    HorizontalDivider(color = DosGray.copy(alpha = 0.2f))
}

private fun positionColor(position: String) = when (position) {
    "PO" -> DosCyan
    "DF" -> DosGreen
    "MC" -> DosYellow
    "DC" -> DosRed
    else -> DosGray
}
