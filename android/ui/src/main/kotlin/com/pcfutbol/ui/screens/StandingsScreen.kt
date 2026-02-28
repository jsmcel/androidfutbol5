package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.components.StandingRow
import com.pcfutbol.ui.theme.*
import com.pcfutbol.ui.viewmodels.StandingsViewModel

@Composable
fun StandingsScreen(
    competitionCode: String,
    onNavigateUp: () -> Unit,
    vm: StandingsViewModel = hiltViewModel(),
) {
    val standings by vm.standings(competitionCode).collectAsState(initial = emptyList())
    val compName = CompetitionDefinitions.displayName(competitionCode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        // Barra superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Text(
                text       = compName.uppercase(),
                color      = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(8.dp))

        DosPanel(title = "CLASIFICACIÓN") {
            // Cabecera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text("Pos", color = DosGray, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                Text("Equipo", color = DosGray, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                listOf("PJ", "G", "E", "P", "GF", "GC", "Pts").forEach { h ->
                    Text(h, color = DosGray, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.width(28.dp))
                }
            }
            HorizontalDivider(color = DosGray, thickness = 1.dp)

            LazyColumn {
                itemsIndexed(standings) { _, s ->
                    StandingRow(
                        position  = s.position,
                        teamName  = s.nameShort,
                        played    = s.played,
                        won       = s.won,
                        drawn     = s.drawn,
                        lost      = s.lost,
                        gf        = s.goalsFor,
                        ga        = s.goalsAgainst,
                        points    = s.points,
                    )
                }
            }
        }
    }
}
