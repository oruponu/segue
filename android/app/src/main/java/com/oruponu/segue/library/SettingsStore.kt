package com.oruponu.segue.library

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable data class Settings(val selectedFolder: String? = null)

class SettingsStore(private val dataStore: DataStore<Settings>) {
    val selectedFolder: Flow<String?> =
        dataStore.data.map { it.selectedFolder }.distinctUntilChanged()

    suspend fun setSelectedFolder(folder: String) {
        dataStore.updateData { it.copy(selectedFolder = folder) }
    }
}
