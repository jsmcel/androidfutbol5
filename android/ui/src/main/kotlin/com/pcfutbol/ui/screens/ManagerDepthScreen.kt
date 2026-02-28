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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.DosBlack
import com.pcfutbol.ui.theme.DosCyan
import com.pcfutbol.ui.theme.DosGray
import com.pcfutbol.ui.theme.DosGreen
import com.pcfutbol.ui.theme.DosWhite
import com.pcfutbol.ui.theme.DosYellow
import com.pcfutbol.ui.viewmodels.ManagerDepthViewModel

private val STAFF_LABELS = mapOf(
    "segundoEntrenador" to "2o entrenador",
    "fisio" to "Fisio",
    "psicologo" to "Psicologo",
    "asistente" to "Asistente",
    "secretario" to "Secretario",
    "ojeador" to "Ojeador",
    "juveniles" to "Juveniles",
    "cuidador" to "Cuidador",
)

@Composable
fun ManagerDepthScreen(
    onNavigateUp: () -> Unit,
    vm: ManagerDepthViewModel = hiltViewModel(),
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
                    text = "ENTRENAMIENTO + STAFF",
                    color = DosYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (state.available) {
                DosButton(
                    text = if (state.saved) "GUARDADO" else "GUARDAR",
                    onClick = vm::save,
                    enabled = !state.saved,
                    color = DosGreen,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando...", color = DosGray, fontFamily = FontFamily.Monospace)
            }
            return@Column
        }

        if (!state.available) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.error ?: "Sin perfil de manager",
                    color = DosGray,
                    fontFamily = FontFamily.Monospace,
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DosPanel(title = "CONTEXTO") {
                Text(
                    text = "Manager: ${state.managerName}",
                    color = DosWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
                Text(
                    text = "Equipo: ${state.teamName}",
                    color = DosCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }

            DosPanel(title = "PLAN DE ENTRENAMIENTO") {
                OptionBar(
                    title = "Intensidad",
                    selected = state.intensity,
                    options = listOf(
                        "LOW" to "Suave",
                        "MEDIUM" to "Media",
                        "HIGH" to "Alta",
                    ),
                    onSelect = vm::setIntensity,
                )
                Spacer(Modifier.height(8.dp))
                OptionBar(
                    title = "Enfoque",
                    selected = state.focus,
                    options = listOf(
                        "BALANCED" to "Equilibrado",
                        "PHYSICAL" to "Fisico",
                        "DEFENSIVE" to "Defensivo",
                        "TECHNICAL" to "Tecnico",
                        "ATTACKING" to "Ataque",
                    ),
                    onSelect = vm::setFocus,
                )
            }

            DosPanel(title = "STAFF") {
                STAFF_LABELS.forEach { (key, label) ->
                    val value = state.staff[key] ?: 50
                    Text(
                        text = "$label: $value",
                        color = DosWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { vm.setStaffValue(key, it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 99,
                        colors = SliderDefaults.colors(
                            thumbColor = DosCyan,
                            activeTrackColor = DosCyan,
                            inactiveTrackColor = DosGray,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun OptionBar(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Text(
        text = title,
        color = DosGray,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
    )
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val active = selected == value
            Box(
                modifier = Modifier
                    .background(if (active) DosCyan else DosBlack)
                    .padding(1.dp),
            ) {
                DosButton(
                    text = label,
                    onClick = { onSelect(value) },
                    color = if (active) DosYellow else DosGray,
                    modifier = Modifier.width(90.dp),
                )
            }
        }
    }
}
