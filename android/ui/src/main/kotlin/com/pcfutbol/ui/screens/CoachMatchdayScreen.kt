package com.pcfutbol.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import kotlin.math.abs
import kotlin.math.max

private const val HUMAN_MATCH_TICK_MS = 3_500L
private const val HUMAN_MAX_EVENT_GAP_MS = 10_500L
private const val HUMAN_SAME_MINUTE_EVENT_GAP_MS = 420L

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
    var ballPosition by remember { mutableStateOf(CoachBallPosition(x = 0.5f, y = 0.5f)) }
    var liveMinute by remember { mutableIntStateOf(1) }
    var liveHomeGoals by remember { mutableIntStateOf(0) }
    var liveAwayGoals by remember { mutableIntStateOf(0) }
    var replayInProgress by remember { mutableStateOf(false) }
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
        ballPosition = CoachBallPosition(x = 0.5f, y = 0.5f)
        liveMinute = 1
        liveHomeGoals = 0
        liveAwayGoals = 0
        replayInProgress = false
        selectedFixture ?: return@LaunchedEffect
        replayEvents += CoachEventUi(
            minute = 1,
            type = "KICKOFF",
            teamId = -1,
            description = "INICIO DEL PARTIDO",
        )
        if (!selectedFixture.played || selectedEvents.isEmpty()) {
            if (selectedFixture.played) {
                liveHomeGoals = selectedFixture.homeGoals.coerceAtLeast(0)
                liveAwayGoals = selectedFixture.awayGoals.coerceAtLeast(0)
                liveMinute = 90
            }
            return@LaunchedEffect
        }
        replayInProgress = true
        val homeTeamId = selectedFixture.homeTeamId
        var previousMinute = 1
        selectedEvents.sortedBy { it.minute }.forEach { event ->
            val minute = event.minute.coerceAtLeast(1)
            val minuteDelta = (minute - previousMinute).coerceAtLeast(0)
            val delayMs = if (minuteDelta == 0) {
                HUMAN_SAME_MINUTE_EVENT_GAP_MS
            } else {
                (minuteDelta * HUMAN_MATCH_TICK_MS).coerceAtMost(HUMAN_MAX_EVENT_GAP_MS)
            }
            delay(delayMs)
            replayEvents += event
            liveMinute = minute
            if (event.type == "GOAL") {
                when (event.teamId) {
                    selectedFixture.homeTeamId -> liveHomeGoals += 1
                    selectedFixture.awayTeamId -> liveAwayGoals += 1
                }
            }
            ballPosition = ballPositionForEvent(event, homeTeamId)
            previousMinute = minute
        }
        liveHomeGoals = selectedFixture.homeGoals.coerceAtLeast(0)
        liveAwayGoals = selectedFixture.awayGoals.coerceAtLeast(0)
        liveMinute = max(liveMinute, 90)
        replayInProgress = false
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
                    text = "PARTIDO - J$matchday (${state.managerControlModeLabel})",
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
                        if (state.coachCommandsEnabled) {
                            vm.setCoachCommand(command)
                        }
                        val cmd = if (state.coachCommandsEnabled) command else CoachCommand.BALANCED
                        vm.simulateMatchday(matchday, System.currentTimeMillis(), cmd)
                    } else {
                        replayNonce += 1
                    }
                },
                enabled = !state.loading && selectedFixture != null,
                color = if (pending) DosGreen else DosCyan,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.coachCommandsEnabled) {
            CoachCommandBar(
                selected = command,
                onSelect = {
                    command = it
                    vm.setCoachCommand(it)
                },
            )
        } else {
            Text(
                text = "Modo Basico: simulacion automatica sin comandos de banquillo",
                color = DosGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

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
                        displayHomeGoals = if (fixture.played) liveHomeGoals else -1,
                        displayAwayGoals = if (fixture.played) liveAwayGoals else -1,
                        minute = liveMinute,
                        replayInProgress = replayInProgress,
                    )

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .border(1.dp, DosGray),
                    ) {
                        CoachPitch(ballPosition = ballPosition)
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
    displayHomeGoals: Int,
    displayAwayGoals: Int,
    minute: Int,
    replayInProgress: Boolean,
) {
    DosPanel(title = "MARCADOR") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    text = if (fixture.played) "$displayHomeGoals - $displayAwayGoals" else "? - ?",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MIN ${minute.coerceIn(1, 120)}",
                    color = DosLightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Text(
                    text = when {
                        !fixture.played -> "PENDIENTE"
                        replayInProgress -> "REPLAY EN VIVO"
                        else -> "FINAL"
                    },
                    color = if (replayInProgress) DosGreen else DosGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CoachPitch(ballPosition: CoachBallPosition) {
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
    val ballX by animateFloatAsState(
        targetValue = ballPosition.x.coerceIn(0.08f, 0.92f),
        animationSpec = tween(durationMillis = 260),
        label = "ballX",
    )
    val ballY by animateFloatAsState(
        targetValue = ballPosition.y.coerceIn(0.08f, 0.92f),
        animationSpec = tween(durationMillis = 260),
        label = "ballY",
    )

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
        fun ChipAt(x: Float, y: Float, color: Color, label: String) {
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * x - 8.dp, y = maxHeight * y - 8.dp)
                    .size(16.dp)
                    .background(color = color, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = DosBlack,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        homeFormation.forEachIndexed { index, (x, y) -> ChipAt(x, y, homeColor, (index + 1).toString()) }
        awayFormation.forEachIndexed { index, (x, y) -> ChipAt(x, y, awayColor, (index + 1).toString()) }

        Box(
            modifier = Modifier
                .offset(x = maxWidth * ballX - 6.dp, y = maxHeight * ballY - 6.dp)
                .size(12.dp)
                .background(DosYellow, CircleShape),
        )
    }
}

@Composable
private fun EventLine(event: CoachEventUi, isHome: Boolean) {
    val typeColor = when (event.type) {
        "GOAL" -> DosYellow
        "OWN_GOAL" -> DosYellow
        "VAR_REVIEW" -> DosCyan
        "VAR_DISALLOWED" -> DosRed
        "TIME_WASTING" -> DosGray
        "RED_CARD" -> DosRed
        "YELLOW_CARD" -> DosYellow
        "INJURY" -> DosRed
        "SUBSTITUTION" -> DosCyan
        "TACTICAL_STOP" -> DosCyan
        "NARRATION" -> DosLightGray
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

private data class CoachBallPosition(
    val x: Float,
    val y: Float,
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

private fun ballPositionForEvent(event: CoachEventUi, homeTeamId: Int): CoachBallPosition {
    val homeSide = event.teamId == homeTeamId
    val y = when (event.type) {
        "GOAL", "OWN_GOAL" -> if (homeSide) 0.12f else 0.88f
        "VAR_REVIEW", "VAR_DISALLOWED" -> if (homeSide) 0.20f else 0.80f
        "YELLOW_CARD", "RED_CARD", "INJURY", "TIME_WASTING" -> if (homeSide) 0.34f else 0.66f
        "SUBSTITUTION" -> if (homeSide) 0.94f else 0.06f
        "TACTICAL_STOP" -> if (homeSide) 0.58f else 0.42f
        "NARRATION" -> if (event.teamId <= 0) 0.50f else if (homeSide) 0.44f else 0.56f
        else -> if (homeSide) 0.44f else 0.56f
    }
    val seed = abs(event.minute * 97 + event.teamId * 13 + event.type.hashCode())
    val laneOffset = (seed % 54) / 100f // 0.00 .. 0.53
    val baseX = when (event.type) {
        "GOAL", "OWN_GOAL", "VAR_DISALLOWED" -> 0.50f
        "SUBSTITUTION" -> if (homeSide) 0.10f else 0.90f
        else -> 0.23f + laneOffset
    }
    return CoachBallPosition(
        x = baseX.coerceIn(0.08f, 0.92f),
        y = y.coerceIn(0.06f, 0.94f),
    )
}
