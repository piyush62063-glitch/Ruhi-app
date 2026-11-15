package com.sumit.ruhi

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class Speaker(ctx: Context, private val onIdle: () -> Unit) {
    private val n = AtomicInteger(0)
    private var ok = false
    private val tts: TextToSpeech = TextToSpeech(ctx) { s ->
        if (s == TextToSpeech.SUCCESS) { ok = true; setup() }
    }

    private fun setup() {
        if (tts.setLanguage(Locale("hi", "IN")) < 0) tts.setLanguage(Locale("en", "IN"))
        tts.setPitch(1.15f); tts.setSpeechRate(1.0f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { fin() }
            override fun onError(id: String?) { fin() }
        })
    }

    private fun fin() {
        if (n.decrementAndGet() <= 0) { n.set(0); Handler(Looper.getMainLooper()).post { onIdle() } }
    }

    fun say(t: String) {
        val s = t.trim()
        if (!ok || s.isEmpty()) return
        n.incrementAndGet()
        tts.speak(s, TextToSpeech.QUEUE_ADD, null, "u" + System.nanoTime())
    }

    fun busy() = n.get() > 0
    fun stop() { tts.stop(); n.set(0) }
    fun shutdown() { tts.shutdown() }
}
