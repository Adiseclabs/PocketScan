// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

@Composable fun BleScannerScreen(vm: BluetoothScanViewModel, onHunt: (BleDevice) -> Unit) {
    val s by vm.state.collectAsState(); val bg by vm.bgOn.collectAsState(); val ctx = LocalContext.current; val perms = remember { blePerms() }
    val sp = remember { ctx.getSharedPreferences("pl", 0) }; val export = rememberExporter()
    var pending by remember { mutableStateOf<BleDevice?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.all { it }) vm.startScan() }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Detects BLE devices actively advertising. Bluetooth Classic and non-discoverable devices are not visible.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Background tracker watch", Modifier.weight(1f))
                    Switch(bg, { on ->
                        if (!ctx.hasAll(perms)) ask.launch(perms)
                        else { val i = Intent(ctx, BleScanService::class.java); if (on) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i) }
                    })
                }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (s.scanning) vm.stopScan() else if (ctx.hasAll(perms)) vm.startScan() else ask.launch(perms) }) { Text(if (s.scanning) "Stop scan" else "Scan now") }
            OutlinedButton(onClick = { export("pocketlab-ble-" + System.currentTimeMillis() / 1000 + ".csv", vm.bleCsv()) }, enabled = !s.scanning && s.devices.isNotEmpty()) { Text("Export CSV") }
        }
        s.error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.devices, key = { it.addr }) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(d.name, style = MaterialTheme.typography.titleSmall); Text(d.addr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Pill(d.tracker?.label ?: "Other", if (d.tracker != null) riskColor("High") else Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(progress = { ((d.rssi + 100) / 60f).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            TextButton(onClick = { if (sp.getBoolean("foxinfo", false)) onHunt(d) else pending = d }) { Text("Hunt") }
                        }
                    }
                }
            }
        }
    }
    pending?.let { d -> FoxInfoDialog(onDismiss = { pending = null }, onStart = { onHunt(d) }) }
}

@Composable fun FoxHuntScreen(vm: BluetoothScanViewModel) {
    val s by vm.state.collectAsState(); val f = s.fox; val export = rememberExporter(); var help by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        if (f == null) {
            Text("Pick a device on the BLE tab and tap Hunt.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { help = true }) { Text("How to use Fox Hunt") }
            return@Column
        }
        val k by animateFloatAsState(((f.rssi + 100) / 55f).coerceIn(0f, 1f), label = "k")
        val col = MaterialTheme.colorScheme.primary
        Text(f.name, style = MaterialTheme.typography.titleLarge)
        Text("${f.rssi} dBm", style = MaterialTheme.typography.displaySmall, color = col)
        Canvas(Modifier.size(260.dp)) {
            val r = size.minDimension / 2
            listOf(1f, .66f, .33f).forEach { drawCircle(col.copy(alpha = .3f), r * it, style = Stroke(2f)) }
            drawCircle(col.copy(alpha = .5f), r * (0.1f + 0.9f * k))
        }
        LinearProgressIndicator(progress = { k }, modifier = Modifier.fillMaxWidth().height(12.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (f.found) "Found it! Signal stronger than -45 dBm." else if (f.active) "Bigger circle and faster buzz = closer. Auto-stops after 10 min." else "Stopped.")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (f.active) Button(onClick = { vm.stopFox(false) }) { Text("Stop") }
            else {
                OutlinedButton(onClick = { export("pocketlab-foxhunt-" + System.currentTimeMillis() / 1000 + ".csv", vm.foxCsv()) }) { Text("Export CSV") }
                Button(onClick = { vm.clearFox() }) { Text("Close") }
            }
            TextButton(onClick = { help = true }) { Text("How to use") }
        }
    }
    if (help) FoxInfoDialog(onDismiss = { help = false }, onStart = null)
}
