package com.example.trackerfinder

import androidx.compose.animation.core.tween
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import com.example.trackerfinder.ui.HomeScreen
import com.example.trackerfinder.ui.LockScreen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.ui.FavoritesScreen
import com.example.trackerfinder.ui.MapScreen
import com.example.trackerfinder.ui.AppTheme
import com.example.trackerfinder.ui.isAppInDarkTheme
import com.example.trackerfinder.findhub.HubSettings
import com.example.trackerfinder.findhub.HubViewModel
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.foundation.clickable
import com.example.trackerfinder.ui.FinderScreen
import com.example.trackerfinder.ui.MoreScreen
import com.example.trackerfinder.ui.NearbyScreen
import com.example.trackerfinder.ui.RadarPulse
import com.example.trackerfinder.ui.TrackerFinderTheme

class MainActivity : ComponentActivity() {

    private val vm: ScanViewModel by viewModels()
    private val hub: HubViewModel by viewModels()
    /** A tab the app should jump to (e.g. from a Find Hub alert). */
    private var requestedTab by mutableStateOf<Tab?>(null)
    private var hasPermissions by mutableStateOf(false)
    private var locked by mutableStateOf(false)

    private val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasPermissions = checkPermissions()
            if (hasPermissions) vm.start()
        }

    /** Favorite whose alert the user is turning on while we ask for notification permission. */
    private var pendingAlertId: String? = null

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Even if declined, the monitor still runs; Android just won't show its alerts.
            pendingAlertId?.let { vm.setAlert(it, true) }
            pendingAlertId = null
        }

    private fun setAlert(id: String, on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAlertId = id
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        vm.setAlert(id, on)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_FAVORITE_ID)?.let { vm.find(it) }
        intent?.removeExtra(EXTRA_FAVORITE_ID)
        intent?.getStringExtra(EXTRA_HUB_DEVICE_ID)?.let { hub.select(it); vm.closeFinder(); requestedTab = Tab.MAP }
        intent?.removeExtra(EXTRA_HUB_DEVICE_ID)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun checkPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.init(this)
        com.example.trackerfinder.ui.MapTiles.init(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hasPermissions = checkPermissions()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val dark = isAppInDarkTheme()
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(style, style)
            }
            TrackerFinderTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked) {
                        BackHandler { finish() }
                        LockScreen { locked = false }
                    } else if (!hasPermissions) {
                        PermissionScreen { permissionLauncher.launch(requiredPermissions) }
                    } else {
                        AppShell(
                            vm = vm,
                            hub = hub,
                            requestedTab = requestedTab,
                            consumeRequestedTab = { requestedTab = null },
                            openFindHub = ::openFindHub,
                            setAlert = ::setAlert,
                            openBluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        )
                    }
                }
            }
        }
    }

    /** Opens Google's Find Hub app if installed, otherwise the Find Hub website. */
    private fun openFindHub() {
        val app = packageManager.getLaunchIntentForPackage(FIND_HUB_PACKAGE)
        try {
            startActivity(app ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/android/find")))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private val btReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            when (i.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)) {
                android.bluetooth.BluetoothAdapter.STATE_ON -> vm.onBluetoothChanged(true)
                android.bluetooth.BluetoothAdapter.STATE_OFF -> vm.onBluetoothChanged(false)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, btReceiver, android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // With app lock on, hide the app's content from the recent-apps preview and screenshots.
        if (AppLock.isEnabled(this)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        AppLock.onForeground()
        locked = AppLock.needsUnlock(this)
        hasPermissions = checkPermissions()
        if (hasPermissions) {
            vm.start()
            MonitorService.sync(this) // restart the left-behind monitor if Android stopped it
        }
        FavoritesWidget.updateAll(this, force = true)
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(btReceiver) }
        vm.stop()
        if (!isChangingConfigurations) AppLock.onBackground()
    }

    companion object {
        const val FIND_HUB_PACKAGE = "com.google.android.apps.adm"
        const val EXTRA_FAVORITE_ID = "favoriteId"
        const val EXTRA_HUB_DEVICE_ID = "hubDeviceId"
    }
}

enum class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector, val title: String) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "Tracker Finder"),
    NEARBY("Nearby", Icons.Filled.Search, Icons.Outlined.Search, "Nearby"),
    MAP("Map", Icons.Filled.LocationOn, Icons.Outlined.LocationOn, "Find Hub"),
    FAVORITES("Favorites", Icons.Filled.Star, Icons.Outlined.Star, "My trackers"),
    MORE("More", Icons.Filled.Menu, Icons.Outlined.Menu, "More"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    vm: ScanViewModel,
    hub: HubViewModel,
    requestedTab: Tab?,
    consumeRequestedTab: () -> Unit,
    openFindHub: () -> Unit,
    setAlert: (String, Boolean) -> Unit,
    openBluetoothSettings: () -> Unit,
) {
    val track by vm.track.collectAsStateWithLifecycle()
    val finderOpen = track != null
    // Keep the screen on only while hunting for a tracker, not whenever the app is open.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(finderOpen) {
        view.keepScreenOn = finderOpen
        onDispose { view.keepScreenOn = false }
    }
    val error by vm.error.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(Tab.HOME.ordinal) }
    // Scan hard only where it matters (see ScanViewModel.setScanMode); debounce quick tab flips.
    LaunchedEffect(tab, finderOpen) {
        kotlinx.coroutines.delay(400)
        vm.setScanMode(
            when {
                finderOpen || tab == Tab.NEARBY.ordinal -> ScanMode.FAST
                tab == Tab.HOME.ordinal || tab == Tab.FAVORITES.ordinal -> ScanMode.BALANCED
                else -> ScanMode.OFF
            },
        )
    }
    LaunchedEffect(requestedTab) { requestedTab?.let { tab = it.ordinal; consumeRequestedTab() } }
    val hubUi by hub.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(hubUi.message) { hubUi.message?.let { snackbar.showSnackbar(it); hub.messageShown() } }
    var linkFor by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    var hubPage by rememberSaveable { mutableIntStateOf(0) }
    val findNearby: (String) -> Unit = { deviceId ->
        val fav = HubSettings.linkedFavorite(ctx, deviceId)?.let { id -> favorites.firstOrNull { it.id == id } }
        if (fav != null) vm.find(fav.id) else linkFor = deviceId
    }

    AnimatedContent(
        targetState = track != null,
        transitionSpec = { (fadeIn() + slideInVertically { it / 12 }) togetherWith fadeOut() },
        label = "finder",
    ) { finderOpen ->
        val t = track
        if (finderOpen && t != null) {
            BackHandler { vm.closeFinder() }
            FinderScreen(vm, t)
        } else {
            val current = Tab.entries[tab]
            val mapFull = hubUi.account?.connected == true && hubUi.devices.isNotEmpty() && !hubUi.loading
            val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            Scaffold(
                modifier = if (current == Tab.MAP) Modifier else Modifier.nestedScroll(scroll.nestedScrollConnection),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    Column {
                        if (current == Tab.MAP) { if (!mapFull) TopAppBar(title = { Text(current.title) }) }
                        else LargeTopAppBar(title = { Text(current.title) }, scrollBehavior = scroll)
                        error?.let { ErrorBanner(it, openBluetoothSettings) { vm.stop(); vm.start() } }
                    }
                },
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { item ->
                            val selected = item == current
                            NavigationBarItem(
                                selected = selected,
                                onClick = { tab = item.ordinal },
                                label = { Text(item.label) },
                                icon = {
                                    if (item == Tab.FAVORITES && favorites.isNotEmpty()) {
                                        BadgedBox(badge = { Badge { Text("${favorites.size}") } }) {
                                            Icon(if (selected) item.selected else item.unselected, null)
                                        }
                                    } else {
                                        Icon(if (selected) item.selected else item.unselected, null)
                                    }
                                },
                            )
                        }
                    }
                },
            ) { padding ->
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                    label = "tabs",
                ) { shown -> when (shown) {
                    Tab.HOME -> HomeScreen(
                        vm, hub, padding,
                        openTagOnMap = { hub.select(it); hubPage = 0; tab = Tab.MAP.ordinal },
                        openHistory = { hub.select(it); hubPage = 1; tab = Tab.MAP.ordinal },
                        goNearby = { tab = Tab.NEARBY.ordinal },
                        goMap = { tab = Tab.MAP.ordinal },
                        goFavorites = { tab = Tab.FAVORITES.ordinal },
                    )
                    Tab.NEARBY -> NearbyScreen(vm, padding)
                    Tab.MAP -> MapScreen(hub, padding, openFindHub, findNearby, hubPage) { hubPage = it }
                    Tab.FAVORITES -> FavoritesScreen(vm, padding, openFindHub, { tab = Tab.NEARBY.ordinal }, setAlert)
                    Tab.MORE -> MoreScreen(vm, hub, padding, openFindHub, openBluetoothSettings)
                } }
            }
        }
    }

    linkFor?.let { deviceId ->
        AlertDialog(
            onDismissRequest = { linkFor = null },
            title = { Text("Which favorite is this tag?") },
            text = {
                Column {
                    Text(
                        "Link this Find Hub tag to one of your favorites so \"Nearby\" opens the right signal finder. " +
                            "If it isn't a favorite yet, open the Nearby list, find it, and star it first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    favorites.forEach { f ->
                        ListItem(
                            headlineContent = { Text(f.nickname) },
                            supportingContent = { Text(f.kind.label) },
                            modifier = Modifier.clickable {
                                HubSettings.linkFavorite(ctx, deviceId, f.id)
                                linkFor = null
                                vm.find(f.id)
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linkFor = null; tab = Tab.NEARBY.ordinal }) { Text("Open Nearby list") } },
            dismissButton = { TextButton(onClick = { linkFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PermissionScreen(request: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RadarPulse(heat = 0.8f, diameter = 200.dp) {
            Icon(Icons.Filled.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.surface)
        }
        Spacer(Modifier.height(24.dp))
        Text("Tracker Finder", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Walk toward your tracker and watch it get hotter. To hear your tracker, the app needs permission " +
                "to scan for nearby Bluetooth devices. Android groups this with location. Nothing leaves your phone.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = request, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Allow Bluetooth scanning") }
    }
}

@Composable
private fun ErrorBanner(msg: String, openBluetoothSettings: () -> Unit, retry: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = openBluetoothSettings) { Text("Bluetooth settings") }
                TextButton(onClick = retry) { Text("Try again") }
            }
        }
    }
}
