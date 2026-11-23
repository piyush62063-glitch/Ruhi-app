package com.sumit.ruhi

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Female Hindi voice; jawab ke pehle vakya se hi bolna shuru kar deti hai (fast). */
class Speaker(ctx: Context, private val idleCb: () -> Unit, private val startCb: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending = 0
    private var streaming = false
    private var first = true
    private var counter = 0
    private val buf = StringBuilder()
    var speed = 1.0f
    var pitch = 1.1f

    init {
        tts = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) {
                ready = true
                setup()
            }
        }
    }

    private fun setup() {
        val t = tts ?: return
        try { t.setLanguage(Locale("hi", "IN")) } catch (e: Exception) { }
        try {
            val vs = t.voices
            if (vs != null) {
                val hi = vs.filter { it.locale.language == "hi" }
                val pick = hi.firstOrNull { it.name.lowercase().contains("female") }
                    ?: hi.firstOrNull { val n = it.name.lowercase(); n.contains("hia") || n.contains("hic") || n.contains("cfn") }
                    ?: hi.firstOrNull()
                if (pick != null) t.setVoice(pick)
            }
        } catch (e: Exception) { }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { startCb() } }
            override fun onDone(utteranceId: String?) { done() }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) { done() }
        })
    }

    private fun done() {
        main.post {
            if (pending > 0) pending--
            if (pending <= 0 && !streaming) { pending = 0; idleCb() }
        }
    }

    private fun clean(s: String): String =
        s.replace(Regex("[\\uD800-\\uDFFF]"), "")
            .replace(Regex("[\\u2600-\\u27BF]"), "")
            .replace("*", "").replace("#", "").replace("_", " ").replace("`", "")

    private fun say(raw: String) {
        val t = tts
        val text = clean(raw).trim()
        if (text.isEmpty() || !ready || t == null) return
        main.post { pending++ }
        t.setSpeechRate(speed)
        t.setPitch(pitch)
        counter++
        t.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), "r$counter")
    }

    fun begin() { streaming = true; first = true; buf.setLength(0) }

    fun feed(d: String) {
        buf.append(d)
        while (true) {
            val s = buf.toString()
            var idx = -1
            for (i in s.indices) {
                val ch = s[i]
                if (ch == '.' || ch == '?' || ch == '!' || ch == '\u0964' || ch == '\n') { idx = i; break }
            }
            if (idx < 0 && first && s.length > 45) {
                val c = s.lastIndexOf(',')
                if (c > 18) idx = c
            }
            if (idx < 0) break
            val part = s.substring(0, idx + 1)
            buf.delete(0, idx + 1)
            first = false
            say(part)
        }
    }

    fun finish() {
        if (buf.isNotBlank()) say(buf.toString())
        buf.setLength(0)
        main.post {
            streaming = false
            if (pending <= 0) { pending = 0; idleCb() }
        }
    }

    fun stop() {
        try { tts?.stop() } catch (e: Exception) { }
        pending = 0
        streaming = false
        buf.setLength(0)
    }

    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
    }
}
