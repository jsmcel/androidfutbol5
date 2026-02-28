package com.pcfutbol.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import com.pcfutbol.ui.screens.*

object Routes {
    const val MAIN_MENU         = "/"
    const val LIGA_SELECT       = "/liga"
    const val SELECCION         = "/seleccion"
    const val PROMANAGER_OFFERS = "/promanager/offers"
    const val TEAM_SQUAD        = "/team"
    const val CONTRACTS         = "/contracts"
    const val MANAGER_DEPTH     = "/manager-depth"
    const val LINEUP            = "/lineup"
    const val TACTIC            = "/tactic"
    const val STATS             = "/stats"
    const val STANDINGS         = "/standings/{comp}"
    const val MATCHDAY          = "/matchday/{round}"
    const val MATCH_RESULT      = "/match/{id}"
    const val TRANSFER_MARKET   = "/market"
    const val NEWS              = "/news"
    const val END_OF_SEASON     = "/end-of-season"
    const val COPA              = "/copa"
    const val CHAMPIONS         = "/champions"
    const val REAL_FOOTBALL     = "/realfootball"

    fun standings(comp: String) = "/standings/$comp"
    fun matchday(round: Int) = "/matchday/$round"
    fun matchResult(id: Int) = "/match/$id"
}

@Composable
fun PcfNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN_MENU,
    ) {
        composable(Routes.MAIN_MENU) {
            MainMenuScreen(
                onLigaManager = { navController.navigate(Routes.LIGA_SELECT) },
                onProManager = { navController.navigate(Routes.PROMANAGER_OFFERS) },
            )
        }

        composable(Routes.LIGA_SELECT) {
            LigaSelectScreen(
                onNavigateUp = { navController.navigateUp() },
                onStandings = { comp -> navController.navigate(Routes.standings(comp)) },
                onMatchday = { round -> navController.navigate(Routes.matchday(round)) },
                onTeam = { navController.navigate(Routes.TEAM_SQUAD) },
                onManagerDepth = { navController.navigate(Routes.MANAGER_DEPTH) },
                onNews = { navController.navigate(Routes.NEWS) },
                onMarket = { navController.navigate(Routes.TRANSFER_MARKET) },
                onCopa = { navController.navigate(Routes.COPA) },
                onStats = { navController.navigate(Routes.STATS) },
                onChampions = { navController.navigate(Routes.CHAMPIONS) },
                onRealFootball = { navController.navigate(Routes.REAL_FOOTBALL) },
                onNationalTeam = { navController.navigate(Routes.SELECCION) },
            )
        }

        composable(Routes.PROMANAGER_OFFERS) {
            ProManagerOffersScreen(
                onNavigateUp = { navController.navigateUp() },
                onOfferAccepted = { navController.navigate(Routes.LIGA_SELECT) },
            )
        }

        composable(Routes.TEAM_SQUAD) {
            TeamSquadScreen(
                onNavigateUp = { navController.navigateUp() },
                onLineup = { navController.navigate(Routes.LINEUP) },
                onTactic = { navController.navigate(Routes.TACTIC) },
                onContracts = { navController.navigate(Routes.CONTRACTS) },
            )
        }

        composable(Routes.CONTRACTS) {
            ContractsScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.MANAGER_DEPTH) {
            ManagerDepthScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.LINEUP) {
            LineupScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.TACTIC) {
            TacticScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.STATS) {
            StatsScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.STANDINGS) { backStack ->
            val comp = backStack.arguments?.getString("comp")
                ?: CompetitionDefinitions.DEFAULT_MANAGER_LEAGUE
            StandingsScreen(
                competitionCode = comp,
                onNavigateUp = { navController.navigateUp() },
            )
        }

        composable(Routes.MATCHDAY) { backStack ->
            val round = backStack.arguments?.getString("round")?.toIntOrNull() ?: 1
            CoachMatchdayScreen(
                matchday = round,
                onNavigateUp = { navController.navigateUp() },
                onMatchResult = { id -> navController.navigate(Routes.matchResult(id)) },
                onSeasonComplete = { navController.navigate(Routes.END_OF_SEASON) },
            )
        }

        composable(Routes.MATCH_RESULT) { backStack ->
            val id = backStack.arguments?.getString("id")?.toIntOrNull() ?: -1
            MatchResultScreen(
                fixtureId = id,
                onNavigateUp = { navController.navigateUp() },
            )
        }

        composable(Routes.TRANSFER_MARKET) {
            TransferMarketScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.NEWS) {
            NewsScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.REAL_FOOTBALL) {
            RealFootballScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.SELECCION) {
            NationalTeamScreen(
                onNavigateUp = { navController.navigateUp() },
                onTactic = { navController.navigate(Routes.TACTIC) },
            )
        }

        composable(Routes.END_OF_SEASON) {
            EndOfSeasonScreen(
                onNextSeason = { goToOffers ->
                    val target = if (goToOffers) Routes.PROMANAGER_OFFERS else Routes.LIGA_SELECT
                    navController.navigate(target) {
                        popUpTo(Routes.MAIN_MENU)
                    }
                },
            )
        }

        composable(Routes.COPA) {
            CopaScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(Routes.CHAMPIONS) {
            ChampionsScreen(onNavigateUp = { navController.navigateUp() })
        }
    }
}
