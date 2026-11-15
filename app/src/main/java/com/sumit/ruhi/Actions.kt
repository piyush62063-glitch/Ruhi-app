package com.sumit.ruhi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

object Actions {
    /** Runs a phone action tag from the AI. Returns an error sentence to speak, or null if fine. */
    fun run(ctx: Context, tag: String): String? {
        val a = tag.split("|").map { it.trim() }
        return try {
            when (a[0].lowercase()) {
                "youtube" -> youtube(ctx, a.getOrElse(1) { "" })
                "call" -> call(ctx, a.getOrElse(1) { "" })
                "whatsapp" -> whatsapp(ctx, a.getOrElse(1) { "" }, a.getOrElse(2) { "" })
                "nav" -> nav(ctx, a.getOrElse(1) { "" })
                "open" -> if (openApp(ctx, a.getOrElse(1) { "" })) null else "Ye app mujhe phone me nahi mila."
                "search" -> if (view(ctx, "https://www.google.com/search?q=" + Uri.encode(a.getOrElse(1) { "" }))) null else "Search nahi khul paaya."
                else -> null
            }
        } catch (e: Exception) {
            "Ye kaam nahi ho paaya, shayad permission band hai."
        }
    }

    private fun go(ctx: Context, i: Intent): Boolean {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun view(ctx: Context, url: String): Boolean = go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun youtube(ctx: Context, q: String): String? {
        if (q.isBlank()) return if (openApp(ctx, "youtube")) null else "YouTube nahi mila."
        val i = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q)
        if (go(ctx, i)) return null
        return if (view(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(q))) null else "YouTube nahi khul paaya."
    }

    private fun call(ctx: Context, who: String): String? {
        if (who.isBlank()) return null
        val digits = who.filter { it.isDigit() || it == '+' }
        val num = if (digits.length >= 6) digits else findNumber(ctx, who)
        if (num == null) return "$who naam ka contact nahi mila."
        val canCall = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val i = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num)))
        return if (go(ctx, i)) null else "Call nahi lag paaya."
    }

    private fun whatsapp(ctx: Context, who: String, msg: String): String? {
        val digits = who.filter { it.isDigit() }
        val num: String? = if (digits.length >= 8) digits else findNumber(ctx, who)
        if (num == null) {
            return if (openApp(ctx, "whatsapp")) "Contact nahi mila, bas WhatsApp khol diya." else "WhatsApp nahi mila."
        }
        var n = num.filter { it.isDigit() }
        if (n.length == 10) n = "91$n"
        val url = "https://wa.me/$n" + (if (msg.isNotBlank()) "?text=" + Uri.encode(msg) else "")
        return if (view(ctx, url)) null else "WhatsApp nahi khul paaya."
    }

    private fun nav(ctx: Context, place: String): String? {
        if (place.isBlank()) return null
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
            .setPackage("com.google.android.apps.maps")
        if (go(ctx, i)) return null
        return if (view(ctx, "geo:0,0?q=" + Uri.encode(place))) null else "Maps nahi khul paaya."
    }

    fun openApp(ctx: Context, name: String): Boolean {
        val q = name.lowercase().trim()
        if (q.isEmpty()) return false
        val alias = mapOf(
            "insta" to "instagram", "fb" to "facebook", "yt" to "youtube", "gpay" to "google pay",
            "snap" to "snapchat", "gmail" to "gmail", "maps" to "maps", "camera" to "camera"
        )
        val want = alias[q] ?: q
        val pm = ctx.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        var best: String? = null
        var score = 0
        for (r in list) {
            val label = r.loadLabel(pm).toString().lowercase()
            val s = when {
                label == want -> 3
                label.startsWith(want) -> 2
                label.contains(want) || (label.length >= 3 && want.contains(label)) -> 1
                else -> 0
            }
            if (s > score) {
                score = s
                best = r.activityInfo.packageName
            }
        }
        val pkg = best ?: return false
        val li = pm.getLaunchIntentForPackage(pkg) ?: return false
        return go(ctx, li)
    }

    private fun findNumber(ctx: Context, name: String): String? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val tries = listOf(name) + name.split(" ").filter { it.length >= 3 }
        for (t in tries) {
            val c = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$t%"), null
            ) ?: continue
            c.use {
                if (it.moveToFirst()) return it.getString(0)
            }
        }
        return null
    }
}
