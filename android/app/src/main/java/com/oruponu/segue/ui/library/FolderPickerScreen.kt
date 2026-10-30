package com.oruponu.segue.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oruponu.segue.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    onDone: () -> Unit,
    onPermissionLost: () -> Unit,
    viewModel: FolderPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    if (state is FolderListState.PermissionDenied) {
        LaunchedEffect(Unit) { onPermissionLost() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.folder_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            FolderListState.Loading,
            FolderListState.PermissionDenied ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            FolderListState.Empty ->
                CenteredMessage(stringResource(R.string.folders_empty), Modifier.padding(padding))
            FolderListState.Failed ->
                CenteredMessage(
                    stringResource(R.string.folders_failed),
                    Modifier.padding(padding),
                ) {
                    Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
            is FolderListState.Loaded ->
                LazyColumn(contentPadding = padding) {
                    items(current.folders, key = { it }) { folder ->
                        FolderRow(
                            folder = folder,
                            selected = folder == selected,
                            onClick = {
                                viewModel.select(folder)
                                onDone()
                            },
                        )
                    }
                }
        }
    }
}

@Composable
private fun FolderRow(folder: String, selected: Boolean, onClick: () -> Unit) {
    val colors =
        if (selected) ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary)
        else ListItemDefaults.colors()
    ListItem(
        headlineContent = { Text(folder) },
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent =
            if (selected) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        colors = colors,
    )
}

@Composable
private fun CenteredMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        action()
    }
}
