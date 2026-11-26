package com.sumit.ruhi

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object Reminders {
    fun schedule(c: Context, id: Int, at: Long, text: String) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(c, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text)
        val pi = PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: Exception) {
            try { am.set(AlarmManager.RTC_WAKEUP, at, pi) } catch (e2: Exception) { }
        }
    }

    fun add(c: Context, at: Long, text: String): Int {
        val id = Store.nextReminderId(c)
        Store.addReminder(c, id, at, text)
        schedule(c, id, at, text)
        return id
    }

    fun cancelAll(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (r in Store.reminders(c)) {
            val i = Intent(c, ReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(c, r.first, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            try { am.cancel(pi) } catch (e: Exception) { }
        }
        Store.clearReminders(c)
    }

    fun rescheduleAll(c: Context) {
        val now = System.currentTimeMillis()
        for (r in Store.reminders(c)) {
            if (r.second > now) schedule(c, r.first, r.second, r.third) else Store.removeReminder(c, r.first)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val text = i.getStringExtra("text") ?: "Reminder"
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            val b: Notification.Builder
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel("ruhi_remind", "Ruhi reminders", NotificationManager.IMPORTANCE_HIGH))
                b = Notification.Builder(c, "ruhi_remind")
            } else b = Notification.Builder(c)
            val pi = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = b.setContentTitle("Ruhi reminder")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(id, n)
        } catch (e: Exception) { }
        Store.removeReminder(c, id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleAll(c)
    }
}
