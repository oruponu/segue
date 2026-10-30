package com.oruponu.segue.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oruponu.segue.data.ApplicationScope
import com.oruponu.segue.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "FolderPickerViewModel"

@HiltViewModel
class FolderPickerViewModel
@Inject
constructor(
    private val repository: LibraryRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow<FolderListState>(FolderListState.Loading)
    val state: StateFlow<FolderListState> = _state.asStateFlow()

    val selected: StateFlow<String?> =
        repository.selectedFolder.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null,
        )

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    fun select(folder: String) {
        // The picker closes at once, so the save and the sync must outlive this ViewModel.
        appScope.launch {
            try {
                repository.selectFolder(folder)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Selecting the folder $folder failed", e)
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.value = FolderListState.Loading
        loadJob = viewModelScope.launch { _state.value = loadFolderList(repository::folders) }
    }
}
