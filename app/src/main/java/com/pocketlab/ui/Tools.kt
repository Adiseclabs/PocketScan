// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
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
class WifiViewModel @Inject constructor(private val a: WifiAudit) : ViewModel() {
    val report = MutableStateFlow<WifiReport?>(null); val busy = MutableStateFlow(false)
    fun run() {
        if (busy.value) return
        viewModelScope.launch { busy.value = true; report.value = runCatching { a.run() }.getOrElse { WifiReport(error = it.message ?: "Scan failed") }; busy.value = false }
    }
}

@HiltViewModel
class AppRiskViewModel @Inject constructor(private val a: AppAudit) : ViewModel() {
    val apps = MutableStateFlow<List<AppRisk>?>(null); val busy = MutableStateFlow(false); val error = MutableStateFlow<String?>(null)
    fun run() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true; error.value = null
            runCatching { a.scan() }.onSuccess { apps.value = it }.onFailure { error.value = it.message ?: "Scan failed" }
            busy.value = false
        }
    }
}

fun ratingLabel(level: String) = when (level) { "Clean" -> "Strong"; "Low" -> "Good"; "Medium" -> "Fair"; "High" -> "Weak"; else -> "Critical" }

@Composable fun WifiSecurityScreen(vm: WifiViewModel = hiltViewModel()) {
    val r by vm.report.collectAsState(); val busy by vm.busy.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Rates the Wi-Fi you are connected to: encryption, router exposure, known router CVEs, DNS and risky devices on it. Nothing leaves your phone.", style = MaterialTheme.typography.bodySmall) }
        item {
            Button(onClick = vm::run, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (r == null) "Check my Wi-Fi security" else "Scan again")
            }
        }
        r?.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
        r?.takeIf { it.error == null }?.let { rep ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(rep.ssid, style = MaterialTheme.typography.titleMedium)
                        Text("${rep.score}/100", style = MaterialTheme.typography.displayMedium, color = riskColor(rep.level))
                        Pill(ratingLabel(rep.level), riskColor(rep.level))
                    }
                }
            }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { rep.facts.forEach { KV(it.first, it.second) } } } }
            items(rep.findings) { f ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(f.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Pill(f.sev, riskColor(f.sev)) }
                        Text(f.detail, style = MaterialTheme.typography.bodySmall)
                        if (f.fix.isNotEmpty()) Text("Fix: " + f.fix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable fun AppCard(a: AppRisk) {
    var open by remember { mutableStateOf(false) }; val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(a.label, style = MaterialTheme.typography.titleSmall); Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Pill("${a.level} · ${a.score}", riskColor(a.level))
            }
            if (a.reasons.isEmpty()) Text("No risk signals found.", style = MaterialTheme.typography.bodySmall) else a.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (open) {
                if (a.perms.isNotEmpty()) Text("Sensitive permissions: " + a.perms.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))) }) { Text("Open app settings") }
            } else Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppRiskScreen(vm: AppRiskViewModel = hiltViewModel()) {
    val apps by vm.apps.collectAsState(); val busy by vm.busy.collectAsState(); val err by vm.error.collectAsState(); var filter by remember { mutableStateOf("All") }
    val list = apps.orEmpty().filter { when (filter) { "Sideloaded" -> it.sideloaded; "Hidden" -> it.hidden; "Over-permissioned" -> it.over; else -> true } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Reviews your installed apps (not system apps): install source, hidden launcher icon and sensitive permissions. These are hints, not proof of malware.", style = MaterialTheme.typography.bodySmall) }
        item {
            Button(onClick = vm::run, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (apps == null) "Scan installed apps" else "Scan again")
            }
        }
        err?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
        apps?.let { all ->
            item {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("All", "Sideloaded", "Hidden", "Over-permissioned").forEach { n -> FilterChip(selected = filter == n, onClick = { filter = n }, label = { Text(n) }, modifier = Modifier.padding(end = 8.dp)) }
                }
            }
            item { Text("${all.size} apps scanned · ${all.count { it.level == "High" || it.level == "Critical" }} need attention", style = MaterialTheme.typography.titleSmall) }
            if (list.isEmpty()) item { Text("Nothing matches this filter.") }
            items(list, key = { it.pkg }) { AppCard(it) }
        }
    }
}

@Composable fun MoreScreen(go: (String) -> Unit) {
    val entries = listOf(
        Triple("wifi", "Check Your Wi-Fi Security", "Rate the security of the Wi-Fi you are on"),
        Triple("apps", "App Risk Review", "Find sideloaded, hidden and over-permissioned apps"),
        Triple("bl", "Blocklists", "Advisory phishing and ad-domain lists"),
        Triple("set", "Settings", "Retention, alerts, battery saver, reset"),
        Triple("about", "About Me", "Project description and contact"))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(entries) { (route, title, desc) ->
            Card(Modifier.fillMaxWidth().clickable { go(route) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
