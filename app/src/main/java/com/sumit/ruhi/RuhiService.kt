package com.sumit.ruhi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class RuhiService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val b = if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel("ruhi", "Ruhi", NotificationManager.IMPORTANCE_LOW))
                Notification.Builder(this, "ruhi")
            } else Notification.Builder(this)
            startForeground(1, b.setContentTitle("Ruhi tumhare saath hai").setSmallIcon(android.R.drawable.ic_btn_speak_now).build())
        } catch (e: Exception) { }
        return START_STICKY
    }
}
