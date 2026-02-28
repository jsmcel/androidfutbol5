package com.pcfutbol.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.*
import com.pcfutbol.ui.viewmodels.TacticViewModel

// -----------------------------------------------------------------------
// Mapa de formaciones predefinidas
// Coordenadas: x (0-1 = izquierda-derecha), y (0-1 = superior-inferior)
// -----------------------------------------------------------------------
val FORMATIONS: Map<String, List<Pair<Float, Float>>> = mapOf(
    "4-3-3" to listOf(
        0.5f to 0.05f,   // Portero
        0.15f to 0.22f,  // LD
        0.38f to 0.22f,  // DCI
        0.62f to 0.22f,  // DCD
        0.85f to 0.22f,  // LI
        0.25f to 0.50f,  // MC
        0.50f to 0.50f,  // MCO
        0.75f to 0.50f,  // MCD
        0.20f to 0.78f,  // EI
        0.50f to 0.82f,  // DC
        0.80f to 0.78f   // ED
    ),
    "4-4-2" to listOf(
        0.5f to 0.05f,   // Portero
        0.15f to 0.22f,  // LD
        0.38f to 0.22f,  // DCI
        0.62f to 0.22f,  // DCD
        0.85f to 0.22f,  // LI
        0.12f to 0.52f,  // MI
        0.38f to 0.52f,  // MCI
        0.62f to 0.52f,  // MCD
        0.88f to 0.52f,  // MD
        0.33f to 0.80f,  // DCI
        0.67f to 0.80f   // DCD
    ),
    "4-5-1" to listOf(
        0.5f to 0.05f,   // Portero
        0.15f to 0.22f,  // LD
        0.38f to 0.22f,  // DCI
        0.62f to 0.22f,  // DCD
        0.85f to 0.22f,  // LI
        0.10f to 0.52f,  // MI
        0.30f to 0.52f,  // MCI
        0.50f to 0.52f,  // MCO
        0.70f to 0.52f,  // MCD
        0.90f to 0.52f,  // MD
        0.50f to 0.82f   // DC
    ),
    "3-5-2" to listOf(
        0.5f to 0.05f,   // Portero
        0.25f to 0.22f,  // DCI
        0.50f to 0.22f,  // DC
        0.75f to 0.22f,  // DCD
        0.10f to 0.52f,  // MI
        0.30f to 0.52f,  // MCI
        0.50f to 0.52f,  // MCO
        0.70f to 0.52f,  // MCD
        0.90f to 0.52f,  // MD
        0.33f to 0.82f,  // DCI
        0.67f to 0.82f   // DCD
    ),
)

@Composable
fun TacticScreen(
    onNavigateUp: () -> Unit,
    vm: TacticViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val preset = state.preset

    var selectedFormation by remember { mutableStateOf("4-3-3") }
    var selectedPlayerIndex by remember { mutableIntStateOf(-1) }

    // Obtener formación actual o la default
    val formation = FORMATIONS[selectedFormation] ?: FORMATIONS["4-3-3"]!!

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
                text = "TÁCTICA",
                color = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(48.dp))
        }

        if (preset == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando táctica...", color = DosGray, fontFamily = FontFamily.Monospace)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Selector de formación (chips)
            FormationSelector(
                selectedFormation = selectedFormation,
                onFormationSelected = { selectedFormation = it }
            )

            // Campo de fútbol interactivo
            FootballPitchCanvas(
                formation = formation,
                selectedIndex = selectedPlayerIndex,
                onPositionTap = { index ->
                    selectedPlayerIndex = if (selectedPlayerIndex == index) -1 else index
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.65f)
                    .padding(vertical = 8.dp)
            )

            // Panel de parámetros tácticos
            DosPanel(title = "PARÁMETROS TÁCTICOS") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Tipo de juego (1-3: DEFENSIVO-EQUILIBRADO-OFENSIVO)
                    TacticOptionSelector(
                        label = "TIPO DE JUEGO",
                        selectedValue = preset.tipoJuego,
                        options = listOf(
                            1 to "DEFENSIVO",
                            2 to "EQUILIBRADO",
                            3 to "OFENSIVO"
                        ),
                        onSelect = { vm.updateParam("tipoJuego", it) }
                    )

                    // Presión (1-3)
                    TacticOptionSelector(
                        label = "PRESIÓN",
                        selectedValue = preset.tipoPresion,
                        options = listOf(
                            1 to "BAJA",
                            2 to "MEDIA",
                            3 to "ALTA"
                        ),
                        onSelect = { vm.updateParam("tipoPresion", it) }
                    )

                    // % Toque
                    TacticSlider(
                        label = "TOQUE",
                        value = preset.porcToque,
                        onValueChange = { vm.updateParam("porcToque", it) }
                    )

                    // % Contragolpe
                    TacticSlider(
                        label = "CONTRAGOLPE",
                        value = preset.porcContra,
                        onValueChange = { vm.updateParam("porcContra", it) }
                    )

                    TacticOptionSelector(
                        label = "PERDIDA DE TIEMPO",
                        selectedValue = preset.perdidaTiempo,
                        options = listOf(
                            0 to "NO",
                            1 to "SI"
                        ),
                        onSelect = { vm.updateParam("perdidaTiempo", it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Botón GUARDAR
        val saved = state.saved
        DosButton(
            text = if (saved) "GUARDADO ✓" else "GUARDAR",
            onClick = vm::save,
            modifier = Modifier.fillMaxWidth(),
            color = if (saved) DosGreen else DosGreen,
            enabled = !saved,
        )
    }
}

/**
 * Selector de formación con chips horizontales
 */
@Composable
private fun FormationSelector(
    selectedFormation: String,
    onFormationSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FORMACIÓN:",
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 4.dp)
        )

        FORMATIONS.keys.forEach { formationName ->
            val selected = formationName == selectedFormation
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (selected) DosCyan.copy(alpha = 0.2f) else DosNavy.copy(alpha = 0.6f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) DosCyan else DosGray,
                        shape = MaterialTheme.shapes.small
                    )
                    .clickable { onFormationSelected(formationName) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formationName,
                    color = if (selected) DosCyan else DosLightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Canvas del campo de fútbol con fichas de jugadores animadas
 */
@Composable
fun FootballPitchCanvas(
    formation: List<Pair<Float, Float>>,
    onPositionTap: (Int) -> Unit,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val playerRadius = with(density) { 14.dp.toPx() }
    val borderWidth = with(density) { 2.dp.toPx() }

    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A2E0A),  // Verde oscuro superior
                        Color(0xFF143D14)   // Verde oscuro inferior
                    )
                ),
                shape = MaterialTheme.shapes.medium
            )
            .border(2.dp, DosGray.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
    ) {
        // Dibujar líneas del campo con Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val width = size.width
            val height = size.height

            drawPitchLines(width, height)
        }

        // Fichas de jugadores posicionadas absolutamente
        formation.forEachIndexed { index, (xPercent, yPercent) ->
            val isSelected = index == selectedIndex

            // Animación spring al cambiar posición
            val animatedX by animateFloatAsState(
                targetValue = xPercent,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 200f
                ),
                label = "x_$index"
            )
            val animatedY by animateFloatAsState(
                targetValue = yPercent,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 200f
                ),
                label = "y_$index"
            )

            PlayerChip(
                number = index + 1,
                isSelected = isSelected,
                modifier = Modifier
                    .size(32.dp)
                    .offset(
                        x = (animatedX * 100).dp - 16.dp + 8.dp, // Ajustar por el padding del canvas
                        y = (animatedY * 100).dp - 16.dp + 8.dp
                    )
                    .pointerInput(index) {
                        detectTapGestures { onPositionTap(index) }
                    }
            )
        }
    }
}

/**
 * Dibuja las líneas del campo de fútbol
 */
private fun DrawScope.drawPitchLines(width: Float, height: Float) {
    val lineColor = Color.White.copy(alpha = 0.6f)
    val strokeWidth = 2.dp.toPx()

    // Rectángulo exterior (líneas de banda y fondo)
    drawRect(
        color = lineColor,
        topLeft = Offset.Zero,
        size = androidx.compose.ui.geometry.Size(width, height),
        style = Stroke(width = strokeWidth)
    )

    // Línea de medio campo (horizontal)
    drawLine(
        color = lineColor,
        start = Offset(0f, height / 2),
        end = Offset(width, height / 2),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // Círculo central
    val centerRadius = width * 0.18f
    drawCircle(
        color = lineColor,
        radius = centerRadius,
        center = Offset(width / 2, height / 2),
        style = Stroke(width = strokeWidth)
    )

    // Punto central
    drawCircle(
        color = lineColor,
        radius = 3.dp.toPx(),
        center = Offset(width / 2, height / 2)
    )

    // Área grande inferior (62% × 22% centrada)
    val areaWidth = width * 0.62f
    val areaHeight = height * 0.22f
    val areaLeft = (width - areaWidth) / 2

    // Área grande inferior (fondo cercano)
    drawRect(
        color = lineColor,
        topLeft = Offset(areaLeft, height - areaHeight),
        size = androidx.compose.ui.geometry.Size(areaWidth, areaHeight),
        style = Stroke(width = strokeWidth)
    )

    // Punto de penalti inferior
    val penaltyYBottom = height - areaHeight * 0.45f
    drawCircle(
        color = lineColor,
        radius = 3.dp.toPx(),
        center = Offset(width / 2, penaltyYBottom)
    )

    // Círculo de penalti inferior (arco)
    drawArc(
        color = lineColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(width / 2 - areaHeight * 0.35f, penaltyYBottom - areaHeight * 0.35f),
        size = androidx.compose.ui.geometry.Size(areaHeight * 0.7f, areaHeight * 0.7f),
        style = Stroke(width = strokeWidth)
    )

    // Área grande superior
    drawRect(
        color = lineColor,
        topLeft = Offset(areaLeft, 0f),
        size = androidx.compose.ui.geometry.Size(areaWidth, areaHeight),
        style = Stroke(width = strokeWidth)
    )

    // Punto de penalti superior
    val penaltyYTop = areaHeight * 0.45f
    drawCircle(
        color = lineColor,
        radius = 3.dp.toPx(),
        center = Offset(width / 2, penaltyYTop)
    )

    // Círculo de penalti superior (arco)
    drawArc(
        color = lineColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(width / 2 - areaHeight * 0.35f, penaltyYTop - areaHeight * 0.35f),
        size = androidx.compose.ui.geometry.Size(areaHeight * 0.7f, areaHeight * 0.7f),
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Ficha circular de jugador
 */
@Composable
private fun PlayerChip(
    number: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) DosYellow else DosCyan
    val bgColor = DosNavy

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = if (isSelected) DosYellow else DosWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Selector de opciones tácticas con botones
 */
@Composable
private fun TacticOptionSelector(
    label: String,
    selectedValue: Int,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            text = label,
            color = DosGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { (value, name) ->
                val selected = value == selectedValue
                OutlinedButton(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) DosCyan.copy(alpha = 0.2f) else DosBlack,
                        contentColor = if (selected) DosCyan else DosGray,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (selected) DosCyan else DosGray
                        )
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Slider 0..100 con valor numérico
 */
@Composable
private fun TacticSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = DosGray, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text(
                text = "$value%",
                color = DosCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
            colors = SliderDefaults.colors(
                thumbColor = DosCyan,
                activeTrackColor = DosCyan,
                inactiveTrackColor = DosGray.copy(alpha = 0.4f),
            ),
        )
    }
}
