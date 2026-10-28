// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
class BlocklistViewModel @Inject constructor(private val repo: BlocklistRepository, dao: BlocklistDao) : ViewModel() {
    val names = repo.sources.map { it.name }
    val counts = dao.counts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false); val msg = MutableStateFlow<String?>(null); val stamps = MutableStateFlow(repo.stamps())
    fun refresh() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            msg.value = when (val r = repo.refreshAll()) { is Res.Ok -> "Done: ${r.v} entries updated"; is Res.Err -> r.e.message }
            stamps.value = repo.stamps(); busy.value = false
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(val prefs: Prefs, private val db: AppDatabase, private val loader: OfflineCveLoader) : ViewModel() {
    val retention = MutableStateFlow(prefs.retentionDays); val boot = MutableStateFlow(prefs.startOnBoot); val saver = MutableStateFlow(prefs.batterySaver); val nearby = MutableStateFlow(prefs.nearbyAlert)
    fun setRetention(v: Int) { retention.value = v; prefs.retentionDays = v }
    fun setBoot(v: Boolean) { boot.value = v; prefs.startOnBoot = v }
    fun setSaver(v: Boolean) { saver.value = v; prefs.batterySaver = v }
    fun setNearby(v: Boolean) { nearby.value = v; prefs.nearbyAlert = v }
    fun reset() { viewModelScope.launch { withContext(Dispatchers.IO) { db.clearAllTables() }; prefs.clearBlocklistMeta(); loader.loadIfNeeded() } }
}

@Composable fun BlocklistScreen(vm: BlocklistViewModel = hiltViewModel()) {
    val counts by vm.counts.collectAsState(); val busy by vm.busy.collectAsState(); val st by vm.stamps.collectAsState(); val msg by vm.msg.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Advisory only: PocketLab flags hostnames seen on your LAN. It never blocks or inspects traffic.", style = MaterialTheme.typography.bodySmall)
        vm.names.forEach { n ->
            val t = st[n] ?: 0L
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(n, style = MaterialTheme.typography.titleSmall)
                    Text("${counts.firstOrNull { it.source == n }?.n ?: 0} entries · updated " + if (t == 0L) "never" else android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", t), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = vm::refresh, enabled = !busy) { Text(if (busy) "Downloading..." else "Refresh now") }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun SwitchRow(label: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(v, on) }
}

@Composable fun SettingsScreen(vm: SettingsViewModel = hiltViewModel(), bl: BlocklistViewModel = hiltViewModel(), onAbout: () -> Unit) {
    val days by vm.retention.collectAsState(); val boot by vm.boot.collectAsState(); val saver by vm.saver.collectAsState(); val busy by bl.busy.collectAsState()
    val nearby by vm.nearby.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Keep data for $days days")
        Slider(days.toFloat(), { vm.setRetention(it.toInt()) }, valueRange = 1f..30f, steps = 28)
        SwitchRow("Start tracker watch on boot", boot, vm::setBoot)
        SwitchRow("Battery saver (scan less often)", saver, vm::setSaver)
        SwitchRow("Alert when a new Bluetooth device is very close", nearby, vm::setNearby)
        Button(onClick = bl::refresh, enabled = !busy) { Text("Refresh blocklists") }
        OutlinedButton(onClick = { confirm = true }) { Text("Reset all data") }
        OutlinedButton(onClick = onAbout) { Text("About PocketLab") }
        AttributionCard()
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Reset data?") }, text = { Text("Deletes devices, findings and blocklists stored on this phone.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.reset() }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
