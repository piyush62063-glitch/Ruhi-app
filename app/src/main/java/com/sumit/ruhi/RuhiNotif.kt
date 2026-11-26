package com.sumit.ruhi

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Part 4B: Naye notification padhna aur WhatsApp/SMS jaise apps ke notification se reply karna.
 * Notification sirf phone ki memory mein rehte hain (kahin save ya upload nahi hote).
 */
class RuhiNotif : NotificationListenerService() {

    class Item(
        val key: String,
        val pkg: String,
        val app: String,
        val title: String,
        val text: String,
        val time: Long,
        val reply: Notification.Action?
    )

    companion object {
        @Volatile var instance: RuhiNotif? = null
        private val items = ArrayList<Item>()

        private val known = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.instagram.android" to "Instagram",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.gm" to "Gmail",
            "com.facebook.orca" to "Messenger",
            "com.facebook.katana" to "Facebook",
            "com.google.android.apps.messaging" to "Messages",
            "com.snapchat.android" to "Snapchat"
        )

        fun enabled(c: Context): Boolean {
            val s = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            return s.contains(c.packageName + "/")
        }

        internal fun put(item: Item) {
            synchronized(items) {
                items.removeAll { it.key == item.key }
                items.add(item)
                while (items.size > 40) items.removeAt(0)
            }
        }

        internal fun drop(key: String) {
            synchronized(items) { items.removeAll { it.key == key } }
        }

        fun recent(n: Int, pkg: String?): List<Item> = synchronized(items) {
            items.filter { pkg == null || it.pkg == pkg }.sortedByDescending { it.time }.take(n)
        }

        fun count(pkg: String?): Int = synchronized(items) {
            items.count { pkg == null || it.pkg == pkg }
        }

        fun latestReplyable(pkg: String?): Item? = synchronized(items) {
            items.filter { it.reply != null && (pkg == null || it.pkg == pkg) }.maxByOrNull { it.time }
        }

        fun clearAll() {
            try { instance?.cancelAllNotifications() } catch (e: Exception) { }
            synchronized(items) { items.clear() }
        }

        /** Notification ke reply action ko chalakar jawab bhejta hai. */
        fun sendReply(c: Context, item: Item, text: String): Boolean {
            val act = item.reply ?: return false
            return try {
                val ris = act.remoteInputs ?: return false
                val intent = Intent()
                val b = Bundle()
                for (r in ris) b.putCharSequence(r.resultKey, text)
                RemoteInput.addResultsToIntent(ris, intent, b)
                act.actionIntent.send(c, 0, intent)
                true
            } catch (e: Exception) { false }
        }
    }

    private fun appName(pkg: String): String {
        val k = known[pkg]
        if (k != null) return k
        return try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg.substringAfterLast('.') }
    }

    private fun conv(sbn: StatusBarNotification): Item? {
        val n = sbn.notification ?: return null
        if (sbn.packageName == packageName) return null
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return null
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        val ex = n.extras ?: return null
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        var text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (text.isBlank()) text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return null
        var reply: Notification.Action? = null
        val acts = n.actions
        if (acts != null) {
            for (a in acts) {
                val ri = a.remoteInputs
                if (ri != null && ri.isNotEmpty() && ri[0].allowFreeFormInput) {
                    reply = a
                    break
                }
            }
        }
        return Item(sbn.key, sbn.packageName, appName(sbn.packageName), title.trim(), text.trim(), sbn.postTime, reply)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        val l = try { activeNotifications } catch (e: Exception) { null }
        if (l != null) {
            for (s in l) {
                val it2 = conv(s)
                if (it2 != null) put(it2)
            }
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val it2 = conv(sbn)
        if (it2 != null) put(it2)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        drop(sbn.key)
    }
}
