package com.pcfutbol.core.data.seed

import android.content.Context
import android.util.Log
import com.pcfutbol.core.data.db.CompetitionEntity
import com.pcfutbol.core.data.db.PcfDatabase
import com.pcfutbol.core.data.db.PlayerEntity
import com.pcfutbol.core.data.db.SeasonStateEntity
import com.pcfutbol.core.data.db.TeamEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SeedLoader"

private data class SyntheticSeedPlayer(
    val teamSlotId: Int,
    val player: SyntheticPlayer,
)

private data class SyntheticSeedPayload(
    val teams: List<TeamEntity>,
    val players: List<SyntheticSeedPlayer>,
    val leagueTeamCounts: Map<String, Int>,
)

/**
 * Carga los datos iniciales de la temporada 2025/26 en la base de datos Room.
 */
@Singleton
class SeedLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PcfDatabase,
) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    suspend fun load() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Iniciando seed de datos 2025/26...")

        val teams = loadTeams()
        val tmMap = loadTmMapping()
        val csvPlayers = loadPlayers()
        val slotCompMap = buildSlotCompetitionMap(csvPlayers)

        val nextSlotId = (teams.maxOfOrNull { it.index } ?: 0) + 1
        val synthetic = seedSyntheticLeagues(startSlotId = nextSlotId)
        val teamCounts = buildCompetitionTeamCounts(slotCompMap, synthetic.leagueTeamCounts)

        seedCompetitions(teamCounts)
        seedTeams(teams, tmMap, slotCompMap, synthetic.teams)
        seedPlayers(csvPlayers, synthetic.players)
        seedSeasonState()

        Log.i(
            TAG,
            "Seed completado: ${teams.size + synthetic.teams.size} equipos, " +
                "${csvPlayers.size + synthetic.players.size} jugadores"
        )
    }

    // ---------------------------------------------------------------------
    // Carga de assets

    private fun loadTeams(): List<SeedTeam> {
        val json = context.assets.open("pcf55_teams_extracted.json")
            .bufferedReader().readText()
        val type = Types.newParameterizedType(List::class.java, SeedTeam::class.java)
        return moshi.adapter<List<SeedTeam>>(type).fromJson(json) ?: emptyList()
    }

    private fun loadTmMapping(): Map<Int, SeedTmMapping> {
        val json = context.assets.open("pcf55_transfermarkt_mapping.json")
            .bufferedReader().readText()
        val type = Types.newParameterizedType(List::class.java, SeedTmMapping::class.java)
        val list = moshi.adapter<List<SeedTmMapping>>(type).fromJson(json) ?: emptyList()
        return list.associateBy { it.pcfIndex }
    }

    private fun loadPlayers(): List<SeedPlayer> {
        val csv = context.assets.open("pcf55_players_2526.csv")
            .bufferedReader().readText()
        return csv
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull(::parseCsvPlayerLine)
            .toList()
    }

    private fun parseCsvPlayerLine(line: String): SeedPlayer? = runCatching {
        val cols = line.split(",")
        require(cols.size >= 19) { "CSV incompleto (${cols.size} columnas)" }

        fun value(index: Int): String = cols.getOrNull(index)?.trim().orEmpty()
        fun intValue(index: Int): Int = value(index).toIntOrNull() ?: 0
        fun longValue(index: Int): Long = value(index).toLongOrNull() ?: 0L

        SeedPlayer(
            teamSlotId = intValue(0),
            teamName = value(1),
            competition = value(2),
            playerName = value(3),
            position = value(4),
            age = intValue(5),
            marketValueEur = longValue(6),
            ve = intValue(7),
            re = intValue(8),
            ag = intValue(9),
            ca = intValue(10),
            me = intValue(11),
            portero = intValue(12),
            entrada = intValue(13),
            regate = intValue(14),
            remate = intValue(15),
            pase = intValue(16),
            tiro = intValue(17),
            source = value(18),
        )
    }.onFailure { e ->
        Log.w(TAG, "Error parseando jugador: $line", e)
    }.getOrNull()

    // ---------------------------------------------------------------------
    // Seed principal

    private fun buildSlotCompetitionMap(players: List<SeedPlayer>): Map<Int, String> =
        players
            .groupBy { it.teamSlotId }
            .mapValues { (_, ps) -> ps.first().competition }

    private fun buildCompetitionTeamCounts(
        slotCompMap: Map<Int, String>,
        syntheticCounts: Map<String, Int>,
    ): Map<String, Int> {
        val csvCounts = slotCompMap
            .entries
            .groupBy(
                keySelector = { (_, tmCode) -> CompetitionDefinitions.tmCompToKey[tmCode] ?: "FOREIGN" },
                valueTransform = { (slotId, _) -> slotId },
            )
            .mapValues { (_, slotIds) -> slotIds.toSet().size }

        val allKeys = (csvCounts.keys + syntheticCounts.keys).toSet()
        return allKeys.associateWith { key ->
            (csvCounts[key] ?: 0) + (syntheticCounts[key] ?: 0)
        }
    }

    private suspend fun seedCompetitions(teamCounts: Map<String, Int>) {
        val entities = CompetitionDefinitions.all(teamCounts).map { it.toEntity() }
        db.competitionDao().insertAll(entities)
        Log.d(TAG, "Competiciones insertadas: ${entities.size}")
    }

    private suspend fun seedTeams(
        teams: List<SeedTeam>,
        tmMap: Map<Int, SeedTmMapping>,
        slotCompMap: Map<Int, String>,
        syntheticTeams: List<TeamEntity>,
    ) {
        val baseTeams = teams.map { team ->
            val tm = tmMap[team.index]
            val rawComp = slotCompMap[team.index] ?: tm?.tmCompetition
            val compKey = rawComp
                ?.let { CompetitionDefinitions.tmCompToKey[it] }
                ?: "FOREIGN"
            TeamEntity(
                slotId = team.index,
                nameShort = team.teamName.take(18),
                nameFull = team.fullName?.takeIf { it.isNotBlank() }
                    ?: tm?.tmTeam
                    ?: team.teamName,
                stadiumName = tm?.tmStadium?.takeIf { it.isNotBlank() }
                    ?: team.stadiumName,
                countryId = inferCountry(rawComp),
                competitionKey = compKey,
                budgetK = tm?.let { (it.tmMarketValueEur / 10_000.0).toInt().coerceAtLeast(500) } ?: 1000,
                membersCount = 0,
                sponsor = "",
                prestige = calcPrestige(tm?.tmMarketValueEur?.toLong() ?: 5_000_000L),
                shieldAssetName = "shield_${team.index}",
                kitAssetName = "kit_${team.index}",
            )
        }

        val entities = baseTeams + syntheticTeams
        db.teamDao().insertAll(entities)
        logTeamDistribution(entities)
    }

    private fun logTeamDistribution(teams: List<TeamEntity>) {
        val total = teams.size
        val byCompetition = teams.groupingBy { it.competitionKey }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .joinToString(", ") { (comp, count) -> "$comp=$count" }
        Log.d(TAG, "Equipos insertados: $total ($byCompetition)")
    }

    private suspend fun seedPlayers(
        csvPlayers: List<SeedPlayer>,
        syntheticPlayers: List<SyntheticSeedPlayer>,
    ) {
        var pid = 1
        val csvEntities = csvPlayers.map { p ->
            PlayerEntity(
                pid = pid++,
                teamSlotId = p.teamSlotId,
                number = 0,
                nameShort = p.playerName.take(15),
                nameFull = p.playerName,
                position = mapPosition(p.position),
                roles = defaultRoles(mapPosition(p.position)),
                citizenship = inferCountryFromComp(p.competition),
                status = 0,
                birthDay = 1,
                birthMonth = 1,
                birthYear = 2025 - p.age,
                height = 178,
                weight = 75,
                skin = 1,
                hair = 1,
                ve = p.ve,
                re = p.re,
                ag = p.ag,
                ca = p.ca,
                remate = p.remate,
                regate = p.regate,
                pase = p.pase,
                tiro = p.tiro,
                entrada = p.entrada,
                portero = p.portero,
                wageK = estimateWage(p.marketValueEur),
                contractEndYear = 2026 + (p.age % 3),
                releaseClauseK = (p.marketValueEur / 1000).toInt(),
            )
        }

        val syntheticEntities = syntheticPlayers.map { s ->
            val p = s.player
            PlayerEntity(
                pid = pid++,
                teamSlotId = s.teamSlotId,
                number = 0,
                nameShort = p.name.take(15),
                nameFull = p.name,
                position = p.position,
                roles = defaultRoles(p.position),
                citizenship = p.country,
                status = 0,
                birthDay = 1,
                birthMonth = 1,
                birthYear = 2025 - p.age,
                height = if (p.position == "PO") 188 else 178,
                weight = if (p.position == "PO") 83 else 75,
                skin = 1,
                hair = 1,
                ve = p.ve,
                re = p.re,
                ag = p.ag,
                ca = p.ca,
                remate = p.remate,
                regate = p.regate,
                pase = p.pase,
                tiro = p.tiro,
                entrada = p.entrada,
                portero = p.portero,
                wageK = estimateWage(p.marketValueEur),
                contractEndYear = 2027 + (p.age % 4),
                releaseClauseK = (p.marketValueEur / 1000).toInt().coerceAtLeast(2500),
            )
        }

        db.playerDao().insertAll(csvEntities + syntheticEntities)
        Log.d(TAG, "Jugadores insertados: ${csvEntities.size + syntheticEntities.size}")
    }

    /**
     * Genera equipos y plantillas para ligas fuera del CSV principal.
     */
    private fun seedSyntheticLeagues(startSlotId: Int): SyntheticSeedPayload {
        var nextSlot = startSlotId
        val teams = mutableListOf<TeamEntity>()
        val players = mutableListOf<SyntheticSeedPlayer>()

        val syntheticLeagues = listOf(
            SyntheticLeagues.ARGENTINA,
            SyntheticLeagues.BRASIL,
            SyntheticLeagues.MEXICO,
            SyntheticLeagues.SAUDI,
            SyntheticLeagues.RFEF1A,
            SyntheticLeagues.RFEF1B,
            SyntheticLeagues.RFEF2A,
            SyntheticLeagues.RFEF2B,
            SyntheticLeagues.RFEF2C,
            SyntheticLeagues.RFEF2D,
        )

        syntheticLeagues.flatten().forEach { spec ->
            val slotId = nextSlot++
            teams += TeamEntity(
                slotId = slotId,
                nameShort = spec.name.take(18),
                nameFull = spec.name,
                stadiumName = "${spec.name} Stadium",
                countryId = spec.country,
                competitionKey = spec.competition,
                budgetK = estimateSyntheticBudget(spec),
                membersCount = 0,
                sponsor = "",
                prestige = estimateSyntheticPrestige(spec.avgMe),
                shieldAssetName = "shield_1",
                kitAssetName = "kit_1",
            )
            SyntheticLeagues.generatePlayers(spec).forEach { player ->
                players += SyntheticSeedPlayer(teamSlotId = slotId, player = player)
            }
        }

        return SyntheticSeedPayload(
            teams = teams,
            players = players,
            leagueTeamCounts = teams.groupingBy { it.competitionKey }.eachCount(),
        )
    }

    private fun estimateSyntheticPrestige(avgMe: Int): Int =
        ((avgMe - 35) / 5).coerceIn(1, 10)

    private fun estimateSyntheticBudget(team: SyntheticTeam): Int =
        (team.avgMe * team.squad * 12).coerceAtLeast(2_000)

    private suspend fun seedSeasonState() {
        val existing = db.seasonStateDao().get()
        if (existing != null) return

        db.seasonStateDao().insert(
            SeasonStateEntity(
                id = 1,
                season = "2025-26",
                currentMatchday = 1,
                currentDate = "2025-08-15",
                phase = "PRESEASON",
                managerTeamId = -1,
                managerMode = "MANAGER:${CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE}",
                transferWindowOpen = true,
                objectivesMet = false,
            )
        )
        Log.d(TAG, "SeasonState inicial creado")
    }

    // ---------------------------------------------------------------------
    // Helpers

    private fun mapPosition(tmPosition: String): String = when {
        tmPosition.contains("Goalkeeper", ignoreCase = true) -> "PO"
        tmPosition.contains("Defender", ignoreCase = true) -> "DF"
        tmPosition.contains("Forward", ignoreCase = true) -> "DC"
        else -> "MC"
    }

    private fun defaultRoles(pos: String): String = when (pos) {
        "PO" -> "[0,0,0,0,0,0]"
        "DF" -> "[1,0,0,0,0,0]"
        "MC" -> "[2,0,0,0,0,0]"
        "DC" -> "[3,0,0,0,0,0]"
        else -> "[2,0,0,0,0,0]"
    }

    private fun inferCountry(tmComp: String?): String = when (tmComp) {
        "ES1", "ES2", "ES3", "E3G1", "E3G2" -> "ES"
        "GB1" -> "GB"
        "IT1" -> "IT"
        "FR1" -> "FR"
        "L1", "DE1" -> "DE"
        "NL1" -> "NL"
        "PO1", "PT1" -> "PT"
        "BE1" -> "BE"
        "TR1" -> "TR"
        "SC1" -> "SC"
        "RU1" -> "RU"
        "DK1" -> "DK"
        "PL1" -> "PL"
        "A1" -> "AT"
        else -> "XX"
    }

    private fun inferCountryFromComp(comp: String): String = inferCountry(comp)

    private fun calcPrestige(marketValueEur: Long): Int = when {
        marketValueEur >= 500_000_000 -> 10
        marketValueEur >= 200_000_000 -> 9
        marketValueEur >= 100_000_000 -> 8
        marketValueEur >= 50_000_000 -> 7
        marketValueEur >= 25_000_000 -> 6
        marketValueEur >= 10_000_000 -> 5
        marketValueEur >= 5_000_000 -> 4
        marketValueEur >= 2_000_000 -> 3
        marketValueEur >= 500_000 -> 2
        else -> 1
    }

    private fun estimateWage(marketValueEur: Long): Int =
        (marketValueEur / 200_000).toInt().coerceAtLeast(1)

    private fun SeedCompetition.toEntity() = CompetitionEntity(
        code = code,
        name = name,
        formatType = formatType,
        teamCount = teamCount,
        matchdayCount = matchdayCount,
        promotionSlots = promotionSlots,
        relegationSlots = relegationSlots,
        europeanSlots = europeanSlots,
        playoffSlots = playoffSlots,
        tiebreakRule = tiebreakRule,
        season = "2025-26",
    )
}
