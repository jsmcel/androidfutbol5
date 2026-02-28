package com.pcfutbol.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.components.MatchResultRow
import com.pcfutbol.ui.theme.*
import com.pcfutbol.ui.viewmodels.LigaSelectViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pantallas stub — serán implementadas en sprints sucesivos.
 * Muestran un marcador de posición con el nombre de la pantalla.
 */

@Composable
fun LigaSelectScreen(
    onNavigateUp: () -> Unit,
    onStandings: (String) -> Unit,
    onStats: () -> Unit = {},
    onMatchday: (Int) -> Unit,
    onTeam: () -> Unit,
    onManagerDepth: () -> Unit = {},
    onNews: () -> Unit,
    onRealFootball: () -> Unit = {},
    onMarket: () -> Unit,
    onCopa: () -> Unit = {},
    onChampions: () -> Unit = {},
    onNationalTeam: () -> Unit = {},
    vm: LigaSelectViewModel = hiltViewModel(),
) {
    val ligaState by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Text(
                text = "LIGA/MANAGER",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.width(48.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            DosPanel(
                title = "MENU",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Team picker when no team selected yet (LIGA/MANAGER mode)
                    if (ligaState.managerTeamId <= 0 && ligaState.teamsForLeague.isNotEmpty()) {
                        Text(
                            text = "ELIGE TU EQUIPO (${ligaState.selectedLeague})",
                            color = DosYellow,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        // Use Column + forEach to avoid nested scrollable conflict
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ligaState.teamsForLeague.forEach { team ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, DosGray)
                                        .clickable { vm.selectTeam(team.slotId) }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = team.nameFull,
                                        color = DosWhite,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                    )
                                    val budgetStr = when {
                                        team.budgetK >= 1_000_000 ->
                                            "${"%.1f".format(team.budgetK / 1_000_000.0)}M€"
                                        team.budgetK >= 1_000 ->
                                            "${"%.0f".format(team.budgetK / 1_000.0)}K€"
                                        else -> "${team.budgetK}K€"
                                    }
                                    Text(
                                        text = budgetStr,
                                        color = DosGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    } else if (ligaState.managerTeamName.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "> ${ligaState.managerTeamName}",
                                color = DosCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (ligaState.managerBudgetK > 0) {
                                val budgetStr = when {
                                    ligaState.managerBudgetK >= 1_000_000 ->
                                        "${"%.1f".format(ligaState.managerBudgetK / 1_000_000.0)}M€"
                                    ligaState.managerBudgetK >= 1_000 ->
                                        "${"%.0f".format(ligaState.managerBudgetK / 1_000.0)}K€"
                                    else -> "${ligaState.managerBudgetK}K€"
                                }
                                Text(
                                    text = budgetStr,
                                    color = DosGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Text(
                        text = "LIGA ACTIVA: ${ligaState.selectedLeague}",
                        color = DosYellow,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DosButton(
                            text = "CLASIFICACION",
                            onClick = { onStandings(ligaState.selectedLeague) },
                            modifier = Modifier.weight(1f),
                        )
                        DosButton(
                            text = "ESTADISTICAS",
                            onClick = onStats,
                            modifier = Modifier.weight(1f),
                            color = DosCyan,
                        )
                    }
                    DosButton(
                        text = "JORNADA ${ligaState.currentMatchday} (${ligaState.selectedLeague})",
                        onClick = { onMatchday(ligaState.currentMatchday) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DosButton(
                        text = "MI PLANTILLA",
                        onClick = onTeam,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DosButton(
                        text = "ENTRENAMIENTO + STAFF",
                        onClick = onManagerDepth,
                        modifier = Modifier.fillMaxWidth(),
                        color = DosYellow,
                    )
                    DosButton(
                        text = "NOTICIAS",
                        onClick = onNews,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DosButton(
                        text = "ACTUALIDAD REAL",
                        onClick = onRealFootball,
                        modifier = Modifier.fillMaxWidth(),
                        color = DosCyan,
                    )
                    DosButton(
                        text = if (ligaState.transferWindowOpen) "MERCADO [ABIERTO]" else "MERCADO",
                        onClick = onMarket,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (ligaState.transferWindowOpen) DosGreen else DosYellow,
                    )
                    DosButton(
                        text = "COPA DEL REY",
                        onClick = onCopa,
                        modifier = Modifier.fillMaxWidth(),
                        color = DosCyan,
                    )
                    DosButton(
                        text = "CHAMPIONS",
                        onClick = onChampions,
                        modifier = Modifier.fillMaxWidth(),
                        color = DosYellow,
                    )
                    DosButton(
                        text = "SELECCION ESPANOLA",
                        onClick = onNationalTeam,
                        modifier = Modifier.fillMaxWidth(),
                        color = DosYellow,
                    )

                    LeagueGroupsSection(
                        selectedLeague = ligaState.selectedLeague,
                        groups = ligaState.leagueGroups,
                        onSelectLeague = vm::selectLeague,
                    )

                    DosButton(
                        text = "VOLVER",
                        onClick = onNavigateUp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LeagueGroupsSection(
    selectedLeague: String,
    groups: List<com.pcfutbol.ui.viewmodels.LeagueGroupUi>,
    onSelectLeague: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "SELECCION DE LIGA",
            color = DosCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        groups.forEach { group ->
            Text(
                text = group.title,
                color = DosGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            group.leagues.forEach { league ->
                val selected = league.code == selectedLeague
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (selected) DosYellow else DosGray)
                        .background(if (selected) DosNavy else DosBlack)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${league.name} (${league.code})",
                        color = if (selected) DosYellow else DosWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    TextButton(onClick = { onSelectLeague(league.code) }) {
                        Text(
                            text = if (selected) "ACTIVA" else "ELEGIR",
                            color = if (selected) DosGreen else DosCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

// -------------------------------------------------------------------------
// MatchdayScreen — Entrega 3: Campo animado top-down con event log
// -------------------------------------------------------------------------

/** Estado del botón de simulación */
private enum class SimulateButtonState { IDLE, LOADING, DONE }

/** Evento de partido para el log */
private data class MatchEvent(
    val id: String = UUID.randomUUID().toString(),
    val minute: Int,
    val type: EventType,
    val description: String,
    val isManagerTeamGoal: Boolean = false,
)

private enum class EventType { GOAL, YELLOW_CARD, RED_CARD, SUBSTITUTION, KICKOFF }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MatchdayScreen(
    matchday: Int,
    onNavigateUp: () -> Unit,
    onMatchResult: (Int) -> Unit,
    onSeasonComplete: () -> Unit = {},
    vm: com.pcfutbol.ui.viewmodels.MatchdayViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    
    // Estados para UI
    var buttonState by remember { mutableStateOf(SimulateButtonState.IDLE) }
    var selectedFixtureIndex by remember { mutableIntStateOf(0) }
    val events = remember { mutableStateListOf<MatchEvent>() }
    var ballZone by remember { mutableIntStateOf(2) } // 0=portería rival, 1=mitad rival, 2=centro, 3=mitad propia, 4=portería propia
    var homeScore by remember { mutableIntStateOf(0) }
    var awayScore by remember { mutableIntStateOf(0) }
    var flashColor by remember { mutableStateOf<Color?>(null) }
    
    LaunchedEffect(matchday) {
        vm.loadMatchday(matchday)
        events.clear()
        homeScore = 0
        awayScore = 0
        buttonState = SimulateButtonState.IDLE
        events.add(MatchEvent(minute = 1, type = EventType.KICKOFF, description = "INICIO del partido"))
    }
    LaunchedEffect(state.seasonComplete) {
        if (state.seasonComplete) onSeasonComplete()
    }
    
    // Animación de flash cuando hay gol
    val animatedFlashColor by animateColorAsState(
        targetValue = flashColor ?: Color.Transparent,
        animationSpec = tween(300),
        label = "flash"
    )
    LaunchedEffect(flashColor) {
        if (flashColor != null) {
            delay(300)
            flashColor = null
        }
    }

    val currentFixture = state.fixtures.getOrNull(selectedFixtureIndex)
    val homeTeamName = currentFixture?.let { state.teamNames[it.homeTeamId] } ?: "LOCAL"
    val awayTeamName = currentFixture?.let { state.teamNames[it.awayTeamId] } ?: "VISITANTE"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        // -----------------------------------------------------------------
        // TopBar: ← [JORNADA 12]    [LIGA1]   [SIMULAR]
        // -----------------------------------------------------------------
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
                    text = "JORNADA $matchday",
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = "[${state.competitionCode}]",
                color = DosCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            SimulateButton(
                state = buttonState,
                enabled = state.fixtures.any { !it.played } || buttonState == SimulateButtonState.DONE,
                onClick = {
                    when (buttonState) {
                        SimulateButtonState.IDLE -> {
                            buttonState = SimulateButtonState.LOADING
                            vm.simulateMatchday(matchday, System.currentTimeMillis())
                            // Simulación visual: cambiar zonas de la pelota y agregar eventos
                            kotlinx.coroutines.GlobalScope.launch {
                                delay(500)
                                ballZone = 3
                                delay(800)
                                ballZone = 1
                                delay(600)
                                // Gol local simulado
                                homeScore++
                                flashColor = DosGreen
                                events.add(0, MatchEvent(
                                    minute = 23,
                                    type = EventType.GOAL,
                                    description = "GOL — $homeTeamName",
                                    isManagerTeamGoal = true
                                ))
                                ballZone = 2
                                delay(1000)
                                ballZone = 3
                                delay(700)
                                // Tarjeta amarilla
                                events.add(0, MatchEvent(
                                    minute = 18,
                                    type = EventType.YELLOW_CARD,
                                    description = "Amarilla — $awayTeamName"
                                ))
                                ballZone = 2
                                buttonState = SimulateButtonState.DONE
                            }
                        }
                        SimulateButtonState.DONE -> {
                            // Siguiente jornada o reset
                            buttonState = SimulateButtonState.IDLE
                            events.clear()
                            events.add(MatchEvent(minute = 1, type = EventType.KICKOFF, description = "INICIO del partido"))
                            homeScore = 0
                            awayScore = 0
                        }
                        SimulateButtonState.LOADING -> { /* no-op */ }
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // -----------------------------------------------------------------
        // Marcador animado
        // -----------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DosNavy.copy(alpha = 0.6f))
                .border(1.dp, DosGray)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Flash overlay
            if (animatedFlashColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(animatedFlashColor.copy(alpha = 0.3f))
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Equipo local
                Text(
                    text = homeTeamName.take(14).uppercase(),
                    color = DosCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(100.dp),
                    textAlign = TextAlign.End,
                )
                
                // Marcador con animación
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedScoreDigit(homeScore, flashColor == DosGreen)
                    Text(
                        text = "-",
                        color = DosWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    AnimatedScoreDigit(awayScore, flashColor == DosYellow)
                }
                
                // Equipo visitante
                Text(
                    text = awayTeamName.take(14).uppercase(),
                    color = DosLightGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(100.dp),
                    textAlign = TextAlign.Start,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // -----------------------------------------------------------------
        // Campo animado (60% altura)
        // -----------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.60f)
                .background(DosNavy.copy(alpha = 0.4f))
                .border(1.dp, DosGray),
        ) {
            LivePitchView(
                ballZone = ballZone,
                homeTeamColor = DosCyan,
                awayTeamColor = DosGray,
            )
        }

        Spacer(Modifier.height(8.dp))

        // -----------------------------------------------------------------
        // Event Log
        // -----------------------------------------------------------------
        DosPanel(
            title = "EVENTOS",
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(events, key = { it.id }) { event ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                        ),
                    ) {
                        EventRow(
                            event = event,
                            isManagerGoal = event.isManagerTeamGoal,
                        )
                    }
                }
            }
        }
    }
}

/** Dígito de marcador con animación slide-up */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedScoreDigit(score: Int, flash: Boolean) {
    val flashColor by animateColorAsState(
        targetValue = if (flash) DosGreen else DosYellow,
        animationSpec = tween(200),
        label = "digitFlash"
    )
    
    AnimatedContent(
        targetState = score,
        transitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            )
        },
        label = "scoreDigit"
    ) { targetScore ->
        Text(
            text = targetScore.toString(),
            color = flashColor,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** Campo animado top-down con pelota y fichas */
@Composable
private fun LivePitchView(
    ballZone: Int,
    homeTeamColor: Color,
    awayTeamColor: Color,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Fondo verde con gradiente
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0a2e0a),
                        Color(0xFF143d14),
                        Color(0xFF0a2e0a)
                    ),
                    startY = 0f,
                    endY = h
                )
            )
            
            // Líneas blancas del campo
            val lineColor = Color.White.copy(alpha = 0.6f)
            val stroke = Stroke(width = 2f)
            
            // Bordes del campo
            drawRect(
                color = lineColor,
                topLeft = Offset(w * 0.05f, h * 0.05f),
                size = Size(w * 0.9f, h * 0.9f),
                style = stroke
            )
            
            // Línea de medio campo
            drawLine(
                color = lineColor,
                start = Offset(w * 0.05f, h * 0.5f),
                end = Offset(w * 0.95f, h * 0.5f),
                strokeWidth = 2f
            )
            
            // Círculo central
            drawCircle(
                color = lineColor,
                radius = w * 0.18f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = stroke
            )
            
            // Área grande local (abajo)
            drawRect(
                color = lineColor,
                topLeft = Offset(w * 0.19f, h * 0.73f),
                size = Size(w * 0.62f, h * 0.22f),
                style = stroke
            )
            
            // Punto de penalty local
            drawCircle(
                color = lineColor,
                radius = 3f,
                center = Offset(w * 0.5f, h * 0.85f)
            )
            
            // Área grande visitante (arriba)
            drawRect(
                color = lineColor,
                topLeft = Offset(w * 0.19f, h * 0.05f),
                size = Size(w * 0.62f, h * 0.22f),
                style = stroke
            )
            
            // Punto de penalty visitante
            drawCircle(
                color = lineColor,
                radius = 3f,
                center = Offset(w * 0.5f, h * 0.15f)
            )
        }
        
        // Fichas de jugadores (posiciones fijas en % del contenedor)
        // Formación 4-3-3 para ambos equipos
        val homeFormation = listOf(
            0.50f to 0.90f, // GK
            0.15f to 0.72f, 0.38f to 0.72f, 0.62f to 0.72f, 0.85f to 0.72f, // 4 DEF
            0.25f to 0.55f, 0.50f to 0.55f, 0.75f to 0.55f, // 3 MID
            0.20f to 0.38f, 0.50f to 0.35f, 0.80f to 0.38f, // 3 ATT
        )
        
        val awayFormation = listOf(
            0.50f to 0.10f, // GK
            0.15f to 0.28f, 0.38f to 0.28f, 0.62f to 0.28f, 0.85f to 0.28f, // 4 DEF
            0.25f to 0.45f, 0.50f to 0.45f, 0.75f to 0.45f, // 3 MID
            0.20f to 0.62f, 0.50f to 0.65f, 0.80f to 0.62f, // 3 ATT
        )
        
        // Dibujar fichas locales (cyan) - posiciones relativas al Box
        homeFormation.forEach { (relX, relY) ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.08f)
                        .absoluteOffset(
                            x = (relX * 100).dp,
                            y = (relY * 100).dp
                        )
                        .background(homeTeamColor.copy(alpha = 0.8f), CircleShape)
                        .border(1.5.dp, homeTeamColor, CircleShape)
                )
            }
        }
        
        // Dibujar fichas visitantes (gris)
        awayFormation.forEach { (relX, relY) ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.08f)
                        .absoluteOffset(
                            x = (relX * 100).dp,
                            y = (relY * 100).dp
                        )
                        .background(awayTeamColor.copy(alpha = 0.6f), CircleShape)
                        .border(1.5.dp, awayTeamColor, CircleShape)
                )
            }
        }
        
        // Pelota animada con glow
        AnimatedBall(
            ballZone = ballZone,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Pelota animada con glow */
@Composable
private fun AnimatedBall(
    ballZone: Int,
    modifier: Modifier = Modifier,
) {
    val ballSize = 10.dp
    
    // Posición objetivo según zona
    val targetOffset = remember(ballZone) {
        when (ballZone) {
            0 -> Offset(0.5f, 0.1f)  // portería rival (arriba)
            1 -> Offset(0.5f, 0.3f)  // mitad rival
            2 -> Offset(0.5f, 0.5f)  // centro
            3 -> Offset(0.5f, 0.7f)  // mitad propia
            else -> Offset(0.5f, 0.9f) // portería propia (abajo)
        }
    }
    
    // Animación de posición con spring
    val animatedOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f),
        label = "ballPosition"
    )
    
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .size(ballSize)
                .absoluteOffset(
                    x = (animatedOffset.x * 100).dp - (ballSize / 2),
                    y = (animatedOffset.y * 100).dp - (ballSize / 2)
                )
        ) {
            // Glow exterior (más transparente, más grande)
            drawCircle(
                color = DosYellow.copy(alpha = 0.3f),
                radius = size.width * 0.8f
            )
            // Glow intermedio
            drawCircle(
                color = DosYellow.copy(alpha = 0.5f),
                radius = size.width * 0.5f
            )
            // Pelota sólida
            drawCircle(
                color = DosYellow,
                radius = size.width * 0.3f
            )
        }
    }
}

/** Fila de evento en el log */
@Composable
private fun EventRow(
    event: MatchEvent,
    isManagerGoal: Boolean,
) {
    val backgroundColor = if (isManagerGoal) DosGreen.copy(alpha = 0.15f) else Color.Transparent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icono según tipo (usando Material Icons)
        when (event.type) {
            EventType.GOAL -> Icon(
                imageVector = Icons.Default.SportsSoccer,
                contentDescription = null,
                tint = DosYellow,
                modifier = Modifier.size(16.dp)
            )
            EventType.YELLOW_CARD -> Box(
                modifier = Modifier
                    .size(12.dp, 16.dp)
                    .background(Color(0xFFFFD600), RoundedCornerShape(2.dp))
            )
            EventType.RED_CARD -> Box(
                modifier = Modifier
                    .size(12.dp, 16.dp)
                    .background(DosRed, RoundedCornerShape(2.dp))
            )
            EventType.SUBSTITUTION -> Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = DosCyan,
                modifier = Modifier.size(16.dp)
            )
            EventType.KICKOFF -> Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(DosGray, CircleShape)
            )
        }
        
        Spacer(Modifier.width(8.dp))
        
        // Minuto
        Text(
            text = "min.${event.minute.toString().padStart(2)} ",
            color = DosLightGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        
        // Descripción
        Text(
            text = event.description,
            color = DosWhite,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Botón SIMULAR con estados animados */
@Composable
private fun SimulateButton(
    state: SimulateButtonState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val borderColor = when (state) {
        SimulateButtonState.IDLE -> DosCyan
        SimulateButtonState.LOADING -> DosYellow
        SimulateButtonState.DONE -> DosGreen
    }
    
    val text = when (state) {
        SimulateButtonState.IDLE -> "SIMULAR"
        SimulateButtonState.LOADING -> "SIMULANDO..."
        SimulateButtonState.DONE -> "SIGUIENTE"
    }
    
    Box(
        modifier = Modifier
            .drawWithContent {
                drawContent()
                if (state == SimulateButtonState.LOADING) {
                    drawArc(
                        color = DosYellow,
                        startAngle = rotation,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(-2f, -2f),
                        size = Size(size.width + 4f, size.height + 4f),
                        style = Stroke(width = 2f)
                    )
                }
            }
            .border(
                width = if (state == SimulateButtonState.LOADING) 0.dp else 1.dp,
                color = if (enabled) borderColor else DosGray,
                shape = RoundedCornerShape(2.dp)
            )
            .background(
                if (state == SimulateButtonState.DONE) DosGreen.copy(alpha = 0.2f) else DosNavy.copy(alpha = 0.8f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state) {
                SimulateButtonState.IDLE -> {
                    Text(
                        text = text,
                        color = if (enabled) DosCyan else DosGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                SimulateButtonState.LOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = DosYellow,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = text,
                        color = DosYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                SimulateButtonState.DONE -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = DosGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = text,
                        color = DosGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------

@Composable
private fun StubScreen(
    title: String,
    onNavigateUp: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Text(
                text       = title,
                color      = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 12.dp),
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "[ EN CONSTRUCCIÓN ]",
                    color      = DosGray,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}
