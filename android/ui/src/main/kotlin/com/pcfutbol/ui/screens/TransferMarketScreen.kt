package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosGreen
import com.pcfutbol.ui.theme.DosLightGray
import com.pcfutbol.ui.theme.DosNavy
import com.pcfutbol.ui.theme.DosRed
import com.pcfutbol.ui.theme.DosWhite
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.MarketTab
import com.pcfutbol.ui.viewmodels.TransferMarketViewModel

private val POSITIONS = listOf("ALL", "PO", "DF", "MC", "DC")

@Composable
fun TransferMarketScreen(
    onNavigateUp: () -> Unit,
    vm: TransferMarketViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val players = vm.visiblePlayers()
    var selectedBuy by remember { mutableStateOf<PlayerEntity?>(null) }
    var selectedSell by remember { mutableStateOf<PlayerEntity?>(null) }

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
                text = "MERCADO",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(48.dp))
        }

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

        Spacer(Modifier.height(6.dp))

        MarketTabs(
            selected = state.tab,
            onSelect = vm::setTab,
        )

        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = vm::setSearch,
            placeholder = {
                Text("Buscar jugador...", fontFamily = FontFamily.Monospace, color = DosGray, fontSize = 12.sp)
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
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        )

        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(POSITIONS) { pos ->
                val selected = pos == state.filterPosition
                Box(
                    modifier = Modifier
                        .border(1.dp, if (selected) DosCyan else DosGray)
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

        DosPanel(title = panelTitle(state.tab, players.size), modifier = Modifier.weight(1f)) {
            if (state.loading) {
                Text("Cargando...", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            } else if (players.isEmpty()) {
                Text("No hay jugadores con estos filtros.", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            } else {
                HeaderForTab(state.tab)
                Divider(color = DosGray.copy(alpha = 0.3f))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(players) { player ->
                        val team = vm.originTeamName(player)
                        MarketPlayerRow(
                            player = player,
                            teamName = team,
                            tab = state.tab,
                            enabled = state.windowOpen,
                            onClick = {
                                if (state.tab == MarketTab.MY_SQUAD) selectedSell = player else selectedBuy = player
                            },
                        )
                    }
                }
            }
        }

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
                Text(msg, color = DosYellow, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::dismissMessage) {
                    Text("OK", color = DosCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }

    selectedBuy?.let { player ->
        OfferDialog(
            title = if (player.teamSlotId == null) "Fichar agente libre" else "Fichar de club",
            player = player,
            windowOpen = state.windowOpen,
            minOfferK = (estimateValueK(player.ca) * if (player.teamSlotId == null) 0.55 else 0.75).toInt(),
            actionLabel = "OFRECER",
            onDismiss = { selectedBuy = null },
            onAction = { amount ->
                vm.makeOffer(player.pid, amount)
                selectedBuy = null
            },
        )
    }

    selectedSell?.let { player ->
        OfferDialog(
            title = "Vender jugador",
            player = player,
            windowOpen = state.windowOpen,
            minOfferK = (estimateValueK(player.ca) * 0.55).toInt(),
            actionLabel = "VENDER",
            onDismiss = { selectedSell = null },
            onAction = { amount ->
                vm.sell(player.pid, amount)
                selectedSell = null
            },
        )
    }
}

@Composable
private fun MarketTabs(
    selected: MarketTab,
    onSelect: (MarketTab) -> Unit,
) {
    val tabs = listOf(
        MarketTab.FREE_AGENTS to "AGENTES",
        MarketTab.CLUB_PLAYERS to "CLUBES",
        MarketTab.MY_SQUAD to "VENTAS",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { (tab, label) ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .border(1.dp, if (active) DosYellow else DosGray)
                    .background(if (active) DosNavy else DosBlack)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    color = if (active) DosYellow else DosLightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun panelTitle(tab: MarketTab, size: Int): String = when (tab) {
    MarketTab.FREE_AGENTS -> "AGENTES LIBRES ($size)"
    MarketTab.CLUB_PLAYERS -> "JUGADORES EN CLUBES ($size)"
    MarketTab.MY_SQUAD -> "TU PLANTILLA EN MERCADO ($size)"
}

@Composable
private fun HeaderForTab(tab: MarketTab) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("JUGADOR", color = DosGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        if (tab != MarketTab.FREE_AGENTS) {
            Text("EQUIPO", color = DosGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(84.dp))
        }
        Text("POS", color = DosGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(28.dp))
        Text("CA", color = DosGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
        Text("VALOR", color = DosGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(52.dp))
    }
}

@Composable
private fun MarketPlayerRow(
    player: PlayerEntity,
    teamName: String,
    tab: MarketTab,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = player.nameShort,
            color = if (enabled) DosWhite else DosGray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (tab != MarketTab.FREE_AGENTS) {
            Text(
                text = teamName.take(12),
                color = DosLightGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(84.dp),
                maxLines = 1,
            )
        }
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
        Text(
            text = "${estimateValueK(player.ca)}K",
            color = DosGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(52.dp),
        )
    }
}

@Composable
private fun OfferDialog(
    title: String,
    player: PlayerEntity,
    windowOpen: Boolean,
    minOfferK: Int,
    actionLabel: String,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    val estValueK = estimateValueK(player.ca)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DosNavy,
        titleContentColor = DosYellow,
        title = {
            Text(
                text = "$title: ${player.nameShort}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Posicion: ${player.position} | Media: ${player.ca}", color = DosWhite, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("Valor estimado: ${estValueK}K", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("Precio minimo recomendado: ${minOfferK}K", color = DosYellow, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                if (!windowOpen) {
                    Text("VENTANA CERRADA", color = DosRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = { Text("Importe (K)", fontFamily = FontFamily.Monospace, color = DosGray, fontSize = 11.sp) },
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
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    )
                }
            }
        },
        confirmButton = {
            if (windowOpen) {
                DosButton(
                    text = actionLabel,
                    onClick = { onAction(amountText.toIntOrNull() ?: 0) },
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

private fun estimateValueK(ca: Int): Int = when {
    ca >= 90 -> ca * 30
    ca >= 80 -> ca * 15
    ca >= 70 -> ca * 7
    ca >= 60 -> ca * 3
    else -> ca
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
    else -> DosWhite
}
