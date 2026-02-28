package com.pcfutbol.seasonstate

import com.pcfutbol.core.data.db.PlayerDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerFormUpdater @Inject constructor(
    private val playerDao: PlayerDao,
) {
    /**
     * Actualiza la forma de todos los jugadores del equipo
     * según el resultado del partido.
     * @param teamId equipo que jugó
     * @param won true si ganó, false si perdió, null si empató
     */
    suspend fun updateFormAfterMatch(teamId: Int, won: Boolean?) {
        val players = playerDao.byTeam(teamId).first()
        players.forEach { p ->
            val formDelta = when (won) {
                true -> +5
                false -> -4
                null -> +1
            }
            val moralDelta = when (won) {
                true -> +6
                false -> -5
                null -> +1
            }
            val newForm = (p.estadoForma + formDelta).coerceIn(0, 100)
            val newMoral = (p.moral + moralDelta).coerceIn(0, 100)
            playerDao.update(p.copy(estadoForma = newForm, moral = newMoral))
        }
    }

    /**
     * Decrementa contadores de lesión y sanción de todos los jugadores del equipo.
     * Llama al final de cada jornada.
     */
    suspend fun decrementInjuryAndSanction(teamId: Int) {
        val players = playerDao.byTeam(teamId).first()
        players.forEach { p ->
            val newInjury = (p.injuryWeeksLeft - 1).coerceAtLeast(0)
            val newSanction = (p.sanctionMatchesLeft - 1).coerceAtLeast(0)
            val newStatus = when {
                newInjury > 0 -> 1
                newSanction > 0 -> 2
                else -> 0
            }
            if (newInjury != p.injuryWeeksLeft || newSanction != p.sanctionMatchesLeft) {
                playerDao.update(
                    p.copy(
                        injuryWeeksLeft = newInjury,
                        sanctionMatchesLeft = newSanction,
                        status = newStatus,
                    )
                )
            }
        }
    }
}
