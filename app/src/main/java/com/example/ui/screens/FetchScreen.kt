package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.BookmarkEntity
import com.example.data.network.AnalyzeResult
import com.example.data.network.PublicSampleCatalog
import com.example.data.network.PublicSampleStream
import com.example.data.network.ResolutionTier
import com.example.data.network.VideoStreamCandidate
import com.example.ui.components.FormatTagPill
import com.example.ui.components.NoResultsCard
import com.example.ui.components.OneUiHeroHeader
import com.example.util.Formatters

@Composable
fun FetchScreen(
    urlInput: String,
    analyzeResult: AnalyzeResult,
    clipboardSuggestion: String?,
    bookmarks: List<BookmarkEntity>,
    onUrlInputChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onPasteClick: () -> Unit,
    onClearClick: () -> Unit,
    onImportLinksText: (String) -> Unit,
    onDownloadCandidate: (VideoStreamCandidate, String) -> Unit,
    onLoadSample: (PublicSampleStream) -> Unit,
    onBookmarkSource: (String, String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onOpenDownloadsTab: () -> Unit,
    onRefreshClipboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var importTextDraft by remember { mutableStateOf("") }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            if (content.isNotBlank()) {
                importTextDraft = content
                showImportDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        onRefreshClipboard()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero_header") {
                OneUiHeroHeader(
                    title = "ClipVault",
                    subtitle = "Scan direct video links, import playlists, and save in 1080p, 720p, 640p, 480p, 360p, or 144p.",
                    badgeText = "ONE-UI MEDIA ENGINE"
                )
            }

            if (clipboardSuggestion != null) {
                item(key = "clipboard_banner") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onPasteClick() }
                            .testTag("clipboard_suggestion_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Paste detected link",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Link detected on clipboard",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = clipboardSuggestion,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Paste",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item(key = "url_input_card") {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Video Link Scanner",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = { showImportDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("open_import_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import")
                            }
                        }

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = onUrlInputChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("url_input_field"),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.url_hint),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = "URL icon"
                                )
                            },
                            trailingIcon = {
                                if (urlInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = onClearClick,
                                        modifier = Modifier.testTag("clear_url_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "Clear link"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    onAnalyzeClick()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    onPasteClick()
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("paste_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_paste))
                            }
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onAnalyzeClick()
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("analyze_link_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_analyze))
                            }
                        }
                    }
                }
            }

            item(key = "analyze_result_section") {
                AnimatedVisibility(
                    visible = analyzeResult !is AnalyzeResult.Idle,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    when (analyzeResult) {
                        is AnalyzeResult.Idle -> Unit
                        is AnalyzeResult.Loading -> {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("analyzing_card")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Scanning media headers & qualities…",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = analyzeResult.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        is AnalyzeResult.NoResults -> {
                            NoResultsCard(
                                title = analyzeResult.title,
                                reason = analyzeResult.reason,
                                suggestion = analyzeResult.suggestion,
                                primaryActionLabel = stringResource(R.string.action_try_sample),
                                onPrimaryAction = {
                                    onLoadSample(PublicSampleCatalog.samples.first())
                                },
                                secondaryActionLabel = "Import",
                                onSecondaryAction = { showImportDialog = true },
                                tertiaryActionLabel = "Clear",
                                onTertiaryAction = onClearClick
                            )
                        }

                        is AnalyzeResult.Error -> {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("analyze_error_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = "Connection error",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Unable to scan link",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        text = analyzeResult.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = onAnalyzeClick,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Retry")
                                        }
                                        TextButton(
                                            onClick = { onLoadSample(PublicSampleCatalog.samples.first()) },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Try Sample Video")
                                        }
                                    }
                                }
                            }
                        }

                        is AnalyzeResult.Found -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${analyzeResult.candidates.size} Stream${if (analyzeResult.candidates.size > 1) "s" else ""} Ready",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = {
                                            onBookmarkSource(analyzeResult.pageTitle, analyzeResult.sourceUrl)
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("bookmark_source_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.BookmarkAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bookmark")
                                    }
                                }

                                MultiResolutionStreamCard(
                                    candidates = analyzeResult.candidates,
                                    onDownload = { candidate, customName ->
                                        onDownloadCandidate(candidate, customName)
                                        onOpenDownloadsTab()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (bookmarks.isNotEmpty()) {
                item(key = "bookmarks_header") {
                    Text(
                        text = "Saved & Imported Links",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(bookmarks, key = { "bm_${it.id}" }) { bm ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onUrlInputChange(bm.url); onAnalyzeClick() }
                            .testTag("bookmark_card_${bm.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bm.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = bm.domain,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteBookmark(bm.id) },
                                modifier = Modifier.testTag("delete_bookmark_${bm.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove bookmark"
                                )
                            }
                        }
                    }
                }
            }

            item(key = "public_samples_header") {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "Public Test Streams",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Open-source Creative Commons MP4 clips to verify fast downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(PublicSampleCatalog.samples, key = { it.url }) { sample ->
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onLoadSample(sample) }
                        .testTag("sample_stream_${sample.title.lowercase().replace(' ', '_')}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sample.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sample.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FormatTagPill(text = sample.qualityLabel)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Video Links") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste one or more direct video/page URLs below, or load a .txt / .m3u playlist file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            fileImportLauncher.launch(arrayOf("text/*", "audio/x-mpegurl", "application/vnd.apple.mpegurl"))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("import_pick_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Load .txt or .m3u File")
                    }
                    OutlinedTextField(
                        value = importTextDraft,
                        onValueChange = { importTextDraft = it },
                        placeholder = { Text("https://example.com/video-1080p.mp4\nhttps://example.com/clip.webm") },
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_links_textarea")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportLinksText(importTextDraft)
                        importTextDraft = ""
                        showImportDialog = false
                    },
                    enabled = importTextDraft.isNotBlank(),
                    modifier = Modifier.testTag("confirm_import_links_button")
                ) {
                    Text("Import & Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiResolutionStreamCard(
    candidates: List<VideoStreamCandidate>,
    onDownload: (VideoStreamCandidate, String) -> Unit
) {
    var selectedCandidate by remember(candidates) { mutableStateOf(candidates.first()) }
    var titleDraft by remember(candidates) { mutableStateOf(candidates.first().title) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detected_stream_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!selectedCandidate.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = selectedCandidate.posterUrl,
                        contentDescription = "Video poster preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 86.dp, height = 56.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormatTagPill(text = selectedCandidate.formatLabel)
                        FormatTagPill(
                            text = selectedCandidate.qualityLabel,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${selectedCandidate.domain} • ${Formatters.formatBytes(selectedCandidate.contentLengthBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Select Resolution",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResolutionTier.entries.forEach { tier ->
                        val matching = candidates.firstOrNull { it.resolutionTier == tier }
                        val isSelected = selectedCandidate.streamUrl == matching?.streamUrl
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (matching != null) {
                                    selectedCandidate = matching
                                }
                            },
                            enabled = matching != null,
                            label = {
                                Text(
                                    if (matching != null) tier.shortLabel else "${tier.shortLabel} (N/A)"
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resolution_chip_${tier.shortLabel}")
                        )
                    }

                    val untiered = candidates.filter { it.resolutionTier == null }
                    untiered.forEachIndexed { idx, extra ->
                        val isSelected = selectedCandidate.streamUrl == extra.streamUrl
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCandidate = extra },
                            label = {
                                Text(
                                    if (untiered.size > 1) "${extra.qualityLabel} #${idx + 1}" else extra.qualityLabel
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resolution_chip_other_$idx")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = titleDraft,
                onValueChange = { titleDraft = it },
                label = { Text("Save as") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stream_title_input")
            )

            Text(
                text = selectedCandidate.streamUrl,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Button(
                onClick = { onDownload(selectedCandidate, titleDraft) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("download_stream_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download (${selectedCandidate.qualityLabel})")
            }
        }
    }
}
