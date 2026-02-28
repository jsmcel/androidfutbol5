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
import com.pcfutbol.ui.viewmodels.ContractsViewModel

@Composable
fun ContractsScreen(
    onNavigateUp: () -> Unit,
    vm: ContractsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val players = vm.visibleContracts()
    var selectedPlayer by remember { mutableStateOf<PlayerEntity?>(null) }

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
                    text = "CONTRATOS",
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            val budgetStr = when {
                state.budgetK >= 1_000_000 -> "${"%.1f".format(state.budgetK / 1_000_000.0)}M€"
                state.budgetK >= 1_000 -> "${"%.0f".format(state.budgetK / 1_000.0)}K€"
                else -> "${state.budgetK}K€"
            }
            Text(
                text = budgetStr,
                color = DosGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip(
                label = "TODOS",
                active = !state.expiringOnly,
                onClick = { vm.setExpiringOnly(false) },
            )
            ToggleChip(
                label = "EXPIRAN <=1A",
                active = state.expiringOnly,
                onClick = { vm.setExpiringOnly(true) },
            )
        }

        Spacer(Modifier.height(8.dp))

        DosPanel(title = "PLANTILLA ${state.teamName} (${players.size})", modifier = Modifier.weight(1f)) {
            if (players.isEmpty()) {
                Text("No hay contratos para mostrar.", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("JUGADOR", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("FIN", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.width(42.dp))
                    Text("SAL", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.width(46.dp))
                    Text("CLAU", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.width(56.dp))
                }
                Divider(color = DosGray.copy(alpha = 0.3f))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(players) { player ->
                        ContractRow(
                            player = player,
                            seasonYear = state.seasonYear,
                            onClick = { selectedPlayer = player },
                        )
                    }
                }
            }
        }

        state.message?.let { msg ->
            Spacer(Modifier.height(8.dp))
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
                    Text("OK", color = DosCyan, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    selectedPlayer?.let { player ->
        ContractEditDialog(
            player = player,
            seasonYear = state.seasonYear,
            onDismiss = { selectedPlayer = null },
            onRenew = { endYear, wage, clause ->
                vm.renewContract(player.id, endYear, wage, clause)
                selectedPlayer = null
            },
            onRescind = {
                vm.rescindContract(player.id)
                selectedPlayer = null
            },
        )
    }
}

@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .border(1.dp, if (active) DosYellow else DosGray)
            .background(if (active) DosNavy else DosBlack)
            .clickable(onClick = onClick)
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

@Composable
private fun ContractRow(
    player: PlayerEntity,
    seasonYear: Int,
    onClick: () -> Unit,
) {
    val yearsLeft = (player.contractEndYear - seasonYear).coerceAtLeast(0)
    val endColor = when {
        yearsLeft <= 0 -> DosRed
        yearsLeft == 1 -> DosYellow
        else -> DosGreen
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = player.nameShort,
            color = DosWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = "${player.contractEndYear}",
            color = endColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(42.dp),
        )
        Text(
            text = "${player.wageK}K",
            color = DosCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(46.dp),
        )
        Text(
            text = "${player.releaseClauseK}K",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(56.dp),
        )
    }
}

@Composable
private fun ContractEditDialog(
    player: PlayerEntity,
    seasonYear: Int,
    onDismiss: () -> Unit,
    onRenew: (Int, Int, Int) -> Unit,
    onRescind: () -> Unit,
) {
    var endYearText by remember { mutableStateOf(player.contractEndYear.toString()) }
    var wageText by remember { mutableStateOf(player.wageK.toString()) }
    var clauseText by remember { mutableStateOf(player.releaseClauseK.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DosNavy,
        titleContentColor = DosYellow,
        title = {
            Text(
                text = "Contrato: ${player.nameShort}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Temporada base: $seasonYear", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                ContractField(label = "Fin contrato", value = endYearText, onValueChange = { endYearText = it.filter(Char::isDigit) })
                ContractField(label = "Salario K/sem", value = wageText, onValueChange = { wageText = it.filter(Char::isDigit) })
                ContractField(label = "Clausula K", value = clauseText, onValueChange = { clauseText = it.filter(Char::isDigit) })
            }
        },
        confirmButton = {
            DosButton(
                text = "RENOVAR",
                onClick = {
                    val end = endYearText.toIntOrNull() ?: player.contractEndYear
                    val wage = wageText.toIntOrNull() ?: player.wageK
                    val clause = clauseText.toIntOrNull() ?: player.releaseClauseK
                    onRenew(end, wage, clause)
                },
                color = DosGreen,
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DosButton(
                    text = "RESCINDIR",
                    onClick = onRescind,
                    color = DosRed,
                )
                TextButton(onClick = onDismiss) {
                    Text("CANCELAR", color = DosGray, fontFamily = FontFamily.Monospace)
                }
            }
        },
    )
}

@Composable
private fun ContractField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = FontFamily.Monospace, color = DosGray, fontSize = 11.sp) },
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
