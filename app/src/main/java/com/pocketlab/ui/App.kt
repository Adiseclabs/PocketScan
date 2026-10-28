// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketlab.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltViewModel
class StartupViewModel @Inject constructor(loader: OfflineCveLoader, bl: BlocklistRepository, dao: BlocklistDao) : ViewModel() {
    val loading = MutableStateFlow(false)
    init { viewModelScope.launch { loader.loadIfNeeded(); if (dao.total() == 0) { loading.value = true; bl.refreshAll(); loading.value = false } } }
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)
private val titles = mapOf("dash" to "LAN Dashboard", "ble" to "BLE Scanner", "fox" to "Fox Hunt", "sec" to "Security", "more" to "More", "wifi" to "Wi-Fi Security",
    "apps" to "App Risk Review", "bl" to "Blocklists", "set" to "Settings", "about" to "About Me")
private val subRoutes = setOf("wifi", "apps", "bl", "set", "about")

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MainScaffold(ble: BluetoothScanViewModel) {
    val nav = rememberNavController()
    val tabs = listOf(NavTab("dash", "Dashboard", Icons.Default.Home), NavTab("ble", "BLE", Icons.Default.Share), NavTab("fox", "Fox Hunt", Icons.Default.Search),
        NavTab("sec", "Security", Icons.Default.Lock), NavTab("more", "More", Icons.Default.Menu))
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(titles[route] ?: "PocketLab") },
                navigationIcon = { if (route in subRoutes) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(selected = route == t.route || (t.route == "more" && route in subRoutes), onClick = { nav.navigate(t.route) { popUpTo("dash"); launchSingleTop = true } },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label, fontSize = 11.sp, maxLines = 1) })
                }
            }
        }
    ) { p ->
        NavHost(nav, "dash", Modifier.padding(p)) {
            composable("dash") { DashboardScreen() }
            composable("ble") { BleScannerScreen(ble) { d -> nav.navigate("fox"); ble.startFox(d) } }
            composable("fox") { FoxHuntScreen(ble) }
            composable("sec") { SecurityScreen() }
            composable("more") { MoreScreen { nav.navigate(it) } }
            composable("wifi") { WifiSecurityScreen() }
            composable("apps") { AppRiskScreen() }
            composable("bl") { BlocklistScreen() }
            composable("set") { SettingsScreen(onAbout = { nav.navigate("about") }) }
            composable("about") { AboutScreen() }
        }
    }
}

// Call from MainActivity: setContent { PocketTheme { PocketLabApp() } }
@Composable fun PocketLabApp(startup: StartupViewModel = hiltViewModel(), ble: BluetoothScanViewModel = hiltViewModel()) {
    val ctx = LocalContext.current; val sp = remember { ctx.getSharedPreferences("pl", 0) }
    var stage by rememberSaveable { mutableStateOf(0) }
    val loading by startup.loading.collectAsState()
    when (stage) {
        0 -> SplashScreen { stage = if (sp.getBoolean("onboarded", false)) 2 else 1 }
        1 -> Onboarding {
            sp.edit().putBoolean("onboarded", true).apply()
            if (ctx.hasAll(blePerms())) runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, BleScanService::class.java)) }
            stage = 2
        }
        else -> MainScaffold(ble)
    }
    if (loading) AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
        Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text("Downloading blocklists (once, ~10MB)") }
    })
}
