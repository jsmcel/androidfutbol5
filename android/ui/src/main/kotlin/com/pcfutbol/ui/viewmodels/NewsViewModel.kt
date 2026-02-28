package com.pcfutbol.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcfutbol.core.data.db.NewsDao
import com.pcfutbol.core.data.db.NewsEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsUiState(
    val news: List<NewsEntity> = emptyList(),
    val filterCategory: String = NewsViewModel.CATEGORY_ALL,
    val loading: Boolean = true,
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsDao: NewsDao,
) : ViewModel() {

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            newsDao.all().collect { news ->
                _state.update { it.copy(news = news, loading = false) }
            }
        }
    }

    fun setCategory(cat: String) {
        val nextCategory = if (cat in CATEGORIES) cat else CATEGORY_ALL
        _state.update { it.copy(filterCategory = nextCategory) }
    }

    val filteredNews: StateFlow<List<NewsEntity>> = state
        .map { current ->
            if (current.filterCategory == CATEGORY_ALL) {
                current.news
            } else {
                current.news.filter { it.category == current.filterCategory }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        const val CATEGORY_ALL = "ALL"
        const val CATEGORY_RESULT = "RESULT"
        const val CATEGORY_TRANSFER = "TRANSFER"
        const val CATEGORY_INJURY = "INJURY"
        const val CATEGORY_OFFER = "OFFER"
        const val CATEGORY_BOARD = "BOARD"

        val CATEGORIES = listOf(
            CATEGORY_ALL,
            CATEGORY_RESULT,
            CATEGORY_TRANSFER,
            CATEGORY_INJURY,
            CATEGORY_OFFER,
            CATEGORY_BOARD,
        )
    }
}
