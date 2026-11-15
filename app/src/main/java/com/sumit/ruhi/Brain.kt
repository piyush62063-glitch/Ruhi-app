package com.sumit.ruhi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Brain {
    private val models = listOf("gemini-flash-lite-latest", "gemini-flash-latest")
    private val ttsModels = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts")

    private fun post(model: String, key: String, body: String, readMs: Int): Pair<Int, String> {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10000
        c.readTimeout = readMs
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val s = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return code to s
    }

    private fun extract(s: String): String {
        val parts = JSONObject(s).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text", "") ?: "")
        return sb.toString().trim()
    }

    fun ask(key: String, name: String, hist: List<Pair<Boolean, String>>): String {
        if (key.isBlank()) return "Pehle Settings mein apni Gemini key daalo, phir baat karte hain."
        val contents = JSONArray()
        hist.takeLast(8).forEach { (isUser, text) ->
            contents.put(JSONObject()
                .put("role", if (isUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", text))))
        }
        val sys = "Tum Ruhi ho, $name ki pyaari, caring aur masti wali dost aur AI assistant. " +
            "Ladki ki tarah, Hinglish mein, apnepan se baat karo, kabhi kabhi $name naam se bulao. " +
            "Jawab 1-2 chhoti line mein do, thoda mazaak aur care ke saath. " +
            "Jawab bolke sunaya jayega, isliye markdown, bullet aur emoji bilkul mat use karo. " +
            "Phone ke kaam (youtube, call, whatsapp, maps, app kholna) tum kar sakti ho, user ko bolne ko kaho."
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", contents).toString()
        var last = 0
        for (pass in 0..1) {
            for (m in models) {
                try {
                    val (code, s) = post(m, key, body, 20000)
                    last = code
                    if (code == 200) {
                        val t = extract(s)
                        if (t.isNotBlank()) return t
                        last = -2
                    } else if (code == 400 || code == 403) {
                        return "Key ya request mein dikkat hai (error $code). Settings mein key check karo."
                    }
                } catch (e: IOException) {
                    last = -1
                } catch (e: Exception) {
                    last = -3
                }
            }
        }
        return when (last) {
            -1 -> "Internet nahi mil raha. Net check karke phir bolo."
            -2, -3 -> "Gemini ne jawab nahi diya, ek baar phir bolo."
            else -> "Gemini abhi busy hai (error $last). Thodi der baad phir bolo."
        }
    }

    /** Gemini TTS, voice Kore. Returns raw 16-bit mono PCM at 24000 Hz, or null on failure. */
    fun speechPcm(key: String, text: String): ByteArray? {
        if (key.isBlank()) return null
        val say = "Say in a soft, warm, friendly tone: " + text.take(400)
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", say)))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Kore")))))
            .toString()
        for (m in ttsModels) {
            try {
                val (code, s) = post(m, key, body, 30000)
                if (code == 200) {
                    val d = JSONObject(s).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                        ?.optJSONArray("parts")?.optJSONObject(0)?.optJSONObject("inlineData")?.optString("data", "") ?: ""
                    if (d.isNotBlank()) return Base64.decode(d, Base64.DEFAULT)
                }
            } catch (e: Exception) { }
        }
        return null
    }
}

object Actions {
    private fun go(c: Context, i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); true
    } catch (e: Exception) { false }

    private fun clean(t: String, drop: List<String>) =
        t.split(Regex("\\s+")).filter { it !in drop }.joinToString(" ").trim()

    private fun has(t: String, vararg k: String) = k.any { t.contains(it) }

    fun handle(c: Context, raw: String): String? {
        val t = raw.lowercase().trim()
        val words = t.split(Regex("\\s+"))
        val num = Regex("\\+?\\d[\\d\\s-]{8,}\\d").find(t)?.value?.replace(Regex("[\\s-]"), "")
        val filler = listOf("pe", "par", "पर", "on", "mein", "में", "ka", "का", "ki", "की", "ke", "के", "ko", "को",
            "karo", "करो", "kar", "do", "दो", "hai", "है", "please", "plz", "ruhi", "रुही")

        if (has(t, "whatsapp", "व्हाट्सएप", "वाट्सएप", "व्हाट्सअप")) {
            if (num != null) {
                val n = if (num.length == 10) "91$num" else num.removePrefix("+")
                val i = listOf("message", "मैसेज", "msg", "likho", "लिखो").map { t.indexOf(it) }.filter { it >= 0 }.minOrNull()
                val msg = if (i != null) t.substring(i).split(" ").drop(1).joinToString(" ")
                    .replace("bhejo", "").replace("भेजो", "").trim() else ""
                return if (go(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n?text=" + Uri.encode(msg)))))
                    "WhatsApp khol diya, bas send daba do." else "WhatsApp nahi khul paya."
            }
            val i = c.packageManager.getLaunchIntentForPackage("com.whatsapp")
            return if (i != null && go(c, i)) "WhatsApp khol diya." else "WhatsApp install nahi mila."
        }
        if (has(t, "youtube", "यूट्यूब", "यूट्यब")) {
            val q = clean(t, filler + listOf("youtube", "यूट्यूब", "यूट्यब", "search", "सर्च", "play", "चलाओ", "chalao",
                "chala", "gana", "गाना", "song", "खोलो", "kholo", "open", "ओपन"))
            val i = if (q.isBlank()) c.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            else Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
            return if (i != null && go(c, i)) (if (q.isBlank()) "YouTube khol diya." else "YouTube pe \"$q\" dhoond rahi hoon.")
            else "YouTube nahi khul paya."
        }
        if (has(t, "call", "कॉल", "फोन", "phone")) {
            if (num == null) return "Kisko call karna hai? Number bolo, contact name abhi support nahi hai."
            val ok = c.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            val i = Intent(if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num"))
            return if (go(c, i)) "Call laga rahi hoon." else "Call nahi lag paya."
        }
        if (has(t, "navigate", "navigation", "नेविगेट", "रास्ता", "rasta", "direction", "map", "मैप")) {
            val d = clean(t, filler + listOf("navigate", "navigation", "नेविगेट", "रास्ता", "rasta", "directions", "direction",
                "map", "maps", "मैप", "dikhao", "दिखाओ", "chalo", "चलो", "jana", "जाना", "tak", "तक", "se", "liye", "लिए",
                "set", "start", "shuru", "open", "kholo", "खोलो"))
            if (d.isBlank()) return "Kahan jana hai? Jagah ka naam bolo."
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(d))).setPackage("com.google.android.apps.maps")
            return if (go(c, i) || go(c, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(d)))))
                "$d ka rasta khol diya." else "Maps nahi khul paya."
        }
        if (words.size <= 4 && has(t, "open", "kholo", "खोलो", "खोल", "ओपन")) {
            val n = clean(t, filler + listOf("open", "kholo", "खोलो", "खोल", "ओपन", "app", "एप"))
            if (n.isBlank()) return null
            val pm = c.packageManager
            val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
                ?: return "\"$n\" naam ka app nahi mila. App ka naam English mein bolo."
            val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
            return if (i != null && go(c, i)) "${hit.loadLabel(pm)} khol diya." else "App nahi khul paya."
        }
        return null
    }
}
