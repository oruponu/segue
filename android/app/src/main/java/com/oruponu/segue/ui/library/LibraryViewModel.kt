package com.oruponu.segue.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oruponu.segue.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "LibraryViewModel"

@HiltViewModel
class LibraryViewModel @Inject constructor(private val repository: LibraryRepository) :
    ViewModel() {
    val state: StateFlow<LibraryUiState> =
        combine(repository.albums, repository.syncState, repository.selectedFolder) {
                albums,
                sync,
                folder ->
                LibraryUiState(albums, sync, folder, loaded = true)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun onPermissionGranted() {
        viewModelScope.launch {
            try {
                repository.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Library sync after the permission grant failed", e)
            }
        }
    }
}
