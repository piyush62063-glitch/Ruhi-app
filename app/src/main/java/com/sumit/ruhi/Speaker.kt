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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
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

class Speaker(ctx: Context, private val onIdle: () -> Unit) {
    private val n = AtomicInteger(0)
    @Volatile private var gen = 0
    @Volatile private var ok = false
    @Volatile private var latch: CountDownLatch? = null
    @Volatile private var cur: AudioTrack? = null
    private val pool = Executors.newFixedThreadPool(3)
    private val q = LinkedBlockingQueue<Pair<String, Future<ByteArray?>>>()
    private val tts: TextToSpeech = TextToSpeech(ctx) { s -> if (s == TextToSpeech.SUCCESS) { ok = true; setup() } }

    init { Thread { loop() }.apply { isDaemon = true; start() } }

    private fun setup() {
        if (tts.setLanguage(Locale("hi", "IN")) < 0) tts.setLanguage(Locale("en", "IN"))
        tts.setPitch(1.15f); tts.setSpeechRate(1.0f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { latch?.countDown() }
            override fun onError(id: String?) { latch?.countDown() }
        })
    }

    private fun loop() {
        while (true) {
            try {
                val (txt, fut) = q.take()
                val g = gen
                val pcm = try { fut.get(3500, TimeUnit.MILLISECONDS) } catch (e: Exception) { null }
                if (g == gen) { if (pcm != null) play(pcm, g) else phone(txt) }
                if (g == gen && n.decrementAndGet() <= 0) { n.set(0); Handler(Looper.getMainLooper()).post { onIdle() } }
            } catch (e: Exception) { }
        }
    }

    private fun phone(t: String) {
        if (!ok) return
        val l = CountDownLatch(1); latch = l
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
        l.await(20, TimeUnit.SECONDS)
    }

    private fun play(d: ByteArray, g: Int) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(d.size).setTransferMode(AudioTrack.MODE_STATIC).build()
        cur = t; t.write(d, 0, d.size); t.play()
        var ms = d.size / 48
        while (ms > 0 && g == gen) { Thread.sleep(50); ms -= 50 }
        try { t.stop() } catch (e: Exception) { }
        t.release(); cur = null
    }

    private fun kore(t: String): ByteArray? {
        return try {
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=" + Store.gemini).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 20000
            c.setRequestProperty("Content-Type", "application/json")
            val b = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Say in a soft, warm, loving female voice: $t")))))
                .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Kore")))))
            c.outputStream.use { it.write(b.toString().toByteArray()) }
            if (c.responseCode != 200) return null
            val r = JSONObject(c.inputStream.bufferedReader().readText())
            val d = r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getJSONObject("inlineData").getString("data")
            Base64.decode(d, Base64.DEFAULT)
        } catch (e: Exception) { null }
    }

    fun say(t: String) {
        val s = t.trim()
        if (s.isEmpty()) return
        n.incrementAndGet()
        val fut: Future<ByteArray?> = if (Store.voice == 0 && Store.gemini.isNotBlank()) pool.submit(Callable<ByteArray?> { kore(s) }) else pool.submit(Callable<ByteArray?> { null })
        q.put(s to fut)
    }

    fun busy() = n.get() > 0
    fun stop() {
        gen++; q.clear(); n.set(0)
        try { cur?.stop() } catch (e: Exception) { }
        try { tts.stop() } catch (e: Exception) { }
        latch?.countDown()
    }
    fun shutdown() { try { tts.shutdown() } catch (e: Exception) { } }
}
