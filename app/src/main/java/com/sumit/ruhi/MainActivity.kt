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
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private val BG = Color.parseColor("#05030F")
    private val CARD = Color.parseColor("#120C2B")
    private val PUR = Color.parseColor("#8B4DFF")
    private val DIM = Color.parseColor("#9A92C8")
    private val EDGE = Color.parseColor("#887C4DFF")
    private val ui = Handler(Looper.getMainLooper())
    private val log = ArrayList<String>()
    private val tabs = ArrayList<TextView>()
    private val chips = ArrayList<TextView>()
    private val chipState = listOf(1, 3, 2)
    private lateinit var body: FrameLayout
    private lateinit var orb: OrbView
    private lateinit var homeOrb: OrbView
    private lateinit var status: TextView
    private lateinit var subStatus: TextView
    private lateinit var charImg: ImageView
    private lateinit var avatar: ImageView
    private lateinit var homeView: View
    private lateinit var speaker: Speaker
    private var rec: SpeechRecognizer? = null
    private var live = false
    private var thinking = false
    private var fails = 0
    private var sub = 0
    private var backTab = 3
    private var curTab = 0
    private lateinit var mic: MicView
    private lateinit var wave: WaveView
    private lateinit var overlay: FrameLayout
    private lateinit var hero: FrameLayout
    private lateinit var fade: View
    private var heroBmp: Bitmap? = null
    private var floatAnim: android.animation.ObjectAnimator? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        window.statusBarColor = BG; window.navigationBarColor = BG
        speaker = Speaker(this) { if (live && !thinking) listen() else if (!live) idle() }
        orb = OrbView(this); orb.showBase = true
        homeOrb = OrbView(this)
        status = tv("Tap to Talk", 24f, Color.WHITE, true); subStatus = tv("Speak or type anything...", 14f, DIM)
        mic = MicView(this) { orb.state }; wave = WaveView(this) { orb.state }; wave.n = 10
        overlay = buildOverlay()
        charImg = ImageView(this); avatar = ImageView(this)
        homeView = buildHome()
        val root = FrameLayout(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#05030F"), Color.parseColor("#120A32"), Color.parseColor("#05030F")))
        root.clipChildren = false
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

    // ---------- helpers ----------
    private fun hap(v: View) { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun rounded(fill: Int, r: Int, sw: Int = 0, sc: Int = 0) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(r).toFloat(); if (sw > 0) setStroke(dp(sw), sc) }
    private fun glass(r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#5A3B1FA0"), Color.parseColor("#C0100A30"))).apply { cornerRadius = dp(r).toFloat(); setStroke(dp(1), EDGE) }
    private fun circle(fill: Int, sw: Int = 0, sc: Int = 0) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill); if (sw > 0) setStroke(dp(sw), sc) }

    private fun tv(t: CharSequence, sz: Float = 16f, col: Int = Color.WHITE, bold: Boolean = false): TextView {
        val x = TextView(this); x.text = t; x.textSize = sz; x.setTextColor(col)
        x.typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        return x
    }

    private fun btn(t: String, f: () -> Unit): TextView {
        val x = tv(t, 16f); x.gravity = Gravity.CENTER; x.setPadding(0, dp(14), 0, dp(14))
        x.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#8B4DFF"), Color.parseColor("#C04DFF"))).apply { cornerRadius = dp(30).toFloat() }
        x.setOnClickListener { hap(x); x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction { x.animate().scaleX(1f).scaleY(1f).setDuration(110).start(); f() }.start() }
        x.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        return x
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this); c.orientation = LinearLayout.VERTICAL; c.setPadding(dp(18), dp(14), dp(18), dp(16))
        c.background = glass(24)
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
        if (back) h.addView(tv("←", 26f).apply { setPadding(0, 0, dp(16), 0); setOnClickListener { if (backTab == 0) { sub = 0; show(0) } else go(0) } })
        h.addView(tv(title, 26f, Color.WHITE, true)); l.addView(h)
        fill(l); sv.addView(l); return sv
    }

    private fun go(n: Int) { sub = n; show(3) }
    private fun enter(n: Int, from: Int) { backTab = from; go(n) }

    private fun show(t: Int) {
        body.removeAllViews()
        body.addView(when (t) {
            0 -> homeView
            1 -> chat()
            2 -> history()
            else -> when (sub) { 1 -> conn(); 2 -> look(); 3 -> chars(); 4 -> phone(); 5 -> tools(); 6 -> models(); 7 -> voicePage(); else -> settingsMain() }
        })
        val nv = body.getChildAt(0); nv.alpha = 0f; nv.translationY = dp(14).toFloat(); nv.animate().alpha(1f).translationY(0f).setDuration(220).start()
        val hi = if (t == 3 && backTab == 0 && sub != 0) 0 else t
        for (i in tabs.indices) tabs[i].setTextColor(if (i == hi) Color.parseColor("#C9A5FF") else DIM)
        curTab = t
    }

    private fun buildNav(): View {
        val n = LinearLayout(this); n.gravity = Gravity.CENTER_VERTICAL; n.clipChildren = false; n.setPadding(dp(8), 0, dp(8), 0)
        n.background = rounded(CARD, 34, 1, Color.parseColor("#4A3A8F"))
        val labels = listOf("🏠\nHome", "💬\nChat", "🕘\nHistory", "⚙\nSettings")
        for (i in 0..3) {
            if (i == 2) {
                val c = LotusView(this); c.setPadding(dp(16), dp(16), dp(16), dp(16))
                c.background = circle(Color.parseColor("#0B0620"), 2, Color.parseColor("#9B6CFF"))
                c.translationY = -dp(14).toFloat(); c.setOnClickListener { hap(c); toggle() }
                n.addView(c, LinearLayout.LayoutParams(dp(66), dp(66)).apply { setMargins(dp(6), 0, dp(6), 0) })
            }
            val x = tv(labels[i], 12f, DIM); x.gravity = Gravity.CENTER; x.setOnClickListener { hap(x); sub = 0; show(i) }
            tabs.add(x); n.addView(x, LinearLayout.LayoutParams(0, -1, 1f))
        }
        return n
    }

    // ---------- characters ----------
    private val bundled = listOf("Lavender morning", "Midnight hoodie", "Neon city", "Headphones girl")

    private fun loadChar(c: Int): Bitmap? = try {
        if (c >= 100) assets.open("chars/b${c - 100}.jpg").use { BitmapFactory.decodeStream(it) }
        else if (c >= 0) BitmapFactory.decodeFile(File(filesDir, "c$c.jpg").path) else null
    } catch (e: Exception) { null }

    // ---------- home ----------
    private fun buildHome(): View {
        val sv = ScrollView(this); sv.isVerticalScrollBarEnabled = false
        val l = LinearLayout(this); l.orientation = LinearLayout.VERTICAL; l.setPadding(0, 0, 0, dp(24))
        hero = FrameLayout(this); hero.clipChildren = true
        charImg.scaleType = ImageView.ScaleType.MATRIX
        hero.addView(charImg, FrameLayout.LayoutParams(-1, -1))
        fade = View(this)
        fade.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.parseColor("#0A0620")))
        hero.addView(fade, FrameLayout.LayoutParams(-1, dp(230), Gravity.BOTTOM))

        // top bar
        val top = LinearLayout(this); top.gravity = Gravity.CENTER_VERTICAL; top.setPadding(dp(16), dp(12), dp(16), 0)
        top.addView(LotusView(this), LinearLayout.LayoutParams(dp(44), dp(44)))
        val tc = LinearLayout(this); tc.orientation = LinearLayout.VERTICAL; tc.setPadding(dp(10), 0, 0, 0)
        val ttl = SpannableString("Ruhi AI"); ttl.setSpan(ForegroundColorSpan(Color.parseColor("#B070FF")), 5, 7, 0)
        tc.addView(tv(ttl, 26f, Color.WHITE, true).apply { setShadowLayer(10f, 0f, 2f, Color.BLACK) })
        tc.addView(tv("Your Ultimate AI Assistant ♡", 12f, Color.parseColor("#E6DFFF")).apply { setShadowLayer(8f, 0f, 2f, Color.BLACK) })
        top.addView(tc, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(tv("👑 Pro", 13f, Color.parseColor("#FFD27A")).apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(Color.parseColor("#90100A30"), 20, 1, Color.parseColor("#AA7C4DFF")) })
        top.addView(tv("🔔", 18f).apply { gravity = Gravity.CENTER; background = circle(Color.parseColor("#90100A30"), 1, Color.parseColor("#AA7C4DFF")) }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(8), 0, dp(8), 0) })
        avatar.scaleType = ImageView.ScaleType.CENTER_CROP
        avatar.background = circle(PUR, 2, Color.parseColor("#7C9CFF"))
        avatar.outlineProvider = ViewOutlineProvider.BACKGROUND; avatar.clipToOutline = true
        avatar.setOnClickListener { hap(avatar); enter(3, 0) }
        top.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))
        hero.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // greeting card
        val g = LinearLayout(this); g.orientation = LinearLayout.VERTICAL; g.setPadding(dp(16), dp(12), dp(16), dp(12)); g.background = glass(22)
        g.addView(tv("Hello Sumit 👋", 24f, Color.WHITE, true))
        g.addView(tv("How can I help you\ntoday?", 16f, Color.parseColor("#E6DFFF")).apply { setPadding(0, dp(4), 0, dp(8)) })
        g.addView(tv("●  Online • Always Here", 12f, Color.parseColor("#4FE3C1")).apply { setPadding(dp(10), dp(5), dp(10), dp(5)); background = rounded(Color.parseColor("#331F8A6B"), 16, 1, Color.parseColor("#664FE3C1")) })
        hero.addView(g, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(14), dp(80), 0, 0) })

        homeOrb.setOnClickListener { hap(homeOrb); micTap() }
        hero.addView(homeOrb, FrameLayout.LayoutParams(dp(210), dp(210), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        l.addView(hero, LinearLayout.LayoutParams(-1, dp(440)))

        // tap to talk pill
        val pill = LinearLayout(this); pill.gravity = Gravity.CENTER_VERTICAL; pill.setPadding(dp(10), dp(8), dp(18), dp(8))
        pill.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#E0120A30"), Color.parseColor("#E01E1050"))).apply { cornerRadius = dp(44).toFloat(); setStroke(dp(2), Color.parseColor("#AA8B4DFF")) }
        mic.setOnClickListener { hap(mic); micTap() }
        pill.addView(mic, LinearLayout.LayoutParams(dp(76), dp(76)))
        val pc = LinearLayout(this); pc.orientation = LinearLayout.VERTICAL; pc.setPadding(dp(10), 0, 0, 0)
        pc.addView(tv("Tap to Talk", 22f, Color.WHITE, true)); pc.addView(tv("Speak or type anything...", 13f, DIM))
        pill.addView(pc, LinearLayout.LayoutParams(0, -2, 1f))
        pill.addView(wave, LinearLayout.LayoutParams(dp(80), dp(40)))
        pill.setOnClickListener { hap(pill); micTap() }
        l.addView(pill, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(4), dp(14), dp(14)) })

        // 6 cards
        fun grid(items: List<Triple<String, String, Pair<String, () -> Unit>>>) {
            val r = LinearLayout(this)
            for ((ic, t, p) in items) {
                val c = LinearLayout(this); c.orientation = LinearLayout.VERTICAL; c.gravity = Gravity.CENTER; c.setPadding(dp(6), dp(14), dp(6), dp(12))
                c.background = glass(22)
                c.addView(tv(ic, 30f).apply { gravity = Gravity.CENTER })
                c.addView(tv(t, 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
                c.addView(tv(p.first, 11f, DIM).apply { gravity = Gravity.CENTER })
                c.addView(tv("›", 14f, Color.WHITE).apply { gravity = Gravity.CENTER; background = circle(Color.TRANSPARENT, 1, Color.parseColor("#AA9B7CFF")) }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { topMargin = dp(6) })
                c.setOnClickListener { hap(c); p.second() }
                r.addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
            }
            l.addView(r, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(10), dp(5), dp(10), dp(5)) })
        }
        grid(listOf(
            Triple("💬", "Chat", "Ask anything" to { sub = 0; show(1) }),
            Triple("🧠", "Characters", "Change her look" to { enter(3, 0) }),
            Triple("🎨", "Orbs", "11 stunning styles" to { enter(2, 0) })))
        grid(listOf(
            Triple("🚀", "AI Tools", "Smart utilities" to { enter(5, 0) }),
            Triple("🧊", "AI Models", "Switch models" to { enter(6, 0) }),
            Triple("🕘", "Memory", "Your past chats" to { sub = 0; show(2) })))

        // quick access
        val qa = LinearLayout(this); qa.gravity = Gravity.CENTER_VERTICAL; qa.setPadding(dp(14), dp(12), dp(14), dp(12)); qa.background = glass(24)
        qa.addView(tv("⚡", 20f).apply { gravity = Gravity.CENTER; background = circle(Color.parseColor("#2A1F55")) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val qc = LinearLayout(this); qc.orientation = LinearLayout.VERTICAL; qc.setPadding(dp(12), 0, 0, 0)
        qc.addView(tv("Quick Access", 17f, Color.WHITE, true)); qc.addView(tv("Get things done faster...", 13f, DIM))
        qa.addView(qc, LinearLayout.LayoutParams(0, -2, 1f))
        qa.addView(tv("View All  →", 13f, Color.WHITE).apply { setPadding(dp(14), dp(8), dp(14), dp(8)); background = rounded(Color.TRANSPARENT, 20, 1, Color.parseColor("#AA9B7CFF")) })
        qa.setOnClickListener { hap(qa); enter(5, 0) }
        l.addView(qa, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(10), dp(14), dp(4)) })
        sv.addView(l); return sv
    }

    private fun refreshHome() {
        orb.style = Store.orb; homeOrb.style = Store.orb
        val bmp = loadChar(Store.chr)
        val lp = hero.layoutParams
        floatAnim?.cancel(); charImg.scaleX = 1f; charImg.scaleY = 1f
        if (bmp != null) {
            heroBmp = bmp; charImg.setImageBitmap(bmp); avatar.setImageBitmap(bmp)
            fade.visibility = View.VISIBLE; lp.height = dp(440)
            charImg.post {
                fitTop()
                floatAnim = android.animation.ObjectAnimator.ofFloat(charImg, "scaleX", 1f, 1.035f).apply {
                    duration = 2600; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
                    addUpdateListener { charImg.scaleY = charImg.scaleX }; start()
                }
            }
        } else {
            heroBmp = null; charImg.setImageDrawable(null); avatar.setImageDrawable(null); fade.visibility = View.GONE; lp.height = dp(330)
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

    // ---------- full-screen orb ----------
    private fun chip(t: String): TextView {
        val x = tv(t, 13f, DIM); x.gravity = Gravity.CENTER; x.setPadding(dp(14), dp(8), dp(14), dp(8))
        x.background = rounded(Color.parseColor("#33100A30"), 20, 1, Color.parseColor("#443A2F6B")); chips.add(x); return x
    }

    private fun buildOverlay(): FrameLayout {
        val f = FrameLayout(this); f.visibility = View.GONE; f.isClickable = true
        f.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#F5030210"), Color.parseColor("#F5160A3A")))
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(dp(16), dp(12), dp(16), dp(24))
        val hdr = LinearLayout(this); hdr.gravity = Gravity.CENTER_VERTICAL
        val hl = LinearLayout(this); hl.orientation = LinearLayout.VERTICAL; hl.setPadding(dp(14), dp(8), dp(14), dp(8)); hl.background = rounded(Color.parseColor("#90100A30"), 16, 1, EDGE)
        hl.addView(tv("✦ Ruhi AI", 18f, Color.WHITE, true)); hl.addView(tv("Your Ultimate AI Assistant ♡", 11f, DIM))
        hdr.addView(hl, LinearLayout.LayoutParams(0, -2, 1f))
        hdr.addView(tv("●  Online   |   👑 Premium", 12f, Color.parseColor("#E6DFFF")).apply { setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(Color.parseColor("#90100A30"), 16, 1, EDGE) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(hdr)
        col.addView(orb, LinearLayout.LayoutParams(-1, 0, 1f))
        status.gravity = Gravity.CENTER; subStatus.gravity = Gravity.CENTER
        col.addView(status, LinearLayout.LayoutParams(-1, -2)); col.addView(subStatus, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val cr = LinearLayout(this); cr.gravity = Gravity.CENTER
        for (t in listOf("🎙 Listening", "🧠 Thinking", "🔊 Speaking")) cr.addView(chip(t), LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
        col.addView(cr, LinearLayout.LayoutParams(-1, -2))
        val r = LinearLayout(this); r.gravity = Gravity.CENTER
        r.addView(btn("▾  Minimize") { showOverlay(false) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        r.addView(btn("⏹  End") { toggle() }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        col.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        f.addView(col, FrameLayout.LayoutParams(-1, -1))
        return f
    }

    private fun showOverlay(v: Boolean) {
        if (v) { overlay.alpha = 0f; overlay.visibility = View.VISIBLE; overlay.animate().alpha(1f).setDuration(220).start() }
        else if (overlay.visibility == View.VISIBLE) overlay.animate().alpha(0f).setDuration(180).withEndAction { overlay.visibility = View.GONE }.start()
    }

    private fun micTap() { if (live) showOverlay(true) else toggle() }

    // 0 idle, 1 listening, 2 speaking, 3 thinking
    private fun setState(s: Int) {
        orb.state = s; homeOrb.state = s
        when (s) {
            1 -> { status.text = "Listening..."; subStatus.text = "Speak now..." }
            2 -> { status.text = "Speaking..."; subStatus.text = "Ruhi bol rahi hai" }
            3 -> { status.text = "Thinking..."; subStatus.text = "Bas ek second..." }
            else -> { status.text = "Tap to Talk"; subStatus.text = "Speak or type anything..." }
        }
        for (i in chips.indices) {
            val on = chipState[i] == s
            chips[i].setTextColor(if (on) Color.WHITE else DIM)
            chips[i].background = rounded(if (on) Color.parseColor("#667C4DFF") else Color.parseColor("#33100A30"), 20, 1, if (on) Color.parseColor("#CCB58CFF") else Color.parseColor("#443A2F6B"))
        }
    }

    // ---------- voice loop ----------
    private fun toggle() {
        if (live) { live = false; speaker.stop(); rec?.cancel(); idle() } else { live = true; fails = 0; Sfx.ping(); showOverlay(true); listen() }
    }

    private fun idle() { setState(0); showOverlay(false) }

    private fun listen() {
        if (!live) return
        setState(1)
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
                    ui.postDelayed({ listen() }, 600)
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
        // jaldi sunna band karo taaki jawab tez shuru ho
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        rec!!.startListening(i)
    }

    private fun ask(q: String) {
        thinking = true; setState(3)
        log.add("Tum: $q"); Store.addHist(q)
        val act = Actions.run(this, q)
        if (act != null) { log.add("Ruhi: $act"); thinking = false; setState(2); speaker.say(act); return }
        log.add("Ruhi: ")
        val idx = log.size - 1
        Brain.ask(q,
            { s -> ui.post { setState(2); log[idx] = log[idx] + s + " "; speaker.say(s); if (curTab == 1) show(1) } },
            { ui.post { thinking = false; if (!speaker.busy()) { if (live) listen() else idle() } } },
            { m -> ui.post { thinking = false; log[idx] = "Ruhi: ($m)"; setState(0); status.text = m; speaker.say("Sumit, नेट या key में कुछ गड़बड़ है"); if (curTab == 1) show(1) } })
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
        e.background = rounded(CARD, 26, 1, EDGE)
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
        l.addView(tv("Memory", 26f, Color.WHITE, true).apply { setPadding(dp(14), 0, 0, dp(8)) })
        val h = Store.hist()
        if (h.isEmpty()) l.addView(tv("Abhi kuch nahi", 15f, DIM).apply { setPadding(dp(14), 0, 0, 0) })
        for (s in h) { val c = card(); c.addView(tv(s, 15f)); l.addView(c) }
        sv.addView(l); return sv
    }

    // ---------- settings ----------
    private fun settingsMain() = page("Settings", false) { l ->
        l.addView(tv("AI SETTINGS", 13f, DIM).apply { setPadding(dp(20), 0, 0, 0) })
        val c = card()
        c.addView(row("🔑", "Connections", "Groq key") { enter(1, 3) })
        c.addView(row("🎙", "Voice", "Female voice, speed, pitch") { enter(7, 3) })
        c.addView(row("🧊", "AI Model", if (Store.model == 0) "Fast (llama 3.1 8B)" else "Smart (llama 3.3 70B)") { enter(6, 3) })
        c.addView(row("🎨", "Look & feel", "Orb style: ${OrbView.names[Store.orb % OrbView.names.size]}") { enter(2, 3) })
        c.addView(row("🧑", "Home character", "Photo on Home screen") { enter(3, 3) })
        l.addView(c)
        l.addView(tv("APP SETTINGS", 13f, DIM).apply { setPadding(dp(20), dp(12), 0, 0) })
        val c2 = card()
        c2.addView(row("📱", "Phone control", "Permissions and background mode") { enter(4, 3) })
        l.addView(c2)
    }

    private fun keyCard(l: LinearLayout, t: String, s: String, ready: Boolean, b: String, save: (String) -> Unit) {
        val c = card(); c.addView(tv(t, 18f))
        c.addView(tv(if (ready) "✅ Ready" else "⚠ Not set", 13f, if (ready) Color.parseColor("#4FE3C1") else Color.parseColor("#FFB020")))
        c.addView(tv(s, 13f, DIM))
        val e = EditText(this); e.hint = if (ready) "•••• saved (nayi paste karo)" else "Key paste karo"
        e.setHintTextColor(DIM); e.setTextColor(Color.WHITE); e.setSingleLine()
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.background = rounded(Color.parseColor("#1B1240"), 24)
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
        keyCard(l, "Groq keys", "Ruhi ka tez dimaag. console.groq.com se free key lo. Kai keys ho to comma se alag karo", Store.groq.isNotBlank(), "Save Groq keys") { Store.groq = it }
    }

    private fun models() = page("AI Models", true) { l ->
        val c = card()
        c.addView(row("⚡", "Fast", if (Store.model == 0) "✔ selected: llama 3.1 8B, sabse tez jawab" else "llama 3.1 8B, sabse tez jawab") { Store.model = 0; go(6) })
        c.addView(row("🧠", "Smart", if (Store.model == 1) "✔ selected: llama 3.3 70B, zyada samajhdaar" else "llama 3.3 70B, zyada samajhdaar") { Store.model = 1; go(6) })
        l.addView(c)
        val c2 = card(); c2.addView(tv("Tez reply chahiye to Fast rakho.", 14f, DIM)); l.addView(c2)
    }

    private fun tools() = page("AI Tools", true) { l ->
        val c = card()
        c.addView(tv("Ruhi se ye bolo:", 15f, DIM))
        for (s in listOf("▶  \"YouTube pe Arijit Singh ke gaane chalao\"", "📞  \"Call 98xxxxxxxx\"", "💬  \"WhatsApp 98xxxxxxxx pe message bhejo ...\"",
            "🧭  \"Raipur ka navigation lagao\"", "🔦  \"Torch on karo\"", "📶  \"WiFi kholo\" / \"Bluetooth kholo\"", "📱  \"Instagram kholo\" (koi bhi app)"))
            c.addView(tv(s, 15f).apply { setPadding(0, dp(8), 0, dp(8)) })
        l.addView(c)
    }

    private fun slider(l: LinearLayout, label: String, mn: Float, mx: Float, cur: Float, onSet: (Float) -> Unit) {
        val t = tv("$label: ${"%.2f".format(cur)}", 15f); t.setPadding(0, dp(10), 0, 0)
        val sb = SeekBar(this); sb.max = 100; sb.progress = ((cur - mn) / (mx - mn) * 100).toInt().coerceIn(0, 100)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                val v = mn + (mx - mn) * p / 100f
                t.text = "$label: ${"%.2f".format(v)}"
                if (u) onSet(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { speaker.apply() }
        })
        l.addView(t); l.addView(sb)
    }

    private fun voicePage() = page("Voice", true) { l ->
        val c = card()
        c.addView(tv("Ruhi sirf female Hindi voice mein bolti hai.", 14f, DIM))
        c.addView(tv("Abhi ki voice: " + speaker.currentName(), 14f, Color.WHITE).apply { setPadding(0, dp(8), 0, 0) })
        c.addView(btn("🔄  Dusri awaaz suno") {
            val n = speaker.nextVoice(); speaker.stop(); speaker.say("नमस्ते Sumit, मैं Ruhi हूँ। बताओ, क्या करना है?")
            Toast.makeText(this, n, Toast.LENGTH_SHORT).show(); go(7)
        })
        slider(c, "Speed", 0.7f, 1.4f, Store.rate) { Store.rate = it }
        slider(c, "Pitch", 0.9f, 1.5f, Store.pitch) { Store.pitch = it }
        c.addView(btn("▶  Test karo") { speaker.apply(); speaker.stop(); speaker.say("नमस्ते Sumit, मैं Ruhi हूँ। बताओ, क्या करना है?") })
        l.addView(c)
        val c2 = card()
        c2.addView(tv("Hindi voice na mile ya awaaz kharab lage to: Phone Settings > Text-to-speech > Google > Install voice data > Hindi (India) download karo.", 13f, DIM))
        c2.addView(btn("Text-to-speech settings kholo") {
            try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })
        l.addView(c2)
    }

    private fun look() = page("Look & feel", true) { l ->
        l.addView(tv("Orb style chuno", 14f, DIM).apply { setPadding(dp(20), 0, 0, dp(6)) })
        var i = 0
        while (i < OrbView.names.size) {
            val r = LinearLayout(this); r.setPadding(dp(10), 0, dp(10), 0)
            for (j in i until minOf(i + 2, OrbView.names.size)) {
                val cell = LinearLayout(this); cell.orientation = LinearLayout.VERTICAL; cell.gravity = Gravity.CENTER
                cell.background = rounded(CARD, 22, 2, if (Store.orb == j) PUR else Color.parseColor("#2A2550"))
                val o = OrbView(this); o.style = j
                cell.addView(o, LinearLayout.LayoutParams(dp(120), dp(120)))
                cell.addView(tv(OrbView.names[j], 15f).apply { setPadding(0, 0, 0, dp(10)) })
                cell.setOnClickListener { Store.orb = j; orb.style = j; homeOrb.style = j; go(2) }
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
