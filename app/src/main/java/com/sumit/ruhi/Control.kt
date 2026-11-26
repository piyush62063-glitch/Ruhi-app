package com.sumit.ruhi

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Part 4B ke voice commands: phone control (Accessibility) aur notifications.
 * Order: Tasks -> Control -> Actions -> Groq. Jo command yahan ka nahi, wo false lautata hai.
 */
object Control {
    /** true ho to Ruhi jawab ke baad dobara sunegi (jaise reply ki haan/na ke liye). */
    @Volatile var followUp = false

    private class PendingReply(val item: RuhiNotif.Item, val body: String, val at: Long)
    private var pending: PendingReply? = null

    private class Cmd(val orig: List<String>, val w: List<String>)

    private const val NEED_ACC = "Ye kaam ke liye Ruhi ki Accessibility service on karni hogi. Ruhi Settings mein Accessibility allow karo"
    private const val NEED_NOTIF = "Ye kaam ke liye Ruhi Settings mein Notification access allow karo"

    // Lambe, saaf shabd: seedha replace kiya ja sakta hai
    private val pre: List<Pair<String, String>> = listOf(
        "\u0935\u093E\u0908-\u092B\u093E\u0908" to "wifi", "\u0935\u093E\u0908 \u092B\u093E\u0908" to "wifi",
        "\u0935\u093E\u0908\u092B\u093E\u0908" to "wifi", "\u0935\u093E\u0907\u092B\u093E\u0907" to "wifi",
        "\u0935\u093E\u092F\u092B\u093E\u092F" to "wifi", "wi-fi" to "wifi", "wi fi" to "wifi",
        "\u092C\u094D\u0932\u0942 \u091F\u0942\u0925" to "bluetooth", "\u092C\u094D\u0932\u0942\u091F\u0942\u0925" to "bluetooth",
        "\u092C\u094D\u0932\u0942\u091F\u0941\u0925" to "bluetooth", "blue tooth" to "bluetooth",
        "\u0935\u094D\u0939\u093E\u091F\u094D\u0938\u090F\u092A" to "whatsapp", "\u0935\u094D\u0939\u093E\u091F\u094D\u0938\u0910\u092A" to "whatsapp",
        "\u0935\u0949\u091F\u094D\u0938\u0910\u092A" to "whatsapp", "\u0935\u0949\u091F\u094D\u0938\u090F\u092A" to "whatsapp",
        "\u0935\u094D\u0939\u093E\u091F\u094D\u0938 \u090F\u092A" to "whatsapp", "\u0935\u093E\u091F\u094D\u0938\u090F\u092A" to "whatsapp",
        "\u0935\u094D\u0939\u093E\u091F\u094D\u0938\u0905\u092A" to "whatsapp", "\u0935\u0949\u091F\u094D\u0938 \u090F\u092A" to "whatsapp"
    )

    // Chhote shabd: sirf poore token par badalte hain (taaki doosre shabd na bigdein)
    private val tokMap: Map<String, String> = mapOf(
        "\u0938\u094D\u0915\u094D\u0930\u0940\u0928\u0936\u0949\u091F" to "screenshot", "\u0938\u094D\u0915\u094D\u0930\u0940\u0928" to "screen", "\u0936\u0949\u091F" to "shot",
        "\u0938\u094D\u0915\u094D\u0930\u0949\u0932" to "scroll", "\u0938\u094D\u0915\u094D\u0930\u094B\u0932" to "scroll",
        "\u090A\u092A\u0930" to "upar", "\u0909\u092A\u0930" to "upar", "\u0928\u0940\u091A\u0947" to "neeche",
        "\u0928\u094B\u091F\u093F\u092B\u093F\u0915\u0947\u0936\u0928" to "notification", "\u0928\u094B\u091F\u093F\u092B\u093F\u0915\u0947\u0936\u0902\u0938" to "notification",
        "\u0928\u094B\u091F\u093F\u092B\u093C\u093F\u0915\u0947\u0936\u0928" to "notification", "\u0928\u094B\u091F\u093F\u092B\u093F\u0915\u0947\u0936\u0928\u094D\u0938" to "notification",
        "\u0930\u093F\u092A\u094D\u0932\u093E\u0908" to "reply", "\u0930\u093F\u092A\u094D\u0932\u0948" to "reply",
        "\u091C\u0935\u093E\u092C" to "jawab", "\u091C\u092C\u093E\u092C" to "jawab",
        "\u0930\u0940\u0938\u0947\u0902\u091F" to "recent", "\u0930\u093F\u0938\u0947\u0902\u091F" to "recent", "\u0930\u0940\u0938\u0947\u0902\u091F\u094D\u0938" to "recents",
        "\u0915\u094D\u0935\u093F\u0915" to "quick", "\u0938\u0947\u091F\u093F\u0902\u0917" to "settings", "\u0938\u0947\u091F\u093F\u0902\u0917\u094D\u0938" to "settings",
        "\u0932\u0949\u0915" to "lock", "\u0905\u0928\u0932\u0949\u0915" to "unlock",
        "\u0935\u093E\u092A\u0938" to "back", "\u0935\u093E\u092A\u093F\u0938" to "back", "\u092A\u0940\u091B\u0947" to "back", "\u092C\u0948\u0915" to "back",
        "\u0939\u094B\u092E" to "home",
        "\u091F\u0948\u092A" to "tap", "\u0915\u094D\u0932\u093F\u0915" to "click",
        "\u0926\u092C\u093E\u0913" to "dabao", "\u0926\u092C\u093E" to "dabao", "\u0926\u092C\u093E\u0907\u090F" to "dabao",
        "\u092A\u095D\u094B" to "padho", "\u092A\u0922\u093C\u094B" to "padho", "\u092A\u0922\u094B" to "padho", "\u092A\u0922\u093C\u0915\u0930" to "padho", "\u092A\u0922\u093C" to "padho",
        "\u0938\u0941\u0928\u093E\u0913" to "sunao", "\u092C\u0924\u093E\u0913" to "batao", "\u092C\u0924\u093E\u0907\u090F" to "batao",
        "\u0926\u093F\u0916\u093E\u0913" to "dikhao", "\u0926\u093F\u0916\u093E" to "dikha",
        "\u092E\u0948\u0938\u0947\u091C" to "message", "\u092E\u0947\u0938\u0947\u091C" to "message", "\u0938\u0902\u0926\u0947\u0936" to "message",
        "\u0928\u092F\u093E" to "naya", "\u0928\u090F" to "naya", "\u0928\u092F\u0940" to "naya",
        "\u0915\u094C\u0928" to "kaun", "\u0915\u094B\u0928" to "kaun", "\u0915\u094D\u092F\u093E" to "kya",
        "\u0906\u092F\u093E" to "aaya", "\u0906\u090F" to "aaya", "\u0906\u0908" to "aaya",
        "\u0938\u093E\u092B" to "saaf", "\u0915\u094D\u0932\u093F\u092F\u0930" to "clear", "\u0939\u091F\u093E\u0913" to "hatao", "\u092E\u093F\u091F\u093E\u0913" to "mita",
        "\u092D\u0947\u091C\u094B" to "bhejo", "\u0932\u093F\u0916\u094B" to "likho",
        "\u0915\u0930\u094B" to "karo", "\u0915\u0930\u0928\u093E" to "karo", "\u0915\u0940\u091C\u093F\u090F" to "karo", "\u0915\u0930\u0947\u0902" to "karo", "\u0915\u0930\u093F\u090F" to "karo",
        "\u0915\u0930" to "kar", "\u0926\u094B" to "do",
        "\u0916\u094B\u0932\u094B" to "kholo", "\u0916\u094B\u0932" to "kholo", "\u0916\u094B\u0932\u093F\u090F" to "kholo",
        "\u091A\u093E\u0932\u0942" to "on", "\u091A\u093E\u0932\u0941" to "on", "\u0911\u0928" to "on", "\u0936\u0941\u0930\u0942" to "on",
        "\u092C\u0902\u0926" to "band", "\u092C\u0928\u094D\u0926" to "band", "\u0911\u092B" to "off", "\u0911\u092B\u093C" to "off",
        "\u0939\u093E\u0901" to "haan", "\u0939\u093E\u0902" to "haan", "\u0939\u093E" to "haan",
        "\u0928\u0939\u0940\u0902" to "nahi", "\u0928\u0939\u0940" to "nahi",
        "\u0920\u0940\u0915" to "theek", "\u0930\u094B\u0915\u094B" to "roko", "\u0930\u0941\u0915\u094B" to "roko", "\u092C\u093F\u0932\u094D\u0915\u0941\u0932" to "bilkul"
    )

    private val onWords = listOf("on", "chalu", "enable", "start")
    private val offWords = listOf("band", "off", "disable", "stop")

    private fun parse(raw: String): Cmd {
        var s = raw.lowercase().replace("\u200c", "").replace("\u200d", "")
        for ((d, r) in pre) s = s.replace(d, " $r ")
        s = s.replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ")
        val orig = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        val w = orig.map { tokMap[it] ?: it }
        return Cmd(orig, w)
    }

    /** Is command ka type: reply, nread, nclear, npanel, shot, lock, scroll, tap, screen, quick, recents, home, back, tile ya khali. */
    private fun kind(c: Cmd): String {
        val w = c.w
        val n = w.size
        val has = { x: String -> w.contains(x) }
        val anyOf = { xs: List<String> -> xs.any { w.contains(it) } }

        val ri = w.indexOf("reply")
        if ((ri in 0..2) || (has("jawab") && anyOf(listOf("message", "whatsapp", "notification")))) return "reply"

        val notif = has("notification") || has("notifications")
        if (notif && anyOf(listOf("saaf", "clear", "hatao", "delete", "mita", "mitao"))) return "nclear"
        if (notif && anyOf(listOf("kholo", "open", "panel", "neeche", "dikhao", "dikha")) && !anyOf(listOf("padho", "sunao", "batao", "read"))) return "npanel"
        val sendW = anyOf(listOf("bhejo", "bhej", "send", "likho", "likh"))
        val msgAsk = has("message") && anyOf(listOf("padho", "sunao", "aaya", "read", "batao", "dikhao", "kaun", "naya")) && !sendW
        if (notif || msgAsk) return "nread"

        if (has("screenshot") || (has("screen") && has("shot"))) return "shot"
        if (has("lock") && !has("unlock") && n <= 5) return "lock"
        if (has("scroll")) return "scroll"
        if ((has("tap") || has("click") || has("dabao")) && n >= 2) return "tap"
        if (has("screen") && anyOf(listOf("padho", "read", "sunao", "likha", "kya", "batao"))) return "screen"

        val other = anyOf(listOf("call", "gana", "gaana", "song", "music", "youtube", "whatsapp", "sms", "note", "reminder", "alarm", "timer"))
        val navOk = n == 1 || anyOf(listOf("jao", "jaao", "screen", "karo", "kar", "do", "chalo", "par", "pe", "go", "ruhi", "please", "kholo", "open", "dikhao"))
        if (n <= 5 && !other && navOk) {
            if (has("quick") && has("settings")) return "quick"
            if (has("recent") || has("recents")) return "recents"
            if (has("home")) return "home"
            if (anyOf(listOf("back", "wapas", "vapas", "peeche", "pichhe", "piche"))) return "back"
        }

        val tk = if (has("wifi")) "wifi" else if (has("bluetooth")) "bluetooth" else ""
        if (tk.isNotEmpty() && (anyOf(onWords) || anyOf(offWords)) && !has("youtube")) return "tile"
        return ""
    }

    /** Phone locked ho to jo kaam screen par chalte hain (Accessibility wale) unke liye unlock chahiye. */
    fun needsUnlock(raw: String): Boolean {
        val k = kind(parse(raw))
        val acc = RuhiAccess.instance != null
        return when (k) {
            "shot", "scroll", "tap", "screen", "quick", "recents", "home", "back", "tile", "npanel" -> acc
            else -> false
        }
    }

    // ---------------------------------------------------------------- reply ki haan/na
    fun confirm(a: Context, raw: String): String? {
        val p = pending ?: return null
        pending = null
        if (System.currentTimeMillis() - p.at > 120000L) return null
        val w = parse(raw).w
        val no = listOf("nahi", "mat", "cancel", "roko", "no", "nahin").any { w.contains(it) }
        val yes = listOf("haan", "han", "yes", "bhejo", "bhej", "ok", "okay", "theek", "sahi", "bilkul", "send", "kar").any { w.contains(it) }
        if (no) return "Theek hai, reply cancel kar diya"
        if (!yes) return null
        val who = p.item.title.ifBlank { p.item.app }
        return if (RuhiNotif.sendReply(a, p.item, p.body)) "$who ko reply bhej diya" else "Reply nahi ja paaya"
    }

    private fun openSettings(a: Context, bt: Boolean): Boolean = try {
        val i = if (bt) Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        else if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        else Intent(Settings.ACTION_WIFI_SETTINGS)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
        true
    } catch (e: Exception) { false }

    /** true = command yahan ka tha, jawab cb se aayega (kabhi kabhi thodi der baad). */
    fun run(a: Context, raw: String, cb: (String) -> Unit): Boolean {
        followUp = false
        val c = parse(raw)
        val w = c.w
        val has = { x: String -> w.contains(x) }
        val k = kind(c)
        if (k.isEmpty()) return false
        val acc = RuhiAccess.instance

        // ---- notification par reply
        if (k == "reply") {
            val ri = w.indexOf("reply")
            val ti = if (ri >= 0) ri else w.indexOf("jawab")
            val fill = setOf("karo", "kar", "do", "bhejo", "bhej", "likho", "likh", "de", "dena", "ki", "ke", "ko", "ruhi", "ye", "yeh",
                "isko", "usko", "is", "us", "message", "ka", "ek", "please", "yaar", "mein", "me", "bhi", "bol")
            var from = ti + 1
            while (from < c.orig.size && w[from] in fill) from++
            var body = c.orig.subList(from, c.orig.size).joinToString(" ").trim()
            if (body.isEmpty()) {
                val before = ArrayList<String>()
                for (i in 0 until maxOf(0, ti)) {
                    if (w[i] !in fill && w[i] != "whatsapp" && w[i] != "notification") before.add(c.orig[i])
                }
                body = before.joinToString(" ").trim()
            }
            if (!RuhiNotif.enabled(a)) { cb(NEED_NOTIF); return true }
            val pkg: String? = if (has("whatsapp")) "com.whatsapp" else null
            val item = RuhiNotif.latestReplyable(pkg)
            if (item == null) { cb("Abhi koi aisa message nahi hai jisko reply ho sake"); return true }
            if (body.isEmpty()) { cb("Kya reply likhu? Jaise bolo: reply karo main aa raha hu"); return true }
            pending = PendingReply(item, body, System.currentTimeMillis())
            followUp = true
            val who = item.title.ifBlank { item.app }
            cb("$who ko reply bhejun: $body. Bhejne ke liye haan bolo")
            return true
        }

        // ---- notifications padhna / saaf karna / panel
        if (k == "nclear") {
            if (!RuhiNotif.enabled(a)) { cb(NEED_NOTIF); return true }
            RuhiNotif.clearAll()
            cb("Saare notification hata diye")
            return true
        }
        if (k == "npanel" && acc != null) {
            cb(if (acc.act(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) "Notification panel khol diya" else "Panel nahi khul paaya")
            return true
        }
        if (k == "nread" || k == "npanel") {
            if (!RuhiNotif.enabled(a)) { cb(NEED_NOTIF); return true }
            val pkg: String? = if (has("whatsapp")) "com.whatsapp" else null
            val l = RuhiNotif.recent(3, pkg)
            if (l.isEmpty()) {
                cb(if (pkg != null) "WhatsApp ka koi naya message nahi hai" else "Abhi koi naya notification nahi hai")
                return true
            }
            val total = RuhiNotif.count(pkg)
            val sb = StringBuilder()
            sb.append(if (total > l.size) "$total notification hain, latest ${l.size} sunati hu. " else "${l.size} notification hain. ")
            for (item in l) {
                sb.append(item.app)
                if (item.title.isNotBlank()) sb.append(" se ").append(item.title)
                if (item.text.isNotBlank()) sb.append(": ").append(item.text.take(100))
                sb.append(". ")
            }
            cb(sb.toString().trim())
            return true
        }

        // ---- WiFi / Bluetooth: Accessibility na ho to purana tarika (settings panel) chalne do
        if (k == "tile") {
            if (acc == null) return false
            val bt = has("bluetooth")
            val onW = onWords.any { has(it) }
            val offW = offWords.any { has(it) }
            val want = onW && !offW
            val nm = if (bt) "Bluetooth" else "WiFi"
            val names = if (bt) listOf("bluetooth") else listOf("wi-fi", "wifi")
            val st = if (want) "on" else "band"
            acc.toggleTile(names, want) { r ->
                if (r == null) {
                    val ok = openSettings(a, bt)
                    cb(if (ok) "$nm ka button quick settings mein nahi mila, settings khol di, wahan se kar lo" else "$nm change nahi ho paaya")
                } else if (r == "already") cb("$nm pehle se $st hai")
                else cb("$nm $st kar diya")
            }
            return true
        }

        // ---- baaki sab kaam ke liye Accessibility zaroori hai
        if (acc == null) { cb(NEED_ACC); return true }

        when (k) {
            "shot" -> {
                if (Build.VERSION.SDK_INT < 28) cb("Is Android version par screenshot ka ye tarika nahi chalta")
                else acc.screenshot { ok -> cb(if (ok) "Screenshot le liya" else "Screenshot nahi le paayi") }
            }
            "lock" -> {
                if (Build.VERSION.SDK_INT < 28) cb("Is Android version par ye tarika nahi chalta")
                else cb(if (acc.act(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) "Phone lock kar rahi hu" else "Lock nahi ho paaya")
            }
            "scroll" -> {
                val down = !(has("upar") || has("up"))
                acc.scroll(down) { ok -> cb(if (ok) "Scroll kar diya" else "Scroll nahi ho paaya") }
            }
            "tap" -> {
                val drop = setOf("tap", "click", "dabao", "karo", "kar", "do", "par", "pe", "pr", "ko", "ruhi", "button", "icon", "option",
                    "on", "the", "ka", "ki", "ke", "ek", "please", "yaar", "mein", "me", "wala", "wali", "wale")
                val q = c.orig.filterIndexed { i, _ -> w[i] !in drop }.joinToString(" ").trim()
                if (q.isEmpty()) cb("Kya dabaun? Jaise bolo: tap settings")
                else cb(if (acc.tapText(q)) "$q daba diya" else "Screen par $q nahi mila")
            }
            "screen" -> {
                val s = acc.readScreen()
                cb(if (s.isBlank()) "Screen par padhne layak kuch nahi mila" else "Screen par ye hai: $s")
            }
            "quick" -> cb(if (acc.act(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) "Quick settings khol di" else "Quick settings nahi khuli")
            "recents" -> cb(if (acc.act(AccessibilityService.GLOBAL_ACTION_RECENTS)) "Recent apps khol diye" else "Recent apps nahi khule")
            "home" -> cb(if (acc.act(AccessibilityService.GLOBAL_ACTION_HOME)) "Home par aa gayi" else "Home nahi ho paaya")
            "back" -> cb(if (acc.act(AccessibilityService.GLOBAL_ACTION_BACK)) "Peeche aa gayi" else "Back nahi ho paaya")
            else -> return false
        }
        return true
    }
}
