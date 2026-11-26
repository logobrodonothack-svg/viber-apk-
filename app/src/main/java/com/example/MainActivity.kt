package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.DownloadStatus
import com.example.ui.ClipVaultViewModel
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FetchScreen
import com.example.ui.screens.VaultSettingsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.ClipVaultTheme

object Routes {
    const val FETCH = "fetch"
    const val DOWNLOADS = "downloads"
    const val VAULT = "vault"
    const val PLAYER = "player/{downloadId}"

    fun player(id: Long) = "player/$id"
}

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopTabs = listOf(
    NavTab(Routes.FETCH, "Fetch", Icons.Rounded.Link, Icons.Outlined.Link),
    NavTab(Routes.DOWNLOADS, "Downloads", Icons.Rounded.Download, Icons.Outlined.Download),
    NavTab(Routes.VAULT, "Vault", Icons.Rounded.Settings, Icons.Outlined.Settings)
)

class MainActivity : ComponentActivity() {
    private val clipVaultViewModel: ClipVaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }
        setContent {
            val preferences by clipVaultViewModel.preferences.collectAsStateWithLifecycle()

            ClipVaultTheme(themeMode = preferences.themeMode) {
                ClipVaultApp(viewModel = clipVaultViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                clipVaultViewModel.handleSharedText(sharedText)
            }
        }
    }
}

@Composable
fun ClipVaultApp(viewModel: ClipVaultViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val analyzeResult by viewModel.analyzeResult.collectAsStateWithLifecycle()
    val clipboardSuggestion by viewModel.clipboardSuggestion.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val filteredDownloads by viewModel.filteredDownloads.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()

    val activeDownloadCount = allDownloads.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val isTopLevelRoute = currentRoute in setOf(Routes.FETCH, Routes.DOWNLOADS, Routes.VAULT)

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevelRoute && !useNavRail) {
                    NavigationBar {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        activeDownloadCount = activeDownloadCount
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useNavRail) {
                    NavigationRail {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        activeDownloadCount = activeDownloadCount
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.FETCH,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.FETCH) {
                        FetchScreen(
                            urlInput = urlInput,
                            analyzeResult = analyzeResult,
                            clipboardSuggestion = clipboardSuggestion,
                            bookmarks = bookmarks,
                            onUrlInputChange = viewModel::onUrlInputChange,
                            onAnalyzeClick = viewModel::analyzeCurrentUrl,
                            onPasteClick = viewModel::pasteFromClipboardAndAnalyze,
                            onClearClick = viewModel::clearUrlAndReset,
                            onImportLinksText = viewModel::importLinksFromText,
                            onDownloadCandidate = viewModel::startDownload,
                            onLoadSample = viewModel::loadPublicSample,
                            onBookmarkSource = viewModel::addBookmark,
                            onDeleteBookmark = viewModel::deleteBookmark,
                            onOpenDownloadsTab = { navigateToTab(Routes.DOWNLOADS) },
                            onRefreshClipboard = viewModel::refreshClipboardHint
                        )
                    }

                    composable(Routes.DOWNLOADS) {
                        DownloadsScreen(
                            allDownloadsCount = allDownloads.size,
                            filteredDownloads = filteredDownloads,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            onSearchQueryChange = viewModel::onSearchQueryChange,
                            onFilterSelected = viewModel::onFilterSelected,
                            onResetSearchAndFilters = viewModel::resetSearchAndFilters,
                            onGoToFetch = { navigateToTab(Routes.FETCH) },
                            onPause = viewModel::pauseDownload,
                            onResumeOrRetry = viewModel::resumeOrRetryDownload,
                            onDelete = viewModel::deleteDownload,
                            onRename = viewModel::renameDownload,
                            onPlay = { item -> navController.navigate(Routes.player(item.id)) },
                            onSaveToGallery = viewModel::saveToGallery,
                            onShare = viewModel::shareVideo
                        )
                    }

                    composable(Routes.VAULT) {
                        VaultSettingsScreen(
                            stats = storageStats,
                            preferences = preferences,
                            onThemeModeChange = viewModel::setThemeMode,
                            onWifiOnlyChange = viewModel::setWifiOnly,
                            onAutoClipboardChange = viewModel::setAutoCheckClipboard,
                            onClearCompleted = viewModel::clearCompletedHistory,
                            onClearAll = viewModel::clearAllDownloads
                        )
                    }

                    composable(
                        route = Routes.PLAYER,
                        arguments = listOf(navArgument("downloadId") { type = NavType.LongType })
                    ) { entry ->
                        val downloadId = entry.arguments?.getLong("downloadId") ?: -1L
                        val item by viewModel.observeDownloadById(downloadId)
                            .collectAsStateWithLifecycle()

                        VideoPlayerScreen(
                            item = item,
                            onBack = { navController.popBackStack() },
                            onSaveToGallery = viewModel::saveToGallery,
                            onShare = viewModel::shareVideo,
                            onOpenExternal = viewModel::openInExternalPlayer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: NavTab,
    selected: Boolean,
    activeDownloadCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab.route == Routes.DOWNLOADS && activeDownloadCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(activeDownloadCount.toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}
