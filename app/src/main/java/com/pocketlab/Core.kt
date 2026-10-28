// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

const val TAGLINE = "A small contribution to the open-source community — free forever, no paid servers, no telemetry."

sealed interface Res<out T> {
    data class Ok<T>(val v: T) : Res<T>
    data class Err(val e: Throwable) : Res<Nothing>
}

@Singleton
class Orchestrator @Inject constructor() {
    val foxHunt = MutableStateFlow(false)
    val scanning = MutableStateFlow(false)
    val lanService = MutableStateFlow(false)
    val bleService = MutableStateFlow(false)
}

@Singleton
class Prefs @Inject constructor(@ApplicationContext c: Context) {
    private val p = c.getSharedPreferences("pl", Context.MODE_PRIVATE)
    var retentionDays: Int
        get() = p.getInt("ret", 7)
        set(v) { p.edit().putInt("ret", v).apply() }
    var startOnBoot: Boolean
        get() = p.getBoolean("boot", false)
        set(v) { p.edit().putBoolean("boot", v).apply() }
    var batterySaver: Boolean
        get() = p.getBoolean("saver", false)
        set(v) { p.edit().putBoolean("saver", v).apply() }
    var nearbyAlert: Boolean
        get() = p.getBoolean("nearby", true)
        set(v) { p.edit().putBoolean("nearby", v).apply() }
    fun etag(s: String): String? = p.getString("etag_$s", null)
    fun setEtag(s: String, v: String) { p.edit().putString("etag_$s", v).apply() }
    fun updated(s: String): Long = p.getLong("upd_$s", 0L)
    fun setUpdated(s: String) { p.edit().putLong("upd_$s", System.currentTimeMillis()).apply() }
    fun clearBlocklistMeta() {
        val e = p.edit(); p.all.keys.filter { it.startsWith("etag_") || it.startsWith("upd_") }.forEach { e.remove(it) }; e.apply()
    }
}
