package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.ui.DownloadFilter
import com.example.ui.components.FormatTagPill
import com.example.ui.components.NoResultsCard
import com.example.ui.components.StatusPill
import com.example.util.Formatters
import java.io.File

@Composable
fun DownloadsScreen(
    allDownloadsCount: Int,
    filteredDownloads: List<DownloadEntity>,
    searchQuery: String,
    selectedFilter: DownloadFilter,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (DownloadFilter) -> Unit,
    onResetSearchAndFilters: () -> Unit,
    onGoToFetch: () -> Unit,
    onPause: (Long) -> Unit,
    onResumeOrRetry: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onPlay: (DownloadEntity) -> Unit,
    onSaveToGallery: (DownloadEntity) -> Unit,
    onShare: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DownloadEntity?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "downloads_header") {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$allDownloadsCount saved or queued item${if (allDownloadsCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("downloads_search_field"),
                        placeholder = {
                            Text(
                                text = "Search title, domain, MP4, 1080p…",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search downloads"
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DownloadFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { onFilterSelected(filter) },
                                label = { Text(filter.label) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("filter_chip_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (allDownloadsCount == 0) {
                item(key = "empty_vault_state") {
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_downloads_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = "Your video vault is empty",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Paste a direct video URL or try an open sample stream in the Fetch tab to download videos for offline playback.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onGoToFetch,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("empty_go_to_fetch_button")
                            ) {
                                Text("Find & Download Video")
                            }
                        }
                    }
                }
            } else if (filteredDownloads.isEmpty()) {
                item(key = "no_results_state") {
                    val activeFilterDesc = buildString {
                        if (searchQuery.isNotBlank()) append("\"$searchQuery\"")
                        if (selectedFilter != DownloadFilter.ALL) {
                            if (isNotEmpty()) append(" in ")
                            append(selectedFilter.label)
                        }
                    }.ifBlank { "the selected filter" }

                    NoResultsCard(
                        title = stringResource(R.string.no_results_title),
                        reason = "No downloads matched $activeFilterDesc.",
                        suggestion = stringResource(R.string.no_results_search_hint),
                        primaryActionLabel = stringResource(R.string.action_clear_filters),
                        onPrimaryAction = onResetSearchAndFilters
                    )
                }
            } else {
                items(filteredDownloads, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = { onPause(item.id) },
                        onResumeOrRetry = { onResumeOrRetry(item.id) },
                        onPlay = { onPlay(item) },
                        onSaveToGallery = { onSaveToGallery(item) },
                        onShare = { onShare(item) },
                        onRenameClick = {
                            renameTarget = item
                            renameDraft = item.title
                        },
                        onDeleteClick = { deleteTarget = item }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Video") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_dialog_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(target.id, renameDraft)
                        renameTarget = null
                    },
                    modifier = Modifier.testTag("rename_confirm_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Download?") },
            text = {
                Text("This will remove \"${target.title}\" and delete the downloaded video file from your vault.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(target.id)
                        deleteTarget = null
                    },
                    modifier = Modifier.testTag("delete_confirm_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResumeOrRetry: () -> Unit,
    onPlay: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = item.status == DownloadStatus.COMPLETED) { onPlay() }
            .testTag("download_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbFile = item.thumbnailPath?.let(::File)?.takeIf { it.exists() }
                    when {
                        thumbFile != null -> {
                            AsyncImage(
                                model = thumbFile,
                                contentDescription = "Video thumbnail for ${item.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        !item.posterUrl.isNullOrBlank() -> {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = "Video poster for ${item.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (item.status == DownloadStatus.COMPLETED) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play video",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (item.durationMs > 0L) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = Formatters.formatDuration(item.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(status = item.status)
                        FormatTagPill(text = item.formatLabel)
                        val res = item.resolution ?: item.qualityLabel
                        if (res.isNotBlank()) {
                            FormatTagPill(
                                text = res,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.domain} • ${Formatters.formatDate(item.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("download_menu_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = {
                                Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            }
                        )
                        if (item.status == DownloadStatus.COMPLETED) {
                            DropdownMenuItem(
                                text = { Text("Save to Gallery") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSaveToGallery()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Share, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
                val progress = if (item.totalBytes > 0L) {
                    (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else null

                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sizeLine = if (item.totalBytes > 0L) {
                        "${Formatters.formatBytes(item.downloadedBytes)} / ${Formatters.formatBytes(item.totalBytes)}"
                    } else {
                        Formatters.formatBytes(item.downloadedBytes)
                    }
                    Text(
                        text = "$sizeLine • ${Formatters.formatSpeed(item.speedBytesPerSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onPause,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("pause_download_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pause")
                    }
                }
            } else if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
                item.errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onResumeOrRetry,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("retry_download_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (item.status == DownloadStatus.PAUSED) "Resume" else "Retry")
                    }
                    OutlinedButton(
                        onClick = onDeleteClick,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Remove")
                    }
                }
            } else if (item.status == DownloadStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append(Formatters.formatBytes(item.totalBytes))
                            if (item.exportedToGallery) append(" • In Gallery")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onSaveToGallery,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("export_gallery_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery")
                        }
                        Button(
                            onClick = onPlay,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("play_download_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play")
                        }
                    }
                }
            }
        }
    }
}
