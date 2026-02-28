package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Táctica guardada.
 * Equivalente a TACTICS\TACTIC.%03X del original.
 * Los parámetros tácticos son los del contrato del simulador (pcf55_reverse_spec.md §8).
 */
@Entity(tableName = "tactic_presets")
data class TacticPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val managerProfileId: Int,
    val slotIndex: Int,             // 0..9 (hasta 10 tácticas guardadas)
    val name: String = "Táctica ${slotIndex + 1}",
    val formation: String = "4-4-2", // "4-3-3", "3-5-2", etc.
    // Parámetros tácticos del simulador
    val tipoJuego: Int = 2,         // 1=defensivo 2=equilibrado 3=ofensivo
    val tipoMarcaje: Int = 1,       // 1=al hombre 2=zona
    val tipoDespejes: Int = 1,      // 1=largo 2=controlado
    val tipoPresion: Int = 2,       // 1=baja 2=media 3=alta
    val faltas: Int = 2,            // 1=limpio 2=normal 3=duro
    val porcToque: Int = 50,        // % juego de toque (0..100)
    val porcContra: Int = 30,       // % contragolpe (0..100)
    val marcajeDefensas: Int = 50,
    val marcajeMedios: Int = 50,
    val puntoDefensa: Int = 40,
    val puntoAtaque: Int = 60,
    val area: Int = 50,
    val perdidaTiempo: Int = 0,     // 0=normal 1=ralentizar partido
    // Alineación: 11 player IDs ordenados por posición (null = vacío)
    val lineupJson: String = "[]",
)
