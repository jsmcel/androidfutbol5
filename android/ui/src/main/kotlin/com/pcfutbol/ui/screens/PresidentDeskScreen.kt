package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.pcfutbol.core.data.db.PRESIDENT_CAP_BALANCED
import com.pcfutbol.core.data.db.PRESIDENT_CAP_FLEX
import com.pcfutbol.core.data.db.PRESIDENT_CAP_STRICT
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosGreen
import com.pcfutbol.ui.theme.DosRed
import com.pcfutbol.ui.theme.DosWhite
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.PresidentAction
import com.pcfutbol.ui.viewmodels.PresidentDeskViewModel

@Composable
fun PresidentDeskScreen(
    onNavigateUp: () -> Unit,
    vm: PresidentDeskViewModel = hiltViewModel(),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
                }
                Text(
                    text = "DESPACHO DEL PRESIDENTE",
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = "J${state.matchday} ${state.season}",
                color = DosGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }

        if (state.loading) {
            Spacer(Modifier.height(14.dp))
            Text("Cargando...", color = DosGray, fontFamily = FontFamily.Monospace)
            return@Column
        }

        if (!state.available) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = state.error ?: "No disponible.",
                color = DosGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DosPanel(title = "CONTEXTO") {
                DeskLine("Club", state.teamName, DosCyan)
                DeskLine("Propiedad", state.ownershipLabel, DosYellow)
                DeskLine("Presupuesto", "${state.budgetK}K", DosGreen)
                DeskLine("Presion junta", state.pressure.toString(), pressureColor(state.pressure))
            }

            DosPanel(title = "TOPE SALARIAL") {
                DeskLine("Modo", state.salaryCapModeLabel, DosCyan)
                DeskLine("Masa semanal", "${state.wageBillK}K", DosWhite)
                DeskLine("Tope", "${state.salaryCapK}K", DosWhite)
                DeskLine(
                    "Margen",
                    "${if (state.salaryCapMarginK >= 0) "+" else ""}${state.salaryCapMarginK}K",
                    if (state.salaryCapMarginK >= 0) DosGreen else DosRed,
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CapButton(
                        label = "ESTRICTO",
                        active = state.salaryCapModeKey == PRESIDENT_CAP_STRICT,
                        onClick = { vm.setSalaryCapMode(PRESIDENT_CAP_STRICT) },
                    )
                    CapButton(
                        label = "EQUILIBRADO",
                        active = state.salaryCapModeKey == PRESIDENT_CAP_BALANCED,
                        onClick = { vm.setSalaryCapMode(PRESIDENT_CAP_BALANCED) },
                    )
                    CapButton(
                        label = "FLEX",
                        active = state.salaryCapModeKey == PRESIDENT_CAP_FLEX,
                        onClick = { vm.setSalaryCapMode(PRESIDENT_CAP_FLEX) },
                    )
                }
            }

            DosPanel(title = "OPERACIONES") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DosButton(
                        text = "INVERSOR",
                        onClick = { vm.trigger(PresidentAction.PRIVATE_INVESTOR) },
                        modifier = Modifier.weight(1f),
                        color = DosCyan,
                    )
                    DosButton(
                        text = "CLUB-ESTADO",
                        onClick = { vm.trigger(PresidentAction.STATE_PROJECT) },
                        modifier = Modifier.weight(1f),
                        color = DosYellow,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DosButton(
                        text = "IPO",
                        onClick = { vm.trigger(PresidentAction.IPO) },
                        modifier = Modifier.weight(1f),
                        color = DosCyan,
                        enabled = !state.ipoDone,
                    )
                    DosButton(
                        text = "PELOTAZO",
                        onClick = { vm.trigger(PresidentAction.PELOTAZO) },
                        modifier = Modifier.weight(1f),
                        color = DosYellow,
                        enabled = !state.pelotazoDone,
                    )
                }
                Spacer(Modifier.height(6.dp))
                DeskLine("Rondas inversor", "${state.investorRounds}/2", DosGray)
                DeskLine("IPO ejecutada", if (state.ipoDone) "SI" else "NO", if (state.ipoDone) DosGreen else DosGray)
                DeskLine(
                    "Pelotazo ejecutado",
                    if (state.pelotazoDone) "SI" else "NO",
                    if (state.pelotazoDone) DosGreen else DosGray,
                )
            }

            DosPanel(title = "MERCADO SOCIAL") {
                DeskLine("Masa social", "${state.socialMassK}K", DosCyan)
                DeskLine("Precio camiseta", "${state.shirtPriceEur}€", DosWhite)
                DeskLine("Prensa", state.pressRating.toString(), DosYellow)
                DeskLine("Canal club", state.channelLevel.toString(), DosYellow)
                DeskLine("Animo aficion", state.fanMood.toString(), DosGreen)
                DeskLine("Entorno", state.environment.toString(), DosCyan)
                DeskLine("Tendencia", "${if (state.marketTrend >= 0) "+" else ""}${state.marketTrend}", if (state.marketTrend >= 0) DosGreen else DosRed)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DosButton(
                        text = "CAMISETA +",
                        onClick = { vm.trigger(PresidentAction.SHIRT_PRICE_UP) },
                        modifier = Modifier.weight(1f),
                        color = DosGray,
                    )
                    DosButton(
                        text = "CAMISETA -",
                        onClick = { vm.trigger(PresidentAction.SHIRT_PRICE_DOWN) },
                        modifier = Modifier.weight(1f),
                        color = DosGray,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DosButton(
                        text = "PRENSA",
                        onClick = { vm.trigger(PresidentAction.PRESS_CAMPAIGN) },
                        modifier = Modifier.weight(1f),
                        color = DosCyan,
                    )
                    DosButton(
                        text = "CANAL",
                        onClick = { vm.trigger(PresidentAction.CHANNEL_EXPANSION) },
                        modifier = Modifier.weight(1f),
                        color = DosYellow,
                    )
                }
            }

            DosPanel(title = "DECLARACIONES") {
                DeskLine("Moviola", state.refereeVerdictLabel, when {
                    state.refereeBalance <= -2 -> DosRed
                    state.refereeBalance >= 2 -> DosGreen
                    else -> DosGray
                })
                DeskLine(
                    "Balance arbitral",
                    "${if (state.refereeBalance >= 0) "+" else ""}${state.refereeBalance}",
                    if (state.refereeBalance >= 0) DosGreen else DosRed,
                )
                DeskLine(
                    "Declaracion en J",
                    if (state.lastStatementMatchday == state.matchday) "YA HECHA" else "DISPONIBLE",
                    if (state.lastStatementMatchday == state.matchday) DosGray else DosCyan,
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DosButton(
                        text = "DT CALMA",
                        onClick = { vm.trigger(PresidentAction.COACH_CALM) },
                        modifier = Modifier.weight(1f),
                        color = DosGray,
                    )
                    DosButton(
                        text = "DT EXIGE",
                        onClick = { vm.trigger(PresidentAction.COACH_DEMAND) },
                        modifier = Modifier.weight(1f),
                        color = DosGray,
                    )
                    DosButton(
                        text = "DT ARBITRO",
                        onClick = { vm.trigger(PresidentAction.COACH_REFEREE) },
                        modifier = Modifier.weight(1f),
                        color = DosGray,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DosButton(
                        text = "PRESI CALMA",
                        onClick = { vm.trigger(PresidentAction.PRESIDENT_CALM) },
                        modifier = Modifier.weight(1f),
                        color = DosCyan,
                    )
                    DosButton(
                        text = "PRESI EXIGE",
                        onClick = { vm.trigger(PresidentAction.PRESIDENT_DEMAND) },
                        modifier = Modifier.weight(1f),
                        color = DosCyan,
                    )
                    DosButton(
                        text = "PRESI ARBITRO",
                        onClick = { vm.trigger(PresidentAction.PRESIDENT_REFEREE) },
                        modifier = Modifier.weight(1f),
                        color = DosCyan,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.refereeMoviola,
                    color = DosWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }

            state.message?.let { message ->
                DosPanel(title = "ULTIMA DECISION") {
                    Text(
                        text = message,
                        color = DosWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    DosButton(
                        text = "CERRAR MENSAJE",
                        onClick = vm::dismissMessage,
                        modifier = Modifier.fillMaxWidth(),
                        color = DosGray,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeskLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun RowScope.CapButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    DosButton(
        text = label,
        onClick = onClick,
        modifier = Modifier.weight(1f),
        color = if (active) DosGreen else DosGray,
    )
}

private fun pressureColor(pressure: Int): androidx.compose.ui.graphics.Color = when {
    pressure >= 9 -> DosRed
    pressure >= 6 -> DosYellow
    else -> DosGreen
}
