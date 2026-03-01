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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosGreen
import com.pcfutbol.ui.theme.DosLightGray
import com.pcfutbol.ui.theme.DosRed
import com.pcfutbol.ui.theme.DosWhite
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.FinanceViewModel

@Composable
fun FinanceScreen(
    onNavigateUp: () -> Unit,
    vm: FinanceViewModel = hiltViewModel(),
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
                    text = "ECONOMIA",
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = "J${state.matchday} ${state.season}",
                color = DosLightGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando datos...", color = DosGray, fontFamily = FontFamily.Monospace)
            }
            return@Column
        }

        Spacer(Modifier.height(6.dp))
        DosPanel(title = "CAJA ${state.teamName}") {
            FinanceLine("Presupuesto", "${state.budgetK}K", DosGreen)
            FinanceLine("Nomina semanal", "-${state.payrollWeeklyK}K", DosRed)
            FinanceLine("Sponsor (estim.)", "+${state.projectedSponsorK}K", DosCyan)
            FinanceLine("Taquilla (estim.)", "+${state.projectedTicketK}K", DosCyan)
            FinanceLine("Merchandising (estim.)", "+${state.projectedMerchK}K", DosCyan)
            FinanceLine("Comunicacion (estim.)", "-${state.projectedCommunicationCostK}K", DosRed)
            val net = state.projectedNetWeeklyK
            FinanceLine("Balance semanal", "${if (net >= 0) "+" else ""}${net}K", if (net >= 0) DosGreen else DosRed)
            FinanceLine("Valor plantilla", "${state.squadMarketValueK}K", DosYellow)
            FinanceLine("Masa social", "${state.socialMassK}K", DosCyan)
            FinanceLine("Precio camiseta", "${state.shirtPriceEur}€", DosWhite)
            FinanceLine("Prensa / Canal", "${state.pressRating} / ${state.channelLevel}", DosYellow)
            FinanceLine("Animo / Entorno", "${state.fanMood} / ${state.environment}", DosYellow)
            FinanceLine("Tendencia mercado", "${if (state.marketTrend >= 0) "+" else ""}${state.marketTrend}", if (state.marketTrend >= 0) DosGreen else DosRed)
            FinanceLine("Moviola", state.refereeVerdictLabel, DosGray)
        }

        Spacer(Modifier.height(8.dp))
        DosPanel(title = "MOVIMIENTOS RECIENTES", modifier = Modifier.weight(1f)) {
            if (state.financeNews.isEmpty()) {
                Text("Sin movimientos financieros todavia.", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.financeNews) { news ->
                        Column {
                            Text(
                                text = "J${news.matchday} - ${news.titleEs}",
                                color = DosYellow,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = news.bodyEs,
                                color = DosWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}
