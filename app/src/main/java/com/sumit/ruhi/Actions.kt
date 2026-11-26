package com.sumit.ruhi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings

object Actions {
    // Hindi (Devanagari) bolne par voice engine Devanagari text deta hai, isliye pehle Roman mein badalte hain.
    private val dict: List<Pair<String, String>> = listOf(
        "यू ट्यूब" to "youtube", "यूट्यूब" to "youtube", "यूटयूब" to "youtube", "यूट्यब" to "youtube",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "वॉट्सएप" to "whatsapp",
        "व्हाट्स एप" to "whatsapp", "वाट्सएप" to "whatsapp", "व्हाट्सअप" to "whatsapp",
        "फ्लैशलाइट" to "torch", "फ़्लैशलाइट" to "torch", "टॉर्च" to "torch", "टोर्च" to "torch", "टार्च" to "torch", "फ्लैश" to "torch",
        "वाई-फाई" to "wifi", "वाई फाई" to "wifi", "वाईफाई" to "wifi", "वाइफाई" to "wifi", "वायफाय" to "wifi",
        "ब्लू टूथ" to "bluetooth", "ब्लूटूथ" to "bluetooth", "ब्लूटुथ" to "bluetooth",
        "मोबाइल डेटा" to "internet", "इंटरनेट" to "internet", "डेटा" to "internet",
        "नेविगेशन" to "navigate", "नेविगेट" to "navigate", "डायरेक्शन" to "navigate", "रास्ता" to "navigate",
        "रस्ता" to "navigate", "नक्शा" to "navigate", "मैप्स" to "navigate", "मैप" to "navigate",
        "सॉन्ग" to "song", "सोंग" to "song", "गाने" to "song", "गाना" to "song", "गीत" to "song",
        "चला दो" to "chalao", "चलाइए" to "chalao", "चलाओ" to "chalao",
        "खोल दो" to "kholo", "खोलिए" to "kholo", "खोलो" to "kholo", "खोल" to "kholo",
        "बन्द" to "band", "बंद" to "band", "ऑफ़" to "band", "ऑफ" to "band",
        "चालू" to "on", "ऑन" to "on", "जलाओ" to "on",
        "कॉल" to "call", "फ़ोन" to "call", "फोन" to "call",
        "सर्च" to "search", "प्ले" to "play",
        "युटुब" to "youtube", "यूटूब" to "youtube", "यूटुब" to "youtube", "युट्यूब" to "youtube", "यू-ट्यूब" to "youtube",
        "यूट्यूब" to "youtube", "यु ट्यूब" to "youtube", "यू टयूब" to "youtube", "युटूब" to "youtube",
        "ओपन" to "open", "ओपेन" to "open", "औपन" to "open", "ऑपन" to "open", "ओपण" to "open",
        "स्टार्ट" to "start", "चालू करो" to "open", "शुरू करो" to "open", "शुरू" to "start",
        "वॉट्स एप" to "whatsapp", "वाट्स एप" to "whatsapp", "व्हाट्सेप" to "whatsapp", "वाट्सऐप" to "whatsapp",
        "अलार्म" to "alarm", "अलार्म" to "alarm", "टाइमर" to "timer", "बैटरी" to "battery", "वॉल्यूम" to "volume",
        "आवाज़" to "volume", "आवाज" to "volume", "बढ़ाओ" to "badhao", "बढ़ा दो" to "badhao", "घटाओ" to "ghatao",
        "कम करो" to "kam", "म्यूट" to "mute", "कितने बजे" to "kitne baje", "बजे" to "baje", "सुबह" to "subah",
        "शाम" to "shaam", "रात" to "raat", "दोपहर" to "dopahar", "टाइम" to "time", "समय" to "time",
        "तारीख़" to "date", "तारीख" to "date", "मिनट" to "minute", "सेकंड" to "second", "सेकेंड" to "second",
        "घंटे" to "hour", "घंटा" to "hour", "ढूंढो" to "search", "ढूँढो" to "search", "खोजो" to "search"
    )

    private val appAlias = mapOf(
        "इंस्टाग्राम" to "instagram", "फेसबुक" to "facebook", "कैमरा" to "camera", "गैलरी" to "gallery",
        "सेटिंग" to "settings", "सेटिंग्स" to "settings", "क्रोम" to "chrome", "स्नैपचैट" to "snapchat",
        "टेलीग्राम" to "telegram", "गूगल" to "google", "जीमेल" to "gmail", "फोनपे" to "phonepe",
        "पेटीएम" to "paytm", "कैलकुलेटर" to "calculator", "घड़ी" to "clock", "म्यूजिक" to "music",
        "प्ले स्टोर" to "play store", "स्पॉटिफाई" to "spotify", "मैसेज" to "messages", "संदेश" to "messages",
        "इंस्टा" to "instagram", "इन्स्टाग्राम" to "instagram", "इंस्टाग्रम" to "instagram", "फ़ेसबुक" to "facebook", "फेसबुक" to "facebook", "कॅमेरा" to "camera", "कैमेरा" to "camera", "फाइल" to "files", "कैलेंडर" to "calendar", "ट्विटर" to "x", "नेटफ्लिक्स" to "netflix"
    )

    private val stop = setOf(
        "youtube", "yt", "par", "pe", "me", "mein", "pr", "search", "karo", "kar", "do", "chalao", "chala", "play",
        "gana", "gaana", "song", "songs", "ka", "ki", "ko", "kholo", "open", "sunao", "suna", "mujhe", "ruhi",
        "whatsapp", "message", "bhejo", "bhej", "likho", "navigate", "rasta", "maps", "map", "directions", "route",
        "call", "phone", "laga", "lagao", "to", "ke", "liye", "start", "app", "khol", "please", "yaar",
        "on", "band", "off", "se", "hai", "jana", "chalo", "le", "ek", "zara", "jara", "wala", "wali",
        "पर", "में", "का", "की", "को", "के", "लिए", "से", "तो", "हो", "है", "जाना", "चलो", "ले", "मुझे", "रुही",
        "करना", "करें", "कीजिए", "कीजिये", "करिए", "करियो", "कर दो", "open", "ओपन", "प्लीज", "यार", "ज़रा", "जरा", "एक", "वाला", "वाली", "करो", "कर", "दो", "ऐप", "एप", "ऐप्प", "app"
    )

    internal fun norm(raw: String): String {
        var t = " " + raw.lowercase().trim() + " "
        for ((d, r) in dict) t = t.replace(d, " $r ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    internal fun clean(t: String) = t.split(" ").filter { it.isNotBlank() && it !in stop }.joinToString(" ")

    // ---------- Devanagari -> Roman (app / contact naam match karne ke liye)
    private val cons = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d",
        'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y",
        'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
    )
    private val indep = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ए' to "e", 'ऐ' to "ai",
        'ओ' to "o", 'औ' to "au", 'ऑ' to "o"
    )
    private val matra = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'े' to "e", 'ै' to "ai",
        'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ं' to "n", 'ँ' to "n", 'ः' to "h"
    )

    private fun translit(s: String): String {
        val o = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val cv = cons[c]
            if (cv != null) {
                o.append(cv)
                var n = if (i + 1 < s.length) s[i + 1] else ' '
                if (n == '\u093C') { i++; n = if (i + 1 < s.length) s[i + 1] else ' ' }
                if (matra.containsKey(n) || n == '\u094D') { /* aage handle hoga */ }
                else if (n.isLetter()) o.append("a")
            } else if (indep.containsKey(c)) o.append(indep[c])
            else if (matra.containsKey(c)) o.append(matra[c])
            else if (c == '\u094D' || c == '\u093C') { }
            else o.append(c)
            i++
        }
        return o.toString()
    }

    internal fun skel(s: String): String {
        val r = translit(s.lowercase()).replace("w", "v").replace("c", "k").replace("q", "k")
            .filter { it.isLetter() && it !in "aeiouyh" }
        val b = StringBuilder()
        for (ch in r) if (b.isEmpty() || b.last() != ch) b.append(ch)
        return b.toString()
    }

    private fun go(a: Context, i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); a.startActivity(i); true
    } catch (e: Exception) { false }

    private fun torch(a: Context, on: Boolean): Boolean = try {
        val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cm.setTorchMode(id, on); true
    } catch (e: Exception) { false }

    private fun openApp(a: Context, name0: String): Boolean {
        val pm = a.packageManager
        var q = name0.trim().lowercase()
        if (q.isEmpty()) return false
        for ((d, r) in appAlias) q = q.replace(d, r)
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val labels = list.map { it.loadLabel(pm).toString().lowercase() }
        var idx = labels.indexOfFirst { it == q }
        if (idx < 0) idx = labels.indexOfFirst { it.contains(q) }
        if (idx < 0) {
            val sq = skel(q)
            if (sq.length >= 2) {
                idx = labels.indexOfFirst { skel(it) == sq }
                if (idx < 0 && sq.length >= 3) idx = labels.indexOfFirst { val sl = skel(it); sl.isNotEmpty() && (sl.contains(sq) || sq.contains(sl)) }
            }
        }
        if (idx < 0) return false
        val li = pm.getLaunchIntentForPackage(list[idx].activityInfo.packageName) ?: return false
        return go(a, li)
    }

    internal fun findNumber(a: Context, name: String): String? {
        if (a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val q = name.lowercase().trim()
        if (q.isEmpty()) return null
        val sq = skel(q)
        var best: String? = null
        var bestScore = 0
        try {
            val c = a.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )
            c?.use {
                while (it.moveToNext()) {
                    val n = (it.getString(0) ?: "").lowercase()
                    val num = it.getString(1) ?: continue
                    val s = skel(n)
                    var sc = 0
                    if (n == q) sc = 100
                    else if (n.contains(q)) sc = 80
                    else if (s.isNotEmpty() && sq.isNotEmpty() && s == sq) sc = 70
                    else if (sq.length >= 3 && s.isNotEmpty() && (s.startsWith(sq) || sq.startsWith(s))) sc = 50
                    if (sc > bestScore) { bestScore = sc; best = num }
                }
            }
        } catch (e: Exception) { }
        return best
    }

    private fun panel(a: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 29) go(a, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) else go(a, Intent(Settings.ACTION_WIFI_SETTINGS))


    /** Phone locked ho to jo kaam screen/app kholte hain (unlock chahiye) unhe pehchanta hai. */
    fun needsUnlock(raw: String): Boolean {
        val t = norm(raw)
        val w = t.split(" ")
        val has = { x: String -> w.contains(x) }
        return has("youtube") || has("song") || has("gana") || t.startsWith("play ") || has("whatsapp") ||
            has("navigate") || has("rasta") || has("directions") || has("route") || has("maps") || has("map") ||
            has("search") || has("dhundo") || has("khojo") || has("dhoondo") || has("kholo") || has("open") ||
            has("khol") || has("start") || has("wifi") || has("internet") || has("bluetooth")
    }

    /** Phone ka kaam ho to jawab (bolne wala text) deta hai, warna null -> Groq se baat. */
    fun handle(a: Context, raw: String): String? {
        val t = norm(raw)
        val has = { w: String -> t.split(" ").contains(w) }

        if (has("alarm")) {
            val nums = Regex("\\d+").findAll(t).map { it.value.toIntOrNull() ?: 0 }.toList()
            if (nums.isEmpty()) return if (go(a, Intent(AlarmClock.ACTION_SHOW_ALARMS))) "Alarm khol diye" else "Alarm nahi khule"
            var h = nums[0]
            val m = if (nums.size > 1) nums[1] else 0
            if ((has("shaam") || has("raat") || has("pm") || has("dopahar")) && h in 1..11) h += 12
            if (has("raat") && h == 12) h = 0
            if (h > 23 || m > 59) return "Samay samajh nahi aaya, dobara bolo"
            val ai = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            return if (go(a, ai)) "Alarm " + h + ":" + (if (m < 10) "0$m" else "$m") + " ka laga diya" else "Alarm nahi lag paaya"
        }
        if (has("timer")) {
            val n = Regex("\\d+").find(t)?.value?.toIntOrNull()
            if (n == null || n <= 0) return if (go(a, Intent(AlarmClock.ACTION_SET_TIMER))) "Timer khol diya" else "Timer nahi khula"
            val secs = if (has("hour") || has("ghante") || has("ghanta")) n * 3600 else if (has("second") || has("sec")) n else n * 60
            val ti = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            return if (go(a, ti)) "Timer laga diya" else "Timer nahi lag paaya"
        }
        if (has("battery")) {
            return try {
                val bm = a.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                "Battery abhi " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " percent hai"
            } catch (e: Exception) { "Battery ka pata nahi chala" }
        }
        if (has("volume")) {
            val up = has("badhao") || has("badha") || has("up") || has("zyada") || has("increase") || has("full")
            val down = has("kam") || has("ghatao") || has("down") || has("decrease") || has("low")
            if (!up && !down && !has("mute")) return "Volume badhana hai ya kam karna?"
            return try {
                val au = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val dir = if (up) AudioManager.ADJUST_RAISE else if (down) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_MUTE
                val times = if (has("full")) 15 else if (has("mute")) 1 else 3
                for (k in 0 until times) au.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, if (k == 0) AudioManager.FLAG_SHOW_UI else 0)
                if (up) "Volume badha diya" else if (down) "Volume kam kar diya" else "Mute kar diya"
            } catch (e: Exception) { "Volume change nahi ho paaya" }
        }
        if (has("time") || t.contains("kitne baje") || t.contains("kya baja")) {
            val f = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH)
            return "Abhi " + f.format(java.util.Date()) + " ho rahe hain"
        }
        if (has("date") || has("tarikh") || t.contains("aaj kya din") || t.contains("aaj kaun sa din")) {
            val f = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH)
            return "Aaj " + f.format(java.util.Date()) + " hai"
        }

        if (has("torch")) {
            val on = !has("band")
            return if (torch(a, on)) (if (on) "Torch on kar di" else "Torch band kar di") else "Torch nahi chal paayi"
        }
        if (has("wifi") || has("internet")) {
            return if (panel(a)) "Internet panel khol diya, wahan se WiFi ya data on off kar lo" else "Settings nahi khul paayi"
        }
        if (has("bluetooth")) {
            return if (go(a, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) "Bluetooth settings khol di, wahan se on off kar lo" else "Bluetooth settings nahi khuli"
        }
        if (has("youtube") || has("song") || has("gana") || t.startsWith("play ")) {
            val q = clean(t)
            val i = if (q.isEmpty()) a.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            else Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
            return if (i != null && go(a, i)) (if (q.isEmpty()) "YouTube khol rahi hu" else "$q YouTube pe dhoondh rahi hu") else "YouTube nahi khul paaya"
        }
        if (has("call") || t.contains("phone laga")) {
            val d = t.filter { it.isDigit() }
            var num: String? = if (d.length >= 5) d else null
            val name = clean(t)
            if (num == null && name.isNotEmpty()) num = findNumber(a, name)
            if (num == null) {
                go(a, Intent(Intent.ACTION_DIAL))
                return if (name.isNotEmpty()) "$name ka number nahi mila, dialer khol diya" else "Dialer khol diya, number ya naam chuno"
            }
            val ok = if (a.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                go(a, Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))))
            else go(a, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))
            return if (ok) (if (name.isNotEmpty()) "$name ko call laga rahi hu" else "Call laga rahi hu") else "Call nahi lag paayi"
        }
        if (has("whatsapp")) {
            val skipW = setOf("whatsapp", "message", "msg", "मैसेज")
            val toks = t.split(" ").filter { it.isNotBlank() && it !in skipW }.toMutableList()
            val endW = setOf("bhejo", "bhej", "likho", "karo", "kar", "do", "par", "pe", "mein", "me", "ruhi", "भेजो", "भेज", "लिखो", "करो", "कर", "दो", "पर", "पे", "में", "रुही")
            while (toks.isNotEmpty() && toks.last() in endW) toks.removeAt(toks.size - 1)
            val ki = toks.indexOfFirst { it == "ko" || it == "को" }
            if (ki > 0 && ki < toks.size - 1) {
                val nm = toks.subList(0, ki).filter { it !in stop }.joinToString(" ")
                val body = toks.subList(ki + 1, toks.size).joinToString(" ")
                val found = if (nm.isNotEmpty()) findNumber(a, nm) else null
                if (found != null) {
                    var dg = found.filter { it.isDigit() }
                    if (dg.length == 10) dg = "91" + dg
                    val wi = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + dg + "?text=" + Uri.encode(body))).setPackage("com.whatsapp")
                    return if (go(a, wi)) "$nm ko message likh diya, bas send dabana" else "WhatsApp nahi khul paaya"
                }
            }
            val m = clean(t)
            val i = if (m.isEmpty()) a.packageManager.getLaunchIntentForPackage("com.whatsapp")
            else Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.whatsapp").putExtra(Intent.EXTRA_TEXT, m)
            return if (i != null && go(a, i)) "WhatsApp khol rahi hu" else "WhatsApp nahi mila"
        }
        if (has("navigate") || has("rasta") || has("directions") || has("route") || has("maps") || has("map")) {
            val p = clean(t)
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(p)))
            i.setPackage("com.google.android.apps.maps")
            val ok = go(a, i) || go(a, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(p))))
            return if (ok) (if (p.isEmpty()) "Maps khol rahi hu" else "$p ka rasta Maps mein laga rahi hu") else "Maps nahi khul paaya"
        }
        if (has("search") || has("dhundo") || has("khojo") || has("dhoondo")) {
            val q = clean(t).split(" ").filter { it.isNotBlank() && it != "google" && it != "dhundo" && it != "khojo" && it != "dhoondo" }.joinToString(" ")
            if (q.isEmpty()) return "Kya search karu?"
            return if (go(a, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))) "$q Google pe dhoondh rahi hu" else "Search nahi khul paayi"
        }
        if (has("kholo") || has("open") || has("khol") || has("start")) {
            val n = clean(t)
            if (n.isEmpty()) return "Kaunsa app kholu?"
            return if (openApp(a, n)) "$n khol rahi hu" else "Mujhe $n naam ka app nahi mila"
        }
        return null
    }
}
