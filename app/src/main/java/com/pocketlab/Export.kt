// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.text.format.DateFormat

object Export {
    // Device-controlled text (hostnames, BLE names) is neutralised against spreadsheet formula injection.
    private fun cell(v: Any?): String {
        if (v is Number || v is Boolean) return v.toString()
        var s = v?.toString() ?: ""
        if (s.isNotEmpty() && s[0] in "=+-@\t\r") s = "'$s"
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
    private fun row(vararg f: Any?) = f.joinToString(",") { cell(it) } + "\n"
    private fun t(ms: Long) = DateFormat.format("yyyy-MM-dd HH:mm:ss", ms).toString()

    fun lan(l: List<DeviceEntity>) = "ip,hostname,mac,vendor,type,risk_level,risk_score,open_ports,banner,online,first_seen,last_seen\n" +
        l.joinToString("") { row(it.ip, it.hostname, it.mac, it.vendor, it.deviceType, it.riskLevel, it.riskScore, it.openPorts.replace(',', ' '), it.banner, it.online, t(it.firstSeen), t(it.lastSeen)) }

    fun ble(l: List<BleDevice>) = "name,address,rssi_dbm,category\n" + l.joinToString("") { row(it.name, it.addr, it.rssi, it.tracker?.label ?: "Other") }

    fun fox(f: FoxState, s: List<Pair<Long, Int>>) = "time,device_name,address,rssi_dbm,found\n" +
        if (s.isEmpty()) row(t(System.currentTimeMillis()), f.name, f.addr, f.rssi, f.found) else s.joinToString("") { row(t(it.first), f.name, f.addr, it.second, f.found) }
}
