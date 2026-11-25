package com.sumit.ruhi

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val BG = 0xFF02010A.toInt()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var speaker: Speaker
    private var rec: SpeechRecognizer? = null
    private var voiceOn = false
    private var busy = false
    private var askedExtra = false
    private var screen = "splash"
    private val convo = ArrayList<Pair<String, String>>()

    private var orb: StateView? = null
    private var gl: GlowView? = null
    private var gr: GlowView? = null
    private var gb: GlowView? = null
    private var errTv: TextView? = null
    private var chatList: LinearLayout? = null
    private var chatScroll: ScrollView? = null
    private var live: TextView? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        root = FrameLayout(this)
        root.setBackgroundColor(BG)
        setContentView(root)
        speaker = Speaker(this, { onSpeakIdle() }, { if (screen == "voice") setState(3) })
        speaker.speed = Store.speed(this)
        speaker.pitch = Store.pitch(this)
        showSplash()
        ensureWake()
    }

    override fun onPause() {
        super.onPause()
        if (screen == "voice") stopVoice()
    }

    override fun onResume() {
        super.onResume()
        startWakeIfReady()
    }

    private fun micOk(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startWakeIfReady() {
        if (micOk() && Store.wake(this)) WakeService.start(this)
    }

    private fun ensureWake() {
        val need = ArrayList<String>()
        if (!micOk()) need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            need.add("android.permission.POST_NOTIFICATIONS")
        if (need.isNotEmpty()) { requestPermissions(need.toTypedArray(), 9); return }
        startWakeIfReady()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { rec?.destroy() } catch (e: Exception) { }
        speaker.shutdown()
    }

    override fun onBackPressed() {
        if (screen == "home" || screen == "splash") super.onBackPressed()
        else { stopVoice(); showHome() }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 9) { startWakeIfReady(); return }
        if (code == 7) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
            else showErr("Mic permission do, tabhi sun paungi")
        }
    }

    private fun setScreen(v: View, name: String) {
        screen = name
        orb = null; gl = null; gr = null; gb = null; errTv = null
        chatList = null; chatScroll = null; live = null
        root.removeAllViews()
        root.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun pressDrawable(): StateListDrawable {
        val sl = StateListDrawable()
        val g = GradientDrawable()
        g.setColor(0x33B58CFF)
        g.cornerRadius = 40f
        sl.addState(intArrayOf(android.R.attr.state_pressed), g)
        sl.addState(intArrayOf(), ColorDrawable(0))
        return sl
    }

    private fun hit(p: FracLayout, l: Float, t: Float, r: Float, b: Float, f: () -> Unit) {
        val v = View(this)
        v.background = pressDrawable()
        v.setOnClickListener { f() }
        p.add(v, l, t, r, b)
    }

    private fun image(res: Int): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(res)
        iv.scaleType = ImageView.ScaleType.FIT_XY
        return iv
    }

    // ---------------------------------------------------------------- SPLASH
    private fun showSplash() {
        val p = FracLayout(this, 941f, 1672f)
        p.add(image(R.drawable.splash_bg), 0f, 0f, 941f, 1672f)
        val pv = ProgressView(this)
        p.add(pv, 130f, 1105f, 815f, 1213f)
        setScreen(p, "splash")
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 2800
        a.addUpdateListener {
            val v = it.animatedValue as Float
            pv.p = v
            pv.label = if (v < 0.4f) "Initializing Ruhi AI..." else if (v < 0.8f) "Loading voice & brain..." else "Almost ready..."
            pv.invalidate()
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: android.animation.Animator) { if (screen == "splash") showHome() }
        })
        a.start()
    }

    // ---------------------------------------------------------------- HOME
    private fun showHome() {
        val p = FracLayout(this, 841f, 1808f)
        p.add(image(R.drawable.home_bg), 0f, 0f, 841f, 1808f)
        hit(p, 523f, 28f, 602f, 106f) { toast("Pro active hai") }
        hit(p, 625f, 28f, 700f, 106f) { toast("Koi nayi notification nahi") }
        hit(p, 725f, 23f, 812f, 110f) { showSettings() }
        hit(p, 285f, 470f, 555f, 745f) { openVoice() }
        hit(p, 45f, 728f, 797f, 923f) { openVoice() }
        hit(p, 28f, 973f, 268f, 1203f) { showChat() }
        hit(p, 293f, 973f, 548f, 1203f) { toast("Characters jaldi aa rahe hain") }
        hit(p, 573f, 973f, 813f, 1203f) { openVoice() }
        hit(p, 28f, 1233f, 268f, 1443f) { showTools() }
        hit(p, 293f, 1233f, 548f, 1443f) { showModels() }
        hit(p, 573f, 1233f, 813f, 1443f) { showHistory() }
        hit(p, 28f, 1478f, 812f, 1585f) { showTools() }
        hit(p, 205f, 1623f, 325f, 1763f) { showChat() }
        hit(p, 340f, 1590f, 500f, 1750f) { openVoice() }
        hit(p, 515f, 1623f, 635f, 1763f) { showHistory() }
        hit(p, 670f, 1623f, 790f, 1763f) { showSettings() }
        setScreen(p, "home")
        if (Store.key(this).isBlank()) toast("Pehle Settings mein Groq key daalo")
    }

    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }

    // ---------------------------------------------------------------- VOICE / ORB
    private fun openVoice() {
        showVoice()
        startVoice()
    }

    private fun showVoice() {
        val oy = 300f
        val p = FracLayout(this, 1254f, 1950f)
        p.add(image(R.drawable.orb_bg), 0f, oy, 1254f, oy + 1254f)
        val l = GlowView(this); val r = GlowView(this); val bt = GlowView(this)
        p.add(l, 20f, oy + 380f, 320f, oy + 740f)
        p.add(r, 940f, oy + 380f, 1235f, oy + 740f)
        p.add(bt, 310f, oy + 745f, 945f, oy + 905f)
        val sv = StateView(this)
        p.add(sv, 395f, oy + 510f, 860f, oy + 745f)
        hit(p, 300f, oy + 180f, 960f, oy + 500f) { if (voiceOn) stopVoice() else startVoice() }
        val e = FitText(this, 0.30f)
        e.setTextColor(0xFFFF9BD6.toInt())
        p.add(e, 80f, 1580f, 1174f, 1660f)
        val mn = PillButton(this, "Minimize")
        mn.setOnClickListener { stopVoice(); showHome() }
        p.add(mn, 90f, 1700f, 610f, 1850f)
        val en = PillButton(this, "End")
        en.setOnClickListener { stopVoice() }
        p.add(en, 644f, 1700f, 1164f, 1850f)
        setScreen(p, "voice")
        orb = sv; gl = l; gr = r; gb = bt; errTv = e
        setState(0)
    }

    private fun setState(s: Int) {
        orb?.state = s
        gl?.on = (s == 1); gr?.on = (s == 2); gb?.on = (s == 3)
        orb?.invalidate(); gl?.invalidate(); gr?.invalidate(); gb?.invalidate()
    }

    private fun showErr(m: String) { errTv?.text = m }

    private fun startVoice() {
        voiceOn = true
        WakeCtl.paused = true
        showErr("")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 7)
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED && !askedExtra) {
            askedExtra = true
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 8)
        }
        listen()
    }

    private fun stopVoice() {
        voiceOn = false
        WakeCtl.paused = false
        try { rec?.cancel(); rec?.destroy() } catch (e: Exception) { }
        rec = null
        speaker.stop()
        setState(0)
    }

    private fun listen() {
        if (!voiceOn || busy || screen != "voice") return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showErr("Phone mein speech recognition nahi mila"); voiceOn = false; setState(0); return
        }
        try { rec?.destroy() } catch (e: Exception) { }
        setState(1)
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { }
            override fun onBeginningOfSpeech() { }
            override fun onRmsChanged(rms: Float) {
                val v = ((rms + 2f) / 12f).coerceIn(0f, 1f)
                orb?.let { it.level = it.level * 0.6f + v * 0.4f }
            }
            override fun onBufferReceived(b: ByteArray?) { }
            override fun onEndOfSpeech() { }
            override fun onEvent(t: Int, p: Bundle?) { }
            override fun onPartialResults(p: Bundle?) { }
            override fun onError(code: Int) {
                if (!voiceOn) return
                if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    main.postDelayed({ listen() }, 400)
                } else if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    main.postDelayed({ listen() }, 900)
                } else {
                    showErr("Voice error $code"); voiceOn = false; setState(0)
                }
            }
            override fun onResults(res: Bundle?) {
                val list = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val t = if (list != null && list.isNotEmpty()) list[0] else ""
                if (t.isBlank()) main.postDelayed({ listen() }, 300) else onUserText(t, true)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        try { r.startListening(i) } catch (e: Exception) { showErr("Mic start nahi hua"); voiceOn = false; setState(0) }
    }

    private fun onSpeakIdle() {
        if (screen == "voice" && voiceOn && !busy) {
            setState(1)
            main.postDelayed({ listen() }, 250)
        } else if (screen == "voice") setState(0)
    }

    private fun persona(spoken: Boolean): String =
        "Tum Ruhi ho, Sumit ki friendly female AI assistant. Hinglish (Roman Hindi + English) mein ek normal dost ki tarah baat karo: warm, natural, seedhi baat. " +
            "Koi pet name ya 'babu', 'baccha' jaisa shabd mat bolo. Tum female ho, isliye 'rahi hu', 'karungi' jaise forms use karo. " +
            (if (spoken) "Tum bolkar jawab deti ho, isliye jawab chhota rakho (1-3 vakya). " else "Jawab clear rakho, zarurat ho to thoda detail do. ") +
            "Emoji, markdown, stars aur lists mat use karo."

    private fun buildMsgs(spoken: Boolean): JSONArray {
        val a = JSONArray()
        a.put(JSONObject().put("role", "system").put("content", persona(spoken)))
        val from = maxOf(0, convo.size - 12)
        for (i in from until convo.size) a.put(JSONObject().put("role", convo[i].first).put("content", convo[i].second))
        return a
    }

    /** speak=true -> orb screen (bol kar), false -> chat screen (text) */
    private fun onUserText(text: String, speak: Boolean) {
        busy = true
        if (speak) setState(2)
        Store.addHist(this, true, text)
        convo.add(Pair("user", text))
        val local: String? = try { Actions.handle(this, text) } catch (e: Exception) { null }
        if (local != null) {
            finishReply(local, speak)
            return
        }
        val key = Store.key(this)
        if (speak) speaker.begin()
        Thread {
            val sb = StringBuilder()
            var failed = false
            try {
                Brain.ask(key, Store.model(this), buildMsgs(speak), { m -> Store.setModel(this, m) }, { d ->
                    sb.append(d)
                    if (speak) speaker.feed(d)
                    else { val snap = sb.toString(); main.post { live?.text = snap; scrollDown() } }
                })
            } catch (e: GroqErr) {
                failed = true
                sb.setLength(0)
                sb.append(if (e.code == 0) e.message ?: "Groq key daalo" else "Groq error " + e.code + ": " + e.message)
            } catch (e: Exception) {
                failed = true
                sb.setLength(0)
                sb.append("Network problem: " + (e.message ?: "internet check karo"))
            }
            val out = sb.toString()
            main.post {
                busy = false
                if (failed) {
                    voiceOn = false
                    showErr(out)
                    if (!speak) { live?.text = out; scrollDown() }
                    if (speak) { speaker.feed("Mujhe Groq se jawab nahi mila."); speaker.finish() }
                } else {
                    convo.add(Pair("assistant", out))
                    Store.addHist(this, false, out)
                    if (speak) speaker.finish() else { live?.text = out; scrollDown() }
                }
                if (!speak) live = null
            }
        }.start()
    }

    private fun finishReply(reply: String, speak: Boolean) {
        busy = false
        convo.add(Pair("assistant", reply))
        Store.addHist(this, false, reply)
        if (speak) { speaker.begin(); speaker.feed(reply + "."); speaker.finish() }
        else { live?.text = reply; live = null; scrollDown() }
    }

    // ---------------------------------------------------------------- COMMON UI
    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val g = GradientDrawable()
        g.cornerRadius = dp(20).toFloat()
        g.setColor(0xCC120E33.toInt())
        g.setStroke(dp(1), 0xFF4B3FD6.toInt())
        c.background = g
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(14), dp(8), dp(14), dp(8))
        c.layoutParams = lp
        return c
    }

    private fun label(t: String, size: Float, color: Int, bold: Boolean): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD)
        return v
    }

    private fun btn(t: String, f: () -> Unit): TextView {
        val v = label(t, 15f, Color.WHITE, true)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(18), dp(12), dp(18), dp(12))
        val g = GradientDrawable()
        g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        g.setColors(intArrayOf(0xFF7C4DFF.toInt(), 0xFFC13DFF.toInt()))
        g.cornerRadius = dp(24).toFloat()
        v.background = g
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        v.layoutParams = lp
        v.setOnClickListener { f() }
        return v
    }

    private fun navBar(cur: String): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(6), dp(6), dp(6), dp(6))
        val g = GradientDrawable()
        g.cornerRadius = dp(32).toFloat()
        g.setColor(0xFF0A0722.toInt())
        g.setStroke(dp(2), 0xFF6D4BFF.toInt())
        row.background = g
        fun item(emoji: String, name: String, key: String, f: () -> Unit) {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER
            val col = if (cur == key) 0xFFD9C8FF.toInt() else 0xFF8E88B8.toInt()
            c.addView(label(emoji, 18f, col, false))
            c.addView(label(name, 11f, col, cur == key))
            c.setOnClickListener { f() }
            row.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        item("\uD83C\uDFE0", "Home", "home") { showHome() }
        item("\uD83D\uDCAC", "Chat", "chat") { showChat() }
        val lotus = ImageView(this)
        lotus.setImageResource(R.drawable.lotus)
        lotus.scaleType = ImageView.ScaleType.FIT_CENTER
        lotus.setOnClickListener { openVoice() }
        row.addView(lotus, LinearLayout.LayoutParams(dp(62), dp(62)))
        item("\uD83D\uDD52", "History", "history") { showHistory() }
        item("\u2699\uFE0F", "Settings", "settings") { showSettings() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(12), dp(6), dp(12), dp(10))
        row.layoutParams = lp
        return row
    }

    private fun page(title: String, cur: String, body: View, scrollBody: Boolean): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        bg.setColors(intArrayOf(0xFF02010A.toInt(), 0xFF0E0A2A.toInt(), 0xFF1A0F3F.toInt()))
        col.background = bg
        val t = label(title, 24f, Color.WHITE, true)
        t.setPadding(dp(20), dp(16), dp(20), dp(6))
        col.addView(t)
        if (scrollBody) {
            val sc = ScrollView(this)
            sc.addView(body)
            col.addView(sc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            col.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        col.addView(navBar(cur))
        return col
    }

    private fun vbox(): LinearLayout {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.setPadding(0, 0, 0, dp(10))
        return v
    }

    // ---------------------------------------------------------------- CHAT
    private fun bubble(text: String, user: Boolean): TextView {
        val tv = label(text, 15f, Color.WHITE, false)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.maxWidth = dp(290)
        val g = GradientDrawable()
        g.cornerRadius = dp(18).toFloat()
        if (user) {
            g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
            g.setColors(intArrayOf(0xFF7C4DFF.toInt(), 0xFFB455F7.toInt()))
        } else {
            g.setColor(0xDD15123A.toInt())
            g.setStroke(dp(1), 0xFF3F37B8.toInt())
        }
        tv.background = g
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = if (user) Gravity.END else Gravity.START
        lp.setMargins(dp(12), dp(5), dp(12), dp(5))
        tv.layoutParams = lp
        return tv
    }

    private fun scrollDown() { chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) } }

    private fun showChat() {
        stopVoice()
        val list = vbox()
        val sc = ScrollView(this)
        sc.addView(list)
        for (m in Store.hist(this).takeLast(40)) list.addView(bubble(m.second, m.first))
        if (list.childCount == 0) list.addView(bubble("Hi Sumit! Kuch bhi poochho.", false))

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.addView(sc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(4), dp(12), dp(4))
        val et = EditText(this)
        et.hint = "Ask me anything..."
        et.setHintTextColor(0xFF8E88B8.toInt())
        et.setTextColor(Color.WHITE)
        et.setPadding(dp(18), dp(10), dp(18), dp(10))
        val eg = GradientDrawable()
        eg.cornerRadius = dp(26).toFloat()
        eg.setColor(0xFF0B0828.toInt())
        eg.setStroke(dp(1), 0xFF4B3FD6.toInt())
        et.background = eg
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.maxLines = 3
        row.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val send = label("\u27A4", 26f, 0xFFB455F7.toInt(), true)
        send.setPadding(dp(14), dp(6), dp(6), dp(6))
        send.setOnClickListener {
            val t = et.text.toString().trim()
            if (t.isNotEmpty() && !busy) {
                et.setText("")
                list.addView(bubble(t, true))
                val lb = bubble("...", false)
                list.addView(lb)
                live = lb
                scrollDown()
                onUserText(t, false)
            }
        }
        row.addView(send)
        body.addView(row)
        val pg = page("Chat", "chat", body, false)
        setScreen(pg, "chat")
        chatList = list
        chatScroll = sc
        scrollDown()
    }

    // ---------------------------------------------------------------- HISTORY
    private fun showHistory() {
        stopVoice()
        val v = vbox()
        val h = Store.hist(this)
        if (h.isEmpty()) {
            val c = card()
            c.addView(label("Abhi koi chat nahi hai.", 15f, 0xFFB9B9DD.toInt(), false))
            v.addView(c)
        }
        for (m in h) v.addView(bubble(m.second, m.first))
        if (h.isNotEmpty()) {
            val b = btn("History saaf karo") { Store.clearHist(this); convo.clear(); showHistory() }
            val lp = b.layoutParams as LinearLayout.LayoutParams
            lp.setMargins(dp(14), dp(14), dp(14), dp(6))
            v.addView(b)
        }
        setScreen(page("History", "history", v, true), "history")
    }

    // ---------------------------------------------------------------- TOOLS
    private fun showTools() {
        stopVoice()
        val v = vbox()
        val lines = listOf(
            "YouTube", "\"YouTube pe arijit ke gaane chalao\"",
            "WhatsApp", "\"WhatsApp kholo\" ya \"WhatsApp par hello bhejo\"",
            "Call", "\"call 9876543210\"",
            "Maps", "\"navigate Delhi\"",
            "Torch", "\"torch on karo\" / \"torch band karo\"",
            "Settings", "\"wifi\" ya \"bluetooth\" bolo",
            "Koi bhi app", "\"Instagram kholo\", \"Camera kholo\""
        )
        var i = 0
        while (i < lines.size) {
            val c = card()
            c.addView(label(lines[i], 16f, Color.WHITE, true))
            c.addView(label(lines[i + 1], 14f, 0xFFB9B9DD.toInt(), false))
            v.addView(c)
            i += 2
        }
        setScreen(page("AI Tools", "home", v, true), "tools")
    }

    // ---------------------------------------------------------------- MODELS
    private fun showModels() {
        stopVoice()
        val v = vbox()
        val c0 = card()
        val cur = Store.model(this)
        c0.addView(label("Abhi ka model", 13f, 0xFFB9B9DD.toInt(), false))
        c0.addView(label(if (cur.isBlank()) "Auto (404 aaye to khud badal leti hai)" else cur, 16f, Color.WHITE, true))
        v.addView(c0)
        val status = label("Groq se list la rahi hu...", 14f, 0xFFB9B9DD.toInt(), false)
        status.setPadding(dp(20), dp(8), dp(20), dp(8))
        v.addView(status)
        setScreen(page("AI Models", "home", v, true), "models")
        val key = Store.key(this)
        if (key.isBlank()) { status.text = "Pehle Settings mein Groq key daalo"; return }
        Thread {
            var names: List<String> = emptyList()
            var msg = ""
            try { names = Brain.models(key) } catch (e: GroqErr) { msg = "Groq error " + e.code + ": " + e.message } catch (e: Exception) { msg = "Network problem" }
            main.post {
                if (screen != "models") return@post
                if (msg.isNotEmpty()) { status.text = msg; return@post }
                status.text = "Model chuno:"
                val auto = btn("Auto (recommended)") { Store.setModel(this, ""); toast("Auto set"); showModels() }
                val ap = auto.layoutParams as LinearLayout.LayoutParams
                ap.setMargins(dp(14), dp(6), dp(14), dp(2))
                v.addView(auto)
                for (n in names) {
                    val b = btn(n) { Store.setModel(this, n); toast("$n set ho gaya"); showModels() }
                    val lp = b.layoutParams as LinearLayout.LayoutParams
                    lp.setMargins(dp(14), dp(6), dp(14), dp(2))
                    v.addView(b)
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- SETTINGS
    private fun showSettings() {
        stopVoice()
        val v = vbox()

        val c1 = card()
        c1.addView(label("Groq connection", 17f, Color.WHITE, true))
        c1.addView(label("console.groq.com se free key lo aur yahan paste karo.", 13f, 0xFFB9B9DD.toInt(), false))
        val et = EditText(this)
        et.setText(Store.key(this))
        et.hint = "gsk_..."
        et.setHintTextColor(0xFF6F6A99.toInt())
        et.setTextColor(Color.WHITE)
        et.setSingleLine(true)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        c1.addView(et)
        val res = label("", 13f, 0xFFFF9BD6.toInt(), false)
        c1.addView(btn("Save key") { Store.setKey(this, et.text.toString()); res.text = "Key save ho gayi"; toast("Saved") })
        c1.addView(btn("Test key") {
            val k = et.text.toString().trim()
            Store.setKey(this, k)
            res.text = "Check kar rahi hu..."
            Thread {
                var m = ""
                try { val n = Brain.models(k); m = "Key sahi hai, " + n.size + " models mile" }
                catch (e: GroqErr) { m = "Groq error " + e.code + ": " + e.message }
                catch (e: Exception) { m = "Network problem: " + (e.message ?: "") }
                main.post { res.text = m }
            }.start()
        })
        c1.addView(res)
        v.addView(c1)

        val c2 = card()
        c2.addView(label("Voice (female)", 17f, Color.WHITE, true))
        c2.addView(label("Speed", 13f, 0xFFB9B9DD.toInt(), false))
        val sp = SeekBar(this)
        sp.max = 100
        sp.progress = ((Store.speed(this) - 0.6f) / 0.8f * 100f).toInt().coerceIn(0, 100)
        c2.addView(sp)
        c2.addView(label("Pitch", 13f, 0xFFB9B9DD.toInt(), false))
        val pi = SeekBar(this)
        pi.max = 100
        pi.progress = ((Store.pitch(this) - 0.8f) / 0.7f * 100f).toInt().coerceIn(0, 100)
        c2.addView(pi)
        c2.addView(btn("Voice test karo") {
            val s = 0.6f + sp.progress / 100f * 0.8f
            val p = 0.8f + pi.progress / 100f * 0.7f
            Store.setVoice(this, s, p)
            speaker.speed = s
            speaker.pitch = p
            speaker.begin(); speaker.feed("Namaste Sumit, main Ruhi hu. Bataiye, kya kaam hai?"); speaker.finish()
        })
        v.addView(c2)

        val c3 = card()
        c3.addView(label("Model", 17f, Color.WHITE, true))
        val cur = Store.model(this)
        c3.addView(label(if (cur.isBlank()) "Auto" else cur, 13f, 0xFFB9B9DD.toInt(), false))
        c3.addView(btn("Model list dekho") { showModels() })
        v.addView(c3)

        val c5 = card()
        c5.addView(label("Hello Ruhi (background)", 17f, Color.WHITE, true))
        c5.addView(label("App band ho tab bhi 'Hello Ruhi' bolte hi Ruhi sunegi. Neeche ke teeno setting zaroor allow karo.", 13f, 0xFFB9B9DD.toInt(), false))
        var tg: TextView? = null
        val wakeText = { if (Store.wake(this)) "Wake word: ON (band karne ke liye tap)" else "Wake word: OFF (chalu karne ke liye tap)" }
        tg = btn(wakeText()) {
            val on = !Store.wake(this)
            Store.setWake(this, on)
            if (on) ensureWake() else WakeService.stop(this)
            tg?.text = wakeText()
        }
        c5.addView(tg)
        c5.addView(btn("Display over other apps: allow") {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))) }
            catch (e: Exception) { toast("Settings nahi khuli") }
        })
        c5.addView(btn("Battery: Ruhi ko unrestricted karo") {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (e: Exception) { toast("Settings nahi khuli") }
        })
        v.addView(c5)

        val c4 = card()
        c4.addView(label("Ruhi AI v16 (Part 1) \u2022 Made for Sumit", 14f, 0xFFB9B9DD.toInt(), false))
        v.addView(c4)
        setScreen(page("Settings", "settings", v, true), "settings")
    }
}
