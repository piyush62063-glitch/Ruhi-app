package com.sumit.ruhi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Siri jaisa background engine: app band ho tab bhi "Hello Ruhi" sunta rehta hai.
 * Beep/tune ko recognizer ke start/end par thodi der ke liye mute karke dabaya jata hai.
 */
class WakeService : Service() {

    companion object {
        @Volatile var instance: WakeService? = null

        fun start(c: Context) {
            try {
                val i = Intent(c, WakeService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) { }
        }

        fun stop(c: Context) {
            try { c.stopService(Intent(c, WakeService::class.java)) } catch (e: Exception) { }
        }

        private const val GREET = "hello|helo|hallo|hullo|halo|hey|hi|hai|ok|okay|हेलो|हैलो|हेल्लो|हलो|हाय|हाई|हे|ओके|अरे|सुनो"
        private const val NAME = "ruhi|rui|roohi|ruhee|ruhhi|rohi|ruchi|ruhy|रुही|रूही|रुहि|रूहि|रोही|रुचि|रुई"
        private val WAKE = Regex("(?:^| )(?:$GREET) ?(?:$NAME)(?: |$)")
        private val NAME_ONLY = Regex("^(?:$NAME)$")
        private val STOPS = setOf(
            "bas", "stop", "ruko", "rehne do", "thank you", "thanks", "shukriya", "dhanyawad", "theek hai", "bye",
            "बस", "रुको", "रहने दो", "धन्यवाद", "शुक्रिया", "थैंक यू", "ठीक है", "बाय"
        )
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var speaker: Speaker? = null
    private var wl: PowerManager.WakeLock? = null
    private lateinit var am: AudioManager

    private var running = false
    private var mode = 0          // 0 = "Hello Ruhi" ka intezar, 1 = command sun rahi, 2 = soch/bol rahi
    private var afterSpeak = 0    // bolne ke baad: 1 = agla command sunna, 0 = wapas wake mode
    private var offline = true
    private var fails = 0
    private var muted = false
    private val convo = ArrayList<Pair<String, String>>()

    private val loopRun = Runnable { listen() }
    private val unmuteRun = Runnable { unmute() }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (!startFg()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            speaker = Speaker(this, { onSpeakIdle() }, { if (WakeCtl.session) WakeCtl.update(WakeCtl.SPEAK) })
            speaker?.speed = Store.speed(this)
            speaker?.pitch = Store.pitch(this)
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ruhi:wake")
                wl?.setReferenceCounted(false)
                wl?.acquire()
            } catch (e: Exception) { }
            mode = 0
            retry(600)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        main.removeCallbacks(loopRun)
        main.removeCallbacks(unmuteRun)
        destroyRec()
        unmute()
        try { speaker?.shutdown() } catch (e: Exception) { }
        try { if (wl?.isHeld == true) wl?.release() } catch (e: Exception) { }
        WakeCtl.end()
        super.onDestroy()
    }

    private fun startFg(): Boolean {
        return try {
            val nm = getSystemService(NotificationManager::class.java)
            val b: Notification.Builder
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel("ruhi_wake", "Ruhi background", NotificationManager.IMPORTANCE_LOW)
                ch.setSound(null, null)
                ch.enableVibration(false)
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
                b = Notification.Builder(this, "ruhi_wake")
            } else b = Notification.Builder(this)
            val pi = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = b.setContentTitle("Ruhi sun rahi hai")
                .setContentText("Bolo: Hello Ruhi")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, n)
            true
        } catch (e: Exception) { false }
    }

    // ---------------------------------------------------------------- beep ko dabana
    private val beepStreams = intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_MUSIC)

    private fun mute() {
        if (muted) return
        muted = true
        for (s in beepStreams) {
            if (s == AudioManager.STREAM_MUSIC && am.isMusicActive) continue
            try { am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0) } catch (e: Exception) { }
        }
    }

    private fun unmute() {
        if (!muted) return
        muted = false
        for (s in beepStreams) {
            try { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) { }
        }
    }

    private fun scheduleUnmute(ms: Long) {
        main.removeCallbacks(unmuteRun)
        main.postDelayed(unmuteRun, ms)
    }

    // ---------------------------------------------------------------- sunne ka loop
    private fun retry(ms: Long) {
        main.removeCallbacks(loopRun)
        main.postDelayed(loopRun, ms)
    }

    private fun destroyRec() {
        try { rec?.cancel(); rec?.destroy() } catch (e: Exception) { }
        rec = null
    }

    private fun listen() {
        if (!running || WakeCtl.paused || mode == 2) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            WakeCtl.error = "Phone mein speech recognition nahi mila"
            retry(15000)
            return
        }
        destroyRec()
        val r = try { SpeechRecognizer.createSpeechRecognizer(this) } catch (e: Exception) { null }
        if (r == null) { retry(2500); return }
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { scheduleUnmute(550) }
            override fun onBeginningOfSpeech() { }
            override fun onRmsChanged(v: Float) { WakeCtl.level = ((v + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(b: ByteArray?) { }
            override fun onEndOfSpeech() { mute(); scheduleUnmute(900) }
            override fun onEvent(t: Int, p: Bundle?) { }
            override fun onPartialResults(p: Bundle?) { }
            override fun onError(code: Int) { handleError(code) }
            override fun onResults(res: Bundle?) {
                fails = 0
                val l: List<String> = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: ArrayList<String>()
                handleResults(l)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        if (offline && mode == 0) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        mute()
        scheduleUnmute(2500)
        try { r.startListening(i) } catch (e: Exception) { scheduleUnmute(300); retry(1500) }
    }

    private fun handleError(code: Int) {
        if (!running || WakeCtl.paused) return
        mute()
        scheduleUnmute(700)
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                if (mode == 1) backToWake() else retry(200)
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> retry(1200)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                WakeCtl.error = "Mic permission do"
                running = false
                stopSelf()
            }
            12, 13 -> { offline = false; retry(300) }
            else -> { fails++; retry(minOf(8000L, 700L * fails)) }
        }
    }

    private fun handleResults(list: List<String>) {
        if (!running || WakeCtl.paused) return
        mute()
        scheduleUnmute(500)
        if (mode == 0) {
            val cmd = findWake(list)
            if (cmd == null) { retry(150); return }
            onWake(cmd)
        } else if (mode == 1) {
            val t = if (list.isNotEmpty()) list[0].trim() else ""
            if (t.isBlank() || STOPS.contains(norm(t).trim())) { backToWake(); return }
            process(t)
        }
    }

    private fun norm(s: String): String =
        " " + s.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ").replace(Regex(" +"), " ").trim() + " "

    /** "Hello Ruhi ..." mile to uske baad ka command (khali ho sakta hai) deta hai, warna null. */
    private fun findWake(list: List<String>): String? {
        for (raw in list) {
            val s = norm(raw)
            val m = WAKE.find(s)
            if (m != null) return s.substring(minOf(s.length, m.range.last + 1)).trim()
            if (NAME_ONLY.matches(s.trim())) return ""
        }
        return null
    }

    // ---------------------------------------------------------------- session
    private fun onWake(cmd: String) {
        WakeCtl.begin()
        if (cmd.isNotBlank()) { process(cmd); return }
        mode = 2
        afterSpeak = 1
        unmute()
        val sp = speaker
        if (sp == null) { onSpeakIdle(); return }
        sp.begin(); sp.feed("Haan, bolo."); sp.finish()
    }

    private fun backToWake() {
        mode = 0
        afterSpeak = 0
        WakeCtl.end()
        retry(250)
    }

    private fun onSpeakIdle() {
        if (!running || WakeCtl.paused) return
        if (afterSpeak == 1) {
            mode = 1
            WakeCtl.update(WakeCtl.LISTEN)
            retry(350)
        } else backToWake()
    }

    private fun persona(): String =
        "Tum Ruhi ho, Sumit ki friendly female AI assistant. Hinglish (Roman Hindi + English) mein ek normal dost ki tarah baat karo: warm, natural, seedhi baat. " +
            "Koi pet name ya 'babu', 'baccha' jaisa shabd mat bolo. Tum female ho, isliye 'rahi hu', 'karungi' jaise forms use karo. " +
            "Tum bolkar jawab deti ho, isliye jawab chhota rakho (1-3 vakya). Emoji, markdown, stars aur lists mat use karo."

    private fun buildMsgs(): JSONArray {
        val a = JSONArray()
        a.put(JSONObject().put("role", "system").put("content", persona()))
        val from = maxOf(0, convo.size - 12)
        for (i in from until convo.size) a.put(JSONObject().put("role", convo[i].first).put("content", convo[i].second))
        return a
    }

    private fun reply(t: String, after: Int) {
        convo.add(Pair("assistant", t))
        Store.addHist(this, false, t)
        afterSpeak = after
        WakeCtl.update(WakeCtl.SPEAK, t)
        val sp = speaker
        if (sp == null) { onSpeakIdle(); return }
        sp.begin(); sp.feed("$t."); sp.finish()
    }

    private fun process(text: String) {
        mode = 2
        WakeCtl.update(WakeCtl.THINK, text)
        unmute()
        Store.addHist(this, true, text)
        convo.add(Pair("user", text))
        if (convo.size > 24) repeat(12) { convo.removeAt(0) }

        val local: String? = try { Actions.handle(this, text) } catch (e: Exception) { null }
        if (local != null) { reply(local, 0); return }

        val sp = speaker
        if (sp == null) { backToWake(); return }
        val key = Store.key(this)
        sp.begin()
        Thread {
            val sb = StringBuilder()
            var failed = false
            try {
                Brain.ask(key, Store.model(this), buildMsgs(), { m -> Store.setModel(this, m) }, { d ->
                    sb.append(d)
                    sp.feed(d)
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
                if (running) {
                    if (failed) {
                        WakeCtl.error = out
                        sp.feed("Mujhe Groq se jawab nahi mila.")
                        afterSpeak = 0
                    } else {
                        convo.add(Pair("assistant", out))
                        Store.addHist(this, false, out)
                        afterSpeak = 1
                    }
                    WakeCtl.update(WakeCtl.SPEAK, if (failed) "" else out)
                    sp.finish()
                }
            }
        }.start()
    }

    /** App ki voice screen khuli/band hone par: mic ka takraav na ho. */
    fun pauseChanged() {
        main.post {
            if (running) {
                if (WakeCtl.paused) {
                    main.removeCallbacks(loopRun)
                    destroyRec()
                    speaker?.stop()
                    unmute()
                    mode = 0
                    afterSpeak = 0
                    WakeCtl.end()
                } else {
                    speaker?.speed = Store.speed(this)
                    speaker?.pitch = Store.pitch(this)
                    mode = 0
                    retry(800)
                }
            }
        }
    }
}
