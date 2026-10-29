package app.kickpocket.ui

import app.kickpocket.BrowseViewModel
import app.kickpocket.MainActivity
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material3.Surface

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kickpocket.StreamViewModel
import app.kickpocket.data.RecentChannel
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun PuntApp(viewModel: StreamViewModel, browseModel: BrowseViewModel, pipState: StateFlow<Boolean>) {
    val pip by pipState.collectAsStateWithLifecycle()
    val browse by browseModel.state.collectAsStateWithLifecycle()
    val followed by browseModel.followedChannels.collectAsStateWithLifecycle()
    val search by browseModel.search.collectAsStateWithLifecycle()
    val account by browseModel.account.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val updates by viewModel.updates.state.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    var library by rememberSaveable { mutableStateOf(false) }
    var chatFilters by rememberSaveable { mutableStateOf(false) }
    var liveAlerts by rememberSaveable { mutableStateOf(false) }
    var pendingAlert by remember { mutableStateOf<RecentChannel?>(null) }
    var emotePicker by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = requireNotNull(LocalActivity.current)
    val keyboard = LocalSoftwareKeyboardController.current
    val channel = state.channel
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingAlert
        pendingAlert = null
        if (granted && pending != null) viewModel.toggleAlert(pending)
    }

    LaunchedEffect(channel?.slug, state.recording?.id) {
        if (channel != null) { keyboard?.hide(); browseModel.search("") } else fullscreen = false
    }
    LaunchedEffect(login, settings, emotePicker, library, chatFilters, liveAlerts) {
        (activity as? MainActivity)?.suppressPip(login || settings || emotePicker || library || chatFilters || liveAlerts)
    }
    DisposableEffect(fullscreen, pip) {
        val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen && !pip) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(enabled = !pip && (channel != null || state.loading || browse.category != null)) {
        if (fullscreen) fullscreen = false else if (channel != null || state.loading) viewModel.leave() else browseModel.backToCategories()
    }

    Surface(Modifier.fillMaxSize(), color = AppBackground, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(if (pip) Modifier.fillMaxSize() else Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            if (channel == null) {
                HomeScreen(state, preferences, browse, account, viewModel::open,
                    onSettings = { settings = true }, onLogin = { login = true }, onSignOut = browseModel::signOut,
                    onTab = browseModel::select, onSort = browseModel::sort, onCategory = browseModel::category, onCategoryBack = browseModel::backToCategories,
                    onRefresh = browseModel::refresh, onMore = browseModel::more,
                    followedChannels = followed, onPin = viewModel::toggleFavorite,
                    updateVersion = updates.available?.versionName, search = search, onSearch = browseModel::search,
                    onLibrary = { library = true }, onOpenSaved = viewModel::openSaved)
            } else {
                StreamLayout(viewModel, state, preferences, landscape, fullscreen, pip,
                    onFullscreen = { fullscreen = !fullscreen }, onSettings = { settings = true }, onLogin = { login = true },
                    onPickerVisibility = { emotePicker = it })
            }
        }
    }
    if (login && !pip) LoginDialog(account, browseModel::checkLogin, onDismiss = { login = false })
    if (settings && !pip) SettingsDialog(preferences, viewModel::animatedEmotes, viewModel::fontSize, viewModel::clearRecent,
        onDismiss = { settings = false }, onWifiQuality = viewModel::wifiQuality, onMobileQuality = viewModel::mobileQuality,
        updates = updates, onCheckUpdates = viewModel.updates::check, onDownloadUpdate = viewModel.updates::download,
        onInstallUpdate = { viewModel.updates.install(activity) }, onCancelDownload = viewModel.updates::cancelDownload,
        onChatFilters = { chatFilters = true }, onLiveAlerts = { liveAlerts = true })
    if (library && !pip) LibraryDialog(preferences, viewModel::openSaved, viewModel::removeHistory,
        viewModel::saveRecording, viewModel::removeBookmark, onDismiss = { library = false })
    if (chatFilters && !pip) ChatFilterDialog(preferences, viewModel::chatFilters, onDismiss = { chatFilters = false })
    if (liveAlerts && !pip) LiveAlertsDialog(preferences, channel?.let { RecentChannel(it.slug, it.displayName, it.avatarUrl) },
        onToggle = { selected ->
            if (preferences.liveAlertChannels.any { it.slug == selected.slug } || Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                viewModel.toggleAlert(selected)
            else { pendingAlert = selected; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }, onDismiss = { liveAlerts = false })
}
