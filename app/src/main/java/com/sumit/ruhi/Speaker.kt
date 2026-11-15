package com.sumit.ruhi

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class Speaker(ctx: Context, private val onDone: () -> Unit) {
    private class Item(val gen: Int, val text: String?)

    private val q = LinkedBlockingQueue<Item>()
    @Volatile private var gen = 0
    @Volatile private var track: AudioTrack? = null
    @Volatile private var latch: CountDownLatch? = null
    @Volatile private var ttsReady = false
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val worker: Thread

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
                tts?.setSpeechRate(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { latch?.countDown() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { latch?.countDown() }
                })
                ttsReady = true
            }
        }
        worker = Thread {
            try {
                while (true) {
                    val it = q.take()
                    if (it.gen != gen) continue
                    val text = it.text
                    if (text == null) {
                        val g = it.gen
                        main.post { if (g == gen) onDone() }
                    } else {
                        speakOne(text, it.gen)
                    }
                }
            } catch (e: InterruptedException) {
            }
        }
        worker.isDaemon = true
        worker.start()
    }

    /** Starts a fresh reply turn; stops anything currently playing. Main thread. */
    fun newTurn(): Int {
        gen++
        q.clear()
        stopNow()
        return gen
    }

    fun say(g: Int, s: String) { q.put(Item(g, s)) }
    fun endReply(g: Int) { q.put(Item(g, null)) }

    private fun stopNow() {
        try { tts?.stop() } catch (e: Exception) {}
        latch?.countDown()
        try {
            track?.pause()
            track?.flush()
            track?.stop()
        } catch (e: Exception) {}
    }

    fun shutdown() {
        gen++
        q.clear()
        stopNow()
        try { tts?.shutdown() } catch (e: Exception) {}
        worker.interrupt()
    }

    private fun speakOne(text: String, g: Int) {
        val eng = Store.voiceEngine
        var pcm: ByteArray? = null
        try {
            pcm = when {
                eng == 1 && Store.geminiKey.isNotBlank() -> geminiPcm(text)
                eng == 2 && Store.elevenKey.isNotBlank() -> elevenPcm(text)
                else -> null
            }
        } catch (e: Exception) {
            pcm = null
        }
        if (g != gen) return
        if (pcm != null && pcm.isNotEmpty()) playPcm(pcm, g) else androidSpeak(text)
    }

    private fun androidSpeak(text: String) {
        var w = 0
        while (!ttsReady && w < 60) {
            Thread.sleep(50)
            w++
        }
        val t = tts ?: return
        if (!ttsReady) return
        val l = CountDownLatch(1)
        latch = l
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
        l.await(25, TimeUnit.SECONDS)
    }

    private fun playPcm(pcm: ByteArray, g: Int) {
        val minBuf = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(24000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = at
        try {
            at.play()
            var off = 0
            while (off < pcm.size && g == gen) {
                val n = at.write(pcm, off, minOf(4096, pcm.size - off))
                if (n <= 0) break
                off += n
            }
            val total = pcm.size / 2
            var guard = 0
            while (g == gen && at.playbackHeadPosition < total && guard < 400) {
                Thread.sleep(25)
                guard++
            }
        } finally {
            try { at.stop() } catch (e: Exception) {}
            at.release()
            track = null
        }
    }

    private fun geminiPcm(text: String): ByteArray? {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(
                JSONObject().put("text", "Say in a warm, caring, friendly girl's voice: $text")))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put(
                    "prebuiltVoiceConfig", JSONObject().put("voiceName", Store.geminiVoice)))))
            .toString()
        val res = Net.post(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=${Store.geminiKey}",
            body, emptyMap(), 25000
        )
        if (res.first != 200) return null
        val b64 = JSONObject(String(res.second)).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getJSONObject("inlineData").getString("data")
        return Base64.decode(b64, Base64.DEFAULT)
    }

    private fun elevenPcm(text: String): ByteArray? {
        val body = JSONObject().put("text", text).put("model_id", "eleven_flash_v2_5").toString()
        val res = Net.post(
            "https://api.elevenlabs.io/v1/text-to-speech/${Store.elevenVoiceId}?output_format=pcm_24000",
            body, mapOf("xi-api-key" to Store.elevenKey), 25000
        )
        return if (res.first == 200) res.second else null
    }
}
