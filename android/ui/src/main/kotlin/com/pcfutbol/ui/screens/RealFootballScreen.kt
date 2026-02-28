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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosNavy
import com.pcfutbol.ui.theme.DosRed
import com.pcfutbol.ui.theme.DosWhite
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.LEAGUE_MAP
import com.pcfutbol.ui.viewmodels.RealFootballViewModel
import com.pcfutbol.ui.viewmodels.RealMatchResult
import com.pcfutbol.ui.viewmodels.RealStandingRow

@Composable
fun RealFootballScreen(
    onNavigateUp: () -> Unit,
    vm: RealFootballViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val selectedLeagueName = LEAGUE_MAP[state.selectedLeagueCode]?.first ?: state.selectedLeagueCode

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
                text = "ACTUALIDAD REAL",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(48.dp))
        }

        Text(
            text = "LIGA: $selectedLeagueName (${state.selectedLeagueCode})",
            color = DosCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        DosPanel(
            title = "SELECCION DE LIGA",
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 190.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(LEAGUE_MAP.entries.toList(), key = { it.key }) { entry ->
                    val selected = entry.key == state.selectedLeagueCode
                    val borderColor = if (selected) DosYellow else DosGray
                    val textColor = if (selected) DosYellow else DosWhite
                    val bgColor = if (selected) DosNavy else DosBlack

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, borderColor)
                            .background(bgColor)
                            .clickable { vm.selectLeague(entry.key) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${entry.value.first} (${entry.key})",
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(
                                text = "ACTIVA",
                                color = DosCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DosButton(
                text = "CLASIFICACION",
                onClick = { vm.selectTab(0) },
                modifier = Modifier.weight(1f),
                color = if (state.activeTab == 0) DosYellow else DosCyan,
            )
            DosButton(
                text = "RESULTADOS",
                onClick = { vm.selectTab(1) },
                modifier = Modifier.weight(1f),
                color = if (state.activeTab == 1) DosYellow else DosCyan,
            )
        }

        Spacer(Modifier.height(8.dp))

        DosPanel(
            title = if (state.activeTab == 0) "CLASIFICACION" else "RESULTADOS",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                state.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Cargando...",
                            color = DosGray,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Sin conexión",
                            color = DosRed,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                state.activeTab == 0 -> StandingsTable(state.standings)
                else -> ResultsList(state.results)
            }
        }
    }
}

@Composable
private fun StandingsTable(rows: List<RealStandingRow>) {
    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Sin datos disponibles",
                color = DosGray,
                fontFamily = FontFamily.Monospace,
            )
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Pos", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(28.dp))
        Text("Equipo", color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
        listOf("PJ", "G", "E", "P", "GF", "GC", "Pts").forEach { header ->
            Text(
                text = header,
                color = DosGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.End,
            )
        }
    }

    HorizontalDivider(color = DosGray, thickness = 1.dp)
    Spacer(Modifier.height(4.dp))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(rows, key = { "${it.position}-${it.teamName}" }) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = row.position.toString().padStart(2),
                    color = DosYellow,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = row.teamName.take(22),
                    color = DosWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                listOf(row.played, row.won, row.drawn, row.lost, row.goalsFor, row.goalsAgainst, row.points).forEach { value ->
                    Text(
                        text = value.toString(),
                        color = if (value == row.points) DosYellow else DosWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.width(30.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsList(results: List<RealMatchResult>) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Sin datos disponibles",
                color = DosGray,
                fontFamily = FontFamily.Monospace,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(results.take(20), key = { "${it.date}-${it.homeTeam}-${it.awayTeam}" }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DosGray)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.date,
                    color = DosGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.homeTeam.take(18),
                        color = DosWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = " ${item.homeScore}-${item.awayScore} ",
                        color = DosYellow,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Text(
                        text = item.awayTeam.take(18),
                        color = DosWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
