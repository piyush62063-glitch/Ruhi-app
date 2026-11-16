package com.sumit.ruhi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class RuhiService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            // old channel "ruhi" could have sound settings stuck on it; use a fresh silent one
            try { nm.deleteNotificationChannel("ruhi") } catch (e: Exception) { }
            val ch = NotificationChannel("ruhi_quiet", "Ruhi background", NotificationManager.IMPORTANCE_LOW)
            ch.setSound(null, null); ch.enableVibration(false); ch.enableLights(false); ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(this, "ruhi_quiet")
                .setContentTitle("Ruhi tumhare saath hai")
                .setContentText("Sunti rahegi. Tap karke wapas aao")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pi)
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, n)
        } catch (e: Exception) { }
        return START_STICKY
    }
}
