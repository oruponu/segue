package com.oruponu.segue.ui.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oruponu.segue.R
import com.oruponu.segue.library.Album
import com.oruponu.segue.library.UNKNOWN_ALBUM
import com.oruponu.segue.ui.common.TrackArtwork
import com.oruponu.segue.ui.common.durationMs
import com.oruponu.segue.ui.common.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(state: LibraryUiState) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.library_title)) })
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(state.albums, key = { it.key }) { album ->
                AlbumRow(album)
            }
        }
    }
}

@Composable
private fun AlbumRow(album: Album) {
    ListItem(
        headlineContent = {
            Text(albumTitle(album.title), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
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
