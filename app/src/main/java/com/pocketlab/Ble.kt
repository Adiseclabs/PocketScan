// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class Tracker(val label: String) { AIRTAG("Apple Find My"), SMARTTAG("Samsung SmartTag"), TILE("Tile"), CHIPOLO("Chipolo"), PEBBLEBEE("Pebblebee") }

// Best-effort signatures; vendors change formats and Apple/Google rotate addresses.
object TrackerSignatures {
    private fun u(s: Int) = ParcelUuid.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(s))
    private fun ScanRecord.has(s: Int) = getServiceData(u(s)) != null || serviceUuids?.contains(u(s)) == true
    fun detect(r: ScanResult): Tracker? {
        val rec = r.scanRecord ?: return null
        val name = rec.deviceName ?: ""
        return when {
            rec.getManufacturerSpecificData(0x004C)?.firstOrNull() == 0x12.toByte() -> Tracker.AIRTAG
            rec.has(0xFD5A) -> Tracker.SMARTTAG
            rec.has(0xFEED) || rec.has(0xFD84) -> Tracker.TILE
            rec.has(0xFE33) -> Tracker.CHIPOLO
            name.startsWith("Pebblebee", true) || rec.getServiceData(u(0xFEAA))?.firstOrNull().let { it == 0x40.toByte() || it == 0x41.toByte() } -> Tracker.PEBBLEBEE
            else -> null
        }
    }
}

data class BleDevice(val addr: String, val name: String, val rssi: Int, val tracker: Tracker?)
data class FoxState(val addr: String, val name: String, val rssi: Int, val found: Boolean = false, val active: Boolean = true)
data class BleUiState(val devices: List<BleDevice> = emptyList(), val scanning: Boolean = false, val fox: FoxState? = null, val error: String? = null)

@SuppressLint("MissingPermission")
@HiltViewModel
class BluetoothScanViewModel @Inject constructor(@ApplicationContext private val c: Context, private val orch: Orchestrator) : ViewModel() {
    private val _s = MutableStateFlow(BleUiState())
    val state = _s.asStateFlow()
    val bgOn = orch.bleService
    private val seen = ConcurrentHashMap<String, BleDevice>()
    private val scanner get() = c.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
    private var foxJob: Job? = null
    private var hapticJob: Job? = null
    private val foxLog = java.util.Collections.synchronizedList(ArrayList<Pair<Long, Int>>())

    private val listCb = object : ScanCallback() {
        override fun onScanResult(t: Int, r: ScanResult) {
            val n = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull() ?: "Unknown"
            seen[r.device.address] = BleDevice(r.device.address, n, r.rssi, TrackerSignatures.detect(r))
            _s.update { it.copy(devices = seen.values.sortedByDescending { d -> d.rssi }) }
        }
    }
    private val foxCb = object : ScanCallback() {
        override fun onScanResult(t: Int, r: ScanResult) {
            if (foxLog.size < 5000) foxLog.add(System.currentTimeMillis() to r.rssi)
            _s.update { it.copy(fox = it.fox?.copy(rssi = r.rssi)) }
            if (r.rssi > -45) stopFox(true)
        }
    }

    private fun fail(m: String) = _s.update { it.copy(error = m) }

    fun startScan() {
        if (_s.value.scanning) return
        val sc = scanner ?: return fail("Bluetooth is off or unavailable")
        seen.clear()
        runCatching { sc.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), listCb) }
            .onSuccess { _s.update { it.copy(scanning = true, error = null) } }.onFailure { fail(it.message ?: "Scan failed") }
    }

    fun stopScan() { runCatching { scanner?.stopScan(listCb) }; _s.update { it.copy(scanning = false) } }

    fun startFox(d: BleDevice) {
        if (orch.scanning.value) return fail("LAN scan running - wait until it finishes")
        val sc = scanner ?: return fail("Bluetooth is off or unavailable")
        stopScan(); stopFox(false); orch.foxHunt.value = true; foxLog.clear()
        _s.update { it.copy(fox = FoxState(d.addr, d.name, d.rssi), error = null) }
        val f = listOf(ScanFilter.Builder().setDeviceAddress(d.addr).build())
        runCatching { sc.startScan(f, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), foxCb) }
            .onFailure { fail(it.message ?: "Fox Hunt failed"); stopFox(false); return }
        foxJob = viewModelScope.launch { delay(10 * 60_000L); stopFox(false) }
        hapticJob = viewModelScope.launch {
            val vib = c.getSystemService(Vibrator::class.java)
            while (isActive) {
                val r = _s.value.fox?.rssi ?: break
                val ms = (50 + 200 * ((r + 100) / 55f).coerceIn(0f, 1f)).toLong() // 50ms far -> 250ms close
                vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)); delay(ms + 350)
            }
        }
    }

    fun stopFox(found: Boolean) {
        runCatching { scanner?.stopScan(foxCb) }; foxJob?.cancel(); hapticJob?.cancel(); orch.foxHunt.value = false
        _s.update { it.copy(fox = it.fox?.copy(found = found, active = false)) }
        if (found) c.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun bleCsv() = Export.ble(seen.values.sortedByDescending { it.rssi })
    fun foxCsv(): String { val f = _s.value.fox ?: return ""; return Export.fox(f, synchronized(foxLog) { foxLog.toList() }) }
    fun clearFox() { _s.update { it.copy(fox = null) } }
    override fun onCleared() { stopScan(); stopFox(false) }
}
