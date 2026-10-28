// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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
class DashboardViewModel @Inject constructor(@ApplicationContext private val c: Context, dao: DeviceDao, private val repo: ScanRepository, orch: Orchestrator) : ViewModel() {
    val devices = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progress = repo.progress
    val serviceOn = orch.lanService
    val scanning = orch.scanning
    val msg = MutableStateFlow<String?>(null)
    fun scan() { viewModelScope.launch { (repo.scanOnce() as? Res.Err)?.let { msg.value = it.e.message } } }
    fun toggle(on: Boolean) {
        val i = Intent(c, ScanService::class.java)
        if (on) ContextCompat.startForegroundService(c, i) else c.stopService(i)
    }
}

@Composable fun Stat(label: String, value: String, color: Color, m: Modifier) {
    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun DeviceRow(d: DeviceEntity, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(riskColor(d.riskLevel)))
            Row(Modifier.weight(1f).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.ip, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = onOpen).padding(vertical = 2.dp))
                    if (d.hostname.isNotEmpty()) Text(d.hostname, style = MaterialTheme.typography.bodyMedium)
                    Text("${d.vendor} · ${d.deviceType}" + if (d.online) "" else " · offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (d.openPorts.isNotEmpty()) Text("Ports: ${d.openPorts.replace(",", ", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill(d.riskLevel, riskColor(d.riskLevel))
            }
        }
    }
}

@Composable fun DeviceDetailDialog(d: DeviceEntity, onDismiss: () -> Unit) {
    val ui = LocalUriHandler.current; val clip = LocalClipboardManager.current
    val ports = d.openPorts.split(',').mapNotNull { it.toIntOrNull() }
    val web = ports.firstOrNull { it == 80 || it == 8080 || it == 443 }
    val names = mapOf(22 to "SSH", 80 to "HTTP", 443 to "HTTPS", 8080 to "HTTP-alt")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(d.ip) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Pill(d.riskLevel, riskColor(d.riskLevel)); Text("  score ${d.riskScore}/100", style = MaterialTheme.typography.bodySmall) }
                KV("Hostname", d.hostname.ifEmpty { "-" }); KV("MAC", d.mac ?: "hidden by Android"); KV("Vendor", d.vendor); KV("Type", d.deviceType)
                KV("Open ports", if (ports.isEmpty()) "none found" else ports.joinToString { p -> RiskyPorts.map[p]?.let { "$p ${it.name} (risky)" } ?: "$p ${names[p] ?: ""}".trim() })
                KV("Banner", d.banner.ifEmpty { "-" }); KV("First seen", ts(d.firstSeen)); KV("Last seen", ts(d.lastSeen)); KV("Status", if (d.online) "Online" else "Offline")
                ports.mapNotNull { p -> RiskyPorts.map[p]?.let { "Port $p: ${it.why}" } }.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Row {
                if (web != null) TextButton(onClick = { ui.openUri((if (web == 443) "https" else "http") + "://" + d.ip + if (web == 8080) ":8080" else "") }) { Text("Open") }
                TextButton(onClick = { clip.setText(AnnotatedString(d.ip)) }) { Text("Copy IP") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DashboardScreen(vm: DashboardViewModel = hiltViewModel()) {
    val devs by vm.devices.collectAsState(); val prog by vm.progress.collectAsState()
    val on by vm.serviceOn.collectAsState(); val scanning by vm.scanning.collectAsState(); val msg by vm.msg.collectAsState()
    val ctx = LocalContext.current; val host = remember { SnackbarHostState() }; val export = rememberExporter()
    var ask by remember { mutableStateOf(false) }; var selId by remember { mutableStateOf<Long?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.toggle(true) }
    LaunchedEffect(msg) { msg?.let { host.showSnackbar(it); vm.msg.value = null } }
    Box(Modifier.fillMaxSize()) {
        Column {
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        val risky = devs.count { it.riskScore >= 50 }
                        Stat("Devices", "${devs.size}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        Stat("Online", "${devs.count { it.online }}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                        Stat("At risk", "$risky", riskColor(if (risky > 0) "High" else "Clean"), Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Background LAN monitor", Modifier.weight(1f))
                        Switch(on, { want ->
                            if (want && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ask = true
                            else vm.toggle(want)
                        })
                    }
                    OutlinedButton(onClick = { export("pocketlab-lan-" + System.currentTimeMillis() / 1000 + ".csv", Export.lan(devs)) }, enabled = !scanning && devs.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Export devices (CSV)") }
                }
            }
            prog?.let { LinearProgressIndicator(progress = { it.first / it.second.toFloat() }, modifier = Modifier.fillMaxWidth()) }
            Text("Tap an IP address for details", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PullToRefreshBox(isRefreshing = scanning, onRefresh = vm::scan) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)) {
                    if (devs.isEmpty()) item { Text("No devices yet. Connect to Wi-Fi and tap Scan.", Modifier.padding(16.dp)) }
                    items(devs, key = { it.id }) { d -> DeviceRow(d) { selId = d.id } }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = vm::scan, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Text(if (scanning) "Scanning..." else "Scan") }
        SnackbarHost(host, Modifier.align(Alignment.BottomStart))
    }
    devs.firstOrNull { it.id == selId }?.let { DeviceDetailDialog(it) { selId = null } }
    if (ask) AlertDialog(onDismissRequest = { ask = false }, title = { Text("Allow notifications?") },
        text = { Text("PocketLab needs notification permission to tell you when a new device joins your network. Nothing leaves your phone.") },
        confirmButton = { TextButton(onClick = { ask = false; perm.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Continue") } })
}
