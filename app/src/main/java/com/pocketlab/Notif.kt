// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.app.*
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val c: Context) {
    companion object { const val ALERTS = "pocketlab_alerts"; const val TRACKER = "pocketlab_tracker"; const val SERVICE = "pocketlab_service" }
    private val nm = c.getSystemService(NotificationManager::class.java)
    private val netVib = longArrayOf(0, 300, 150, 300)
    private val trkVib = longArrayOf(0, 500, 200, 500, 200, 800)

    init {
        nm.createNotificationChannel(NotificationChannel(ALERTS, "Network alerts", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true); vibrationPattern = netVib })
        nm.createNotificationChannel(NotificationChannel(TRACKER, "Tracker alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true); vibrationPattern = trkVib
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        })
        nm.createNotificationChannel(NotificationChannel(SERVICE, "Background monitor", NotificationManager.IMPORTANCE_LOW))
    }

    private fun open() = PendingIntent.getActivity(c, 0, c.packageManager.getLaunchIntentForPackage(c.packageName)!!, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun service(text: String): Notification = NotificationCompat.Builder(c, SERVICE).setSmallIcon(android.R.drawable.ic_menu_search)
        .setContentTitle("PocketLab").setContentText(text).setOngoing(true).setContentIntent(open()).build()

    fun newDevice(ip: String, host: String) = nm.notify(ip.hashCode(), NotificationCompat.Builder(c, ALERTS)
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("New device: $ip $host".trim()).setContentText("First time seen on this network")
        .setPriority(NotificationCompat.PRIORITY_HIGH).setVibrate(netVib).setAutoCancel(true).setContentIntent(open()).build())

    fun nearby(name: String, addr: String, rssi: Int) = nm.notify(("n" + addr).hashCode(), NotificationCompat.Builder(c, ALERTS)
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("New device very close: $name")
        .setContentText("Bluetooth signal $rssi dBm (roughly 1-3 m away)").setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVibrate(netVib).setAutoCancel(true).setContentIntent(open()).build())

    fun trackerAlert(label: String, addr: String, why: String) = nm.notify(addr.hashCode(), NotificationCompat.Builder(c, TRACKER)
        .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("CRITICAL: $label may be following you").setContentText(why)
        .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setVibrate(trkVib)
        .setFullScreenIntent(open(), true).setAutoCancel(true).setContentIntent(open()).build())
}
