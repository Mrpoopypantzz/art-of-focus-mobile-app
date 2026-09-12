package com.artoffocus.app.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artoffocus.app.data.local.PhotoStore
import com.artoffocus.app.data.repository.ArtworkRepository
import com.artoffocus.app.data.repository.MuseumRepository
import com.artoffocus.app.domain.focus.canDeletePicture
import com.artoffocus.app.domain.focus.picturesInUse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PhotoLibraryUiState(
    val photos: List<String> = emptyList(),
    val loading: Boolean = true,
    val notice: String? = null,
    /**
     * Pictures a painting depends on — locked against deletion, but still selectable.
     *
     * Holds normalised keys, not paths: ask `canDeletePicture(ref, inUse)` rather than
     * testing membership directly, or a path that addresses the same file by a different
     * prefix will slip through.
     */
    val inUse: Set<String> = emptySet(),
)

/**
 * The user's own pictures. Photos are never consumed: the same picture can back any number of
 * paintings, and abandoning a painting leaves it here untouched.
 */
class PhotoLibraryViewModel(
    private val photoStore: PhotoStore,
    private val museumRepository: MuseumRepository,
    private val artworkRepository: ArtworkRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PhotoLibraryUiState())
    val state: StateFlow<PhotoLibraryUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                photos = photoStore.list(),
                inUse = currentlyInUse(),
                loading = false,
            )
        }
    }

    private suspend fun currentlyInUse(): Set<String> = picturesInUse(
        completed = museumRepository.getAll(),
        active = artworkRepository.loadActive(),
    )

    fun import(uri: Uri) {
        viewModelScope.launch {
            val ref = photoStore.import(uri)
            _state.value = _state.value.copy(
                photos = photoStore.list(),
                inUse = currentlyInUse(),
                notice = if (ref == null) "That picture could not be added." else null,
            )
        }
    }

    /**
     * Deletes a picture, unless a painting depends on it. The UI does not offer removal for a
     * locked picture, but the guard lives here too — an invariant this important should not
     * rest on a screen remembering to ask.
     */
    fun remove(ref: String) {
        viewModelScope.launch {
            val inUse = currentlyInUse()
            if (!canDeletePicture(ref, inUse)) {
                _state.value = _state.value.copy(inUse = inUse)
                return@launch
            }
            photoStore.delete(ref)
            _state.value = _state.value.copy(photos = photoStore.list(), inUse = inUse)
        }
    }

    /** Shuffle — one of the saved pictures at random. Null when the library is empty. */
    fun shuffled(): String? = _state.value.photos.randomOrNull()

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    class Factory(
        private val photoStore: PhotoStore,
        private val museumRepository: MuseumRepository,
        private val artworkRepository: ArtworkRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PhotoLibraryViewModel(photoStore, museumRepository, artworkRepository) as T
        }
    }
}
