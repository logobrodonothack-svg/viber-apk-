package com.example.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.network.AnalyzeResult
import com.example.data.network.DownloadEngine
import com.example.data.network.MediaExporter
import com.example.data.network.PublicSampleStream
import com.example.data.network.VideoLinkAnalyzer
import com.example.data.network.VideoStreamCandidate
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

enum class DownloadFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    PAUSED_OR_FAILED("Paused / Failed"),
    MP4("MP4")
}

data class StorageStats(
    val totalBytesUsed: Long = 0L,
    val completedCount: Int = 0,
    val activeCount: Int = 0,
    val mp4Count: Int = 0,
    val otherCount: Int = 0
)

class ClipVaultViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val downloadDao = db.downloadDao()
    private val bookmarkDao = db.bookmarkDao()
    private val preferencesRepository = PreferencesRepository(application)
    private val analyzer = VideoLinkAnalyzer()
    private val downloadEngine = DownloadEngine(application, downloadDao)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadDao.observeAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _analyzeResult = MutableStateFlow<AnalyzeResult>(AnalyzeResult.Idle)
    val analyzeResult: StateFlow<AnalyzeResult> = _analyzeResult.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(DownloadFilter.ALL)
    val selectedFilter: StateFlow<DownloadFilter> = _selectedFilter.asStateFlow()

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    val filteredDownloads: StateFlow<List<DownloadEntity>> = combine(
        allDownloads,
        _searchQuery,
        _selectedFilter
    ) { list, query, filter ->
        val q = query.trim().lowercase()
        list.filter { item ->
            val matchesFilter = when (filter) {
                DownloadFilter.ALL -> true
                DownloadFilter.ACTIVE -> item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED
                DownloadFilter.COMPLETED -> item.status == DownloadStatus.COMPLETED
                DownloadFilter.PAUSED_OR_FAILED -> item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED
                DownloadFilter.MP4 -> item.formatLabel.equals("MP4", ignoreCase = true)
            }
            val matchesQuery = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.domain.lowercase().contains(q) ||
                item.formatLabel.lowercase().contains(q) ||
                (item.resolution?.lowercase()?.contains(q) == true)
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storageStats: StateFlow<StorageStats> = allDownloads.combine(
        MutableStateFlow(Unit)
    ) { list, _ ->
        val completed = list.filter { it.status == DownloadStatus.COMPLETED }
        val bytes = completed.sumOf { item ->
            val fileLen = item.localFilePath?.let { path -> File(path).takeIf { it.exists() }?.length() }
            fileLen ?: item.downloadedBytes
        }
        val mp4 = completed.count { it.formatLabel.equals("MP4", ignoreCase = true) }
        StorageStats(
            totalBytesUsed = bytes,
            completedCount = completed.size,
            activeCount = list.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED },
            mp4Count = mp4,
            otherCount = (completed.size - mp4).coerceAtLeast(0)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    fun observeDownloadById(id: Long): StateFlow<DownloadEntity?> =
        downloadDao.observeById(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onUrlInputChange(value: String) {
        _urlInput.value = value
        if (value.isBlank() && _analyzeResult.value !is AnalyzeResult.Idle) {
            _analyzeResult.value = AnalyzeResult.Idle
        }
    }

    fun clearUrlAndReset() {
        _urlInput.value = ""
        _analyzeResult.value = AnalyzeResult.Idle
    }

    fun refreshClipboardHint() {
        if (!preferences.value.autoCheckClipboard) {
            _clipboardSuggestion.value = null
            return
        }
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(getApplication())?.toString()?.trim()
        if (!clipText.isNullOrBlank() &&
            (clipText.startsWith("http://", true) || clipText.startsWith("https://", true)) &&
            clipText != _urlInput.value
        ) {
            _clipboardSuggestion.value = clipText
        } else {
            _clipboardSuggestion.value = null
        }
    }

    fun pasteFromClipboardAndAnalyze() {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(getApplication())?.toString()?.trim()
        if (clipText.isNullOrBlank()) {
            _snackbarMessages.tryEmit("Clipboard is empty")
            return
        }
        _urlInput.value = clipText
        _clipboardSuggestion.value = null
        analyzeUrl(clipText)
    }

    fun analyzeCurrentUrl() {
        analyzeUrl(_urlInput.value)
    }

    fun analyzeUrl(url: String) {
        viewModelScope.launch {
            _urlInput.value = url
            _analyzeResult.value = AnalyzeResult.Loading(url)
            _analyzeResult.value = analyzer.analyze(url)
        }
    }

    fun loadPublicSample(sample: PublicSampleStream) {
        _urlInput.value = sample.url
        analyzeUrl(sample.url)
    }

    fun startDownload(candidate: VideoStreamCandidate, customTitle: String) {
        downloadEngine.enqueueAndStart(
            candidate = candidate,
            customTitle = customTitle,
            wifiOnly = preferences.value.wifiOnly
        )
        _snackbarMessages.tryEmit("Added \"${customTitle.ifBlank { candidate.title }}\" to Downloads")
    }

    fun pauseDownload(id: Long) {
        downloadEngine.pauseDownload(id)
    }

    fun resumeOrRetryDownload(id: Long) {
        downloadEngine.resumeOrRetry(id, preferences.value.wifiOnly)
    }

    fun deleteDownload(id: Long) {
        downloadEngine.deleteDownload(id)
        _snackbarMessages.tryEmit("Download deleted")
    }

    fun renameDownload(id: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            downloadDao.rename(id, trimmed)
            _snackbarMessages.tryEmit("Renamed to \"$trimmed\"")
        }
    }

    fun saveToGallery(item: DownloadEntity) {
        viewModelScope.launch {
            val result = MediaExporter.saveToGallery(getApplication(), item)
            result.onSuccess { msg ->
                downloadDao.markExported(item.id)
                _snackbarMessages.tryEmit(msg)
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Export failed")
            }
        }
    }

    fun shareVideo(item: DownloadEntity) {
        val ok = MediaExporter.shareVideo(getApplication(), item)
        if (!ok) _snackbarMessages.tryEmit("Video file unavailable to share")
    }

    fun openInExternalPlayer(item: DownloadEntity) {
        val ok = MediaExporter.openExternalPlayer(getApplication(), item)
        if (!ok) _snackbarMessages.tryEmit("No external video player found")
    }

    fun addBookmark(title: String, url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return
        val host = runCatching { URI(trimmedUrl).host?.removePrefix("www.") ?: "saved" }.getOrDefault("saved")
        viewModelScope.launch {
            bookmarkDao.insert(
                BookmarkEntity(
                    title = title.trim().ifBlank { host },
                    url = trimmedUrl,
                    domain = host
                )
            )
            _snackbarMessages.tryEmit("Source bookmarked")
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkDao.deleteById(id)
        }
    }

    fun handleSharedText(sharedText: String) {
        val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        val extracted = urlRegex.find(sharedText)?.value?.trim() ?: sharedText.trim()
        if (extracted.isNotBlank()) {
            analyzeUrl(extracted)
        }
    }

    fun importLinksFromText(rawText: String) {
        val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        val urls = urlRegex.findAll(rawText)
            .map { it.value.trim().trimEnd(',', ';', ')') }
            .distinct()
            .take(25)
            .toList()

        if (urls.isEmpty()) {
            _snackbarMessages.tryEmit("No valid http:// or https:// links found to import")
            return
        }

        viewModelScope.launch {
            urls.forEach { link ->
                val host = runCatching { URI(link).host?.removePrefix("www.") ?: "imported" }
                    .getOrDefault("imported")
                val segment = runCatching {
                    URI(link).path?.substringAfterLast('/')?.substringBeforeLast('.')
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: host
                bookmarkDao.insert(
                    BookmarkEntity(
                        title = segment.replace('_', ' ').replace('-', ' '),
                        url = link,
                        domain = host
                    )
                )
            }
            _snackbarMessages.tryEmit("Imported ${urls.size} link${if (urls.size == 1) "" else "s"}")
            analyzeUrl(urls.first())
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: DownloadFilter) {
        _selectedFilter.value = filter
    }

    fun resetSearchAndFilters() {
        _searchQuery.value = ""
        _selectedFilter.value = DownloadFilter.ALL
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setWifiOnly(enabled) }
    }

    fun setAutoCheckClipboard(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoCheckClipboard(enabled) }
    }

    fun clearCompletedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            allDownloads.value
                .filter { it.status == DownloadStatus.COMPLETED }
                .forEach { item ->
                    item.localFilePath?.let { runCatching { File(it).delete() } }
                    item.thumbnailPath?.let { runCatching { File(it).delete() } }
                }
            downloadDao.deleteByStatus(DownloadStatus.COMPLETED)
            _snackbarMessages.tryEmit("Cleared completed downloads")
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            allDownloads.value.forEach { item ->
                downloadEngine.deleteDownload(item.id)
            }
            _snackbarMessages.tryEmit("Vault cleared")
        }
    }
}
