package com.sumit.ruhi

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Part 4A ke naye kaam: music control, brightness, DND, SMS, reminders, notes, weather, morning briefing.
 * Yeh sab locked phone par bhi chalte hain (password ke baad). Pehle yahi dekha jata hai, phir Actions.
 */
object Tasks {
    /** true ho to Ruhi jawab ke baad dobara sunegi (jaise SMS ki haan/na ke liye). */
    @Volatile var followUp = false

    private class PendingSms(val num: String, val name: String, val body: String, val at: Long)
    private var pending: PendingSms? = null

    // Hindi bolne par aane wale shabd -> Roman
    private val phrases: List<Pair<String, String>> = listOf(
        "याद दिलाना" to "yaad dilao", "याद दिलाओ" to "yaad dilao", "याद दिला दो" to "yaad dilao",
        "याद दिलाइए" to "yaad dilao", "याद दिला" to "yaad dilao", "रिमाइंडर" to "reminder",
        "गुड मॉर्निंग" to "good morning", "सुप्रभात" to "good morning", "ब्रीफिंग" to "briefing",
        "डू नॉट डिस्टर्ब" to "dnd", "डिस्टर्ब" to "disturb",
        "म्यूज़िक" to "music", "म्यूजिक" to "music", "संगीत" to "music",
        "ब्राइटनेस" to "brightness", "रोशनी" to "brightness",
        "पॉज़" to "pause", "पॉज" to "pause", "पोज" to "pause", "रोको" to "roko", "रुको" to "roko",
        "अगला" to "agla", "अगले" to "agla", "पिछला" to "pichla", "पिछले" to "pichla", "नेक्स्ट" to "next",
        "मौसम" to "mausam", "वेदर" to "weather", "तापमान" to "mausam",
        "एसएमएस" to "sms", "एस एम एस" to "sms", "नोट्स" to "notes", "नोट" to "note",
        "शहर" to "shehar", "सिटी" to "city", "मेरा" to "mera", "मेरी" to "meri"
    )

    private val tokMap = mapOf(
        "कल" to "kal", "परसों" to "parso", "बाद" to "baad", "हाँ" to "haan", "हां" to "haan", "हा" to "haan",
        "नहीं" to "nahi", "नही" to "nahi", "मत" to "mat", "तेज़" to "tez", "तेज" to "tez", "फुल" to "full",
        "मैसेज" to "message", "मेरे" to "mere", "बताओ" to "batao", "पढ़ो" to "padho", "सुनाओ" to "sunao",
        "डिलीट" to "delete", "साफ" to "saaf", "मिटाओ" to "mita", "हटाओ" to "hatao", "भेजो" to "bhejo",
        "कम" to "kam", "बढ़ाओ" to "badhao", "में" to "mein", "को" to "ko", "है" to "hai", "ठीक" to "theek"
    )

    private fun n(raw: String): String {
        var t = " " + Actions.norm(raw) + " "
        for ((d, r) in phrases) t = t.replace(d, " $r ")
        val toks = t.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { tokMap[it] ?: it }
        return toks.joinToString(" ")
    }

    // ---------------------------------------------------------------- helpers
    private fun media(a: Context, code: Int): Boolean = try {
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        true
    } catch (e: Exception) { false }

    @Suppress("DEPRECATION")
    private fun sendSms(a: Context, num: String, body: String): Boolean = try {
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) a.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        val parts = sm.divideMessage(body)
        if (parts.size > 1) sm.sendMultipartTextMessage(num, null, parts, null, null)
        else sm.sendTextMessage(num, null, body, null, null)
        true
    } catch (e: Exception) { false }

    private fun http(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun describe(at: Long): String {
        val now = System.currentTimeMillis()
        val d = at - now
        if (d < 3600000L) return maxOf(1L, d / 60000L).toString() + " minute baad"
        val c1 = Calendar.getInstance()
        val c2 = Calendar.getInstance()
        c2.timeInMillis = at
        val f = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(at))
        val sameDay = c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) && c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
        return if (sameDay) "aaj $f" else "kal $f"
    }

    private fun parseWhen(t: String): Pair<Long, String>? {
        val now = System.currentTimeMillis()
        val w = t.split(" ")
        val rel = Regex("(\\d+)\\s*(second|sec|minute|min|hour|ghante|ghanta)").find(t)
        if (rel != null && (w.contains("baad") || w.contains("mein") || w.contains("me"))) {
            val k = rel.groupValues[1].toLongOrNull() ?: return null
            if (k <= 0L) return null
            val u = rel.groupValues[2]
            val ms = if (u.startsWith("sec")) k * 1000L else if (u.startsWith("min")) k * 60000L else k * 3600000L
            return Pair(now + ms, t.replace(rel.value, " "))
        }
        val ab = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(baje|am|pm)").find(t)
        if (ab != null) {
            var h = ab.groupValues[1].toIntOrNull() ?: return null
            val m = ab.groupValues[2].toIntOrNull() ?: 0
            val unit = ab.groupValues[3]
            if (h > 23 || m > 59) return null
            if ((unit == "pm" || w.contains("shaam") || w.contains("dopahar")) && h in 1..11) h += 12
            if (w.contains("raat") && h in 6..11) h += 12
            if ((unit == "am" || w.contains("subah") || w.contains("raat")) && h == 12) h = 0
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, h)
            c.set(Calendar.MINUTE, m)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (w.contains("kal")) c.add(Calendar.DAY_OF_YEAR, 1)
            else if (w.contains("parso")) c.add(Calendar.DAY_OF_YEAR, 2)
            else if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
            return Pair(c.timeInMillis, t.replace(ab.value, " "))
        }
        return null
    }

    private fun codeText(code: Int): String = when (code) {
        0 -> "aasman saaf hai"
        1, 2, 3 -> "halke badal hain"
        45, 48 -> "kohra hai"
        51, 53, 55, 56, 57 -> "boondabaandi ho rahi hai"
        61, 63, 65, 66, 67 -> "baarish ho rahi hai"
        71, 73, 75, 77 -> "barf gir rahi hai"
        80, 81, 82 -> "baarish ki fuhaar hai"
        95, 96, 99 -> "toofan aane wala hai"
        else -> "mausam normal hai"
    }

    private fun weather(city: String): String {
        val g = JSONObject(http("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name=" + Uri.encode(city)))
        val rs = g.optJSONArray("results")
        if (rs == null || rs.length() == 0) return "$city naam ka shehar nahi mila"
        val r0 = rs.getJSONObject(0)
        val lat = r0.getDouble("latitude")
        val lon = r0.getDouble("longitude")
        val nm = r0.optString("name", city)
        val f = JSONObject(http(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"
        ))
        val cur = f.getJSONObject("current")
        val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
        val feel = Math.round(cur.getDouble("apparent_temperature")).toInt()
        val code = cur.optInt("weather_code", 0)
        val d = f.getJSONObject("daily")
        val mx = Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)).toInt()
        val mn = Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)).toInt()
        val rain = d.optJSONArray("precipitation_probability_max")?.optInt(0, 0) ?: 0
        return "$nm mein abhi $temp degree hai, mehsoos $feel jaisa, " + codeText(code) +
            ". Aaj ka max $mx aur min $mn degree. Baarish ke chance $rain percent."
    }

    private fun briefing(a: Context): String {
        val now = Date()
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hi = if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
        val sb = StringBuilder()
        sb.append(hi).append(" Sumit. Aaj ")
            .append(SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(now))
            .append(" hai, abhi ")
            .append(SimpleDateFormat("h:mm a", Locale.ENGLISH).format(now))
            .append(" ho rahe hain. ")
        val city = Store.city(a)
        if (city.isNotEmpty()) {
            val wt = try { weather(city) } catch (e: Exception) { "" }
            if (wt.isNotEmpty()) sb.append(wt).append(" ")
        } else sb.append("Mausam ke liye bolo, mera shehar, aur shehar ka naam. ")
        try {
            val bm = a.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            sb.append("Battery ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).append(" percent hai. ")
        } catch (e: Exception) { }
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        val today = Store.reminders(a).count { it.second > now.time && it.second <= c.timeInMillis }
        if (today > 0) sb.append("Aaj ke ").append(today).append(" reminder hain.")
        else sb.append("Aaj ka koi reminder nahi hai.")
        return sb.toString()
    }

    // ---------------------------------------------------------------- confirm (SMS)
    fun confirm(a: Context, raw: String): String? {
        val p = pending ?: return null
        pending = null
        if (System.currentTimeMillis() - p.at > 120000L) return null
        val w = n(raw).split(" ")
        val no = listOf("nahi", "mat", "cancel", "roko", "no", "nahin").any { w.contains(it) }
        val yes = listOf("haan", "han", "yes", "bhejo", "bhej", "ok", "okay", "theek", "sahi", "bilkul", "send", "kar").any { w.contains(it) }
        if (no) return "Theek hai, SMS cancel kar diya"
        if (!yes) return null
        return if (sendSms(a, p.num, p.body)) "${p.name} ko SMS bhej diya" else "SMS nahi ja paaya"
    }

    // ---------------------------------------------------------------- sync kaam
    fun handle(a: Context, raw: String, locked: Boolean): String? {
        followUp = false
        val t = n(raw)
        val w = t.split(" ")
        val has = { x: String -> w.contains(x) }
        val anyOf = { xs: List<String> -> xs.any { w.contains(it) } }

        // shehar yaad rakhna (mausam ke liye)
        if ((has("shehar") || has("sheher") || has("city")) && (has("mera") || has("meri") || has("set") || has("badlo"))) {
            val skip = setOf("mera", "meri", "shehar", "sheher", "city", "hai", "hain", "set", "karo", "kar", "do", "ka", "naam",
                "badlo", "ruhi", "ye", "yeh", "mein", "me", "rehta", "rehti", "hu", "hun", "main")
            val c = w.filter { it.isNotBlank() && it !in skip }.joinToString(" ").trim()
            if (c.isEmpty()) return "Shehar ka naam bhi bolo"
            Store.setCity(a, c)
            return "Theek hai, tumhara shehar $c set kar diya"
        }

        // music control
        val music = anyOf(listOf("music", "gana", "gaana", "song", "songs", "gane"))
        if (!has("youtube")) {
            if (has("pause") || (has("roko") && (music || w.size <= 3)) || (music && anyOf(listOf("band", "stop")) && w.size <= 5))
                return if (media(a, KeyEvent.KEYCODE_MEDIA_PAUSE)) "Music pause kar diya" else "Music control nahi ho paaya"
            if ((has("next") || has("agla")) && (music || w.size <= 3))
                return if (media(a, KeyEvent.KEYCODE_MEDIA_NEXT)) "Agla gaana laga diya" else "Music control nahi ho paaya"
            if ((has("pichla") || has("previous") || has("prev")) && (music || w.size <= 3))
                return if (media(a, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) "Pichla gaana laga diya" else "Music control nahi ho paaya"
            if (has("resume") || (music && (has("on") || has("chalu")) && w.size <= 5))
                return if (media(a, KeyEvent.KEYCODE_MEDIA_PLAY)) "Music chala diya" else "Music control nahi ho paaya"
        }

        // brightness
        if (has("brightness")) {
            if (!Settings.System.canWrite(a)) return "Brightness ke liye Ruhi Settings mein Brightness permission allow karni padegi"
            return try {
                val cr = a.contentResolver
                val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
                val up = anyOf(listOf("badhao", "badha", "tez", "zyada", "full", "max", "up"))
                val down = anyOf(listOf("kam", "ghatao", "low", "dim", "down"))
                val nv = if (has("full") || has("max")) 255 else if (up) cur + 70 else if (down) cur - 70 else -1
                if (nv < 0) "Brightness badhani hai ya kam karni hai?"
                else {
                    val v = nv.coerceIn(10, 255)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v)
                    "Brightness " + (v * 100 / 255) + " percent kar di"
                }
            } catch (e: Exception) { "Brightness change nahi ho paayi" }
        }

        // do not disturb
        if (has("dnd") || has("disturb")) {
            val nm = a.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return "Do not disturb ke liye Ruhi Settings mein DND permission allow karo"
            val off = anyOf(listOf("band", "off", "hatao", "khatam"))
            return try {
                nm.setInterruptionFilter(if (off) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                if (off) "Do not disturb band kar diya" else "Do not disturb on kar diya"
            } catch (e: Exception) { "Do not disturb change nahi ho paaya" }
        }

        // SMS
        if (has("sms")) {
            val skip = setOf("sms", "message", "msg", "text", "send", "ruhi")
            val endW = setOf("bhejo", "bhej", "likho", "karo", "kar", "do", "ruhi")
            val toks = w.filter { it.isNotBlank() && it !in skip }.toMutableList()
            while (toks.isNotEmpty() && toks.last() in endW) toks.removeAt(toks.size - 1)
            while (toks.isNotEmpty() && toks.first() in endW) toks.removeAt(0)
            val ki = toks.indexOf("ko")
            if (ki <= 0 || ki >= toks.size - 1) return "Aise bolo: sms bhejo Rahul ko, main aa raha hu"
            val nm = toks.subList(0, ki).joinToString(" ")
            val body = toks.subList(ki + 1, toks.size).joinToString(" ")
            if (a.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                return "SMS bhejne ke liye Ruhi Settings mein SMS permission allow karo"
            val digits = nm.filter { it.isDigit() }
            val num: String? = if (digits.length >= 8) digits else Actions.findNumber(a, nm)
            if (num == null) return "$nm ka number nahi mila"
            pending = PendingSms(num, nm, body, System.currentTimeMillis())
            followUp = true
            return "$nm ko SMS bhejun: $body. Bhejne ke liye haan bolo"
        }

        // reminder
        if (has("reminder") || has("yaad")) {
            val parsed = parseWhen(t)
            if (parsed == null) {
                if (anyOf(listOf("delete", "saaf", "hatao", "cancel", "mita"))) {
                    Reminders.cancelAll(a)
                    return "Saare reminder hata diye"
                }
                if (anyOf(listOf("mere", "kitne", "list", "dikhao", "batao", "kaun"))) {
                    val now = System.currentTimeMillis()
                    val l = Store.reminders(a).filter { it.second > now }.sortedBy { it.second }
                    if (l.isEmpty()) return "Abhi koi reminder nahi hai"
                    val s = l.take(3).joinToString(". ") { describe(it.second) + ", " + it.third }
                    return "Tumhare " + l.size + " reminder hain. " + s
                }
                return "Kab yaad dilau? Jaise bolo: 10 minute baad paani peena yaad dilana, ya 5 baje dawai yaad dilana"
            }
            val drop = setOf("baad", "mein", "me", "baje", "am", "pm", "subah", "shaam", "raat", "dopahar", "kal", "parso", "aaj",
                "yaad", "dilao", "dilana", "dila", "reminder", "remind", "set", "karo", "kar", "do", "mujhe", "ruhi", "mere",
                "liye", "hai", "hain", "ek", "lagao", "laga")
            val toks = parsed.second.split(" ").filter { it.isNotBlank() && it !in drop }.toMutableList()
            while (toks.isNotEmpty() && toks.last() in setOf("ki", "ka", "ke", "ko")) toks.removeAt(toks.size - 1)
            val body = if (toks.isEmpty()) "Reminder" else toks.joinToString(" ")
            Reminders.add(a, parsed.first, body)
            return "Theek hai, " + describe(parsed.first) + " yaad dila dungi: $body"
        }

        // notes
        if (has("note") || has("notes")) {
            if (anyOf(listOf("delete", "saaf", "mita", "hatao"))) {
                Store.clearNotes(a)
                return "Saare notes hata diye"
            }
            if (anyOf(listOf("padho", "sunao", "batao", "dikhao", "kya", "list", "mere"))) {
                val l = Store.notes(a)
                if (l.isEmpty()) return "Abhi koi note nahi hai"
                return "Tumhare " + l.size + " notes hain. " + l.takeLast(5).joinToString(". ")
            }
            val drop = setOf("note", "notes", "karo", "kar", "do", "likho", "likh", "lo", "ruhi", "mera", "ek", "naya", "add",
                "mujhe", "ye", "yeh", "ki", "ke")
            val body = w.filter { it.isNotBlank() && it !in drop }.joinToString(" ").trim()
            if (body.isEmpty()) return "Note mein kya likhu?"
            Store.addNote(a, body)
            return "Note likh liya: $body"
        }
        return null
    }

    // ---------------------------------------------------------------- internet wale kaam (background thread)
    fun async(a: Context, raw: String, locked: Boolean, cb: (String) -> Unit): Boolean {
        val t = n(raw)
        val w = t.split(" ")
        val isWeather = w.contains("weather") || w.contains("mausam")
        val isBrief = t.contains("good morning") || w.contains("briefing") || t.contains("subah ho gayi")
        if (!isWeather && !isBrief) return false
        val ctx = a.applicationContext
        if (isWeather && !isBrief) {
            val skip = setOf("weather", "mausam", "kaisa", "kaisi", "hai", "hain", "ka", "ki", "ke", "ko", "mein", "me", "aaj", "abhi",
                "batao", "bata", "bataiye", "kya", "hoga", "aur", "the", "ruhi", "mujhe", "tell", "is", "today", "in", "of", "please",
                "yaar", "dikhao", "pata", "karo", "kar", "do", "temperature", "tapman", "garmi", "sardi", "hota", "rahega")
            var city = w.filter { it.isNotBlank() && it !in skip }.joinToString(" ").trim()
            if (city.isEmpty()) city = Store.city(ctx)
            if (city.isEmpty()) {
                cb("Kaunse shehar ka mausam batau? Pehle bolo: mera shehar, aur shehar ka naam")
                return true
            }
            val c0 = city
            Thread {
                val out = try { weather(c0) } catch (e: Exception) { "Mausam abhi nahi mil paaya, internet check karo" }
                cb(out)
            }.start()
            return true
        }
        Thread {
            val out = try { briefing(ctx) } catch (e: Exception) { "Good morning Sumit" }
            cb(out)
        }.start()
        return true
    }
}
