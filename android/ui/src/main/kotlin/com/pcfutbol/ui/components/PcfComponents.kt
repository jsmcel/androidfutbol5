package com.pcfutbol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcfutbol.ui.theme.*

/** Botón estilo DOS — borde cyan, fondo oscuro, texto monospace */
@Composable
fun DosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = DosCyan,
) {
    Box(
        modifier = modifier
            .border(1.dp, if (enabled) color else DosGray, RoundedCornerShape(2.dp))
            .background(DosNavy.copy(alpha = 0.8f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = text,
            color      = if (enabled) color else DosGray,
            fontWeight = FontWeight.Bold,
            fontSize   = 14.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

/** Fila de clasificación */
@Composable
fun StandingRow(
    position: Int,
    teamName: String,
    played: Int,
    won: Int,
    drawn: Int,
    lost: Int,
    gf: Int,
    ga: Int,
    points: Int,
    isManagerTeam: Boolean = false,
) {
    val bg = if (isManagerTeam) DosCyan.copy(alpha = 0.12f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = position.toString().padStart(2),
            color    = if (position <= 3) DosYellow else DosLightGray,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text     = teamName.take(16).padEnd(16),
            color    = if (isManagerTeam) DosCyan else DosWhite,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        listOf(played, won, drawn, lost, gf, ga, points).forEach { value ->
            Text(
                text      = value.toString().padStart(3),
                color     = if (value == points && value > 0) DosYellow else DosLightGray,
                fontSize  = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier  = Modifier.width(28.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Panel con borde estilo ventana DOS */
@Composable
fun DosPanel(
    title: String = "",
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .border(1.dp, DosGray)
            .background(DosNavy.copy(alpha = 0.6f))
            .padding(12.dp),
    ) {
        if (title.isNotBlank()) {
            Text(
                text       = " $title ",
                color      = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier   = Modifier.padding(bottom = 8.dp),
            )
            HorizontalDivider(color = DosGray, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

/** Resultado de partido compacto */
@Composable
fun MatchResultRow(
    homeTeam: String,
    awayTeam: String,
    homeGoals: Int,
    awayGoals: Int,
    isManagerMatch: Boolean = false,
    onClick: () -> Unit = {},
) {
    val color = if (isManagerMatch) DosCyan else DosLightGray
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(homeTeam.take(14).padEnd(14), color = color, fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f))
        val scoreText = if (homeGoals < 0) "?  -  ?" else "$homeGoals  -  $awayGoals"
        Text(
            text = scoreText,
            color = if (homeGoals < 0) DosGray else DosYellow,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
        )
        Text(awayTeam.take(14).padEnd(14), color = color, fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End)
    }
}
