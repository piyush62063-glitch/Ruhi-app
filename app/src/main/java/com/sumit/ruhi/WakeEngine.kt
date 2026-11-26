package com.sumit.ruhi

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * "Hello Ruhi" ko phone ke andar hi (offline) pehchanta hai: Vosk, koi key nahi, koi beep nahi.
 * Model APK ke assets/vosk-model mein hota hai (GitHub Actions build ke waqt download hota hai).
 * Model na ho ya kuch fail ho to WakeService purane tarike (speech recognizer) par chala jata hai.
 */
class WakeEngine(
    private val ctx: Context,
    private val onHit: () -> Unit,
    private val onChange: () -> Unit
) {
    companion object {
        private const val DIR = "vosk-model"
        private const val MARK = "vosk-model.ok"
        private const val RATE = 16000
        private const val CHUNK = 800            // 50 ms

        @Volatile var lastError: String = ""
        @Volatile var modelReady: Boolean = false
        @Volatile private var model: Model? = null
        @Volatile private var loading = false
        @Volatile private var assetState = 0     // 0 = pata nahi, 1 = hai, 2 = nahi
        private val lock = Any()

        // "hello ruhi" ko model aksar hello ruby / hello rue he / hello roohi sunta hai
        private val WAKE = Regex("\\b(?:hello|hallo|hullo|helo|hey|hi|hay)\\s+(r\\w{1,8})\\b")
        private val NAME = Regex("\\b(?:ruhi|roohi|rouhi|ruhee|roohee|ruhe)\\b")
        private val NOT_NAME = setOf(
            "really", "right", "ready", "rather", "read", "real", "reason", "remember", "rest", "run",
            "ran", "rich", "ride", "rock", "room", "round", "rule", "rice", "race", "rain", "red", "record"
        )

        fun matches(text: String): Boolean {
            val t = text.lowercase()
            if (NAME.containsMatchIn(t)) return true
            val m = WAKE.find(t) ?: return false
            return !NOT_NAME.contains(m.groupValues[1])
        }

        fun ready(c: Context): Boolean {
            if (assetState == 0) {
                assetState = try {
                    val l = c.assets.list(DIR)
                    if (l != null && l.isNotEmpty()) 1 else 2
                } catch (e: Exception) { 2 }
            }
            return assetState == 1
        }

        private fun copyAsset(am: AssetManager, src: String, dst: File) {
            val list = am.list(src)
            if (list != null && list.isNotEmpty()) {
                dst.mkdirs()
                for (n in list) copyAsset(am, "$src/$n", File(dst, n))
            } else {
                try {
                    dst.parentFile?.mkdirs()
                    am.open(src).use { i -> FileOutputStream(dst).use { o -> i.copyTo(o) } }
                } catch (e: Exception) {
                    dst.mkdirs()   // khali folder tha
                }
            }
        }
    }

    @Volatile private var running = false
    private var th: Thread? = null
    @Volatile private var failUntil = 0L

    private fun loadModel(): Boolean {
        synchronized(lock) {
            if (model != null) return true
            try {
                val dir = File(ctx.filesDir, DIR)
                val mark = File(ctx.filesDir, MARK)
                if (!mark.exists() || !dir.exists()) {
                    dir.deleteRecursively()
                    copyAsset(ctx.assets, DIR, dir)
                    mark.writeText("1")
                }
                model = Model(dir.absolutePath)
                modelReady = true
                lastError = ""
                return true
            } catch (e: Throwable) {
                lastError = e.message ?: e.toString()
                return false
            }
        }
    }

    /** true = engine ab sun raha hai. false = abhi taiyar nahi (model load ho raha / fail), purana tarika chale. */
    fun start(): Boolean {
        if (!ready(ctx)) return false
        if (SystemClock.uptimeMillis() < failUntil) return false
        if (running) return true
        if (model == null) {
            if (!loading) {
                loading = true
                Thread {
                    val ok = loadModel()
                    loading = false
                    if (!ok) failUntil = SystemClock.uptimeMillis() + 60000L
                    onChange()
                }.start()
            }
            return false
        }
        running = true
        val t = Thread { loop() }
        th = t
        t.start()
        return true
    }

    fun stop() {
        if (!running && th == null) return
        running = false
        val t = th
        th = null
        if (t != null && t !== Thread.currentThread()) {
            try { t.join(400) } catch (e: Exception) { }
        }
    }

    fun release() {
        stop()
    }

    private fun fail(msg: String) {
        lastError = msg
        failUntil = SystemClock.uptimeMillis() + 60000L
    }

    private fun loop() {
        var ar: AudioRecord? = null
        var rcg: Recognizer? = null
        var hit = false
        var failed = false
        try {
            val m = model
            if (m == null) throw IllegalStateException("Vosk model load nahi hua")
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufSize = maxOf(minBuf, CHUNK * 2 * 8)
            val a = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            ar = a
            if (a.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Mic start nahi hua")
            val r = Recognizer(m, RATE.toFloat())
            rcg = r
            a.startRecording()
            val buf = ShortArray(CHUNK)
            val pre = ArrayList<ShortArray>()
            var active = false
            var lastVoice = 0L
            var floor = 150f
            val born = SystemClock.uptimeMillis()
            while (running) {
                val n = a.read(buf, 0, CHUNK)
                if (n < 0) throw IllegalStateException("Mic read error $n")
                if (n == 0) continue
                var s = 0.0
                for (i in 0 until n) { val v = buf[i].toInt(); s += (v * v).toDouble() }
                val rms = sqrt(s / n).toFloat()
                val now = SystemClock.uptimeMillis()
                val thr = maxOf(320f, floor * 2.8f)
                if (rms > thr) lastVoice = now
                val voiced = lastVoice != 0L && now - lastVoice < 1500L
                if (voiced) {
                    if (!active) {
                        active = true
                        for (p in pre) r.acceptWaveForm(p, p.size)
                        pre.clear()
                    }
                    val done = r.acceptWaveForm(buf, n)
                    val txt = if (done) JSONObject(r.result).optString("text") else JSONObject(r.partialResult).optString("partial")
                    if (txt.isNotEmpty() && now - born > 500L && matches(txt)) { hit = true; break }
                } else {
                    if (active) { active = false; r.reset() }
                    floor = floor * 0.98f + rms * 0.02f
                    pre.add(buf.copyOf(n))
                    if (pre.size > 4) pre.removeAt(0)
                }
            }
        } catch (e: Throwable) {
            failed = true
            fail(e.message ?: e.toString())
        } finally {
            try { ar?.stop() } catch (e: Throwable) { }
            try { ar?.release() } catch (e: Throwable) { }
            try { rcg?.close() } catch (e: Throwable) { }
            running = false
        }
        if (hit) onHit() else if (failed) onChange()
    }
}
