package com.pcfutbol.matchsim

/**
 * Contrato público del simulador de partidos.
 * Todos los parámetros derivan del análisis RE de MANDOS.DAT (pcf55_reverse_spec.md §8).
 */

/** Atributos de un jugador para el simulador (0..99 cada uno) */
data class PlayerSimAttrs(
    val playerId: Int = -1,
    val playerName: String = "Jugador",
    val ve: Int,        // velocidad
    val re: Int,        // resistencia
    val ag: Int,        // agresividad
    val ca: Int,        // calidad
    val remate: Int,
    val regate: Int,
    val pase: Int,
    val tiro: Int,
    val entrada: Int,
    val portero: Int,
    // Estado runtime
    val estadoForma: Int = 50,
    val moral: Int = 50,
)

/** Parámetros tácticos del equipo para el simulador */
data class TacticParams(
    val tipoJuego: Int = 2,         // 1=defensivo 2=equilibrado 3=ofensivo
    val tipoMarcaje: Int = 1,       // 1=al hombre 2=zona
    val tipoPresion: Int = 2,       // 1=baja 2=media 3=alta
    val tipoDespejes: Int = 1,      // 1=largo 2=controlado
    val faltas: Int = 2,            // 1=limpio 2=normal 3=duro
    val porcToque: Int = 50,        // 0..100
    val porcContra: Int = 30,       // 0..100
    val marcajeDefensas: Int = 50,
    val marcajeMedios: Int = 50,
    val puntoDefensa: Int = 40,
    val puntoAtaque: Int = 60,
    val area: Int = 50,
)

/** Input de un equipo al simulador */
data class TeamMatchInput(
    val teamId: Int,
    val teamName: String,
    val squad: List<PlayerSimAttrs>,  // los 11 titulares
    val tactic: TacticParams = TacticParams(),
    val isHome: Boolean,
)

/** Tipo de evento en el partido */
enum class EventType {
    GOAL, OWN_GOAL, YELLOW_CARD, RED_CARD, SUBSTITUTION, INJURY, VAR_DISALLOWED
}

/** Evento de partido */
data class MatchEvent(
    val minute: Int,
    val type: EventType,
    val teamId: Int,
    val playerId: Int? = null,
    val playerName: String? = null,
    val injuryWeeks: Int? = null,
    val description: String,
)

/** Resultado del partido */
data class MatchResult(
    val homeGoals: Int,
    val awayGoals: Int,
    val events: List<MatchEvent>,
    val homeStrength: Double,
    val awayStrength: Double,
    val seed: Long,
    val addedTimeFirstHalf: Int = 0,   // minutos añadidos 1ª parte
    val addedTimeSecondHalf: Int = 0,  // minutos añadidos 2ª parte
    val varDisallowedHome: Int = 0,    // goles anulados al local
    val varDisallowedAway: Int = 0,    // goles anulados al visitante
)

/** Contexto completo de un partido */
data class MatchContext(
    val fixtureId: Int,
    val home: TeamMatchInput,
    val away: TeamMatchInput,
    val seed: Long,
    val neutral: Boolean = false,
)
