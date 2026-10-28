// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class Risky(val name: String, val why: String, val weight: Int)

object RiskyPorts {
    val map = mapOf(
        21 to Risky("FTP", "Sends passwords in clear text; prefer SFTP.", 20),
        23 to Risky("Telnet", "Unencrypted remote shell, often left with default passwords.", 25),
        3389 to Risky("RDP", "Remote Desktop is a top ransomware entry point; restrict it.", 25),
        445 to Risky("SMB", "File sharing exposed; historically abused by worms.", 20),
        6379 to Risky("Redis", "Database often has no password; LAN users can read/write.", 25),
        27017 to Risky("MongoDB", "Database may allow unauthenticated access.", 25)
    )
}

object RiskScorer {
    fun score(ports: List<Int>, cvss: List<Double>): Int =
        (ports.sumOf { RiskyPorts.map[it]?.weight ?: 0 } + (cvss.sum() * 3).toInt()).coerceAtMost(100)
    fun level(s: Int) = when { s >= 75 -> "Critical"; s >= 50 -> "High"; s >= 25 -> "Medium"; s > 0 -> "Low"; else -> "Clean" }
}

internal fun verParts(s: String) = Regex("\\d+").findAll(s).map { it.value.toInt() }.toList()
internal fun verCmp(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) { val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }; if (d != 0) return d }
    return 0
}

@Singleton
class OfflineCveLoader @Inject constructor(@ApplicationContext private val c: Context, private val dao: CveRecordDao) {
    suspend fun loadIfNeeded() {
        withContext(Dispatchers.IO) {
            if (dao.count() > 0) return@withContext
            runCatching {
                val arr = JSONArray(c.assets.open("cve-db.json").bufferedReader().use { it.readText() })
                val rows = ArrayList<CveRecordEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i); val vs = o.getJSONArray("versions"); val cs = o.getJSONArray("cves")
                    for (v in 0 until vs.length()) for (k in 0 until cs.length()) {
                        val ve = vs.getJSONObject(v); val ce = cs.getJSONObject(k)
                        rows += CveRecordEntity(service = o.getString("service").lowercase(), fromV = ve.getString("from"), toV = ve.getString("to"),
                            cveId = ce.getString("id"), cvss = ce.getDouble("cvss"), severity = ce.getString("severity"),
                            description = ce.getString("description"), reference = ce.getString("reference"))
                    }
                }
                dao.insertAll(rows)
            }
        }
    }
}

// Banner versions do not account for distro backports — heuristic indicators only.
@Singleton
class CveMatcher @Inject constructor(private val dao: CveRecordDao) {
    suspend fun match(b: Banner?): List<CveRecordEntity> {
        if (b == null || b.version.isEmpty()) return emptyList()
        val v = verParts(b.version)
        return dao.forService(b.service).filter { verCmp(v, verParts(it.fromV)) >= 0 && verCmp(v, verParts(it.toV)) <= 0 }
    }
}
