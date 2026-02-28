package com.pcfutbol.promanager

import com.pcfutbol.core.data.db.ManagerProfileDao
import com.pcfutbol.core.data.db.ManagerProfileEntity
import com.pcfutbol.core.data.db.TeamDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProManagerRepository @Inject constructor(
    private val managerProfileDao: ManagerProfileDao,
    private val teamDao: TeamDao,
) {

    fun allManagers(): Flow<List<ManagerProfileEntity>> =
        managerProfileDao.allActive()

    suspend fun createManager(name: String, password: String): ManagerProfileEntity {
        val profile = ManagerProfileEntity(
            name         = name,
            passwordHash = hashPassword(password),
            prestige     = 1,
        )
        val id = managerProfileDao.insert(profile).toInt()
        return profile.copy(id = id)
    }

    suspend fun authenticate(name: String, password: String): ManagerProfileEntity? {
        val profile = managerProfileDao.byName(name) ?: return null
        return if (profile.passwordHash == hashPassword(password)) profile else null
    }

    suspend fun generateOffers(managerId: Int): List<OfferPoolGenerator.ManagerOffer> {
        val manager = managerProfileDao.byId(managerId) ?: return emptyList()
        val allTeams = teamDao.allTeams()
            .first()
            .filter { it.competitionKey.isNotBlank() && it.competitionKey != "FOREIGN" }
        val occupiedTeams = managerProfileDao.allActive()
            .first()
            .mapNotNull { profile -> profile.currentTeamId.takeIf { it > 0 } }
            .toSet()
        return OfferPoolGenerator.generate(
            manager = manager,
            allTeams = allTeams,
            teamsWithManager = occupiedTeams,
        )
    }

    suspend fun acceptOffer(managerId: Int, teamSlotId: Int) {
        val manager = managerProfileDao.byId(managerId) ?: return
        managerProfileDao.update(manager.copy(currentTeamId = teamSlotId))
    }

    suspend fun managerIdForTeam(teamId: Int): Int =
        managerProfileDao.byTeam(teamId)?.id ?: -1

    suspend fun recordSeasonEnd(
        managerId: Int,
        teamId: Int,
        objectivesMet: Boolean,
        finalPosition: Int,
    ) {
        val manager = managerProfileDao.byId(managerId) ?: return
        val newPrestige = if (objectivesMet)
            (manager.prestige + 1).coerceAtMost(10)
        else
            (manager.prestige - 1).coerceAtLeast(1)

        managerProfileDao.update(manager.copy(
            prestige        = newPrestige,
            totalSeasons    = manager.totalSeasons + 1,
            currentTeamId   = -1,  // desvinculado, espera nuevas ofertas
        ))
    }

    // -------------------------------------------------------------------------

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
