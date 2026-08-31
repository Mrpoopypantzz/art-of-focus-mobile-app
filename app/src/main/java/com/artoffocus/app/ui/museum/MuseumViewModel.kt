package com.artoffocus.app.ui.museum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artoffocus.app.data.repository.MuseumRepository
import com.artoffocus.app.domain.museum.CompletedPainting
import com.artoffocus.app.domain.museum.MonthGroup
import com.artoffocus.app.domain.museum.byMostRecent
import com.artoffocus.app.domain.museum.groupByMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class MuseumView { WALL, TIMELINE }

data class MuseumUiState(
    val paintings: List<CompletedPainting> = emptyList(),
    val view: MuseumView = MuseumView.WALL,
) {
    val wallOrder: List<CompletedPainting> get() = byMostRecent(paintings)
    val timeline: List<MonthGroup> get() = groupByMonth(paintings)
}

/** F9.1/F9.2 — Wall and Timeline are two views over the same permanent, append-only record (I12). */
class MuseumViewModel(museumRepository: MuseumRepository) : ViewModel() {

    private val _state = MutableStateFlow(MuseumUiState())
    val state: StateFlow<MuseumUiState> = _state

    init {
        viewModelScope.launch {
            museumRepository.observeAll().collect { paintings ->
                _state.value = _state.value.copy(paintings = paintings)
            }
        }
    }

    fun selectView(view: MuseumView) {
        _state.value = _state.value.copy(view = view)
    }

    class Factory(private val museumRepository: MuseumRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MuseumViewModel(museumRepository) as T
        }
    }
}
