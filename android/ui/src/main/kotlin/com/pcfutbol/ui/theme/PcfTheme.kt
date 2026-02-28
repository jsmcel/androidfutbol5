package com.pcfutbol.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// -----------------------------------------------------------------------
// Paleta estilo PC Fútbol 5 — DOS dark con acentos cyan/amarillo
// -----------------------------------------------------------------------

val DosBlack      = Color(0xFF0A0A0F)
val DosNavy       = Color(0xFF0D1B2A)
val DosCyan       = Color(0xFF00E5FF)
val DosYellow     = Color(0xFFFFD600)
val DosGreen      = Color(0xFF00E676)
val DosRed        = Color(0xFFFF1744)
val DosGray       = Color(0xFF424242)
val DosLightGray  = Color(0xFF9E9E9E)
val DosWhite      = Color(0xFFE0E0E0)

private val PcfColorScheme = darkColorScheme(
    primary          = DosCyan,
    onPrimary        = DosBlack,
    primaryContainer = DosNavy,
    secondary        = DosYellow,
    onSecondary      = DosBlack,
    background       = DosBlack,
    onBackground     = DosWhite,
    surface          = DosNavy,
    onSurface        = DosWhite,
    error            = DosRed,
    outline          = DosGray,
)

val PcfTypography = Typography(
    displayLarge = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Bold,
        fontSize    = 28.sp,
        color       = DosCyan,
    ),
    headlineMedium = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Bold,
        fontSize    = 18.sp,
        color       = DosYellow,
    ),
    bodyLarge = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontSize    = 14.sp,
        color       = DosWhite,
    ),
    bodyMedium = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontSize    = 12.sp,
        color       = DosLightGray,
    ),
    labelSmall = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontSize    = 10.sp,
        color       = DosGray,
    ),
)

@Composable
fun PcfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PcfColorScheme,
        typography  = PcfTypography,
        content     = content,
    )
}
