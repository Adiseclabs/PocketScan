// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui
import androidx.compose.runtime.saveable.rememberSaveable

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable fun SplashScreen(onDone: () -> Unit) {
    val a by rememberInfiniteTransition(label = "r").animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "a")
    LaunchedEffect(Unit) { delay(2000); onDone() }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Radar(a, Modifier.size(200.dp)); Spacer(Modifier.height(24.dp)); Text("PocketLab", style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable fun Onboarding(onDone: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }; val ctx = LocalContext.current
    val perms = remember { buildList { if (Build.VERSION.SDK_INT >= 33) { add(Manifest.permission.POST_NOTIFICATIONS); add(Manifest.permission.NEARBY_WIFI_DEVICES) }; addAll(blePerms()) }.toTypedArray() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { step = 2 }
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.spacedBy(16.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        when (step) {
            0 -> { Text("Welcome to PocketLab", style = MaterialTheme.typography.headlineMedium); Text(TAGLINE, textAlign = TextAlign.Center); Button(onClick = { step = 1 }) { Text("Next") } }
            1 -> {
                Text("Permissions", style = MaterialTheme.typography.headlineMedium)
                Text("Notifications: alert you to new devices and trackers.\nNearby Wi-Fi devices: scan your own network (never used for location).\nBluetooth: spot BLE trackers near you.\nBattery: lets the tracker watch run reliably.")
                Button(onClick = { ask.launch(perms) }) { Text("Grant permissions") }
                TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery optimization settings") }
                TextButton(onClick = { step = 2 }) { Text("Skip") }
            }
            else -> { Text("You're all set", style = MaterialTheme.typography.headlineMedium); Button(onClick = onDone) { Text("Open PocketLab") } }
        }
    }
}

@Composable private fun AboutSection(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable fun AboutScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally) {
        Radar(40f, Modifier.size(96.dp))
        Text("PocketLab", style = MaterialTheme.typography.headlineMedium)
        Text("Aditya Bhosale", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
        Text(TAGLINE, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
        AboutSection("What is PocketLab?", "PocketLab is a pocket-sized security toolkit for Android. It shows what is connected to your Wi-Fi, which Bluetooth devices and trackers are around you, how safe your Wi-Fi is, and which installed apps deserve a closer look. Everything runs on your phone.")
        AboutSection("What it can do", "\u2022 LAN Scanner: finds devices on your Wi-Fi, reads service banners, scores risk; tap any IP for details.\n\u2022 New-device alerts: optional background monitor.\n\u2022 BLE tracker detection: spots AirTag, SmartTag, Tile, Chipolo and Pebblebee following you.\n\u2022 Fox Hunt: radar and vibration guide you to a hidden tracker.\n\u2022 Nearby alert: notifies you when a new Bluetooth device appears within a few metres.\n\u2022 Offline CVE hints and a network risk score.\n\u2022 Wi-Fi Security Check: rates your connection and router.\n\u2022 App Risk Review: flags sideloaded, hidden and over-permissioned apps.\n\u2022 Advisory blocklists and CSV export of LAN, BLE and Fox Hunt results.")
        AboutSection("How it works", "LAN scans use a parallel ping sweep with TCP fallback, then match service banners against a bundled offline CVE list. BLE detection reads advertisement packets and recognises tracker formats. Fox Hunt turns signal strength (RSSI) into a radar and haptic pulses. The Wi-Fi check reads your connection's encryption type and probes your router for exposed services. The app review reads what Android already provides: install source, launcher icon and permissions.")
        AboutSection("Privacy", "No account, no ads, no analytics, no telemetry, no paid servers. The only network requests are public blocklist downloads. Scan results stay in a local database you can wipe in Settings, and exports are saved only where you choose. PocketLab has no VPN, no firewall, no packet capture and needs no root.")
        AboutSection("Built with", "Kotlin, Jetpack Compose (Material 3), MVVM, Hilt, Room, coroutines and Flow, OkHttp. Open source under Apache-2.0 and designed to pass F-Droid review.")
        AboutSection("Good to know", "Results are indicators, not proof: banner versions ignore distro backports, Android hides other devices' MAC addresses, trackers rotate their Bluetooth address, and app flags such as hidden can have legitimate reasons. Use them as a starting point for investigation.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Developer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Aditya Bhosale", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), fontWeight = FontWeight.Medium)
                Contact()
            }
        }
    }
}
