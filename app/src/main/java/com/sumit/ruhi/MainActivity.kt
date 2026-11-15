package com.sumit.ruhi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.*
import android.text.InputType
import android.view.*
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private val BG = Color.parseColor("#0B0B1A")
    private val CARD = Color.parseColor("#151530")
    private val PUR = Color.parseColor("#9B5CFF")
    private val DIM = Color.parseColor("#8E8AB8")
    private val ui = Handler(Looper.getMainLooper())
    private val log = ArrayList<String>()
    private val tabs = ArrayList<TextView>()
    private lateinit var body: FrameLayout
    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var charImg: ImageView
    private lateinit var avatar: ImageView
    private lateinit var homeView: View
    private lateinit var speaker: Speaker
    private var rec: SpeechRecognizer? = null
    private var live = false
    private var thinking = false
    private var fails = 0
    private var sub = 0
    private lateinit var mic: MicView
    private lateinit var wave: WaveView
    private lateinit var overlay: FrameLayout
    private lateinit var hero: FrameLayout
    private lateinit var fade: View
    private var heroBmp: Bitmap? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        window.statusBarColor = BG; window.navigationBarColor = BG
        speaker = Speaker(this) { if (live && !thinking) listen() else if (!live) idle() }
        orb = OrbView(this); status = tv("Tap to Talk", 18f, Color.WHITE); mic = MicView(this) { orb.state }; wave = WaveView(this) { orb.state }; overlay = buildOverlay()
        charImg = ImageView(this); avatar = ImageView(this)
        homeView = buildHome()
        val root = FrameLayout(this); root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#0A0A1F"), Color.parseColor("#1A1040"), Color.parseColor("#0A0A1F"))); root.clipChildren = false
        body = FrameLayout(this)
        root.addView(body, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(96) })
        root.addView(buildNav(), FrameLayout.LayoutParams(-1, dp(76), Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(10)) })
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, i.systemWindowInsetTop, 0, i.systemWindowInsetBottom); i }
        setContentView(root)
        refreshHome(); show(0)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onDestroy() { super.onDestroy(); speaker.shutdown(); rec?.destroy() }

    private fun hap(v: View) { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    private var floatAnim: android.animation.ObjectAnimator? = null

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun tv(t: String, sz: Float = 16f, col: Int = Color.WHITE, bold: Boolean = false): TextView {
        val x = TextView(this); x.text = t; x.textSize = sz; x.setTextColor(col)
        x.typeface = if (bold) Typeface.create("serif", Typeface.BOLD) else Typeface.create("sans-serif-light", Typeface.NORMAL)
        return x
    }

    private fun btn(t: String, f: () -> Unit): TextView {
        val x = tv(t, 16f); x.gravity = Gravity.CENTER; x.setPadding(0, dp(14), 0, dp(14))
        x.background = GradientDrawable().apply { setColor(PUR); cornerRadius = dp(30).toFloat() }
        x.setOnClickListener { hap(x); x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction { x.animate().scaleX(1f).scaleY(1f).setDuration(110).start(); f() }.start() }
        x.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        return x
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this); c.orientation = LinearLayout.VERTICAL; c.setPadding(dp(18), dp(14), dp(18), dp(16))
        c.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#4A5B3FB0"), Color.parseColor("#B0140F3A"))).apply { cornerRadius = dp(24).toFloat(); setStroke(dp(1), Color.parseColor("#66B58CFF")) }
        c.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) }
        return c
    }

    private fun row(ic: String, t: String, s: String, f: () -> Unit): View {
        val r = LinearLayout(this); r.gravity = Gravity.CENTER_VERTICAL; r.setPadding(0, dp(10), 0, dp(10))
        r.addView(tv(ic, 22f).apply { setPadding(0, 0, dp(14), 0) })
        val m = LinearLayout(this); m.orientation = LinearLayout.VERTICAL; m.addView(tv(t, 17f)); m.addView(tv(s, 13f, DIM))
        r.addView(m, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(tv("›", 24f, DIM)); r.setOnClickListener { hap(r); f() }
        return r
    }

    private fun page(title: String, back: Boolean, fill: (LinearLayout) -> Unit): View {
        val sv = ScrollView(this); val l = LinearLayout(this); l.orientation = LinearLayout.VERTICAL; l.setPadding(0, dp(16), 0, dp(24))
        val h = LinearLayout(this); h.gravity = Gravity.CENTER_VERTICAL; h.setPadding(dp(16), 0, dp(16), dp(8))
        if (back) h.addView(tv("←", 26f).apply { setPadding(0, 0, dp(16), 0); setOnClickListener { go(0) } })
        h.addView(tv(title, 26f, Color.WHITE, true)); l.addView(h)
        fill(l); sv.addView(l); return sv
    }

    private fun go(n: Int) { sub = n; show(3) }

    private fun show(t: Int) {
        body.removeAllViews()
        body.addView(when (t) {
            0 -> homeView
            1 -> chat()
            2 -> history()
            else -> when (sub) { 1 -> conn(); 2 -> look(); 3 -> chars(); 4 -> phone(); else -> settingsMain() }
        })
        val nv = body.getChildAt(0); nv.alpha = 0f; nv.translationY = dp(14).toFloat(); nv.animate().alpha(1f).translationY(0f).setDuration(220).start()
        for (i in tabs.indices) tabs[i].setTextColor(if (i == t) Color.parseColor("#FFB020") else DIM)
        curTab = t
    }
    private var curTab = 0

    private fun buildNav(): View {
        val n = LinearLayout(this); n.gravity = Gravity.CENTER_VERTICAL; n.clipChildren = false; n.setPadding(dp(8), 0, dp(8), 0)
        n.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(34).toFloat(); setStroke(dp(1), Color.parseColor("#3A2F6B")) }
        val labels = listOf("🏠\nHome", "💬\nChat", "🕘\nHistory", "⚙\nSettings")
        for (i in 0..3) {
            if (i == 2) {
                avatar.scaleType = ImageView.ScaleType.CENTER_CROP
                avatar.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(PUR); setStroke(dp(3), Color.parseColor("#C9A15B")) }
                avatar.outlineProvider = ViewOutlineProvider.BACKGROUND; avatar.clipToOutline = true
                avatar.translationY = -dp(14).toFloat(); avatar.setOnClickListener { toggle() }
                n.addView(avatar, LinearLayout.LayoutParams(dp(68), dp(68)).apply { setMargins(dp(6), 0, dp(6), 0) })
            }
            val x = tv(labels[i], 12f, DIM); x.gravity = Gravity.CENTER; x.setOnClickListener { hap(x); sub = 0; show(i) }
            tabs.add(x); n.addView(x, LinearLayout.LayoutParams(0, -1, 1f))
        }
        return n
    }

    private val bundled = listOf("Lavender morning", "Midnight hoodie", "Neon city")

    private fun loadChar(c: Int): Bitmap? = try {
        if (c >= 100) assets.open("chars/b${c - 100}.jpg").use { BitmapFactory.decodeStream(it) }
        else if (c >= 0) BitmapFactory.decodeFile(File(filesDir, "c$c.jpg").path) else null
    } catch (e: Exception) { null }

    private fun buildHome(): View {
        val sv = ScrollView(this); sv.isVerticalScrollBarEnabled = false
        val l = LinearLayout(this); l.orientation = LinearLayout.VERTICAL; l.setPadding(0, 0, 0, dp(24))
        hero = FrameLayout(this); hero.clipChildren = true
        charImg.scaleType = ImageView.ScaleType.MATRIX
        hero.addView(charImg, FrameLayout.LayoutParams(-1, -1))
        fade = View(this)
        fade.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.parseColor("#0A0A1F")))
        hero.addView(fade, FrameLayout.LayoutParams(-1, dp(190), Gravity.BOTTOM))
        hero.addView(tv("Namaste, Sumit 💜", 26f, Color.WHITE, true).apply { setShadowLayer(10f, 0f, 2f, Color.BLACK) },
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(20), dp(24), 0, 0) })
        hero.addView(tv("Main Ruhi, sirf tumhari", 14f, Color.parseColor("#E6DFFF")).apply { setShadowLayer(8f, 0f, 2f, Color.BLACK) },
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(20), dp(62), 0, 0) })
        l.addView(hero, LinearLayout.LayoutParams(-1, dp(380)))
        mic.setOnClickListener { hap(mic); micTap() }
        l.addView(mic, LinearLayout.LayoutParams(dp(140), dp(140)).apply { topMargin = -dp(75); gravity = Gravity.CENTER_HORIZONTAL })
        l.addView(tv("Tap to Talk", 24f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = -dp(4) })
        l.addView(wave, LinearLayout.LayoutParams(-1, dp(34)).apply { setMargins(dp(40), dp(8), dp(40), dp(8)) })
        val bar = LinearLayout(this); bar.gravity = Gravity.CENTER_VERTICAL; bar.setPadding(dp(8), dp(8), dp(8), dp(8))
        bar.background = GradientDrawable().apply { setColor(Color.parseColor("#B0151530")); cornerRadius = dp(34).toFloat(); setStroke(dp(1), Color.parseColor("#66B58CFF")) }
        bar.addView(tv("✨", 18f).apply { gravity = Gravity.CENTER; background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#2A1F55")) } }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val e = EditText(this); e.hint = "Ask me anything..."; e.setHintTextColor(DIM); e.setTextColor(Color.WHITE); e.setSingleLine()
        e.background = null; e.setPadding(dp(12), 0, dp(8), 0)
        bar.addView(e, LinearLayout.LayoutParams(0, -2, 1f))
        val send = tv("➤", 20f).apply { gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(PUR); cornerRadius = dp(18).toFloat() } }
        send.setOnClickListener { hap(send); val q = e.text.toString().trim(); if (q.isNotEmpty()) { e.setText(""); ask(q); sub = 0; show(1) } }
        bar.addView(send, LinearLayout.LayoutParams(dp(56), dp(44)))
        l.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(4), dp(16), dp(14)) })
        val r = LinearLayout(this)
        fun fc(ic: String, t: String, s: String, f: () -> Unit) {
            val c = LinearLayout(this); c.orientation = LinearLayout.VERTICAL; c.gravity = Gravity.CENTER; c.setPadding(dp(6), dp(12), dp(6), dp(12))
            c.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#4A5B3FB0"), Color.parseColor("#B0140F3A"))).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.parseColor("#66B58CFF")) }
            c.addView(tv(ic, 20f).apply { gravity = Gravity.CENTER; background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#2A1F55")) } }, LinearLayout.LayoutParams(dp(40), dp(40)))
            c.addView(tv(t, 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            c.addView(tv(s, 11f, DIM).apply { gravity = Gravity.CENTER })
            c.setOnClickListener { hap(c); f() }
            r.addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        fc("💬", "Chat", "Get instant answers") { sub = 0; show(1) }
        fc("🧑", "Characters", "Change her look") { go(3) }
        fc("🎨", "Orbs", "10 glowing styles") { go(2) }
        l.addView(r, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), 0, dp(12), 0) })
        sv.addView(l); return sv
    }

    private fun refreshHome() {
        orb.style = Store.orb
        val bmp = loadChar(Store.chr)
        val lp = hero.layoutParams
        floatAnim?.cancel(); charImg.scaleX = 1f; charImg.scaleY = 1f
        if (bmp != null) {
            heroBmp = bmp; charImg.setImageBitmap(bmp); avatar.setImageBitmap(bmp)
            fade.visibility = View.VISIBLE; lp.height = dp(380)
            charImg.post {
                fitTop()
                floatAnim = android.animation.ObjectAnimator.ofFloat(charImg, "scaleX", 1f, 1.035f).apply {
                    duration = 2600; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
                    addUpdateListener { charImg.scaleY = charImg.scaleX }; start()
                }
            }
        } else {
            heroBmp = null; charImg.setImageDrawable(null); avatar.setImageDrawable(null); fade.visibility = View.GONE; lp.height = dp(120)
        }
        hero.layoutParams = lp
    }

    private fun fitTop() {
        val b = heroBmp ?: return
        val w = charImg.width.toFloat(); val h = charImg.height.toFloat()
        if (w == 0f || h == 0f) return
        val s = maxOf(w / b.width, h / b.height); val m = Matrix(); m.setScale(s, s)
        m.postTranslate((w - b.width * s) / 2f, -(b.height * s - h) * 0.12f)
        charImg.imageMatrix = m; charImg.pivotX = w / 2f; charImg.pivotY = h
    }

    private fun buildOverlay(): FrameLayout {
        val f = FrameLayout(this); f.visibility = View.GONE; f.isClickable = true
        f.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#F2090918"), Color.parseColor("#F21A0F3D")))
        f.addView(orb, FrameLayout.LayoutParams(dp(320), dp(320), Gravity.CENTER).apply { bottomMargin = dp(60) })
        status.gravity = Gravity.CENTER
        f.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { topMargin = dp(270) })
        val r = LinearLayout(this); r.gravity = Gravity.CENTER
        r.addView(btn("▾  Minimize") { showOverlay(false) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        r.addView(btn("⏹  End") { toggle() }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        f.addView(r, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(16), 0, dp(16), dp(40)) })
        return f
    }

    private fun showOverlay(v: Boolean) {
        if (v) { overlay.alpha = 0f; overlay.visibility = View.VISIBLE; overlay.animate().alpha(1f).setDuration(220).start() }
        else if (overlay.visibility == View.VISIBLE) overlay.animate().alpha(0f).setDuration(180).withEndAction { overlay.visibility = View.GONE }.start()
    }

    private fun micTap() { if (live) showOverlay(true) else toggle() }

    // ---------- voice loop ----------
    private fun toggle() {
        if (live) { live = false; speaker.stop(); rec?.cancel(); idle() } else { live = true; fails = 0; Sfx.ping(); showOverlay(true); listen() }
    }

    private fun idle() { orb.state = 0; status.text = "Tap to Talk"; showOverlay(false) }

    private fun listen() {
        if (!live) return
        orb.state = 1; status.text = "Sun rahi hoon..."
        if (rec == null) {
            rec = SpeechRecognizer.createSpeechRecognizer(this)
            rec!!.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
                override fun onError(e: Int) {
                    if (!live || thinking) return
                    fails++
                    if (fails > 12) { live = false; idle(); status.text = "Mic error $e, phir se tap karo"; return }
                    ui.postDelayed({ listen() }, 700)
                }
                override fun onResults(b: Bundle?) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (t.isBlank()) { onError(7); return }
                    fails = 0; ask(t)
                }
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        rec!!.startListening(i)
    }

    private fun ask(q: String) {
        thinking = true; orb.state = 0; status.text = "Soch rahi hoon..."
        log.add("Tum: $q"); Store.addHist(q)
        val act = Actions.run(this, q)
        if (act != null) { log.add("Ruhi: $act"); thinking = false; orb.state = 2; speaker.say(act); return }
        log.add("Ruhi: ")
        val idx = log.size - 1
        Brain.ask(q,
            { s -> ui.post { orb.state = 2; status.text = "Bol rahi hoon..."; log[idx] = log[idx] + s + " "; speaker.say(s); if (curTab == 1) show(1) } },
            { ui.post { thinking = false; if (!speaker.busy()) { if (live) listen() else idle() } } },
            { m -> ui.post { thinking = false; log[idx] = "Ruhi: ($m)"; status.text = m; speaker.say("Sumit, net ya key mein kuch gadbad hai") } })
    }

    // ---------- tabs ----------
    private fun chat(): View {
        val l = LinearLayout(this); l.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        val t = tv(if (log.isEmpty()) "Kuch bhi poocho ya bolo 💬" else log.joinToString("\n\n"), 16f)
        t.setPadding(dp(18), dp(18), dp(18), dp(18)); sv.addView(t)
        l.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        val r = LinearLayout(this); r.gravity = Gravity.CENTER_VERTICAL; r.setPadding(dp(12), dp(8), dp(12), dp(8))
        val e = EditText(this); e.hint = "Ask me anything..."; e.setHintTextColor(DIM); e.setTextColor(Color.WHITE); e.setSingleLine()
        e.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(26).toFloat() }
        e.setPadding(dp(18), dp(12), dp(18), dp(12))
        r.addView(e, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(tv("➤", 26f, PUR).apply {
            setPadding(dp(14), 0, dp(6), 0)
            setOnClickListener { val q = e.text.toString().trim(); if (q.isNotEmpty()) { e.setText(""); ask(q); show(1) } }
        })
        l.addView(r)
        sv.post { sv.fullScroll(View.FOCUS_DOWN) }
        return l
    }

    private fun history(): View {
        val sv = ScrollView(this); val l = LinearLayout(this); l.orientation = LinearLayout.VERTICAL; l.setPadding(dp(8), dp(16), dp(8), dp(16))
        l.addView(tv("History", 26f, Color.WHITE, true).apply { setPadding(dp(14), 0, 0, dp(8)) })
        val h = Store.hist()
        if (h.isEmpty()) l.addView(tv("Abhi kuch nahi", 15f, DIM).apply { setPadding(dp(14), 0, 0, 0) })
        for (s in h) { val c = card(); c.addView(tv(s, 15f)); l.addView(c) }
        sv.addView(l); return sv
    }

    // ---------- settings ----------
    private fun settingsMain() = page("Settings", false) { l ->
        l.addView(tv("AI SETTINGS", 13f, DIM).apply { setPadding(dp(20), 0, 0, 0) })
        val c = card()
        c.addView(row("🔑", "Connections", "Gemini and Groq keys") { go(1) })
        c.addView(row("🎨", "Look & feel", "Orb style: ${OrbView.names[Store.orb % OrbView.names.size]}") { go(2) })
        c.addView(row("🧑", "Home character", "Photo on Home screen") { go(3) })
        c.addView(row("🎙", "Voice", if (Store.voice == 0) "Kore: natural, LIA style (tap to switch)" else "Phone voice: fastest (tap to switch)") { Store.voice = 1 - Store.voice; go(0) })
        l.addView(c)
        l.addView(tv("APP SETTINGS", 13f, DIM).apply { setPadding(dp(20), dp(12), 0, 0) })
        val c2 = card()
        c2.addView(row("📱", "Phone control", "Permissions and background mode") { go(4) })
        l.addView(c2)
    }

    private fun keyCard(l: LinearLayout, t: String, s: String, ready: Boolean, b: String, save: (String) -> Unit) {
        val c = card(); c.addView(tv(t, 18f))
        c.addView(tv(if (ready) "✅ Ready" else "⚠ Not set", 13f, if (ready) Color.parseColor("#4FE3C1") else Color.parseColor("#FFB020")))
        c.addView(tv(s, 13f, DIM))
        val e = EditText(this); e.hint = if (ready) "•••• saved (nayi paste karo)" else "Key paste karo"
        e.setHintTextColor(DIM); e.setTextColor(Color.WHITE); e.setSingleLine()
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.background = GradientDrawable().apply { setColor(Color.parseColor("#1B1B3D")); cornerRadius = dp(24).toFloat() }
        e.setPadding(dp(18), dp(14), dp(18), dp(14))
        c.addView(e, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        c.addView(btn(b) {
            val v = e.text.toString().trim()
            if (v.isNotEmpty()) { save(v); Toast.makeText(this, "Saved ✅", Toast.LENGTH_SHORT).show(); go(1) }
        })
        l.addView(c)
    }

    private fun conn() = page("Connections", true) { l ->
        l.addView(tv("Keys sirf isi phone mein save hoti hain.", 14f, DIM).apply { setPadding(dp(20), 0, dp(20), dp(4)) })
        keyCard(l, "Gemini key", "Backup brain: Groq na chale to ye kaam karegi", Store.gemini.isNotBlank(), "Save Gemini key") { Store.gemini = it }
        keyCard(l, "Chat - Groq keys", "Fast replies. Kai keys comma se alag karo", Store.groq.isNotBlank(), "Save Groq keys") { Store.groq = it }
    }

    private fun look() = page("Look & feel", true) { l ->
        l.addView(tv("Orb style chuno", 14f, DIM).apply { setPadding(dp(20), 0, 0, dp(6)) })
        var i = 0
        while (i < OrbView.names.size) {
            val r = LinearLayout(this); r.setPadding(dp(10), 0, dp(10), 0)
            for (j in i until minOf(i + 2, OrbView.names.size)) {
                val cell = LinearLayout(this); cell.orientation = LinearLayout.VERTICAL; cell.gravity = Gravity.CENTER
                cell.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(22).toFloat(); setStroke(dp(2), if (Store.orb == j) PUR else Color.parseColor("#2A2550")) }
                val o = OrbView(this); o.style = j
                cell.addView(o, LinearLayout.LayoutParams(dp(110), dp(110)))
                cell.addView(tv(OrbView.names[j], 15f).apply { setPadding(0, 0, 0, dp(10)) })
                cell.setOnClickListener { Store.orb = j; orb.style = j; go(2) }
                r.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
            }
            l.addView(r); i += 2
        }
    }

    private fun chars() = page("Home character", true) { l ->
        val c = card()
        c.addView(row("✨", "Sirf orb", if (Store.chr < 0) "✔ selected" else "Koi photo nahi") { Store.chr = -1; refreshHome(); go(3) })
        for (k in bundled.indices) c.addView(row("🖼", bundled[k], if (Store.chr == 100 + k) "✔ selected" else "Tap to select") { Store.chr = 100 + k; refreshHome(); go(3) })
        for (n in 0 until Store.chars) {
            if (File(filesDir, "c$n.jpg").exists())
                c.addView(row("🖼", "Character ${n + 1}", if (Store.chr == n) "✔ selected" else "Tap to select") { Store.chr = n; refreshHome(); go(3) })
        }
        l.addView(c)
        val c2 = card()
        c2.addView(tv("Apni pasand ki photo (anime ya girl) gallery se add karo, wo Home pe dikhegi.", 14f, DIM))
        c2.addView(btn("➕ Photo add karo") { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 7) })
        l.addView(c2)
    }

    private fun phone() = page("Phone control", true) { l ->
        val c = card()
        c.addView(tv("Background mein bhi chalne ke liye ye teen kaam karo:", 14f, DIM))
        c.addView(btn("1. Display over other apps") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        c.addView(btn("2. App permissions (Mic, Phone)") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
        c.addView(btn("3. Background mode ON") {
            try { startForegroundService(Intent(this, RuhiService::class.java)) } catch (e: Exception) { Toast.makeText(this, "Background mode nahi chala", Toast.LENGTH_SHORT).show() }
        })
        l.addView(c)
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        val u = d?.data
        if (rc == 7 && res == RESULT_OK && u != null) {
            try {
                val o = BitmapFactory.Options(); o.inSampleSize = 2
                val bmp = contentResolver.openInputStream(u)!!.use { BitmapFactory.decodeStream(it, null, o) }!!
                val n = Store.chars
                java.io.FileOutputStream(File(filesDir, "c$n.jpg")).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                Store.chars = n + 1; Store.chr = n; refreshHome(); go(3)
            } catch (e: Exception) { Toast.makeText(this, "Photo nahi khuli", Toast.LENGTH_SHORT).show() }
        }
    }
}
