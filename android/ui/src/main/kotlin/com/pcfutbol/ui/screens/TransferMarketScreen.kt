package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.*
import com.pcfutbol.ui.viewmodels.TransferMarketViewModel

private val POSITIONS = listOf("ALL", "PO", "DF", "MC", "DC")

@Composable
fun TransferMarketScreen(
    onNavigateUp: () -> Unit,
    vm: TransferMarketViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val agents by vm.filteredAgents.collectAsState()

    var selectedPlayer by remember { mutableStateOf<PlayerEntity?>(null) }

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
            Text(
                text = "MERCADO",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(48.dp))
        }

        // Window status + budget banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (state.windowOpen) DosGreen.copy(alpha = 0.2f) else DosRed.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "[ ${state.windowName.uppercase()} ]",
                color = if (state.windowOpen) DosGreen else DosRed,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
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
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Search field
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = vm::setSearch,
            placeholder = {
                Text("Buscar jugador...", fontFamily = FontFamily.Monospace,
                    color = DosGray, fontSize = 12.sp)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DosCyan,
                unfocusedBorderColor = DosGray,
                focusedTextColor = DosWhite,
                unfocusedTextColor = DosWhite,
                cursorColor = DosCyan,
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace, fontSize = 13.sp
            ),
        )

        Spacer(Modifier.height(6.dp))

        // Position filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(POSITIONS) { pos ->
                val selected = pos == state.filterPosition
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (selected) DosCyan else DosGray,
                        )
                        .background(if (selected) DosCyan.copy(alpha = 0.15f) else DosBlack)
                        .clickable { vm.setPositionFilter(pos) }
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

        Spacer(Modifier.height(8.dp))

        // Agent list
        DosPanel(title = "AGENTES LIBRES (${agents.size})", modifier = Modifier.weight(1f)) {
            when {
                state.loading -> Text(
                    "Cargando...", color = DosGray,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                )
                agents.isEmpty() -> Text(
                    "No hay agentes libres con estos filtros.",
                    color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                )
                else -> {
                    // Column headers
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("JUGADOR", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text("POS", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.width(28.dp))
                        Text("CA", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                        Text("VALOR", color = DosGray, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.width(52.dp))
                    }
                    Divider(color = DosGray.copy(alpha = 0.3f))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(agents) { player ->
                            AgentRow(
                                player = player,
                                windowOpen = state.windowOpen,
                                onClick = { selectedPlayer = player },
                            )
                        }
                    }
                }
            }
        }

        // Message toast
        state.message?.let { msg ->
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DosNavy)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, color = DosYellow, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = vm::dismissMessage) {
                    Text("OK", color = DosCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }

    // Offer dialog
    selectedPlayer?.let { player ->
        OfferDialog(
            player = player,
            windowOpen = state.windowOpen,
            onDismiss = { selectedPlayer = null },
            onOffer = { amount ->
                vm.makeOffer(player.pid, amount)
                selectedPlayer = null
            },
        )
    }
}

@Composable
private fun AgentRow(
    player: PlayerEntity,
    windowOpen: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = windowOpen, onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = player.nameShort,
            color = if (windowOpen) DosWhite else DosGray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = player.position,
            color = positionColor(player.position),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = "${player.ca}",
            color = caColor(player.ca),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )
        // Simplified market value display (ca-based estimate)
        val estValueK = estimateValueK(player.ca)
        Text(
            text = "${estValueK}K",
            color = DosGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(52.dp),
        )
    }
}

@Composable
private fun OfferDialog(
    player: PlayerEntity,
    windowOpen: Boolean,
    onDismiss: () -> Unit,
    onOffer: (Int) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    val estValueK = estimateValueK(player.ca)
    val minOfferK = (estValueK * 0.8).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DosNavy,
        titleContentColor = DosYellow,
        title = {
            Text(
                text = "FICHAR: ${player.nameShort}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Posición: ${player.position}  |  Media: ${player.ca}",
                    color = DosWhite, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                )
                Text(
                    "Valor estimado: ${estValueK}K€",
                    color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                )
                Text(
                    "Oferta mínima: ${minOfferK}K€",
                    color = DosYellow, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                )
                if (!windowOpen) {
                    Text(
                        "VENTANA DE MERCADO CERRADA",
                        color = DosRed, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    )
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = {
                            Text("Tu oferta (K€)", fontFamily = FontFamily.Monospace,
                                color = DosGray, fontSize = 11.sp)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DosCyan,
                            unfocusedBorderColor = DosGray,
                            focusedTextColor = DosWhite,
                            unfocusedTextColor = DosWhite,
                            cursorColor = DosCyan,
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            if (windowOpen) {
                DosButton(
                    text = "OFRECER",
                    onClick = {
                        val amount = amountText.toIntOrNull() ?: 0
                        onOffer(amount)
                    },
                    enabled = amountText.toIntOrNull()?.let { it >= minOfferK } == true,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        },
    )
}

// Approximate market value based on quality (no-dependency helper for UI only)
private fun estimateValueK(ca: Int): Int = when {
    ca >= 90 -> ca * 30
    ca >= 80 -> ca * 15
    ca >= 70 -> ca * 7
    ca >= 60 -> ca * 3
    else     -> ca
}

private fun positionColor(pos: String) = when (pos) {
    "PO" -> DosCyan
    "DF" -> DosGreen
    "MC" -> DosYellow
    "DC" -> DosRed
    else -> DosGray
}

private fun caColor(ca: Int) = when {
    ca >= 85 -> DosGreen
    ca >= 70 -> DosYellow
    else     -> DosWhite
}
