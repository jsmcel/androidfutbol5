package com.pcfutbol.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.pcfutbol.competition.CompetitionRepository
import com.pcfutbol.core.data.seed.CompetitionDefinitions
import com.pcfutbol.core.data.seed.SeedLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "pcf_prefs")
private val KEY_SEEDED = booleanPreferencesKey("data_seeded")

/**
 * Verifica si la base de datos ya fue inicializada con los datos 2025/26.
 * Si no, ejecuta SeedLoader y configura los calendarios de liga.
 */
@Singleton
class AppInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seedLoader: SeedLoader,
    private val competitionRepository: CompetitionRepository,
) {
    suspend fun ensureSeeded() {
        val prefs = context.dataStore.data.first()
        if (prefs[KEY_SEEDED] == true) return

        seedLoader.load()
        competitionRepository.setupAllLeagues(CompetitionDefinitions.ALL_LEAGUE_CODES)

        context.dataStore.edit { it[KEY_SEEDED] = true }
    }
}
