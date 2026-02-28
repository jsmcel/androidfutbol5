package com.pcfutbol.core.data.seed

/**
 * Definiciones de competiciones para temporada 2025/26.
 * Las ligas calculan tamano y jornadas en base a los equipos detectados en seed.
 */
object CompetitionDefinitions {

    const val LIGA1 = "LIGA1"
    const val LIGA2 = "LIGA2"
    const val LIGA2B = "LIGA2B"
    const val LIGA2B2 = "LIGA2B2"
    const val RFEF1A = "RFEF1A"
    const val RFEF1B = "RFEF1B"
    const val RFEF2A = "RFEF2A"
    const val RFEF2B = "RFEF2B"
    const val RFEF2C = "RFEF2C"
    const val RFEF2D = "RFEF2D"
    const val CEURO = "CEURO"
    const val RECOPA = "RECOPA"
    const val CUEFA = "CUEFA"
    const val SCESP = "SCESP"
    const val SCEUR = "SCEUR"
    const val DEFAULT_MANAGER_LEAGUE = LIGA1

    private const val DEFAULT_TIEBREAK = "STANDARD"
    private const val ES_TIEBREAK = "ES_LIGA"

    private data class LeagueTemplate(
        val code: String,
        val name: String,
        val country: String,
        val fallbackTeamCount: Int,
        val europeanSlots: Int,
        val playoffSlots: Int,
        val tiebreakRule: String = DEFAULT_TIEBREAK,
        val promotionModel: PromotionModel = PromotionModel.NONE,
    )

    private enum class PromotionModel {
        NONE,
        SECOND_TIER,
        THIRD_TIER,
        RFEF1,
        RFEF2,
    }

    private val leagueTemplates: List<LeagueTemplate> = listOf(
        LeagueTemplate(LIGA1, "Primera Division", "ESP", fallbackTeamCount = 20, europeanSlots = 5, playoffSlots = 1, tiebreakRule = ES_TIEBREAK),
        LeagueTemplate(LIGA2, "Segunda Division", "ESP", fallbackTeamCount = 22, europeanSlots = 0, playoffSlots = 4, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.SECOND_TIER),
        LeagueTemplate(LIGA2B, "Primera RFEF Grupo 1", "ESP", fallbackTeamCount = 18, europeanSlots = 0, playoffSlots = 4, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.THIRD_TIER),
        LeagueTemplate(LIGA2B2, "Primera RFEF Grupo 2", "ESP", fallbackTeamCount = 18, europeanSlots = 0, playoffSlots = 4, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.THIRD_TIER),
        LeagueTemplate(RFEF1A, "1a RFEF Grupo A", "ESP", fallbackTeamCount = 20, europeanSlots = 0, playoffSlots = 4, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.RFEF1),
        LeagueTemplate(RFEF1B, "1a RFEF Grupo B", "ESP", fallbackTeamCount = 20, europeanSlots = 0, playoffSlots = 4, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.RFEF1),
        LeagueTemplate(RFEF2A, "2a RFEF Grupo A", "ESP", fallbackTeamCount = 18, europeanSlots = 0, playoffSlots = 0, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.RFEF2),
        LeagueTemplate(RFEF2B, "2a RFEF Grupo B", "ESP", fallbackTeamCount = 18, europeanSlots = 0, playoffSlots = 0, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.RFEF2),
        LeagueTemplate(RFEF2C, "2a RFEF Grupo C", "ESP", fallbackTeamCount = 18, europeanSlots = 0, playoffSlots = 0, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.RFEF2),
        LeagueTemplate(RFEF2D, "2a RFEF Grupo D", "ESP", fallbackTeamCount = 18, europeanSlots = 0, playoffSlots = 0, tiebreakRule = ES_TIEBREAK, promotionModel = PromotionModel.RFEF2),
        LeagueTemplate("PRML", "Premier League", "ENG", fallbackTeamCount = 20, europeanSlots = 5, playoffSlots = 0),
        LeagueTemplate("SERIA", "Serie A", "ITA", fallbackTeamCount = 20, europeanSlots = 4, playoffSlots = 0),
        LeagueTemplate("LIG1", "Ligue 1", "FRA", fallbackTeamCount = 18, europeanSlots = 3, playoffSlots = 0),
        LeagueTemplate("BUN1", "Bundesliga", "DEU", fallbackTeamCount = 18, europeanSlots = 4, playoffSlots = 0),
        LeagueTemplate("ERED", "Eredivisie", "NLD", fallbackTeamCount = 18, europeanSlots = 3, playoffSlots = 0),
        LeagueTemplate("PRIM", "Primeira Liga", "PRT", fallbackTeamCount = 18, europeanSlots = 3, playoffSlots = 0),
        LeagueTemplate("BELGA", "Belgian Pro League", "BEL", fallbackTeamCount = 16, europeanSlots = 3, playoffSlots = 0),
        LeagueTemplate("SUPERL", "Super Lig", "TUR", fallbackTeamCount = 19, europeanSlots = 2, playoffSlots = 0),
        LeagueTemplate("SCOT", "Scottish Premiership", "SCO", fallbackTeamCount = 12, europeanSlots = 2, playoffSlots = 0),
        LeagueTemplate("RPL", "Russian Premier League", "RUS", fallbackTeamCount = 16, europeanSlots = 2, playoffSlots = 0),
        LeagueTemplate("DSL", "Danish Superliga", "DNK", fallbackTeamCount = 14, europeanSlots = 2, playoffSlots = 0),
        LeagueTemplate("EKSTR", "Ekstraklasa", "POL", fallbackTeamCount = 18, europeanSlots = 2, playoffSlots = 0),
        LeagueTemplate("ABUND", "Austrian Bundesliga", "AUT", fallbackTeamCount = 12, europeanSlots = 2, playoffSlots = 0),
        LeagueTemplate("ARGPD", "Primera Division Argentina", "ARG", fallbackTeamCount = 28, europeanSlots = 0, playoffSlots = 0),
        LeagueTemplate("BRASEA", "Serie A Brasileira", "BRA", fallbackTeamCount = 20, europeanSlots = 0, playoffSlots = 0),
        LeagueTemplate("LIGAMX", "Liga MX", "MEX", fallbackTeamCount = 18, europeanSlots = 0, playoffSlots = 0),
        LeagueTemplate("SPL", "Saudi Pro League", "SAU", fallbackTeamCount = 18, europeanSlots = 0, playoffSlots = 0),
    )

    val ALL_LEAGUE_CODES: List<String> = leagueTemplates.map { it.code }

    val COMPETITION_NAMES: Map<String, String> =
        leagueTemplates.associate { it.code to it.name } + mapOf(
            "CREY" to "Copa del Rey",
            CEURO to "UEFA Champions League",
            RECOPA to "UEFA Europa League",
            CUEFA to "UEFA Conference League",
            SCESP to "Supercopa de Espana",
            SCEUR to "Supercopa de Europa",
            "FOREIGN" to "Liga Extranjera",
        )

    val COMPETITION_MATCHDAYS: Map<String, Int> = leagueTemplates.associate { template ->
        template.code to roundRobinMatchdays(template.fallbackTeamCount)
    }

    val COMPETITION_TIERS: Map<String, Int> = mapOf(
        // Tier 1 (elite)
        LIGA1 to 1,
        "PRML" to 1,
        "SERIA" to 1,
        "LIG1" to 1,
        "BUN1" to 1,
        "SPL" to 1,
        // Tier 2 (segunda/primera internacional)
        LIGA2 to 2,
        "ERED" to 2,
        "PRIM" to 2,
        "BELGA" to 2,
        "SUPERL" to 2,
        "SCOT" to 2,
        "RPL" to 2,
        "DSL" to 2,
        "EKSTR" to 2,
        "ABUND" to 2,
        "ARGPD" to 2,
        "BRASEA" to 2,
        "LIGAMX" to 2,
        // Tier 3 (1a RFEF)
        LIGA2B to 3,
        LIGA2B2 to 3,
        RFEF1A to 3,
        RFEF1B to 3,
        // Tier 4 (2a RFEF)
        RFEF2A to 4,
        RFEF2B to 4,
        RFEF2C to 4,
        RFEF2D to 4,
    )

    private val cupDefinitions: List<SeedCompetition> = listOf(
        SeedCompetition("CREY", "Copa del Rey", "KNOCKOUT", 32, 0, 0, 0, 1, 0, DEFAULT_TIEBREAK),
        SeedCompetition(CEURO, "UEFA Champions League", "GROUP_KNOCKOUT", 36, 8, 0, 0, 0, 0, DEFAULT_TIEBREAK),
        SeedCompetition(RECOPA, "UEFA Europa League", "GROUP_KNOCKOUT", 36, 8, 0, 0, 0, 0, DEFAULT_TIEBREAK),
        SeedCompetition(CUEFA, "UEFA Conference League", "GROUP_KNOCKOUT", 36, 8, 0, 0, 0, 0, DEFAULT_TIEBREAK),
        SeedCompetition(SCESP, "Supercopa de Espana", "KNOCKOUT", 4, 0, 0, 0, 0, 0, DEFAULT_TIEBREAK),
        SeedCompetition(SCEUR, "Supercopa de Europa", "KNOCKOUT", 2, 0, 0, 0, 0, 0, DEFAULT_TIEBREAK),
        SeedCompetition("FOREIGN", "Liga Extranjera", "NONE", 0, 0, 0, 0, 0, 0, DEFAULT_TIEBREAK),
    )

    /** Codigo TM CSV -> clave interna del juego. */
    val tmCompToKey: Map<String, String> = mapOf(
        "ES1" to "LIGA1",
        "ES2" to "LIGA2",
        "ES3" to "LIGA2B",
        "E3G1" to "LIGA2B",
        "E3G2" to "LIGA2B2",
        "GB1" to "PRML",
        "IT1" to "SERIA",
        "FR1" to "LIG1",
        "L1" to "BUN1",
        "DE1" to "BUN1",
        "NL1" to "ERED",
        "PO1" to "PRIM",
        "PT1" to "PRIM",
        "BE1" to "BELGA",
        "TR1" to "SUPERL",
        "SC1" to "SCOT",
        "RU1" to "RPL",
        "DK1" to "DSL",
        "PL1" to "EKSTR",
        "A1" to "ABUND",
    )

    fun all(teamCountsByCode: Map<String, Int>): List<SeedCompetition> {
        val leagues = leagueTemplates.map { template ->
            val teamCount = teamCountsByCode[template.code] ?: template.fallbackTeamCount
            val safeTeamCount = teamCount.coerceAtLeast(0)
            SeedCompetition(
                code = template.code,
                name = template.name,
                formatType = "LEAGUE",
                teamCount = safeTeamCount,
                matchdayCount = roundRobinMatchdays(safeTeamCount),
                promotionSlots = defaultPromotionSlots(template.promotionModel, safeTeamCount),
                relegationSlots = defaultRelegationSlots(safeTeamCount),
                europeanSlots = template.europeanSlots,
                playoffSlots = template.playoffSlots,
                tiebreakRule = template.tiebreakRule,
            )
        }
        return leagues + cupDefinitions
    }

    fun leagueCodes(): Set<String> = leagueTemplates.mapTo(linkedSetOf()) { it.code }

    fun isLeague(code: String): Boolean = leagueTemplates.any { it.code == code }

    fun displayName(code: String): String = COMPETITION_NAMES[code] ?: code

    fun countryCode(code: String): String? =
        leagueTemplates.firstOrNull { it.code == code }?.country

    fun competitionTier(code: String): Int = COMPETITION_TIERS[code] ?: 2

    fun roundRobinMatchdays(teamCount: Int): Int {
        if (teamCount < 2) return 0
        return if (teamCount % 2 == 0) (teamCount - 1) * 2 else teamCount * 2
    }

    fun defaultRelegationSlots(teamCount: Int): Int = when {
        teamCount >= 22 -> 4
        teamCount >= 18 -> 3
        teamCount >= 12 -> 2
        teamCount >= 8 -> 1
        else -> 0
    }

    private fun defaultPromotionSlots(model: PromotionModel, teamCount: Int): Int = when (model) {
        PromotionModel.SECOND_TIER -> when {
            teamCount >= 18 -> 3
            teamCount >= 10 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        PromotionModel.THIRD_TIER -> when {
            teamCount >= 20 -> 4
            teamCount >= 12 -> 3
            teamCount >= 6 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        PromotionModel.RFEF1 -> if (teamCount >= 2) 1 else 0
        PromotionModel.RFEF2 -> when {
            teamCount >= 18 -> 3
            teamCount >= 10 -> 2
            teamCount >= 4 -> 1
            else -> 0
        }
        PromotionModel.NONE -> 0
    }
}
