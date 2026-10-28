// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class Src(val name: String, val url: String, val kind: String)

// Advisory only: used to flag hostnames seen on the LAN. Nothing is ever blocked.
@Singleton
class BlocklistRepository @Inject constructor(private val http: OkHttpClient, private val dao: BlocklistDao, private val prefs: Prefs) {
    val sources = listOf(
        Src("StevenBlack", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "hosts"),
        Src("PhishingArmy", "https://phishing.army/download/phishing_army_blocklist.txt", "hosts"),
        Src("URLhaus", "https://urlhaus.abuse.ch/downloads/csv_online/", "csv"),
        Src("OpenPhish", "https://openphish.com/feed.txt", "urls")
    )
    private val skip = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback", "0.0.0.0")
    private val ipRe = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun stamps(): Map<String, Long> = sources.associate { it.name to prefs.updated(it.name) }

    suspend fun refreshAll(): Res<Int> = withContext(Dispatchers.IO) {
        var n = 0; var err: Throwable? = null
        for (s in sources) when (val r = refresh(s)) { is Res.Ok -> n += r.v; is Res.Err -> err = r.e }
        val e = err
        if (n == 0 && e != null) Res.Err(e) else Res.Ok(n)
    }

    private suspend fun refresh(s: Src): Res<Int> = runCatching {
        val have = dao.countFor(s.name) > 0
        val req = Request.Builder().url(s.url).apply { if (have) prefs.etag(s.name)?.let { header("If-None-Match", it) } }.build()
        http.newCall(req).execute().use { r ->
            if (r.code == 304) { prefs.setUpdated(s.name); return@use Res.Ok(0) }
            if (!r.isSuccessful) error("${s.name}: HTTP ${r.code}")
            val keys = HashSet<String>(); val rows = ArrayList<BlocklistEntryEntity>()
            r.body!!.byteStream().bufferedReader().useLines { ls ->
                ls.forEach { l ->
                    parse(s.kind, l)?.let { k ->
                        if (keys.add(k)) rows += if (ipRe.matches(k)) BlocklistEntryEntity(domain = null, ip = k, source = s.name) else BlocklistEntryEntity(domain = k, ip = null, source = s.name)
                    }
                }
            }
            dao.deleteBySource(s.name); rows.chunked(2000).forEach { dao.insertAll(it) }
            r.header("ETag")?.let { prefs.setEtag(s.name, it) }; prefs.setUpdated(s.name)
            Res.Ok(rows.size)
        }
    }.fold({ it as Res<Int> }, { Res.Err(it) })

    private fun host(u: String) = runCatching { URI(u.trim('"')).host?.lowercase() }.getOrNull()

    private fun parse(kind: String, line: String): String? {
        val l = line.substringBefore('#').trim()
        if (l.isEmpty()) return null
        return when (kind) {
            "hosts" -> { val t = l.split(Regex("\\s+")); val d = (if (t.size >= 2) t[1] else t[0]).lowercase(); if (d in skip || !d.contains('.')) null else d }
            "csv" -> l.split("\",\"").getOrNull(2)?.let { host(it) }
            else -> host(l)
        }
    }

    suspend fun isListed(h: String): Boolean {
        var d = h.lowercase().trimEnd('.')
        while (d.contains('.')) { if (dao.hitDomain(d) > 0) return true; d = d.substringAfter('.') }
        return false
    }
}
