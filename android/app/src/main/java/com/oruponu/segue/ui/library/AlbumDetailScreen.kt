package com.oruponu.segue.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oruponu.segue.R
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.library.Album
import com.oruponu.segue.ui.common.PlayingIcon
import com.oruponu.segue.ui.common.TrackArtwork
import com.oruponu.segue.ui.common.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: Album,
    currentKey: String?,
    isPlaying: Boolean,
    onTrackClick: (index: Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(albumTitle(album.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            itemsIndexed(album.tracks, key = { _, track -> track.key }) { index, track ->
                TrackRow(
                    track = track,
                    current = track.key == currentKey,
                    isPlaying = isPlaying,
                    onClick = { onTrackClick(index) },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    current: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val colors =
        if (current) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                headlineColor = MaterialTheme.colorScheme.primary,
            )
        } else {
            ListItemDefaults.colors()
        }
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current && isPlaying) PlayingIcon(Modifier.padding(start = 8.dp))
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = {
            TrailingRow(artistName(track.artist), formatDuration(track.durationMs))
        },
        leadingContent = { TrackArtwork(track, 48.dp, 4.dp) },
        colors = colors,
    )
}
