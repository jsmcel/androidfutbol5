package com.pcfutbol.promanager

import com.pcfutbol.core.data.db.ManagerProfileDao
import com.pcfutbol.core.data.db.ManagerProfileEntity
import com.pcfutbol.core.data.db.SeasonStateDao
import com.pcfutbol.core.data.db.TeamDao
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProManagerRepository @Inject constructor(
    private val managerProfileDao: ManagerProfileDao,
    private val teamDao: TeamDao,
    private val seasonStateDao: SeasonStateDao,
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

    suspend fun managerById(managerId: Int): ManagerProfileEntity? =
        managerProfileDao.byId(managerId)

    suspend fun recordSeasonEnd(
        managerId: Int,
        teamId: Int,
        objectivesMet: Boolean,
        finalPosition: Int,
    ) {
        if (managerId <= 0) return
        val manager = managerProfileDao.byId(managerId) ?: return
        val season = seasonStateDao.get()?.season ?: "?"
        val team = teamDao.byId(teamId)
        val competition = team?.competitionKey ?: "?"
        val teamName = team?.nameShort ?: "Equipo $teamId"

        val newPrestige = if (objectivesMet)
            (manager.prestige + 1).coerceAtMost(10)
        else
            (manager.prestige - 1).coerceAtLeast(1)
        val champion = finalPosition == 1
        val promoted = isPromotion(competition, finalPosition)
        val relegated = isRelegation(competition, finalPosition)
        val historyEntry = JSONObject()
            .put("season", season)
            .put("teamId", teamId)
            .put("team", teamName)
            .put("competition", competition)
            .put("tier", CompetitionDefinitions.competitionTier(competition))
            .put("position", finalPosition)
            .put("objectiveMet", objectivesMet)
            .put("prestigeAfter", newPrestige)
        val historyJson = appendHistoryEntry(manager.careerHistoryJson, historyEntry)

        managerProfileDao.update(manager.copy(
            prestige        = newPrestige,
            totalSeasons    = manager.totalSeasons + 1,
            titlesWon       = manager.titlesWon + if (champion) 1 else 0,
            promotionsAchieved = manager.promotionsAchieved + if (promoted) 1 else 0,
            relegationsSuffered = manager.relegationsSuffered + if (relegated) 1 else 0,
            careerHistoryJson = historyJson,
            currentTeamId   = -1,  // desvinculado, espera nuevas ofertas
        ))
    }

    // -------------------------------------------------------------------------

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun appendHistoryEntry(existingJson: String, entry: JSONObject): String {
        val array = runCatching { JSONArray(existingJson) }.getOrElse { JSONArray() }
        array.put(entry)
        while (array.length() > 30) {
            array.remove(0)
        }
        return array.toString()
    }

    private fun isPromotion(competition: String, finalPosition: Int): Boolean = when (competition) {
        CompetitionDefinitions.LIGA2 -> finalPosition in 1..3
        CompetitionDefinitions.LIGA2B,
        CompetitionDefinitions.LIGA2B2,
        CompetitionDefinitions.RFEF1A,
        CompetitionDefinitions.RFEF1B -> finalPosition == 1
        CompetitionDefinitions.RFEF2A,
        CompetitionDefinitions.RFEF2B,
        CompetitionDefinitions.RFEF2C,
        CompetitionDefinitions.RFEF2D -> finalPosition in 1..3
        else -> false
    }

    private fun isRelegation(competition: String, finalPosition: Int): Boolean = when (competition) {
        CompetitionDefinitions.LIGA1 -> finalPosition >= 18
        CompetitionDefinitions.LIGA2 -> finalPosition >= 20
        CompetitionDefinitions.LIGA2B,
        CompetitionDefinitions.LIGA2B2,
        CompetitionDefinitions.RFEF1A,
        CompetitionDefinitions.RFEF1B -> finalPosition >= 18
        CompetitionDefinitions.RFEF2A,
        CompetitionDefinitions.RFEF2B,
        CompetitionDefinitions.RFEF2C,
        CompetitionDefinitions.RFEF2D -> finalPosition >= 16
        else -> false
    }
}
