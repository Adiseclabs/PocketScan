// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.net.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class Banner(val service: String, val version: String, val raw: String)
data class Found(val ip: String, val mac: String?, val hostname: String, val ports: List<Int>, val banner: Banner?)

@Singleton
class OuiLookup @Inject constructor(@ApplicationContext private val c: Context) {
    private val map: Map<String, String> by lazy {
        runCatching {
            val o = JSONObject(c.assets.open("oui.json").bufferedReader().use { it.readText() })
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }
    // Android 10+ hides other devices' MACs from apps, so vendor is best-effort and often "Unknown".
    fun vendor(mac: String?): String = mac?.replace(":", "")?.replace("-", "")?.uppercase()?.take(6)?.let { map[it] } ?: "Unknown"
}

@Singleton
class BannerGrabber @Inject constructor() {
    private val re = Regex("([A-Za-z][A-Za-z\\-]*)[/_ ]v?(\\d+(?:\\.\\d+)+)")
    suspend fun grab(ip: String, port: Int): Banner? = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), 2000); s.soTimeout = 2000
                val out = s.getOutputStream(); val rd = s.getInputStream().bufferedReader()
                val line = if (port == 80 || port == 8080) {
                    out.write("HEAD / HTTP/1.0\r\nHost: $ip\r\n\r\n".toByteArray()); out.flush()
                    generateSequence { rd.readLine() }.takeWhile { it.isNotEmpty() }.firstOrNull { it.startsWith("Server:", true) }?.substringAfter(':')?.trim()
                } else rd.readLine()
                line?.let { l -> re.find(l)?.let { Banner(it.groupValues[1].lowercase(), it.groupValues[2], l.take(120)) } ?: Banner("unknown", "", l.take(120)) }
            }
        }.getOrNull()
    }
}

@Singleton
class LanScanner @Inject constructor(@ApplicationContext private val c: Context, private val grabber: BannerGrabber) {
    private val fallback = listOf(80, 443, 22, 8080)
    private val ports = (fallback + RiskyPorts.map.keys).distinct()

    private fun prefix(): String? {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val n = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(n)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true && caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) != true) return null
        return cm.getLinkProperties(n)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress?.substringBeforeLast('.')
    }

    private fun arp(): Map<String, String> = runCatching {
        File("/proc/net/arp").readLines().drop(1).mapNotNull { l ->
            l.trim().split(Regex("\\s+")).takeIf { it.size >= 4 && it[3] != "00:00:00:00:00:00" }?.let { it[0] to it[3] }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun tcp(ip: String, p: Int) = runCatching { Socket().use { it.connect(InetSocketAddress(ip, p), 300); true } }.getOrDefault(false)

    private suspend fun probe(ip: String): Found? {
        val up = runCatching { InetAddress.getByName(ip).isReachable(400) }.getOrDefault(false) || fallback.any { tcp(ip, it) }
        if (!up) return null
        val open = ports.filter { tcp(ip, it) }
        val banner = listOf(21, 22, 80, 8080).firstOrNull { it in open }?.let { grabber.grab(ip, it) }
        val host = runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrDefault(ip).takeIf { it != ip } ?: ""
        return Found(ip, null, host, open, banner)
    }

    suspend fun scan(onProgress: (Int, Int) -> Unit): Res<List<Found>> = withContext(Dispatchers.IO) {
        val pre = prefix() ?: return@withContext Res.Err(IllegalStateException("Connect to Wi-Fi or Ethernet first"))
        val sem = Semaphore(50); val n = AtomicInteger()
        val list = coroutineScope {
            (1..254).map { i -> async { sem.withPermit { probe("$pre.$i").also { onProgress(n.incrementAndGet(), 254) } } } }.awaitAll()
        }.filterNotNull()
        val a = arp()
        Res.Ok(list.map { it.copy(mac = a[it.ip]) })
    }
}
