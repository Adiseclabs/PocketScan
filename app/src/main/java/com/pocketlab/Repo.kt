// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

fun guessType(v: String, p: List<Int>) = when {
    listOf("tp-link", "netgear", "d-link", "huawei").any { v.contains(it, true) } -> "Router"
    3389 in p -> "Windows PC"
    22 in p -> "Server/Linux"
    445 in p -> "PC/NAS"
    listOf("apple", "samsung", "xiaomi").any { v.contains(it, true) } -> "Phone/Laptop"
    v.contains("espressif", true) -> "IoT"
    else -> "Unknown"
}

@Singleton
class ScanRepository @Inject constructor(
    private val scanner: LanScanner, private val oui: OuiLookup, private val matcher: CveMatcher, private val bl: BlocklistRepository,
    private val dd: DeviceDao, private val cd: CveFindingDao, private val dom: DomainDao,
    private val notif: NotificationHelper, private val orch: Orchestrator, private val prefs: Prefs
) {
    val progress = MutableStateFlow<Pair<Int, Int>?>(null)
    private val lock = Mutex()

    suspend fun scanOnce(): Res<Int> {
        if (orch.foxHunt.value) return Res.Err(IllegalStateException("Fox Hunt is active - stop it before scanning"))
        if (!lock.tryLock()) return Res.Err(IllegalStateException("A scan is already running"))
        orch.scanning.value = true
        try {
            val res = scanner.scan { d, t -> progress.value = d to t }
            val found = when (res) { is Res.Err -> return res; is Res.Ok -> res.v }
            val now = System.currentTimeMillis()
            val known = dd.snapshot().associateBy { it.ip }
            val baseline = known.isEmpty() // first scan only builds the baseline, no alert flood
            dd.markAllOffline()
            for (f in found) {
                val m = matcher.match(f.banner); val old = known[f.ip]; val vendor = oui.vendor(f.mac)
                val listed = f.hostname.isNotEmpty() && bl.isListed(f.hostname)
                val score = (RiskScorer.score(f.ports, m.map { it.cvss }) + if (listed) 30 else 0).coerceAtMost(100)
                dd.upsert(DeviceEntity(id = old?.id ?: 0, ip = f.ip, mac = f.mac, hostname = f.hostname, vendor = vendor, deviceType = guessType(vendor, f.ports),
                    riskLevel = RiskScorer.level(score), riskScore = score, openPorts = f.ports.joinToString(","), banner = f.banner?.raw ?: "",
                    firstSeen = old?.firstSeen ?: now, lastSeen = now, online = true))
                cd.deleteFor(f.ip)
                cd.insertAll(m.map { CveFindingEntity(deviceIp = f.ip, cveId = it.cveId, cvss = it.cvss, severity = it.severity, description = it.description,
                    reference = it.reference, fix = "Upgrade ${it.service} above ${it.toV}, or confirm your distro backported the fix", foundAt = now) })
                if (f.hostname.isNotEmpty()) dom.insert(DomainEntity(name = f.hostname, sourceIp = f.ip, seenAt = now))
                if (old == null && !baseline) notif.newDevice(f.ip, f.hostname)
            }
            val cut = now - prefs.retentionDays * 86_400_000L
            dd.purge(cut); cd.purge(cut); dom.purge(cut)
            return Res.Ok(found.size)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { return Res.Err(e)
        } finally { progress.value = null; orch.scanning.value = false; lock.unlock() }
    }
}
