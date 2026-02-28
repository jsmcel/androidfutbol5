package com.pcfutbol.matchsim

/**
 * Calcula la fortaleza de un equipo para el simulador.
 * Fórmula calibrada contra engine_strength_reference_global_final_force_liga2_safe.csv
 *
 * Pesos por posición según el sistema de juego heredado del original:
 *  - Portero: portero (peso alto)
 *  - Defensas: entrada, ca, ve
 *  - Medios: pase, ca, re
 *  - Delanteros: remate, regate, ca, tiro
 */
object StrengthCalculator {

    /**
     * Devuelve la fortaleza del equipo en escala 0..100.
     * El parámetro [squad] debe tener los 11 titulares en orden: PO, DF×4, MC×4, DC×2
     */
    fun calculate(input: TeamMatchInput): Double {
        if (input.squad.isEmpty()) return 50.0

        val gk = input.squad.firstOrNull() ?: return 50.0
        val defenders  = input.squad.drop(1).take(4)
        val midfielders = input.squad.drop(5).take(4)
        val forwards   = input.squad.drop(9).take(2)

        val gkScore = gkStrength(gk)
        val defScore = defenders.map { defStrength(it) }.average().takeIf { !it.isNaN() } ?: 50.0
        val midScore = midfielders.map { midStrength(it) }.average().takeIf { !it.isNaN() } ?: 50.0
        val fwdScore = forwards.map { fwdStrength(it) }.average().takeIf { !it.isNaN() } ?: 50.0

        // Pesos: GK 15% + DEF 30% + MID 30% + FWD 25%
        val base = gkScore * 0.15 + defScore * 0.30 + midScore * 0.30 + fwdScore * 0.25

        // Ajuste táctico
        val tacticalBonus = tacticalAdjustment(input.tactic, input.isHome)

        // Ajuste de forma y moral del equipo
        val runtimeBonus = input.squad.map { runtimeBonus(it) }.average()
            .takeIf { !it.isNaN() } ?: 0.0

        return (base + tacticalBonus + runtimeBonus).coerceIn(10.0, 99.0)
    }

    // -------------------------------------------------------------------------

    private fun gkStrength(p: PlayerSimAttrs): Double =
        p.portero * 0.6 + p.re * 0.2 + p.ca * 0.2 + formFactor(p)

    private fun defStrength(p: PlayerSimAttrs): Double =
        p.entrada * 0.4 + p.ca * 0.3 + p.ve * 0.2 + p.re * 0.1 + formFactor(p)

    private fun midStrength(p: PlayerSimAttrs): Double =
        p.pase * 0.35 + p.ca * 0.30 + p.re * 0.20 + p.tiro * 0.15 + formFactor(p)

    private fun fwdStrength(p: PlayerSimAttrs): Double =
        p.remate * 0.40 + p.regate * 0.25 + p.ca * 0.20 + p.tiro * 0.15 + formFactor(p)

    private fun formFactor(p: PlayerSimAttrs): Double =
        (p.estadoForma - 50) * 0.05

    private fun runtimeBonus(p: PlayerSimAttrs): Double =
        (p.estadoForma - 50) * 0.02 + moralAdjustment(p.moral)

    private fun moralAdjustment(moral: Int): Double =
        ((moral - 50) / 100.0) * 2.0

    private fun tacticalAdjustment(t: TacticParams, isHome: Boolean): Double {
        var bonus = 0.0
        // Ventaja de campo
        if (isHome) bonus += 3.0
        // Tipo de juego: ofensivo añade ataque, defensivo añade defensa
        bonus += when (t.tipoJuego) {
            3 -> 1.5    // ofensivo
            1 -> -1.0   // defensivo (menos goles pero más sólido)
            else -> 0.0
        }
        // Presión alta: más agotamiento pero más robamos
        if (t.tipoPresion == 3) bonus += 1.0
        else if (t.tipoPresion == 1) bonus -= 0.5
        if (t.tipoMarcaje == 1) bonus += 0.3
        if (t.faltas == 3) bonus += 0.2
        if (t.porcContra > 60) bonus += 0.3
        if (t.tipoDespejes == 2) bonus += 0.2
        if (t.perdidaTiempo == 1) bonus -= 0.4
        return bonus
    }
}
