package com.oruponu.segue.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Albums : NavKey

@Serializable data class AlbumDetail(val albumKey: String) : NavKey

@Serializable data object FolderPicker : NavKey

@Serializable data object Permission : NavKey
