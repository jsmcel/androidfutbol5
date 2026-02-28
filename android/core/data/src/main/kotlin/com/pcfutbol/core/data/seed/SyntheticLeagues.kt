package com.pcfutbol.core.data.seed

import kotlin.math.floor
import kotlin.random.Random

data class SyntheticTeam(
    val name: String,
    val country: String,
    val competition: String,
    val avgMe: Int,
    val squad: Int,
)

data class SyntheticPlayer(
    val name: String,
    val country: String,
    val position: String,
    val age: Int,
    val marketValueEur: Long,
    val ve: Int,
    val re: Int,
    val ag: Int,
    val ca: Int,
    val remate: Int,
    val regate: Int,
    val pase: Int,
    val tiro: Int,
    val entrada: Int,
    val portero: Int,
)

object SyntheticLeagues {

    // Argentina - Primera Division 2025/26
    val ARGENTINA = listOf(
        SyntheticTeam("River Plate", "ARG", "ARGPD", avgMe = 72, squad = 25),
        SyntheticTeam("Boca Juniors", "ARG", "ARGPD", avgMe = 70, squad = 25),
        SyntheticTeam("Racing Club", "ARG", "ARGPD", avgMe = 63, squad = 23),
        SyntheticTeam("Independiente", "ARG", "ARGPD", avgMe = 61, squad = 23),
        SyntheticTeam("San Lorenzo", "ARG", "ARGPD", avgMe = 60, squad = 23),
        SyntheticTeam("Estudiantes LP", "ARG", "ARGPD", avgMe = 62, squad = 23),
        SyntheticTeam("Velez Sarsfield", "ARG", "ARGPD", avgMe = 60, squad = 22),
        SyntheticTeam("Talleres Cordoba", "ARG", "ARGPD", avgMe = 59, squad = 22),
        SyntheticTeam("Huracan", "ARG", "ARGPD", avgMe = 55, squad = 22),
        SyntheticTeam("Lanus", "ARG", "ARGPD", avgMe = 55, squad = 22),
        SyntheticTeam("Belgrano", "ARG", "ARGPD", avgMe = 54, squad = 21),
        SyntheticTeam("Newell's Old Boys", "ARG", "ARGPD", avgMe = 54, squad = 21),
        SyntheticTeam("Rosario Central", "ARG", "ARGPD", avgMe = 53, squad = 21),
        SyntheticTeam("Godoy Cruz", "ARG", "ARGPD", avgMe = 53, squad = 21),
        SyntheticTeam("Tigre", "ARG", "ARGPD", avgMe = 50, squad = 21),
        SyntheticTeam("Defensa y Justicia", "ARG", "ARGPD", avgMe = 52, squad = 21),
        SyntheticTeam("Arsenal Sarandi", "ARG", "ARGPD", avgMe = 48, squad = 20),
        SyntheticTeam("Sarmiento Junin", "ARG", "ARGPD", avgMe = 47, squad = 20),
        SyntheticTeam("Central Cordoba", "ARG", "ARGPD", avgMe = 47, squad = 20),
        SyntheticTeam("Platense", "ARG", "ARGPD", avgMe = 46, squad = 20),
        SyntheticTeam("Gimnasia LP", "ARG", "ARGPD", avgMe = 50, squad = 21),
        SyntheticTeam("Colon Santa Fe", "ARG", "ARGPD", avgMe = 49, squad = 20),
        SyntheticTeam("Union Santa Fe", "ARG", "ARGPD", avgMe = 49, squad = 20),
        SyntheticTeam("San Martin Tucuman", "ARG", "ARGPD", avgMe = 46, squad = 20),
        SyntheticTeam("Instituto Cordoba", "ARG", "ARGPD", avgMe = 46, squad = 20),
        SyntheticTeam("Atletico Tucuman", "ARG", "ARGPD", avgMe = 51, squad = 21),
        SyntheticTeam("Barracas Central", "ARG", "ARGPD", avgMe = 46, squad = 20),
        SyntheticTeam("Riestra", "ARG", "ARGPD", avgMe = 45, squad = 20),
    )

    // Brasil - Serie A 2025
    val BRASIL = listOf(
        SyntheticTeam("Flamengo", "BRA", "BRASEA", avgMe = 74, squad = 28),
        SyntheticTeam("Palmeiras", "BRA", "BRASEA", avgMe = 73, squad = 28),
        SyntheticTeam("Sao Paulo FC", "BRA", "BRASEA", avgMe = 68, squad = 26),
        SyntheticTeam("Fluminense", "BRA", "BRASEA", avgMe = 67, squad = 25),
        SyntheticTeam("Corinthians", "BRA", "BRASEA", avgMe = 66, squad = 25),
        SyntheticTeam("Atletico Mineiro", "BRA", "BRASEA", avgMe = 67, squad = 25),
        SyntheticTeam("Internacional", "BRA", "BRASEA", avgMe = 65, squad = 25),
        SyntheticTeam("Gremio", "BRA", "BRASEA", avgMe = 64, squad = 24),
        SyntheticTeam("Santos FC", "BRA", "BRASEA", avgMe = 62, squad = 23),
        SyntheticTeam("Botafogo", "BRA", "BRASEA", avgMe = 64, squad = 24),
        SyntheticTeam("Cruzeiro", "BRA", "BRASEA", avgMe = 63, squad = 23),
        SyntheticTeam("Vasco da Gama", "BRA", "BRASEA", avgMe = 61, squad = 23),
        SyntheticTeam("Fortaleza EC", "BRA", "BRASEA", avgMe = 60, squad = 22),
        SyntheticTeam("Athletico Paranaense", "BRA", "BRASEA", avgMe = 60, squad = 22),
        SyntheticTeam("RB Bragantino", "BRA", "BRASEA", avgMe = 59, squad = 22),
        SyntheticTeam("Bahia", "BRA", "BRASEA", avgMe = 57, squad = 22),
        SyntheticTeam("Ceara SC", "BRA", "BRASEA", avgMe = 55, squad = 21),
        SyntheticTeam("Sport Recife", "BRA", "BRASEA", avgMe = 53, squad = 21),
        SyntheticTeam("Juventude", "BRA", "BRASEA", avgMe = 51, squad = 20),
        SyntheticTeam("Criciuma EC", "BRA", "BRASEA", avgMe = 50, squad = 20),
    )

    // Mexico - Liga MX 2025/26
    val MEXICO = listOf(
        SyntheticTeam("Club America", "MEX", "LIGAMX", avgMe = 71, squad = 26),
        SyntheticTeam("Guadalajara", "MEX", "LIGAMX", avgMe = 70, squad = 26),
        SyntheticTeam("Cruz Azul", "MEX", "LIGAMX", avgMe = 67, squad = 25),
        SyntheticTeam("Pumas UNAM", "MEX", "LIGAMX", avgMe = 65, squad = 24),
        SyntheticTeam("Tigres UANL", "MEX", "LIGAMX", avgMe = 69, squad = 26),
        SyntheticTeam("Monterrey", "MEX", "LIGAMX", avgMe = 68, squad = 25),
        SyntheticTeam("Toluca FC", "MEX", "LIGAMX", avgMe = 63, squad = 23),
        SyntheticTeam("Atlas FC", "MEX", "LIGAMX", avgMe = 61, squad = 23),
        SyntheticTeam("Leon FC", "MEX", "LIGAMX", avgMe = 62, squad = 23),
        SyntheticTeam("Santos Laguna", "MEX", "LIGAMX", avgMe = 60, squad = 22),
        SyntheticTeam("Pachuca", "MEX", "LIGAMX", avgMe = 62, squad = 23),
        SyntheticTeam("Tijuana", "MEX", "LIGAMX", avgMe = 57, squad = 22),
        SyntheticTeam("Necaxa", "MEX", "LIGAMX", avgMe = 55, squad = 21),
        SyntheticTeam("Queretaro", "MEX", "LIGAMX", avgMe = 53, squad = 21),
        SyntheticTeam("San Luis FC", "MEX", "LIGAMX", avgMe = 54, squad = 21),
        SyntheticTeam("Mazatlan FC", "MEX", "LIGAMX", avgMe = 52, squad = 20),
        SyntheticTeam("Puebla FC", "MEX", "LIGAMX", avgMe = 56, squad = 22),
        SyntheticTeam("Juarez FC", "MEX", "LIGAMX", avgMe = 50, squad = 20),
    )

    // Arabia Saudita - Saudi Pro League 2025/26
    val SAUDI = listOf(
        SyntheticTeam("Al-Hilal SFC", "SAU", "SPL", avgMe = 80, squad = 30),
        SyntheticTeam("Al-Nassr FC", "SAU", "SPL", avgMe = 78, squad = 29),
        SyntheticTeam("Al-Ittihad Club", "SAU", "SPL", avgMe = 74, squad = 27),
        SyntheticTeam("Al-Ahli SFC", "SAU", "SPL", avgMe = 72, squad = 26),
        SyntheticTeam("Al-Qadsiah", "SAU", "SPL", avgMe = 64, squad = 24),
        SyntheticTeam("Al-Shabab FC", "SAU", "SPL", avgMe = 63, squad = 23),
        SyntheticTeam("Al-Fayha FC", "SAU", "SPL", avgMe = 58, squad = 22),
        SyntheticTeam("Al-Ettifaq FC", "SAU", "SPL", avgMe = 60, squad = 22),
        SyntheticTeam("Al-Taawoun FC", "SAU", "SPL", avgMe = 58, squad = 22),
        SyntheticTeam("Al-Wehda Club", "SAU", "SPL", avgMe = 56, squad = 21),
        SyntheticTeam("Al-Hazm Club", "SAU", "SPL", avgMe = 52, squad = 20),
        SyntheticTeam("Abha Club", "SAU", "SPL", avgMe = 51, squad = 20),
        SyntheticTeam("Al-Fateh SC", "SAU", "SPL", avgMe = 54, squad = 21),
        SyntheticTeam("Damac FC", "SAU", "SPL", avgMe = 53, squad = 21),
        SyntheticTeam("Al-Riyadh SC", "SAU", "SPL", avgMe = 50, squad = 20),
        SyntheticTeam("Al-Okhdood Club", "SAU", "SPL", avgMe = 49, squad = 20),
        SyntheticTeam("Al-Khaleej SC", "SAU", "SPL", avgMe = 51, squad = 20),
        SyntheticTeam("Al-Qadisiyah", "SAU", "SPL", avgMe = 49, squad = 20),
    )

    val RFEF1A = rfef1aTeams()
    val RFEF1B = rfef1bTeams()
    val RFEF2A = rfef2aTeams()
    val RFEF2B = rfef2bTeams()
    val RFEF2C = rfef2cTeams()
    val RFEF2D = rfef2dTeams()

    fun rfef1aTeams(): List<SyntheticTeam> = listOf(
        SyntheticTeam("Racing de Ferrol", "ES", "RFEF1A", avgMe = 54, squad = 18),
        SyntheticTeam("Arenteiro", "ES", "RFEF1A", avgMe = 53, squad = 18),
        SyntheticTeam("Pontevedra", "ES", "RFEF1A", avgMe = 54, squad = 18),
        SyntheticTeam("SD Compostela", "ES", "RFEF1A", avgMe = 52, squad = 18),
        SyntheticTeam("Real Avila", "ES", "RFEF1A", avgMe = 51, squad = 18),
        SyntheticTeam("Zamora CF", "ES", "RFEF1A", avgMe = 51, squad = 18),
        SyntheticTeam("Burgos Promesas", "ES", "RFEF1A", avgMe = 50, squad = 18),
        SyntheticTeam("Numancia", "ES", "RFEF1A", avgMe = 53, squad = 18),
        SyntheticTeam("Calahorra", "ES", "RFEF1A", avgMe = 50, squad = 18),
        SyntheticTeam("Cayon", "ES", "RFEF1A", avgMe = 49, squad = 18),
        SyntheticTeam("Laredo", "ES", "RFEF1A", avgMe = 49, squad = 18),
        SyntheticTeam("Rayo Majadahonda", "ES", "RFEF1A", avgMe = 54, squad = 18),
        SyntheticTeam("Alcorcon B", "ES", "RFEF1A", avgMe = 52, squad = 18),
        SyntheticTeam("Getafe B", "ES", "RFEF1A", avgMe = 52, squad = 18),
        SyntheticTeam("Real Madrid Castilla B", "ES", "RFEF1A", avgMe = 54, squad = 18),
        SyntheticTeam("Atletico de Madrid B", "ES", "RFEF1A", avgMe = 54, squad = 18),
        SyntheticTeam("Villarreal B", "ES", "RFEF1A", avgMe = 53, squad = 18),
        SyntheticTeam("Levante B", "ES", "RFEF1A", avgMe = 52, squad = 18),
        SyntheticTeam("Deportivo Aragones", "ES", "RFEF1A", avgMe = 50, squad = 18),
        SyntheticTeam("Jacetano", "ES", "RFEF1A", avgMe = 49, squad = 18),
    )

    fun rfef1bTeams(): List<SyntheticTeam> = listOf(
        SyntheticTeam("Castellon B", "ES", "RFEF1B", avgMe = 53, squad = 18),
        SyntheticTeam("Hercules", "ES", "RFEF1B", avgMe = 54, squad = 18),
        SyntheticTeam("Intercity", "ES", "RFEF1B", avgMe = 53, squad = 18),
        SyntheticTeam("UCAM Murcia", "ES", "RFEF1B", avgMe = 52, squad = 18),
        SyntheticTeam("Yeclano", "ES", "RFEF1B", avgMe = 50, squad = 18),
        SyntheticTeam("Lorca Deportiva", "ES", "RFEF1B", avgMe = 50, squad = 18),
        SyntheticTeam("Melilla", "ES", "RFEF1B", avgMe = 51, squad = 18),
        SyntheticTeam("Ceuta FC", "ES", "RFEF1B", avgMe = 52, squad = 18),
        SyntheticTeam("Algeciras CF", "ES", "RFEF1B", avgMe = 51, squad = 18),
        SyntheticTeam("Antequera CF", "ES", "RFEF1B", avgMe = 50, squad = 18),
        SyntheticTeam("Betis Deportivo", "ES", "RFEF1B", avgMe = 54, squad = 18),
        SyntheticTeam("Sevilla Atletico", "ES", "RFEF1B", avgMe = 54, squad = 18),
        SyntheticTeam("Malaga B", "ES", "RFEF1B", avgMe = 52, squad = 18),
        SyntheticTeam("Granada B", "ES", "RFEF1B", avgMe = 52, squad = 18),
        SyntheticTeam("Recreativo de Huelva", "ES", "RFEF1B", avgMe = 52, squad = 18),
        SyntheticTeam("Linense", "ES", "RFEF1B", avgMe = 50, squad = 18),
        SyntheticTeam("Marbella FC", "ES", "RFEF1B", avgMe = 51, squad = 18),
        SyntheticTeam("Atletico Sanluqueno", "ES", "RFEF1B", avgMe = 50, squad = 18),
        SyntheticTeam("Talavera de la Reina", "ES", "RFEF1B", avgMe = 50, squad = 18),
        SyntheticTeam("Cordoba B", "ES", "RFEF1B", avgMe = 49, squad = 18),
    )

    fun rfef2aTeams(): List<SyntheticTeam> = listOf(
        SyntheticTeam("Compostela B", "ES", "RFEF2A", avgMe = 47, squad = 18),
        SyntheticTeam("Ferrol B", "ES", "RFEF2A", avgMe = 46, squad = 18),
        SyntheticTeam("Lugo B", "ES", "RFEF2A", avgMe = 46, squad = 18),
        SyntheticTeam("Viveiro", "ES", "RFEF2A", avgMe = 45, squad = 18),
        SyntheticTeam("Bergantinos", "ES", "RFEF2A", avgMe = 46, squad = 18),
        SyntheticTeam("Somozas", "ES", "RFEF2A", avgMe = 45, squad = 18),
        SyntheticTeam("Arenteiro B", "ES", "RFEF2A", avgMe = 45, squad = 18),
        SyntheticTeam("Fabril", "ES", "RFEF2A", avgMe = 47, squad = 18),
        SyntheticTeam("Deportivo B", "ES", "RFEF2A", avgMe = 47, squad = 18),
        SyntheticTeam("Ourense CF", "ES", "RFEF2A", avgMe = 46, squad = 18),
        SyntheticTeam("Pontevedra B", "ES", "RFEF2A", avgMe = 45, squad = 18),
        SyntheticTeam("Arousa", "ES", "RFEF2A", avgMe = 44, squad = 18),
        SyntheticTeam("Lalin", "ES", "RFEF2A", avgMe = 44, squad = 18),
        SyntheticTeam("Racing Vilalbes", "ES", "RFEF2A", avgMe = 45, squad = 18),
        SyntheticTeam("Valladares", "ES", "RFEF2A", avgMe = 43, squad = 18),
        SyntheticTeam("Coruxo B", "ES", "RFEF2A", avgMe = 44, squad = 18),
        SyntheticTeam("Astorga", "ES", "RFEF2A", avgMe = 45, squad = 18),
        SyntheticTeam("Eibar B", "ES", "RFEF2A", avgMe = 48, squad = 18),
    )

    fun rfef2bTeams(): List<SyntheticTeam> = listOf(
        SyntheticTeam("Bilbao Athletic B", "ES", "RFEF2B", avgMe = 47, squad = 18),
        SyntheticTeam("Baskonia", "ES", "RFEF2B", avgMe = 46, squad = 18),
        SyntheticTeam("Amurrio", "ES", "RFEF2B", avgMe = 45, squad = 18),
        SyntheticTeam("SD Lagunak", "ES", "RFEF2B", avgMe = 45, squad = 18),
        SyntheticTeam("Athletic Club Femenino B", "ES", "RFEF2B", avgMe = 44, squad = 18),
        SyntheticTeam("Tolosa CF", "ES", "RFEF2B", avgMe = 44, squad = 18),
        SyntheticTeam("Izarra", "ES", "RFEF2B", avgMe = 46, squad = 18),
        SyntheticTeam("Osasuna B", "ES", "RFEF2B", avgMe = 48, squad = 18),
        SyntheticTeam("Espanol B Promesas", "ES", "RFEF2B", avgMe = 46, squad = 18),
        SyntheticTeam("Nastic B", "ES", "RFEF2B", avgMe = 45, squad = 18),
        SyntheticTeam("Lleida Esportiu", "ES", "RFEF2B", avgMe = 46, squad = 18),
        SyntheticTeam("Barcelona B", "ES", "RFEF2B", avgMe = 48, squad = 18),
        SyntheticTeam("Girona B", "ES", "RFEF2B", avgMe = 47, squad = 18),
        SyntheticTeam("Figueres", "ES", "RFEF2B", avgMe = 44, squad = 18),
        SyntheticTeam("Peralada", "ES", "RFEF2B", avgMe = 44, squad = 18),
        SyntheticTeam("Badalona", "ES", "RFEF2B", avgMe = 46, squad = 18),
        SyntheticTeam("Prat", "ES", "RFEF2B", avgMe = 44, squad = 18),
        SyntheticTeam("Vilafranca", "ES", "RFEF2B", avgMe = 43, squad = 18),
    )

    fun rfef2cTeams(): List<SyntheticTeam> = listOf(
        SyntheticTeam("Real Madrid C", "ES", "RFEF2C", avgMe = 48, squad = 18),
        SyntheticTeam("Atletico B", "ES", "RFEF2C", avgMe = 47, squad = 18),
        SyntheticTeam("Getafe C", "ES", "RFEF2C", avgMe = 46, squad = 18),
        SyntheticTeam("Leganes B", "ES", "RFEF2C", avgMe = 46, squad = 18),
        SyntheticTeam("Rayo B", "ES", "RFEF2C", avgMe = 46, squad = 18),
        SyntheticTeam("Alcala", "ES", "RFEF2C", avgMe = 45, squad = 18),
        SyntheticTeam("Mostoles", "ES", "RFEF2C", avgMe = 45, squad = 18),
        SyntheticTeam("Navalcarnero", "ES", "RFEF2C", avgMe = 46, squad = 18),
        SyntheticTeam("Majadahonda", "ES", "RFEF2C", avgMe = 46, squad = 18),
        SyntheticTeam("Parla Escuela", "ES", "RFEF2C", avgMe = 44, squad = 18),
        SyntheticTeam("Toledo", "ES", "RFEF2C", avgMe = 47, squad = 18),
        SyntheticTeam("Talavera B", "ES", "RFEF2C", avgMe = 44, squad = 18),
        SyntheticTeam("Torrijos", "ES", "RFEF2C", avgMe = 44, squad = 18),
        SyntheticTeam("Illescas", "ES", "RFEF2C", avgMe = 45, squad = 18),
        SyntheticTeam("Pozuelo", "ES", "RFEF2C", avgMe = 43, squad = 18),
        SyntheticTeam("Alcobendas", "ES", "RFEF2C", avgMe = 43, squad = 18),
        SyntheticTeam("San Fernando", "ES", "RFEF2C", avgMe = 45, squad = 18),
        SyntheticTeam("Arganda", "ES", "RFEF2C", avgMe = 43, squad = 18),
    )

    fun rfef2dTeams(): List<SyntheticTeam> = listOf(
        SyntheticTeam("Betis C", "ES", "RFEF2D", avgMe = 47, squad = 18),
        SyntheticTeam("Sevilla C", "ES", "RFEF2D", avgMe = 47, squad = 18),
        SyntheticTeam("Malaga C", "ES", "RFEF2D", avgMe = 46, squad = 18),
        SyntheticTeam("Granada C", "ES", "RFEF2D", avgMe = 46, squad = 18),
        SyntheticTeam("Almeria B", "ES", "RFEF2D", avgMe = 47, squad = 18),
        SyntheticTeam("Cadiz B", "ES", "RFEF2D", avgMe = 46, squad = 18),
        SyntheticTeam("Jerez Industrial", "ES", "RFEF2D", avgMe = 45, squad = 18),
        SyntheticTeam("Sanlucar de Barrameda", "ES", "RFEF2D", avgMe = 44, squad = 18),
        SyntheticTeam("Jaen", "ES", "RFEF2D", avgMe = 45, squad = 18),
        SyntheticTeam("Linares", "ES", "RFEF2D", avgMe = 45, squad = 18),
        SyntheticTeam("Motril", "ES", "RFEF2D", avgMe = 44, squad = 18),
        SyntheticTeam("Marbella B", "ES", "RFEF2D", avgMe = 45, squad = 18),
        SyntheticTeam("Ecija Balompie", "ES", "RFEF2D", avgMe = 43, squad = 18),
        SyntheticTeam("Cabecense", "ES", "RFEF2D", avgMe = 43, squad = 18),
        SyntheticTeam("El Palo", "ES", "RFEF2D", avgMe = 44, squad = 18),
        SyntheticTeam("Cadiz Femenino B", "ES", "RFEF2D", avgMe = 43, squad = 18),
        SyntheticTeam("Antequera B", "ES", "RFEF2D", avgMe = 44, squad = 18),
        SyntheticTeam("Pulpileno", "ES", "RFEF2D", avgMe = 43, squad = 18),
    )

    val allTeams: List<SyntheticTeam> =
        ARGENTINA + BRASIL + MEXICO + SAUDI + RFEF1A + RFEF1B + RFEF2A + RFEF2B + RFEF2C + RFEF2D

    fun competitionTeamCounts(): Map<String, Int> =
        allTeams.groupingBy { it.competition }.eachCount()

    fun generatePlayers(team: SyntheticTeam): List<SyntheticPlayer> {
        val random = Random(team.name.hashCode())
        val slots = buildPositionSlots(team.squad).toMutableList()
        val starPlayers = starTemplates(team.name)
            .take(team.squad)
            .map { star ->
                slots.remove(star.position)
                star.toPlayer(team.country)
            }
        val generated = slots.mapIndexed { index, pos ->
            createRandomPlayer(
                random = random,
                team = team,
                position = pos,
                idx = index,
            )
        }
        return (starPlayers + generated).take(team.squad)
    }

    // ---------------------------------------------------------------------

    private fun buildPositionSlots(squadSize: Int): List<String> {
        val weights = listOf("PO" to 2, "DF" to 6, "MC" to 5, "DC" to 4)
        val totalWeight = weights.sumOf { it.second }.toDouble()

        val baseCounts = mutableMapOf<String, Int>()
        val fractions = mutableListOf<Pair<String, Double>>()
        weights.forEach { (pos, weight) ->
            val raw = squadSize * (weight / totalWeight)
            val floorValue = floor(raw).toInt()
            val minimum = if (pos == "PO") 1 else 0
            val count = floorValue.coerceAtLeast(minimum)
            baseCounts[pos] = count
            fractions += pos to (raw - floor(raw))
        }

        var assigned = baseCounts.values.sum()
        if (assigned < squadSize) {
            val order = fractions.sortedByDescending { it.second }
            var idx = 0
            while (assigned < squadSize) {
                val pos = order[idx % order.size].first
                baseCounts[pos] = (baseCounts[pos] ?: 0) + 1
                assigned++
                idx++
            }
        }
        while (assigned > squadSize) {
            val removable = listOf("DF", "MC", "DC", "PO")
                .firstOrNull { pos -> (baseCounts[pos] ?: 0) > if (pos == "PO") 1 else 0 }
                ?: break
            baseCounts[removable] = (baseCounts[removable] ?: 0) - 1
            assigned--
        }

        return buildList {
            weights.forEach { (pos, _) ->
                repeat(baseCounts[pos] ?: 0) { add(pos) }
            }
        }
    }

    private fun createRandomPlayer(
        random: Random,
        team: SyntheticTeam,
        position: String,
        idx: Int,
    ): SyntheticPlayer {
        val bank = NameBank.byCountry[team.country] ?: NameBank.defaultBank
        val first = bank.firstNames[(idx + random.nextInt(bank.firstNames.size)) % bank.firstNames.size]
        val last = bank.lastNames[(idx * 3 + random.nextInt(bank.lastNames.size)) % bank.lastNames.size]
        val base = clamp(team.avgMe + random.nextInt(-8, 9))
        val age = random.nextInt(18, 35)
        val attrs = attributesFor(base, position, random)
        return SyntheticPlayer(
            name = "$first $last",
            country = team.country,
            position = position,
            age = age,
            marketValueEur = estimateMarketValue(base, position),
            ve = attrs.ve,
            re = attrs.re,
            ag = attrs.ag,
            ca = attrs.ca,
            remate = attrs.remate,
            regate = attrs.regate,
            pase = attrs.pase,
            tiro = attrs.tiro,
            entrada = attrs.entrada,
            portero = attrs.portero,
        )
    }

    private fun estimateMarketValue(base: Int, position: String): Long {
        val posFactor = if (position == "PO") 0.88 else 1.0
        return ((base * base * 18_000) * posFactor).toLong().coerceAtLeast(250_000L)
    }

    private fun attributesFor(base: Int, position: String, random: Random): AttrSet {
        val spread = { min: Int, max: Int -> clamp(base + random.nextInt(min, max + 1)) }
        return when (position) {
            "PO" -> AttrSet(
                ve = spread(-14, -2),
                re = spread(-8, 4),
                ag = spread(-8, 6),
                ca = spread(-2, 6),
                remate = spread(-28, -10),
                regate = spread(-22, -8),
                pase = spread(-12, 4),
                tiro = spread(-26, -10),
                entrada = spread(-18, -6),
                portero = spread(14, 24),
            )
            "DF" -> AttrSet(
                ve = spread(-6, 5),
                re = spread(-3, 8),
                ag = spread(-1, 10),
                ca = spread(-3, 5),
                remate = spread(-16, -5),
                regate = spread(-10, 2),
                pase = spread(-8, 4),
                tiro = spread(-14, -4),
                entrada = spread(8, 18),
                portero = spread(-35, -20),
            )
            "DC" -> AttrSet(
                ve = spread(2, 10),
                re = spread(-3, 6),
                ag = spread(-8, 5),
                ca = spread(1, 10),
                remate = spread(10, 20),
                regate = spread(4, 14),
                pase = spread(-4, 8),
                tiro = spread(8, 18),
                entrada = spread(-18, -6),
                portero = spread(-35, -20),
            )
            else -> AttrSet(
                ve = spread(-2, 8),
                re = spread(0, 10),
                ag = spread(-6, 6),
                ca = spread(-1, 8),
                remate = spread(-2, 10),
                regate = spread(2, 12),
                pase = spread(4, 14),
                tiro = spread(-2, 10),
                entrada = spread(-4, 8),
                portero = spread(-35, -20),
            )
        }
    }

    private fun starTemplates(teamName: String): List<StarTemplate> = when (teamName) {
        "Al-Hilal SFC" -> listOf(
            StarTemplate("Neymar Jr", "DC", 80, 74, 52, 88, 91, 92, 86, 88, 42, 6, 33_000_000),
            StarTemplate("Ruben Neves", "MC", 68, 84, 70, 86, 74, 79, 90, 82, 74, 5, 31_000_000),
            StarTemplate("Kalidou Koulibaly", "DF", 66, 80, 83, 82, 52, 58, 64, 54, 89, 8, 24_000_000),
            StarTemplate("Sergej Milinkovic-Savic", "MC", 70, 82, 76, 87, 79, 81, 88, 82, 72, 5, 34_000_000),
        )
        "Al-Nassr FC" -> listOf(
            StarTemplate("Cristiano Ronaldo", "DC", 70, 75, 55, 90, 95, 85, 82, 95, 40, 5, 35_000_000),
            StarTemplate("Sadio Mane", "DC", 86, 82, 64, 84, 87, 86, 78, 86, 52, 5, 29_000_000),
            StarTemplate("Marcelo Brozovic", "MC", 70, 84, 72, 84, 72, 78, 87, 79, 76, 5, 24_000_000),
        )
        else -> emptyList()
    }

    private fun clamp(value: Int): Int = value.coerceIn(1, 99)
}

private data class AttrSet(
    val ve: Int,
    val re: Int,
    val ag: Int,
    val ca: Int,
    val remate: Int,
    val regate: Int,
    val pase: Int,
    val tiro: Int,
    val entrada: Int,
    val portero: Int,
)

private data class StarTemplate(
    val name: String,
    val position: String,
    val ve: Int,
    val re: Int,
    val ag: Int,
    val ca: Int,
    val remate: Int,
    val regate: Int,
    val pase: Int,
    val tiro: Int,
    val entrada: Int,
    val portero: Int,
    val marketValueEur: Long,
) {
    fun toPlayer(country: String): SyntheticPlayer =
        SyntheticPlayer(
            name = name,
            country = country,
            position = position,
            age = 26,
            marketValueEur = marketValueEur,
            ve = ve,
            re = re,
            ag = ag,
            ca = ca,
            remate = remate,
            regate = regate,
            pase = pase,
            tiro = tiro,
            entrada = entrada,
            portero = portero,
        )
}

private data class NameBank(
    val firstNames: List<String>,
    val lastNames: List<String>,
) {
    companion object {
        val byCountry: Map<String, NameBank> = mapOf(
            "ARG" to NameBank(
                firstNames = listOf("Juan", "Mateo", "Lautaro", "Santiago", "Nicolas", "Franco", "Agustin", "Bruno"),
                lastNames = listOf("Garcia", "Rodriguez", "Lopez", "Fernandez", "Sosa", "Almada", "Diaz", "Suarez"),
            ),
            "BRA" to NameBank(
                firstNames = listOf("Joao", "Lucas", "Pedro", "Gabriel", "Rafael", "Thiago", "Bruno", "Marcos"),
                lastNames = listOf("Silva", "Santos", "Oliveira", "Pereira", "Costa", "Souza", "Rocha", "Lima"),
            ),
            "MEX" to NameBank(
                firstNames = listOf("Carlos", "Luis", "Javier", "Miguel", "Diego", "Jose", "Andres", "Eduardo"),
                lastNames = listOf("Hernandez", "Gonzalez", "Martinez", "Flores", "Lopez", "Ramirez", "Torres", "Vega"),
            ),
            "SAU" to NameBank(
                firstNames = listOf("Salem", "Firas", "Omar", "Nawaf", "Khalid", "Abdullah", "Saud", "Hassan"),
                lastNames = listOf("Al-Dawsari", "Al-Shahrani", "Al-Faraj", "Al-Ghamdi", "Al-Otaibi", "Al-Amri", "Al-Harbi", "Al-Qahtani"),
            ),
            "ES" to NameBank(
                firstNames = listOf("Carlos", "Javier", "Alvaro", "Sergio", "Diego", "Pablo", "Luis", "Ivan"),
                lastNames = listOf("Garcia", "Perez", "Lopez", "Sanchez", "Martinez", "Ruiz", "Torres", "Navarro"),
            ),
        )

        val defaultBank = NameBank(
            firstNames = listOf("Alex", "Chris", "Sam", "Leo"),
            lastNames = listOf("Lopez", "Silva", "Smith", "Costa"),
        )
    }
}
