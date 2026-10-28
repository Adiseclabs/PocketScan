// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui

import androidx.compose.ui.platform.LocalUriHandler
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

data class SecUi(val score: Int = 0, val level: String = "Clean", val top: List<DeviceEntity> = emptyList(), val groups: List<Pair<String, List<CveFindingEntity>>> = emptyList())

@HiltViewModel
class SecurityViewModel @Inject constructor(dd: DeviceDao, cd: CveFindingDao) : ViewModel() {
    val ui = combine(dd.all(), cd.all()) { d, c ->
        val on = d.filter { it.online }
        val s = if (on.isEmpty()) 0 else ((on.map { it.riskScore }.average() + on.maxOf { it.riskScore }) / 2).toInt()
        SecUi(s, RiskScorer.level(s), d.filter { it.riskScore > 0 }.take(5),
            listOf("Critical", "High", "Medium", "Low").map { sv -> sv to c.filter { it.severity.equals(sv, true) } }.filter { it.second.isNotEmpty() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecUi())
}

@Composable fun CveCard(f: CveFindingEntity) {
    var open by remember { mutableStateOf(false) }; val ui = LocalUriHandler.current
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${f.cveId}  (${f.deviceIp})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Pill("CVSS ${f.cvss}", riskColor(f.severity))
            }
            if (open) {
                Text(f.description, style = MaterialTheme.typography.bodySmall)
                Text("Fix: ${f.fix}", style = MaterialTheme.typography.bodySmall)
                Text(f.reference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { ui.openUri(f.reference) })
            }
        }
    }
}

@Composable fun SecurityScreen(vm: SecurityViewModel = hiltViewModel()) {
    val u by vm.ui.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Network risk score")
                    Text("${u.score}/100", style = MaterialTheme.typography.displaySmall, color = riskColor(u.level))
                    Pill(u.level, riskColor(u.level))
                    Text("Banner versions do not account for distro backports - heuristic indicators only.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Top 5 vulnerable devices", style = MaterialTheme.typography.titleMedium) }
        if (u.top.isEmpty()) item { Text("Nothing risky found. Run a scan from the Dashboard.") }
        items(u.top) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("${d.ip} ${d.hostname}", Modifier.weight(1f)); Pill("${d.riskScore} · ${d.riskLevel}", riskColor(d.riskLevel)) }
                    d.openPorts.split(',').mapNotNull { it.toIntOrNull()?.let { p -> RiskyPorts.map[p]?.let { r -> "Port $p ${r.name}: ${r.why}" } } }
                        .forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        u.groups.forEach { (sev, list) ->
            item { Text("$sev (${list.size})", style = MaterialTheme.typography.titleMedium, color = riskColor(sev)) }
            items(list) { CveCard(it) }
        }
    }
}
