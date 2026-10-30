package com.oruponu.segue.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oruponu.segue.R
import com.oruponu.segue.library.Album
import com.oruponu.segue.library.SyncState
import com.oruponu.segue.library.UNKNOWN_ALBUM
import com.oruponu.segue.ui.common.TrackArtwork
import com.oruponu.segue.ui.common.durationMs
import com.oruponu.segue.ui.common.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    state: LibraryUiState,
    onAlbumClick: (Album) -> Unit,
    onPickFolder: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = onPickFolder) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder_open),
                            contentDescription = stringResource(R.string.choose_folder),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state.empty()) {
            LibraryEmpty.NoFolder ->
                EmptyMessage(
                    R.string.no_folder,
                    R.string.choose_folder,
                    onPickFolder,
                    Modifier.padding(padding),
                )
            LibraryEmpty.EmptyFolder ->
                EmptyMessage(
                    R.string.empty_folder,
                    R.string.reselect_folder,
                    onPickFolder,
                    Modifier.padding(padding),
                )
            null ->
                LazyColumn(contentPadding = padding) {
                    val sync = state.sync
                    if (sync is SyncState.Syncing) item { SyncProgress(sync) }
                    items(state.albums, key = { it.key }) { album ->
                        AlbumRow(
                            album,
                            enabled = !state.isSyncing,
                            onClick = { onAlbumClick(album) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun SyncProgress(sync: SyncState.Syncing) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (sync.total > 0) {
            LinearProgressIndicator(
                progress = { sync.done.toFloat() / sync.total },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text =
                if (sync.total > 0)
                    stringResource(R.string.scanning_progress, sync.done, sync.total)
                else stringResource(R.string.scanning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlbumRow(album: Album, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(albumTitle(album.title), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        supportingContent = {
            TrailingRow(
                artistName(album.artist),
                stringResource(
                    R.string.album_summary,
                    album.tracks.size,
                    formatDuration(album.durationMs()),
                ),
            )
        },
        leadingContent = { TrackArtwork(album.tracks.firstOrNull(), 48.dp, 4.dp) },
    )
}

@Composable
private fun EmptyMessage(
    @StringRes message: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAction) { Text(stringResource(action)) }
    }
}

/** A one-line [start] that takes the free width, followed by a smaller [end]. */
@Composable
internal fun TrailingRow(start: String, end: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = start,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = end,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun albumTitle(title: String): String =
    if (title == UNKNOWN_ALBUM) stringResource(R.string.unknown_album) else title

@Composable
internal fun artistName(artist: String?): String = artist ?: stringResource(R.string.unknown_artist)
