// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class ScanService : Service() {
    @Inject lateinit var repo: ScanRepository
    @Inject lateinit var notif: NotificationHelper
    @Inject lateinit var prefs: Prefs
    @Inject lateinit var orch: Orchestrator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        ServiceCompat.startForeground(this, 1, notif.service("Watching LAN for new devices"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        orch.lanService.value = true
        scope.coroutineContext.cancelChildren()
        scope.launch { while (isActive) { repo.scanOnce(); delay(if (prefs.batterySaver) 15 * 60_000L else 5 * 60_000L) } }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); orch.lanService.value = false; super.onDestroy() }
}

@AndroidEntryPoint
@SuppressLint("MissingPermission")
class BleScanService : Service() {
    @Inject lateinit var notif: NotificationHelper
    @Inject lateinit var prefs: Prefs
    @Inject lateinit var orch: Orchestrator
    private class Sight(val first: Long, var last: Long, val where: MutableSet<String> = mutableSetOf(), var alerted: Boolean = false)
    private val seen = HashMap<String, Sight>()
    private val knownBle = HashSet<String>()
    private var baselineDone = false
    private var lastNearby = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        try { ServiceCompat.startForeground(this, 2, notif.service("Watching for unknown trackers"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }
        catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        orch.bleService.value = true
        scope.coroutineContext.cancelChildren()
        scope.launch { while (isActive) { cycle(); delay((if (prefs.batterySaver) 30 else 15) * 60_000L - 5_000L) } }
        return START_STICKY
    }

    // Weak location proxy (no location permission): default-gateway of the current Wi-Fi, or "mobile".
    private fun where(): String {
        val cm = getSystemService(ConnectivityManager::class.java); val n = cm.activeNetwork ?: return "none"
        if (cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return "mobile"
        return cm.getLinkProperties(n)?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "wifi"
    }

    private suspend fun cycle() {
        val sc = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        val hits = ConcurrentHashMap<String, Tracker>()
        val all = ConcurrentHashMap<String, Pair<String, Int>>()
        val cb = object : ScanCallback() {
            override fun onScanResult(t: Int, r: ScanResult) {
                val a = r.device.address; TrackerSignatures.detect(r)?.let { hits[a] = it }
                val o = all[a]; if (o == null || r.rssi > o.second) all[a] = (r.scanRecord?.deviceName ?: "Unknown device") to r.rssi
            }
        }
        try { sc.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), cb); delay(5_000) }
        catch (e: SecurityException) { return }
        finally { runCatching { sc.stopScan(cb) } }
        val now = System.currentTimeMillis(); val loc = where()
        hits.forEach { (addr, t) ->
            val s = seen.getOrPut(addr) { Sight(now, now) }; s.last = now; s.where += loc
            Log.i("PocketLab", "tracker ${t.label} $addr") // known tracker -> silent log
            val long15 = s.last - s.first >= 14 * 60_000L // scans are 15 min apart; allow jitter
            if (!s.alerted && (long15 || s.where.size >= 3)) {
                s.alerted = true
                notif.trackerAlert(t.label, addr, if (long15) "Seen near you for 15+ minutes" else "Seen at 3+ different places with you")
            }
        }
        seen.entries.removeAll { now - it.value.last > 2 * 3_600_000L }
        // New non-tracker device within ~1-3 m (RSSI >= -55 dBm). First cycle is only a baseline; max 1 alert per 10 min.
        if (prefs.nearbyAlert && baselineDone) {
            val best = all.entries.filter { it.key !in knownBle && !hits.containsKey(it.key) && it.value.second >= -55 }.maxByOrNull { it.value.second }
            if (best != null && now - lastNearby > 10 * 60_000L) { notif.nearby(best.value.first, best.key, best.value.second); lastNearby = now }
        }
        if (knownBle.size > 2000) knownBle.clear()
        knownBle += all.keys; baselineDone = true
    }
    override fun onDestroy() { scope.cancel(); orch.bleService.value = false; super.onDestroy() }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && c.getSharedPreferences("pl", 0).getBoolean("boot", false))
            runCatching { ContextCompat.startForegroundService(c, Intent(c, BleScanService::class.java)) }
    }
}
