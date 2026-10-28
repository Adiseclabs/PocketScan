// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class Finding(val sev: String, val title: String, val detail: String, val fix: String = "")
data class WifiReport(val score: Int = 0, val level: String = "Clean", val ssid: String = "", val facts: List<Pair<String, String>> = emptyList(), val findings: List<Finding> = emptyList(), val error: String? = null)

// Rates the Wi-Fi you are connected to. Reads only what Android exposes without location permission.
@Singleton
class WifiAudit @Inject constructor(@ApplicationContext private val c: Context, private val grabber: BannerGrabber, private val matcher: CveMatcher, private val dd: DeviceDao) {
    private fun tcp(ip: String, p: Int) = runCatching { Socket().use { it.connect(InetSocketAddress(ip, p), 400); true } }.getOrDefault(false)

    @Suppress("DEPRECATION")
    suspend fun run(): WifiReport = withContext(Dispatchers.IO) {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val n = cm.activeNetwork
        if (n == null || cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return@withContext WifiReport(error = "Connect to a Wi-Fi network first")
        val info = c.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
        val lp = cm.getLinkProperties(n)
        val f = ArrayList<Finding>(); val facts = ArrayList<Pair<String, String>>(); var score = 100
        val name = info.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" }
        facts += "Network" to (name ?: "Hidden by Android (needs location permission)")

        val sec = if (Build.VERSION.SDK_INT >= 31) info.currentSecurityType else -99
        val secName = when (sec) {
            WifiInfo.SECURITY_TYPE_OPEN -> { score -= 70; f += Finding("Critical", "Open network (no encryption)", "Anyone nearby can read unprotected traffic and impersonate sites.", "Use a WPA2/WPA3 network, or a trusted VPN on this one."); "Open (none)" }
            WifiInfo.SECURITY_TYPE_WEP -> { score -= 60; f += Finding("Critical", "WEP encryption", "WEP can be cracked in minutes.", "Switch the router to WPA2 or WPA3."); "WEP" }
            WifiInfo.SECURITY_TYPE_PSK -> { score -= 10; f += Finding("Low", "WPA/WPA2 personal", "Solid, but WPA3 resists password guessing better.", "Enable WPA3 or WPA2/WPA3 mixed mode if supported and use a long passphrase."); "WPA/WPA2-Personal" }
            WifiInfo.SECURITY_TYPE_OWE -> { score -= 15; f += Finding("Medium", "Enhanced Open (OWE)", "Traffic is encrypted but the network itself is not authenticated.", "Prefer WPA3 with a password."); "Enhanced Open (OWE)" }
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3-Personal (SAE)"
            WifiInfo.SECURITY_TYPE_EAP -> "Enterprise (802.1X)"
            WifiInfo.SECURITY_TYPE_UNKNOWN, -99 -> { f += Finding("Medium", "Encryption type not reported", "Android did not tell the app which encryption this network uses (needs Android 12+).", "Check the router: use WPA2 or WPA3."); "Unknown" }
            else -> "Other (type $sec)"
        }
        facts += "Encryption" to secName
        if (Build.VERSION.SDK_INT >= 30) {
            val std = when (info.wifiStandard) { 1 -> "Legacy (802.11a/b/g)"; 4 -> "Wi-Fi 4 (802.11n)"; 5 -> "Wi-Fi 5 (802.11ac)"; 6 -> "Wi-Fi 6 (802.11ax)"; 8 -> "Wi-Fi 7 (802.11be)"; else -> "Unknown" }
            facts += "Standard" to std
            if (info.wifiStandard == 1) { score -= 5; f += Finding("Low", "Very old Wi-Fi standard", "Legacy mode is slow and may push the router to weaker settings.", "Check router settings or replace an old router.") }
        }
        facts += "Band" to (if (info.frequency < 3000) "2.4 GHz" else if (info.frequency < 5900) "5 GHz" else "6 GHz")
        facts += "Signal" to "${info.rssi} dBm, link ${info.linkSpeed} Mbps"

        val gw = lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        if (gw != null) {
            facts += "Router" to gw
            val open = listOf(21, 22, 23, 80, 443, 8080).filter { tcp(gw, it) }
            facts += "Router open ports" to open.joinToString(", ").ifEmpty { "none found" }
            if (23 in open) { score -= 30; f += Finding("Critical", "Router Telnet is open", "Telnet is unencrypted and often has default passwords.", "Disable Telnet in the router admin page.") }
            if (21 in open) { score -= 15; f += Finding("High", "Router FTP is open", "FTP sends passwords in clear text.", "Disable FTP or use SFTP.") }
            if ((80 in open || 8080 in open) && 443 !in open) { score -= 10; f += Finding("Medium", "Router admin page uses plain HTTP", "Anyone on the Wi-Fi could sniff the admin password.", "Enable HTTPS admin if available and set a strong admin password.") }
            val b = listOf(80, 8080, 22, 21).firstOrNull { it in open }?.let { grabber.grab(gw, it) }
            if (b != null) {
                facts += "Router software" to b.raw.take(60)
                val cves = matcher.match(b)
                if (cves.isNotEmpty()) { score -= minOf(40, cves.size * 20); f += Finding("High", "Router software matches known CVEs", cves.joinToString { it.cveId } + " (heuristic, banner version only)", "Update the router firmware.") }
            }
        }
        facts += "DNS servers" to (lp?.dnsServers?.joinToString { it.hostAddress ?: "" }?.ifEmpty { "none" } ?: "unknown")
        if (Build.VERSION.SDK_INT >= 28 && lp != null) {
            facts += "Private DNS" to (if (lp.isPrivateDnsActive) "On" else "Off")
            if (!lp.isPrivateDnsActive) f += Finding("Low", "DNS lookups are not encrypted", "Your DNS queries are visible to this network.", "Settings > Network > Private DNS.")
        }
        val risky = dd.snapshot().filter { it.online && it.riskScore >= 50 }
        if (risky.isNotEmpty()) { score -= minOf(15, risky.size * 5); f += Finding("Medium", "${risky.size} risky device(s) on this network", risky.joinToString { it.ip }, "Open the Dashboard and tap the IP for details.") }
        else facts += "LAN devices" to "Run a LAN scan on the Dashboard for deeper results"
        val order = listOf("Critical", "High", "Medium", "Low")
        f.sortBy { order.indexOf(it.sev).let { i -> if (i < 0) 9 else i } }
        if (f.isEmpty()) f += Finding("Clean", "No problems found", "Encryption and router checks look fine.")
        val s = score.coerceIn(0, 100)
        val lvl = when { s >= 90 -> "Clean"; s >= 75 -> "Low"; s >= 55 -> "Medium"; s >= 35 -> "High"; else -> "Critical" }
        WifiReport(s, lvl, name ?: "Your Wi-Fi", facts, f)
    }
}

data class AppRisk(val pkg: String, val label: String, val score: Int, val level: String, val reasons: List<String>, val perms: List<String>, val sideloaded: Boolean, val hidden: Boolean, val over: Boolean)

// Heuristic review of user-installed apps. Flags are hints: e.g. "hidden" apps can be legitimate background services.
@Singleton
class AppAudit @Inject constructor(@ApplicationContext private val c: Context) {
    private val stores = setOf("com.android.vending", "org.fdroid.fdroid", "org.fdroid.basic", "com.aurora.store", "com.sec.android.app.samsungapps", "com.amazon.venezia",
        "com.huawei.appmarket", "com.heytap.market", "com.oppo.market", "com.xiaomi.mipicks", "com.vivo.appstore", "com.google.android.feedback")
    private val high = setOf("SEND_SMS", "READ_SMS", "RECEIVE_SMS", "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS", "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES",
        "MANAGE_EXTERNAL_STORAGE", "ACCESS_BACKGROUND_LOCATION", "BIND_DEVICE_ADMIN", "BIND_ACCESSIBILITY_SERVICE", "ANSWER_PHONE_CALLS")
    private val other = setOf("CAMERA", "RECORD_AUDIO", "READ_CONTACTS", "WRITE_CONTACTS", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_PHONE_STATE", "READ_PHONE_NUMBERS",
        "READ_CALENDAR", "WRITE_CALENDAR", "BODY_SENSORS", "GET_ACCOUNTS", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO", "QUERY_ALL_PACKAGES", "ACTIVITY_RECOGNITION")

    @Suppress("DEPRECATION")
    suspend fun scan(): List<AppRisk> = withContext(Dispatchers.Default) {
        val pm = c.packageManager
        val pis = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())) else pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launch = (if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0L)) else pm.queryIntentActivities(main, 0)).map { it.activityInfo.packageName }.toSet()
        pis.mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || pi.packageName == c.packageName) return@mapNotNull null
            val inst = runCatching { if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pi.packageName).installingPackageName else pm.getInstallerPackageName(pi.packageName) }.getOrNull()
            val side = inst == null || inst !in stores
            val hidden = pi.packageName !in launch
            val req = pi.requestedPermissions ?: emptyArray(); val fl = pi.requestedPermissionsFlags ?: IntArray(0)
            val perms = req.indices.filter { req[it].startsWith("android.permission.") }.map { req[it].removePrefix("android.permission.") to ((fl.getOrElse(it) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) }
            val hi = perms.filter { it.first in high }.map { it.first }
            val ot = perms.filter { it.second && it.first in other }.map { it.first }
            val over = hi.size + ot.size >= 8
            val dbg = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val old = ai.targetSdkVersion < 26
            val s = ((if (side) 25 else 0) + (if (hidden) 20 else 0) + minOf(40, hi.size * 10) + minOf(15, ot.size * 3) + (if (over) 10 else 0) + (if (dbg) 10 else 0) + (if (old) 5 else 0)).coerceAtMost(100)
            val why = ArrayList<String>()
            if (side) why += if (inst == null) "Unknown install source (APK file or adb), not a trusted store" else "Installed by $inst, not a trusted store"
            if (hidden) why += "Hidden: no launcher icon (can be legitimate for services and plugins)"
            if (hi.isNotEmpty()) why += "Powerful permissions: " + hi.joinToString(", ")
            if (over) why += "Over-permissioned: ${hi.size + ot.size} sensitive permissions"
            if (dbg) why += "Debuggable build"
            if (old) why += "Targets an old Android version (API ${ai.targetSdkVersion})"
            AppRisk(pi.packageName, pm.getApplicationLabel(ai).toString(), s, RiskScorer.level(s), why, (hi + ot).distinct(), side, hidden, over)
        }.sortedByDescending { it.score }
    }
}
