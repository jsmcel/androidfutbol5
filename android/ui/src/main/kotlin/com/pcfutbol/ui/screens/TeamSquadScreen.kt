package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.pcfutbol.ui.theme.*
import com.pcfutbol.ui.viewmodels.TeamSquadViewModel

private val SQUAD_FILTERS = listOf("ALL", "PO", "DF", "MC", "DC")

@Composable
fun TeamSquadScreen(
    onNavigateUp: () -> Unit,
    onLineup: () -> Unit,
    onTactic: () -> Unit,
    onContracts: () -> Unit,
    vm: TeamSquadViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    var detailPlayer by remember { mutableStateOf<PlayerEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.teamName.ifBlank { "MI PLANTILLA" }.uppercase(),
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (state.budgetK > 0) {
                    val budgetStr = when {
                        state.budgetK >= 1_000_000 -> "${"%.1f".format(state.budgetK / 1_000_000.0)}M€"
                        state.budgetK >= 1_000 -> "${"%.0f".format(state.budgetK / 1_000.0)}K€"
                        else -> "${state.budgetK}K€"
                    }
                    Text(
                        text = "PRESUPUESTO: $budgetStr",
                        color = DosGreen,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.width(48.dp))
        }

        // Position filter
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            items(SQUAD_FILTERS) { pos ->
                val selected = pos == state.filter
                Box(
                    modifier = Modifier
                        .border(1.dp, if (selected) DosCyan else DosGray)
                        .background(if (selected) DosCyan.copy(alpha = 0.15f) else DosBlack)
                        .clickable { vm.setFilter(pos) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = pos,
                        color = if (selected) DosCyan else DosGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        // Player list
        DosPanel(
            title = "JUGADORES (${state.players.size})",
            modifier = Modifier.weight(1f),
        ) {
            when {
                state.loading -> Text(
                    "Cargando plantilla...",
                    color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                )
                state.players.isEmpty() -> Text(
                    "Sin jugadores en esta posición.",
                    color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                )
                else -> {
                    // Header row
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("N°", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                        Text("JUGADOR", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text("POS", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.width(28.dp))
                        Text("CA", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                        Text("FOR", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.width(28.dp))
                    }
                    Divider(color = DosGray.copy(alpha = 0.3f))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(state.players) { player ->
                            PlayerRow(
                                player = player,
                                onClick = { detailPlayer = player },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            DosButton(
                text = "ALINEACION",
                onClick = onLineup,
                modifier = Modifier.weight(1f),
                color = DosYellow,
            )
            DosButton(
                text = "TACTICA",
                onClick = onTactic,
                modifier = Modifier.weight(1f),
                color = DosCyan,
            )
        }
        Spacer(Modifier.height(8.dp))
        DosButton(
            text = "CONTRATOS",
            onClick = onContracts,
            modifier = Modifier.fillMaxWidth(),
            color = DosYellow,
        )
    }

    // Player detail dialog
    detailPlayer?.let { player ->
        PlayerDetailDialog(
            player = player,
            onDismiss = { detailPlayer = null },
        )
    }
}

@Composable
private fun PlayerRow(
    player: PlayerEntity,
    onClick: () -> Unit,
) {
    val statusColor = when (player.status) {
        1 -> DosRed      // lesionado
        2 -> DosYellow   // sancionado
        else -> DosWhite
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${player.number}",
            color = DosGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = player.nameShort,
            color = statusColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = player.position,
            color = posSquadColor(player.position),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = "${player.ca}",
            color = caSquadColor(player.ca),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(24.dp),
        )
        // Forma como barra visual simple
        val forma = player.estadoForma
        Text(
            text = "${forma}%",
            color = when {
                forma >= 70 -> DosGreen
                forma >= 40 -> DosYellow
                else        -> DosRed
            },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp),
        )
    }
}

@Composable
private fun PlayerDetailDialog(
    player: PlayerEntity,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DosNavy,
        titleContentColor = DosYellow,
        title = {
            Text(
                text = player.nameFull,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val age = 2026 - player.birthYear
                StatLine("Posición", player.position)
                StatLine("Edad", "$age años")
                StatLine("País", player.citizenship)
                StatLine("Estado", when (player.status) {
                    1 -> "LESIONADO (${player.injuryWeeksLeft}sem)"
                    2 -> "SANCIONADO (${player.sanctionMatchesLeft}p)"
                    else -> "Disponible"
                })
                Divider(color = DosGray.copy(alpha = 0.3f))
                StatLine("Calidad (CA)", "${player.ca}")
                StatLine("Velocidad", "${player.ve}")
                StatLine("Resistencia", "${player.re}")
                StatLine("Regate", "${player.regate}")
                StatLine("Remate", "${player.remate}")
                StatLine("Pase", "${player.pase}")
                StatLine("Tiro", "${player.tiro}")
                StatLine("Entrada", "${player.entrada}")
                if (player.position == "PO") StatLine("Portero", "${player.portero}")
                Divider(color = DosGray.copy(alpha = 0.3f))
                StatLine("Forma", "${player.estadoForma}%")
                StatLine("Moral", "${player.moral}%")
                StatLine("Salario", "${player.wageK}K€/sem")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CERRAR", color = DosCyan, fontFamily = FontFamily.Monospace)
            }
        },
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.padEnd(14),
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            color = DosWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun posSquadColor(pos: String) = when (pos) {
    "PO" -> DosCyan
    "DF" -> DosGreen
    "MC" -> DosYellow
    "DC" -> DosRed
    else -> DosGray
}

private fun caSquadColor(ca: Int) = when {
    ca >= 85 -> DosGreen
    ca >= 70 -> DosYellow
    else     -> DosWhite
}
