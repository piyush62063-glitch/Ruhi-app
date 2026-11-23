package com.sumit.ruhi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings

object Actions {
    private val stop = setOf(
        "youtube", "yt", "par", "pe", "me", "mein", "pr", "search", "karo", "kar", "do", "chalao", "chala", "play",
        "gana", "gaana", "song", "songs", "ka", "ki", "ko", "kholo", "open", "sunao", "suna", "mujhe", "ruhi",
        "whatsapp", "message", "bhejo", "bhej", "likho", "navigate", "rasta", "maps", "map", "directions", "route",
        "call", "phone", "laga", "lagao", "to", "ke", "liye", "start", "app", "khol", "please", "yaar"
    )

    private fun clean(t: String) = t.split(" ").filter { it.isNotBlank() && it !in stop }.joinToString(" ")

    private fun go(a: Activity, i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); a.startActivity(i); true
    } catch (e: Exception) { false }

    private fun torch(a: Activity, on: Boolean): Boolean = try {
        val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cm.setTorchMode(id, on); true
    } catch (e: Exception) { false }

    private fun openApp(a: Activity, name: String): Boolean {
        val pm = a.packageManager
        val q = name.trim().lowercase()
        if (q.isEmpty()) return false
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = list.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: list.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return false
        val li = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return false
        return go(a, li)
    }

    /** Phone ka kaam ho to jawab (bolne wala text) deta hai, warna null -> Groq se baat. */
    fun handle(a: Activity, raw: String): String? {
        val t = raw.lowercase().trim()
        val offWords = listOf("off", "band", "bujha", "बंद")
        if (t.contains("torch") || t.contains("flash") || t.contains("टॉर्च")) {
            val on = offWords.none { t.contains(it) }
            return if (torch(a, on)) (if (on) "Torch on kar di" else "Torch band kar di") else "Torch nahi chal paayi"
        }
        if (t.contains("youtube") || t.contains("gana") || t.contains("gaana") || t.startsWith("play ") || t.contains("song")) {
            val q = clean(t)
            val i = if (q.isEmpty()) a.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            else Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
            return if (i != null && go(a, i)) (if (q.isEmpty()) "YouTube khol rahi hu" else "$q YouTube pe dhoondh rahi hu") else "YouTube nahi khul paaya"
        }
        if (t.contains("call") || t.contains("phone laga")) {
            val d = t.filter { it.isDigit() }
            go(a, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$d")))
            return if (d.length >= 5) "Dialer mein number daal diya, call dabao" else "Dialer khol diya, number ya naam chuno"
        }
        if (t.contains("whatsapp")) {
            val m = clean(t)
            val i = if (m.isEmpty()) a.packageManager.getLaunchIntentForPackage("com.whatsapp")
            else Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.whatsapp").putExtra(Intent.EXTRA_TEXT, m)
            return if (i != null && go(a, i)) "WhatsApp khol rahi hu" else "WhatsApp nahi mila"
        }
        if (t.contains("navigate") || t.contains("rasta") || t.contains("maps") || t.contains("directions") || t.contains("route")) {
            val p = clean(t)
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(p))).setPackage("com.google.android.apps.maps")
            val ok = go(a, i) || go(a, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(p))))
            return if (ok) "Maps khol rahi hu" else "Maps nahi khul paaya"
        }
        if (t.contains("wifi") || t.contains("wi-fi")) { go(a, Intent(Settings.ACTION_WIFI_SETTINGS)); return "WiFi settings khol di" }
        if (t.contains("bluetooth")) { go(a, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return "Bluetooth settings khol di" }
        val openWords = listOf("kholo", "khol do", "open", "khol ", "start karo")
        if (openWords.any { t.contains(it) } || t.endsWith("khol")) {
            val n = clean(t)
            return if (openApp(a, n)) "$n khol rahi hu" else "Mujhe $n naam ka app nahi mila"
        }
        return null
    }
}
