package com.pcfutbol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcfutbol.ui.components.DosButton
import com.pcfutbol.ui.components.DosPanel
import com.pcfutbol.ui.theme.*
import com.pcfutbol.ui.viewmodels.ProManagerViewModel

/**
 * Pantalla de selección de ofertas ProManager.
 * Equivalente a la pantalla "SELECCION DE OFERTAS" del original.
 */
@Composable
fun ProManagerOffersScreen(
    onNavigateUp: () -> Unit,
    onOfferAccepted: () -> Unit,
    vm: ProManagerViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(uiState.offerAccepted) {
        if (uiState.offerAccepted) {
            vm.consumeOfferAccepted()
            onOfferAccepted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DosBlack)
            .padding(8.dp),
    ) {
        // Barra superior
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DosCyan)
            }
            Text(
                text       = "PROMANAGER",
                color      = DosYellow,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        DosButton(
            text = "NIVEL CONTROL: ${uiState.managerControlModeLabel}",
            onClick = vm::cycleControlMode,
            modifier = Modifier.fillMaxWidth(),
            color = if (uiState.managerControlMode == "TOTAL") DosGreen else DosCyan,
        )
        if (uiState.managerControlMode == "BASIC") {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Modo Basico: sin ordenes de entrenador en partido.",
                color = DosGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        when {
            uiState.noManager -> {
                // Crear nuevo manager
                CreateManagerSection(
                    onCreated = { name, pwd -> vm.createManager(name, pwd) }
                )
            }

            uiState.pendingLogin -> {
                // Login con contraseña
                LoginSection(
                    managerNames = uiState.managerNames,
                    onLogin      = { name, pwd -> vm.login(name, pwd) },
                )
            }

            else -> {
                // Selección de ofertas
                uiState.managerName?.let { name ->
                    Text(
                        text       = "OFERTAS PARA $name",
                        color      = DosCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.padding(bottom = 8.dp),
                    )
                }

                DosPanel(title = "RESUMEN DE CARRERA") {
                    Text(
                        text = "Prestigio: ${uiState.prestige}",
                        color = DosYellow,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "Temporadas: ${uiState.totalSeasons}",
                        color = DosWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    if (uiState.careerHistory.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        uiState.careerHistory.forEach { line ->
                            Text(
                                text = line,
                                color = DosGray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                DosPanel(title = "SELECCION DE OFERTAS") {
                    if (uiState.offers.isEmpty()) {
                        Text(
                            "Cargando ofertas...",
                            color = DosGray,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(uiState.offers) { offer ->
                                OfferRow(
                                    teamName      = offer.teamName,
                                    competition   = offer.competitionKey,
                                    salaryK       = offer.salaryK,
                                    objective     = offer.objectiveLabel,
                                    selected      = offer.teamId == uiState.selectedOfferId,
                                    onClick       = { vm.selectOffer(offer.teamId) },
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        DosButton(
                            text    = "ACEPTAR OFERTA",
                            onClick = { vm.acceptSelected() },
                            enabled = uiState.selectedOfferId != -1,
                            color   = DosGreen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferRow(
    teamName: String,
    competition: String,
    salaryK: Int,
    objective: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) DosYellow else DosGray
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor)
            .background(if (selected) DosNavy else DosBlack.copy(alpha = 0.0f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(teamName, color = if (selected) DosYellow else DosWhite,
                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace)
            Text("$competition · $objective",
                color = DosLightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Text(
            text = "${salaryK}K€/sem",
            color = DosGreen, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CreateManagerSection(onCreated: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pwd  by remember { mutableStateOf("") }

    DosPanel(title = "NUEVO MANAGER") {
        Text("Nombre:", color = DosGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor    = DosCyan,
                unfocusedBorderColor  = DosGray,
                focusedTextColor      = DosWhite,
                unfocusedTextColor    = DosWhite,
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        Text("Contraseña (PIN):", color = DosGray, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)
        OutlinedTextField(
            value = pwd, onValueChange = { pwd = it.take(4) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor    = DosCyan,
                unfocusedBorderColor  = DosGray,
                focusedTextColor      = DosWhite,
                unfocusedTextColor    = DosWhite,
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        DosButton(
            text     = "CREAR MANAGER",
            onClick  = { if (name.isNotBlank() && pwd.isNotBlank()) onCreated(name, pwd) },
            enabled  = name.isNotBlank() && pwd.isNotBlank(),
        )
    }
}

@Composable
private fun LoginSection(
    managerNames: List<String>,
    onLogin: (String, String) -> Unit,
) {
    var selectedName by remember { mutableStateOf(managerNames.firstOrNull() ?: "") }
    var pwd          by remember { mutableStateOf("") }

    DosPanel(title = "CLAVE DE ACCESO") {
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(managerNames) { name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (name == selectedName) DosNavy else DosBlack)
                        .clickable { selectedName = name }
                        .padding(8.dp),
                ) {
                    Text(name, color = if (name == selectedName) DosCyan else DosWhite,
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = pwd, onValueChange = { pwd = it.take(4) },
            label = { Text("PIN", color = DosGray, fontFamily = FontFamily.Monospace) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DosCyan,
                unfocusedBorderColor = DosGray,
                focusedTextColor = DosWhite,
                unfocusedTextColor = DosWhite,
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        DosButton(
            text    = "ACCEDER",
            onClick = { onLogin(selectedName, pwd) },
            enabled = selectedName.isNotBlank() && pwd.isNotBlank(),
        )
    }
}
