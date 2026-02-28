package com.pcfutbol.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcfutbol.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Menú principal espectacular — versión CRT retro con efectos visuales.
 * Entrega 1: Fondo animado, scanlines, glow, pelota rodante, botones animados.
 */

/**
 * Scanlines horizontales animadas — efecto CRT clásico.
 * Líneas cada 4px con animación de offset en bucle de 3s.
 */
@Composable
fun CrtScanlines(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanlines")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val lineColor = Color.Black.copy(alpha = 0.18f)
        val lineSpacing = 4.dp.toPx()
        val strokeWidth = 1.dp.toPx()

        var y = offsetY
        while (y < size.height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
            y += lineSpacing
        }
    }
}

/**
 * Pelota de fútbol rodante animada.
 * Rueda de izquierda a derecha en bucle de 4s con rotación realista.
 */
@Composable
fun RollingBall(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "rollingBall")

    // Animación de posición X: de izquierda a derecha
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    // Animación de rotación (proporcional al avance)
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Efecto de rebote vertical sutil
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(60.dp)) {
        val ballRadius = 18.dp.toPx()
        val ballDiameter = ballRadius * 2

        // Posición X interpolada con margen para no salirse
        val startX = ballRadius + 20.dp.toPx()
        val endX = size.width - ballRadius - 20.dp.toPx()
        val distance = endX - startX
        val posX = startX + (distance * progress)

        // Rebote sutil
        val bounceY = sin(bounce * PI.toFloat()) * 3.dp.toPx()
        val posY = size.height / 2 + bounceY

        // Sombra de la pelota (se mueve y cambia de opacidad con el rebote)
        val shadowAlpha = 0.3f - (sin(bounce * PI.toFloat()) * 0.1f)
        drawOval(
            color = Color.Black.copy(alpha = shadowAlpha),
            topLeft = Offset(posX - ballRadius * 0.8f, size.height - 8.dp.toPx()),
            size = Size(ballRadius * 1.6f, 6.dp.toPx())
        )

        // Pelota con rotación
        rotate(degrees = rotation * 2, pivot = Offset(posX, posY)) {
            // Cuerpo blanco de la pelota
            drawCircle(
                color = Color.White,
                radius = ballRadius,
                center = Offset(posX, posY)
            )

            // Pentágonos negros (aproximados con círculos pequeños y paths)
            val pentagonRadius = ballRadius * 0.35f
            val smallDotRadius = ballRadius * 0.12f

            // Pentágono central
            drawCircle(
                color = Color.Black,
                radius = pentagonRadius,
                center = Offset(posX, posY)
            )

            // Pentágonos alrededor (5 pentágonos formando el patrón clásico)
            for (i in 0 until 5) {
                val angle = (i * 72f - 90f) * (PI / 180f).toFloat()
                val px = posX + cos(angle) * (ballRadius * 0.65f)
                val py = posY + sin(angle) * (ballRadius * 0.65f)

                drawCircle(
                    color = Color.Black,
                    radius = smallDotRadius * 1.8f,
                    center = Offset(px, py)
                )
            }

            // Hexágonos/blancos intermedios (pequeños puntos negros para detalle)
            for (i in 0 until 5) {
                val angle = (i * 72f - 54f) * (PI / 180f).toFloat()
                val px = posX + cos(angle) * (ballRadius * 0.9f)
                val py = posY + sin(angle) * (ballRadius * 0.9f)

                drawCircle(
                    color = Color.Black,
                    radius = smallDotRadius * 0.8f,
                    center = Offset(px, py)
                )
            }
        }

        // Brillo sutil en la parte superior
        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = ballRadius * 0.3f,
            center = Offset(posX - ballRadius * 0.3f, posY - ballRadius * 0.3f)
        )
    }
}

/**
 * Botón DOS con animaciones de presión.
 * Flash de borde cyan→blanco y escala al presionar.
 */
@Composable
fun DosButtonAnimated(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = DosCyan,
) {
    var isPressed by remember { mutableStateOf(false) }

    // Animación de escala
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    // Animación de color del borde (flash)
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) DosWhite else color,
        animationSpec = tween(150),
        label = "borderColor"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .border(1.dp, if (enabled) borderColor else DosGray, RoundedCornerShape(2.dp))
            .background(DosNavy.copy(alpha = 0.8f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        when {
                            change.pressed && !isPressed -> {
                                isPressed = true
                            }
                            !change.pressed && isPressed -> {
                                isPressed = false
                                onClick()
                            }
                        }
                    }
                }
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) color else DosGray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * Fondo animado con campo de fútbol simplificado dibujado en Canvas.
 * Incluye líneas blancas semi-transparentes del campo.
 */
@Composable
private fun FootballFieldBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineColor = Color.White.copy(alpha = 0.08f)
        val strokeWidth = 1.5.dp.toPx()

        val width = size.width
        val height = size.height

        // Centro del campo
        val centerX = width / 2
        val centerY = height / 2

        // Línea de medio campo (vertical)
        drawLine(
            color = lineColor,
            start = Offset(centerX, 0f),
            end = Offset(centerX, height),
            strokeWidth = strokeWidth
        )

        // Círculo central
        val centerCircleRadius = min(width, height) * 0.12f
        drawCircle(
            color = lineColor,
            radius = centerCircleRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        // Punto central
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        // Área de penal izquierda
        val penaltyAreaWidth = width * 0.18f
        val penaltyAreaHeight = height * 0.35f
        val penaltyAreaTop = centerY - penaltyAreaHeight / 2
        drawRect(
            color = lineColor,
            topLeft = Offset(0f, penaltyAreaTop),
            size = Size(penaltyAreaWidth, penaltyAreaHeight),
            style = Stroke(width = strokeWidth)
        )

        // Área pequeña izquierda
        val smallAreaWidth = width * 0.06f
        val smallAreaHeight = height * 0.15f
        val smallAreaTop = centerY - smallAreaHeight / 2
        drawRect(
            color = lineColor,
            topLeft = Offset(0f, smallAreaTop),
            size = Size(smallAreaWidth, smallAreaHeight),
            style = Stroke(width = strokeWidth)
        )

        // Punto de penal izquierdo
        val penaltyDistance = width * 0.12f
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = Offset(penaltyDistance, centerY)
        )

        // Arco del área izquierdo (semi-círculo)
        val arcRadius = width * 0.06f
        drawArc(
            color = lineColor,
            startAngle = -37f,
            sweepAngle = 74f,
            useCenter = false,
            topLeft = Offset(penaltyDistance - arcRadius, centerY - arcRadius),
            size = Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(width = strokeWidth)
        )

        // Área de penal derecha
        drawRect(
            color = lineColor,
            topLeft = Offset(width - penaltyAreaWidth, penaltyAreaTop),
            size = Size(penaltyAreaWidth, penaltyAreaHeight),
            style = Stroke(width = strokeWidth)
        )

        // Área pequeña derecha
        drawRect(
            color = lineColor,
            topLeft = Offset(width - smallAreaWidth, smallAreaTop),
            size = Size(smallAreaWidth, smallAreaHeight),
            style = Stroke(width = strokeWidth)
        )

        // Punto de penal derecho
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = Offset(width - penaltyDistance, centerY)
        )

        // Arco del área derecho (semi-círculo)
        drawArc(
            color = lineColor,
            startAngle = 143f,
            sweepAngle = 74f,
            useCenter = false,
            topLeft = Offset(width - penaltyDistance - arcRadius, centerY - arcRadius),
            size = Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(width = strokeWidth)
        )

        // Córneres (pequeños cuartos de círculo)
        val cornerRadius = 8.dp.toPx()
        // Superior izquierdo
        drawArc(
            color = lineColor,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-cornerRadius, -cornerRadius),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidth)
        )
        // Superior derecho
        drawArc(
            color = lineColor,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(width - cornerRadius, -cornerRadius),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidth)
        )
        // Inferior izquierdo
        drawArc(
            color = lineColor,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-cornerRadius, height - cornerRadius),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidth)
        )
        // Inferior derecho
        drawArc(
            color = lineColor,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(width - cornerRadius, height - cornerRadius),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Overlay de viñeta CRT — gradiente radial negro desde las esquinas.
 */
@Composable
private fun CrtVignette() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()

                // Gradiente radial desde las esquinas
                val radius = size.maxDimension * 0.8f
                val gradient = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.7f)
                    ),
                    center = Offset.Zero,
                    radius = radius * 1.5f
                )
                drawRect(brush = gradient)

                val gradient2 = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.7f)
                    ),
                    center = Offset(size.width, 0f),
                    radius = radius * 1.5f
                )
                drawRect(brush = gradient2)

                val gradient3 = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.7f)
                    ),
                    center = Offset(0f, size.height),
                    radius = radius * 1.5f
                )
                drawRect(brush = gradient3)

                val gradient4 = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.7f)
                    ),
                    center = Offset(size.width, size.height),
                    radius = radius * 1.5f
                )
                drawRect(brush = gradient4)
            }
    )
}

/**
 * Título "PC FÚTBOL 5" con efecto glow animado.
 * El brillo pulsa entre alpha 0.85 y 1.0 cada 1.5s.
 */
@Composable
private fun TitleWithGlow() {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = glowAlpha
                shadowElevation = glowRadius
            },
        contentAlignment = Alignment.Center,
    ) {
        // Efecto de glow con múltiples capas de texto
        // Capa exterior (más difusa)
        Text(
            text = "PC FÚTBOL 5",
            color = DosCyan.copy(alpha = 0.3f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 6.sp,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = 1.05f
                    scaleY = 1.05f
                }
        )

        // Capa media
        Text(
            text = "PC FÚTBOL 5",
            color = DosCyan.copy(alpha = 0.6f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 6.sp,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
        )

        // Capa principal
        Text(
            text = "PC FÚTBOL 5",
            color = DosCyan,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 6.sp,
        )
    }
}

/**
 * Menú principal espectacular — versión CRT retro completa.
 */
@Composable
fun MainMenuScreen(
    onLigaManager: () -> Unit,
    onProManager: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack),
    ) {
        // Capa 1: Fondo animado con campo de fútbol
        FootballFieldBackground()

        // Capa 2: Viñeta CRT
        CrtVignette()

        // Capa 3: Scanlines
        CrtScanlines()

        // Contenido principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Título con glow animado
            TitleWithGlow()

            Spacer(Modifier.height(8.dp))

            // Subtítulo
            Text(
                text = "TEMPORADA 2025/26",
                color = DosYellow,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(16.dp))

            // Separador decorativo
            Text(
                text = "═".repeat(32),
                color = DosGray.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(32.dp))

            // Pelota rodante
            RollingBall()

            Spacer(Modifier.height(32.dp))

            // Botones de menú
            DosButtonAnimated(
                text = "  LIGA / MANAGER  ",
                onClick = onLigaManager,
                modifier = Modifier.width(260.dp),
            )

            Spacer(Modifier.height(12.dp))

            DosButtonAnimated(
                text = "   PROMANAGER   ",
                onClick = onProManager,
                modifier = Modifier.width(260.dp),
                color = DosYellow,
            )

            Spacer(Modifier.height(40.dp))

            // Footer
            Text(
                text = "© 2026 PCF Android Rewrite\nDatos: Transfermarkt 2025/26",
                color = DosGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
            )
        }
    }
}
