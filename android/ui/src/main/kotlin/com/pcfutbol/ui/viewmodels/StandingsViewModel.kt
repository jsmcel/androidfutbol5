package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.pcfutbol.competition.CompetitionRepository
import com.pcfutbol.core.data.db.StandingWithTeam
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class StandingsViewModel @Inject constructor(
    private val repo: CompetitionRepository,
) : ViewModel() {

    fun standings(comp: String): Flow<List<StandingWithTeam>> = repo.standings(comp)
}
