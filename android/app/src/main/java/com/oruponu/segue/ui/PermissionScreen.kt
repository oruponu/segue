package com.oruponu.segue.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oruponu.segue.R

@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val activity = LocalActivity.current
    fun shouldShowRationale() =
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) == true

    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    // Held as state, as a second denial changes only the rationale and must still recompose.
    var showRationale by remember { mutableStateOf(shouldShowRationale()) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            requestedBefore = true
            showRationale = shouldShowRationale()
            if (granted) onGranted()
        }
    val action = permissionAction(requestedBefore, showRationale)

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        when (action) {
            PermissionAction.Request ->
                Button(onClick = { launcher.launch(Manifest.permission.READ_MEDIA_AUDIO) }) {
                    Text(stringResource(R.string.grant_permission))
                }
            PermissionAction.OpenSettings ->
                Button(
                    onClick = {
                        activity?.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", activity.packageName, null),
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.open_settings))
                }
        }
    }
}
