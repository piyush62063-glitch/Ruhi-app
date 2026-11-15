package com.sumit.ruhi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.hardware.camera2.CameraManager
import android.provider.Settings

object Actions {
    private val norm = listOf(
        "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube", "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp",
        "व्हाट्स एप" to "whatsapp", "व्हाट्सअप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "कॉल" to "call",
        "खोलो" to "kholo", "खोल दो" to "khol do", "मैप्स" to "maps", "बजाओ" to "bajao", "चलाओ" to "chalao",
        "नेविगेशन" to "navigation", "गाना" to "gana"
    )

    fun run(c: Context, raw: String): String? {
        var t = raw.lowercase()
        for ((a, b) in norm) t = t.replace(a, b)
        fun go(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i) }
        fun clean(vararg w: String) = t.replace(Regex("\\b(" + w.joinToString("|") + ")\\b"), " ").replace(Regex("\\s+"), " ").trim()
        val num = Regex("\\+?[0-9][0-9 ]{7,13}").find(t)?.value?.replace(" ", "")
        try {
            if (t.contains("youtube")) {
                val q = clean("youtube", "pe", "par", "mein", "me", "kholo", "open", "chalao", "play", "bajao", "search", "karo", "ka", "ki", "ke", "gana", "song", "songs", "ek")
                go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
                return if (q.isEmpty()) "YouTube khol diya" else "Ye lo Sumit, $q laga diya"
            }
            if (t.contains("whatsapp")) {
                if (num != null) {
                    val m = clean("whatsapp", "pe", "par", "message", "bhejo", "send", "karo", "mein", "me", "ko", "likho").replace(Regex("[+0-9]+"), " ").trim()
                    val full = if (num.length == 10) "91$num" else num.removePrefix("+")
                    go(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$full?text=" + Uri.encode(m))))
                    return "WhatsApp khol diya, bas send daba do"
                }
                val i = c.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (i != null) { go(i); return "WhatsApp khol diya" }
            }
            if (t.contains("navigate") || t.contains("navigation") || t.contains("rasta") || t.contains("maps")) {
                val p = clean("navigate", "navigation", "maps", "map", "rasta", "batao", "karo", "pe", "par", "ko", "jaana", "jana", "chalo", "me", "mein", "lagao", "set", "ka", "ki", "tak", "tk")
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(if (p.isEmpty()) "geo:0,0" else "google.navigation:q=" + Uri.encode(p)))
                i.setPackage("com.google.android.apps.maps"); go(i)
                return if (p.isEmpty()) "Maps khol diya" else "$p ka rasta laga diya"
            }
            if (Regex("\\bcall\\b").containsMatchIn(t) && (num != null || t.contains("karo") || t.contains("laga") || t.contains("kar do"))) {
                go(Intent(Intent.ACTION_DIAL, Uri.parse(if (num != null) "tel:$num" else "tel:")))
                return "Dialer khol diya"
            }
            if (t.contains("torch") || t.contains("flashlight") || t.contains("टॉर्च")) {
                val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val on = !(t.contains("off") || t.contains("band") || t.contains("bujha"))
                cm.setTorchMode(cm.cameraIdList[0], on)
                return if (on) "Torch on kar di" else "Torch off kar di"
            }
            if (t.contains("wifi") || t.contains("wi-fi")) { go(Intent(Settings.ACTION_WIFI_SETTINGS)); return "WiFi settings khol di" }
            if (t.contains("bluetooth")) { go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return "Bluetooth settings khol di" }
            if (t.contains("kholo") || t.contains("open ") || t.contains("khol do")) {
                val n = clean("kholo", "open", "khol", "do", "app", "ko", "please", "plz", "ruhi", "ek")
                if (n.isNotEmpty()) {
                    val pm = c.packageManager
                    val l = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    val a = l.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
                    val i = a?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
                    if (i != null) { go(i); return "$n khol diya" }
                }
            }
        } catch (e: Exception) { return "Ye kaam nahi ho paya Sumit" }
        return null
    }
}
