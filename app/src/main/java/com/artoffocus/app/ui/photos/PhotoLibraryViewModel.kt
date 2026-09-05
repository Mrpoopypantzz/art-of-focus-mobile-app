package com.artoffocus.app.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artoffocus.app.data.local.PhotoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PhotoLibraryUiState(
    val photos: List<String> = emptyList(),
    val loading: Boolean = true,
    val notice: String? = null,
)

/**
 * The user's own pictures. Photos are never consumed: the same picture can back any number of
 * paintings, and abandoning a painting leaves it here untouched.
 */
class PhotoLibraryViewModel(private val photoStore: PhotoStore) : ViewModel() {

    private val _state = MutableStateFlow(PhotoLibraryUiState())
    val state: StateFlow<PhotoLibraryUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(photos = photoStore.list(), loading = false)
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val ref = photoStore.import(uri)
            _state.value = _state.value.copy(
                photos = photoStore.list(),
                notice = if (ref == null) "That picture could not be added." else null,
            )
        }
    }

    fun remove(ref: String) {
        viewModelScope.launch {
            photoStore.delete(ref)
            _state.value = _state.value.copy(photos = photoStore.list())
        }
    }

    /** Shuffle — one of the saved pictures at random. Null when the library is empty. */
    fun shuffled(): String? = _state.value.photos.randomOrNull()

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    class Factory(private val photoStore: PhotoStore) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PhotoLibraryViewModel(photoStore) as T
        }
    }
}
