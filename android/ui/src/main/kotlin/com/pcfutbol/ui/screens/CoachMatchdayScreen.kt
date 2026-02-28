package com.pcfutbol.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.core.data.db.FixtureEntity
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
import com.pcfutbol.ui.viewmodels.CoachCommand
import com.pcfutbol.ui.viewmodels.MatchdayViewModel
import kotlinx.coroutines.delay
import org.json.JSONArray

@Composable
fun CoachMatchdayScreen(
    matchday: Int,
    onNavigateUp: () -> Unit,
    onMatchResult: (Int) -> Unit,
    onSeasonComplete: () -> Unit = {},
    vm: MatchdayViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val replayEvents = remember { mutableStateListOf<CoachEventUi>() }
    var ballZone by remember { mutableIntStateOf(2) } // 0 ataque local, 4 ataque visitante
    var selectedFixtureId by remember(matchday) { mutableIntStateOf(-1) }
    var replayNonce by remember { mutableIntStateOf(0) }
    var command by remember { mutableStateOf(CoachCommand.BALANCED) }

    LaunchedEffect(matchday) { vm.loadMatchday(matchday) }
    LaunchedEffect(state.seasonComplete) { if (state.seasonComplete) onSeasonComplete() }
    LaunchedEffect(state.coachCommand) { command = state.coachCommand }

    LaunchedEffect(state.fixtures, state.managerTeamId) {
        if (state.fixtures.isEmpty()) {
            selectedFixtureId = -1
        } else if (selectedFixtureId <= 0 || state.fixtures.none { it.id == selectedFixtureId }) {
            selectedFixtureId = state.fixtures.firstOrNull {
                it.homeTeamId == state.managerTeamId || it.awayTeamId == state.managerTeamId
            }?.id ?: state.fixtures.first().id
        }
    }

    val selectedFixture = state.fixtures.firstOrNull { it.id == selectedFixtureId }
    val selectedEvents = remember(selectedFixture?.eventsJson) {
        parseFixtureEvents(selectedFixture?.eventsJson.orEmpty())
    }

    LaunchedEffect(selectedFixture?.id, selectedFixture?.eventsJson, replayNonce) {
        replayEvents.clear()
        ballZone = 2
        selectedFixture ?: return@LaunchedEffect
        replayEvents += CoachEventUi(
            minute = 1,
            type = "KICKOFF",
            teamId = -1,
            description = "INICIO DEL PARTIDO",
        )
        if (!selectedFixture.played || selectedEvents.isEmpty()) return@LaunchedEffect
        val homeTeamId = selectedFixture.homeTeamId
        selectedEvents.sortedBy { it.minute }.forEach { event ->
            delay(140)
            replayEvents += event
            ballZone = zoneForEvent(event, homeTeamId)
        }
    }

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
                    text = "MODO ENTRENADOR - J$matchday",
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            val pending = state.fixtures.any { !it.played }
            DosButton(
                text = if (pending) "SIMULAR" else "REPETIR",
                onClick = {
                    if (pending) {
                        vm.setCoachCommand(command)
                        vm.simulateMatchday(matchday, System.currentTimeMillis(), command)
                    } else {
                        replayNonce += 1
                    }
                },
                enabled = !state.loading && selectedFixture != null,
                color = if (pending) DosGreen else DosCyan,
            )
        }

        Spacer(Modifier.height(8.dp))

        CoachCommandBar(
            selected = command,
            onSelect = {
                command = it
                vm.setCoachCommand(it)
            },
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DosPanel(
                title = "PARTIDOS",
                modifier = Modifier.weight(1f),
            ) {
                val managerTeam = state.managerTeamId
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.fixtures.forEach { fixture ->
                        val selected = fixture.id == selectedFixtureId
                        val managerMatch = fixture.homeTeamId == managerTeam || fixture.awayTeamId == managerTeam
                        FixtureRow(
                            fixture = fixture,
                            home = state.teamNames[fixture.homeTeamId] ?: "LOCAL",
                            away = state.teamNames[fixture.awayTeamId] ?: "VISITANTE",
                            selected = selected,
                            managerMatch = managerMatch,
                            onClick = { selectedFixtureId = fixture.id },
                            onOpen = { onMatchResult(fixture.id) },
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(2f)) {
                selectedFixture?.let { fixture ->
                    Scoreboard(
                        fixture = fixture,
                        home = state.teamNames[fixture.homeTeamId] ?: "LOCAL",
                        away = state.teamNames[fixture.awayTeamId] ?: "VISITANTE",
                    )

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .border(1.dp, DosGray),
                    ) {
                        CoachPitch(ballZone = ballZone)
                    }

                    Spacer(Modifier.height(8.dp))

                    DosPanel(
                        title = "NARRACION",
                        modifier = Modifier.weight(1f),
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(replayEvents) { event ->
                                EventLine(event = event, isHome = event.teamId == fixture.homeTeamId)
                            }
                        }
                    }
                } ?: Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, DosGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No hay partidos en esta jornada",
                        color = DosGray,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoachCommandBar(
    selected: CoachCommand,
    onSelect: (CoachCommand) -> Unit,
) {
    val commands = listOf(
        CoachCommand.BALANCED to "Equilibrado",
        CoachCommand.ATTACK_ALL_IN to "A por todas",
        CoachCommand.LOW_BLOCK to "Bloque bajo",
        CoachCommand.HIGH_PRESS to "Presion alta",
        CoachCommand.CALM_GAME to "Calmar",
        CoachCommand.WASTE_TIME to "Perder tiempo",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        commands.forEach { (command, label) ->
            val active = command == selected
            Box(
                modifier = Modifier
                    .border(1.dp, if (active) DosYellow else DosGray)
                    .background(if (active) DosNavy else DosBlack)
                    .clickable { onSelect(command) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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

@Composable
private fun FixtureRow(
    fixture: FixtureEntity,
    home: String,
    away: String,
    selected: Boolean,
    managerMatch: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
) {
    val borderColor = when {
        selected -> DosYellow
        managerMatch -> DosCyan
        else -> DosGray
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor)
            .background(if (selected) DosNavy else DosBlack)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${home.take(12)} vs ${away.take(12)}",
                color = DosWhite,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            val score = if (fixture.played) "${fixture.homeGoals}-${fixture.awayGoals}" else "pendiente"
            Text(
                text = score,
                color = if (fixture.played) DosYellow else DosGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
        Text(
            text = "VER",
            color = DosCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier
                .border(1.dp, DosCyan)
                .clickable(onClick = onOpen)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Scoreboard(
    fixture: FixtureEntity,
    home: String,
    away: String,
) {
    DosPanel(title = "MARCADOR") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = home,
                color = DosCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            Text(
                text = if (fixture.played) "${fixture.homeGoals} - ${fixture.awayGoals}" else "? - ?",
                color = DosYellow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Text(
                text = away,
                color = DosWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CoachPitch(ballZone: Int) {
    val homeColor = DosCyan
    val awayColor = DosLightGray
    val homeFormation = listOf(
        0.5f to 0.88f, 0.15f to 0.72f, 0.38f to 0.72f, 0.62f to 0.72f, 0.85f to 0.72f,
        0.22f to 0.52f, 0.5f to 0.52f, 0.78f to 0.52f, 0.2f to 0.35f, 0.5f to 0.3f, 0.8f to 0.35f,
    )
    val awayFormation = listOf(
        0.5f to 0.12f, 0.15f to 0.28f, 0.38f to 0.28f, 0.62f to 0.28f, 0.85f to 0.28f,
        0.22f to 0.48f, 0.5f to 0.48f, 0.78f to 0.48f, 0.2f to 0.65f, 0.5f to 0.7f, 0.8f to 0.65f,
    )
    val ballTarget = when (ballZone) {
        0 -> 0.12f
        1 -> 0.30f
        2 -> 0.50f
        3 -> 0.70f
        else -> 0.88f
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF0A2E0A), Color(0xFF143D14), Color(0xFF0A2E0A))),
            )
            val line = Color.White.copy(alpha = 0.6f)
            val stroke = Stroke(width = 2f)
            drawRect(line, topLeft = Offset(w * 0.05f, h * 0.05f), size = Size(w * 0.9f, h * 0.9f), style = stroke)
            drawLine(line, Offset(w * 0.05f, h * 0.5f), Offset(w * 0.95f, h * 0.5f), 2f)
            drawCircle(line, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
        }

        @Composable
        fun ChipAt(x: Float, y: Float, color: Color) {
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * x - 6.dp, y = maxHeight * y - 6.dp)
                    .size(12.dp)
                    .background(color = color, shape = CircleShape),
            )
        }

        homeFormation.forEach { (x, y) -> ChipAt(x, y, homeColor) }
        awayFormation.forEach { (x, y) -> ChipAt(x, y, awayColor) }

        Box(
            modifier = Modifier
                .offset(x = maxWidth * 0.5f - 5.dp, y = maxHeight * ballTarget - 5.dp)
                .size(10.dp)
                .background(DosYellow, CircleShape),
        )
    }
}

@Composable
private fun EventLine(event: CoachEventUi, isHome: Boolean) {
    val typeColor = when (event.type) {
        "GOAL" -> DosYellow
        "VAR_DISALLOWED" -> DosRed
        "TIME_WASTING" -> DosGray
        "RED_CARD" -> DosRed
        "YELLOW_CARD" -> DosYellow
        else -> DosLightGray
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "min ${event.minute.toString().padStart(2, '0')}",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.width(54.dp),
        )
        Text(
            text = if (isHome) "L" else "V",
            color = if (event.teamId <= 0) DosGray else if (isHome) DosCyan else DosWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = event.description,
            color = typeColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class CoachEventUi(
    val minute: Int,
    val type: String,
    val teamId: Int,
    val description: String,
)

private fun parseFixtureEvents(eventsJson: String): List<CoachEventUi> {
    if (eventsJson.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(eventsJson)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    CoachEventUi(
                        minute = obj.optInt("minute", 1),
                        type = obj.optString("type", ""),
                        teamId = obj.optInt("teamId", -1),
                        description = obj.optString("description", "Evento"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun zoneForEvent(event: CoachEventUi, homeTeamId: Int): Int {
    val homeSide = event.teamId == homeTeamId
    return when (event.type) {
        "GOAL", "VAR_DISALLOWED" -> if (homeSide) 0 else 4
        "YELLOW_CARD", "RED_CARD", "INJURY", "TIME_WASTING" -> if (homeSide) 1 else 3
        else -> 2
    }
}
