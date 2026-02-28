package com.pcfutbol.competition

import com.pcfutbol.core.data.db.FixtureDao
import com.pcfutbol.core.data.db.StandingDao
import com.pcfutbol.core.data.db.StandingEntity
import com.pcfutbol.core.data.db.TeamDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configura una competición de liga:
 * 1) lee equipos por competición,
 * 2) genera calendario completo,
 * 3) persiste fixtures y clasificación inicial.
 */
@Singleton
class CompetitionSetupUseCase @Inject constructor(
    private val teamDao: TeamDao,
    private val fixtureDao: FixtureDao,
    private val standingDao: StandingDao,
) {
    suspend fun setup(competitionCode: String) {
        val teams = teamDao.byCompetition(competitionCode)
            .first()
            .map { it.slotId }

        if (teams.size < 2) return

        val fixtures = FixtureGenerator.generateLeague(
            competitionCode = competitionCode,
            teamIds = teams,
        )
        val initialStandings = teams.mapIndexed { index, teamId ->
            StandingEntity(
                competitionCode = competitionCode,
                teamId = teamId,
                position = index + 1,
            )
        }

        fixtureDao.deleteByCompetition(competitionCode)
        standingDao.deleteByCompetition(competitionCode)
        fixtureDao.insertAll(fixtures)
        standingDao.insertAll(initialStandings)
    }
}
