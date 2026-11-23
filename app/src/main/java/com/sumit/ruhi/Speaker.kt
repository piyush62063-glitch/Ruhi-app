package com.sumit.ruhi

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object Sfx {
    fun ping() {
        Thread {
            try {
                val sr = 22050; val n = sr * 14 / 100
                val b = ShortArray(n) { i -> (Math.sin(2 * Math.PI * 880 * i / sr) * Math.sin(Math.PI * i / n) * 8000).toInt().toShort() }
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(n * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                t.write(b, 0, n); t.play(); Thread.sleep(240); t.release()
            } catch (e: Exception) { }
        }.start()
    }
}

// Sirf phone ki female Hindi voice: turant bolti hai, vaakya vaakya queue hota hai
class Speaker(ctx: Context, private val onIdle: () -> Unit) {
    private val pending = AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var ok = false
    private val early = ArrayList<String>()
    private val FEM = listOf("hia", "cfn", "hic", "female")
    private val MALE = listOf("hid", "hie", "male")
    private val tts: TextToSpeech = TextToSpeech(ctx) { s ->
        if (s == TextToSpeech.SUCCESS) { ok = true; setup(); flushEarly() }
    }

    private fun setup() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { fin() }
            override fun onError(id: String?) { fin() }
        })
        apply()
    }

    private fun fin() { if (pending.decrementAndGet() <= 0) { pending.set(0); main.post { onIdle() } } }

    fun hindiVoices(): List<Voice> {
        val vs = try { tts.voices?.filter { it.locale.language == "hi" } ?: emptyList() } catch (e: Exception) { emptyList() }
        fun score(v: Voice): Int {
            val n = v.name.lowercase()
            var s = 0
            if (FEM.any { n.contains(it) }) s += 4
            if (MALE.any { n.contains(it) }) s -= 4
            if (!v.isNetworkConnectionRequired) s += 2
            return -s
        }
        return vs.sortedBy { score(it) }
    }

    fun currentName(): String = try { tts.voice?.name ?: "default" } catch (e: Exception) { "default" }

    fun apply() {
        if (!ok) return
        try {
            val vs = hindiVoices()
            val pick = vs.firstOrNull { it.name == Store.voiceName } ?: vs.firstOrNull()
            if (pick != null) tts.voice = pick else tts.language = Locale("hi", "IN")
            tts.setPitch(Store.pitch); tts.setSpeechRate(Store.rate)
        } catch (e: Exception) { }
    }

    fun nextVoice(): String {
        val vs = hindiVoices()
        if (vs.isEmpty()) return "Hindi voice nahi mili"
        val i = vs.indexOfFirst { it.name == currentName() }
        val n = vs[(i + 1) % vs.size]
        Store.voiceName = n.name
        apply()
        return n.name
    }

    private fun flushEarly() { val l = ArrayList(early); early.clear(); for (s in l) speakNow(s) }

    private fun speakNow(s: String) {
        tts.speak(s, TextToSpeech.QUEUE_ADD, null, "u" + System.nanoTime())
    }

    fun say(t: String) {
        val s = t.replace(Regex("[\\p{So}\\p{Cn}*_#`~]"), "").replace(Regex("\\s{2,}"), " ").trim()
        if (s.isEmpty()) return
        pending.incrementAndGet()
        if (ok) speakNow(s) else early.add(s)
    }

    fun busy() = pending.get() > 0
    fun stop() {
        pending.set(0); early.clear()
        try { tts.stop() } catch (e: Exception) { }
    }
    fun shutdown() { try { tts.shutdown() } catch (e: Exception) { } }
}
