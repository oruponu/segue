package com.oruponu.segue.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

enum class PermissionAction {
    Request,
    OpenSettings,
}

/** After a denial without a rationale, the system no longer shows the dialog. */
fun permissionAction(requestedBefore: Boolean, shouldShowRationale: Boolean): PermissionAction =
    if (requestedBefore && !shouldShowRationale) PermissionAction.OpenSettings
    else PermissionAction.Request

@Stable
class AudioPermissionState(private val context: Context) {
    var granted by mutableStateOf(check())
        private set

    fun refresh() {
        granted = check()
    }

    private fun check() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

/** Checks again on every resume, which covers a grant made in the system settings. */
@Composable
fun rememberAudioPermissionState(): AudioPermissionState {
    val context = LocalContext.current
    val state = remember(context) { AudioPermissionState(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refresh() }
    return state
}
