package com.sumit.ruhi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Vm(private val act: MainActivity) {
    private val sp = act.getSharedPreferences("ruhi", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var rec: SpeechRecognizer? = null
    private var listening = false

    var screen by mutableStateOf("home")
    var name by mutableStateOf(sp.getString("name", "Sumit") ?: "Sumit")
    var key by mutableStateOf(sp.getString("key", "") ?: "")
    var voiceOn by mutableStateOf(sp.getBoolean("voice", true))
    var phase by mutableStateOf("idle")
    var partial by mutableStateOf("")
    var note by mutableStateOf("")
    var muted by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var lastReply by mutableStateOf("")
    var draft by mutableStateOf("")
    var avatar by mutableStateOf<ImageBitmap?>(null)
    val msgs = mutableStateListOf<Pair<Boolean, String>>()

    init {
        avatar = loadAvatar()
        try {
            val a = JSONArray(sp.getString("chats", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                msgs.add(o.getBoolean("u") to o.getString("t"))
            }
        } catch (e: Exception) { }
    }

    private fun persist() {
        val a = JSONArray()
        msgs.takeLast(60).forEach { a.put(JSONObject().put("u", it.first).put("t", it.second)) }
        sp.edit().putString("chats", a.toString()).apply()
    }

    fun pickPhoto() { act.pickPhoto() }

    fun clearChats() { msgs.clear(); lastReply = ""; persist() }

    fun save(n: String, k: String, v: Boolean) {
        name = n.trim().ifBlank { "Sumit" }
        key = k.trim()
        voiceOn = v
        sp.edit().putString("name", name).putString("key", key).putBoolean("voice", v).apply()
    }

    private fun loadAvatar(): ImageBitmap? = try {
        val f = File(act.filesDir, "avatar.jpg")
        if (!f.exists()) null else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var s = 1
            while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        }
    } catch (e: Exception) { null }

    fun setAvatar(u: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    act.contentResolver.openInputStream(u)?.use { i ->
                        File(act.filesDir, "avatar.jpg").outputStream().use { o -> i.copyTo(o) }
                    }
                    true
                } catch (e: Exception) { false }
            }
            if (ok) avatar = loadAvatar()
        }
    }

    fun startVoice() { screen = "listen"; muted = false; listen() }

    fun toggleMute() {
        muted = !muted
        if (muted) {
            rec?.cancel(); listening = false; phase = "idle"; act.stopSpeak()
        } else listen()
    }

    fun listen() {
        if (listening || busy || muted) return
        note = ""; partial = ""
        if (act.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            note = "Mic permission do (phone Settings > Apps > Ruhi > Permissions)"; phase = "idle"; return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(act)) {
            note = "Is phone mein voice recognition nahi mila"; phase = "idle"; return
        }
        act.stopSpeak()
        phase = "connect"
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(act)
        rec?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { listening = true; phase = "listen" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(e: Int) {
                listening = false
                phase = "idle"
                note = if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    "Kuch sunai nahi diya. Orb dabao aur phir bolo." else "Voice error $e. Orb dabao aur phir try karo."
            }
            override fun onResults(r: Bundle?) {
                listening = false
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) { phase = "idle"; note = "Kuch sunai nahi diya. Phir bolo." } else send(t)
            }
            override fun onPartialResults(p: Bundle?) {
                p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        rec?.startListening(i)
    }

    fun stop() {
        rec?.destroy(); rec = null
        listening = false; phase = "idle"; muted = false
        act.stopSpeak()
    }

    fun afterSpeak() {
        if (phase == "speak") {
            phase = "idle"
            if (screen == "listen" && !muted && act.foreground) listen()
        }
    }

    fun send(text: String) {
        msgs.add(true to text)
        persist()
        note = ""; partial = ""
        val local = Actions.handle(act, text)
        if (local != null) { reply(local); return }
        busy = true
        phase = "think"
        val hist = msgs.toList()
        scope.launch {
            val r = withContext(Dispatchers.IO) { Brain.ask(key, name, hist) }
            busy = false
            reply(r)
        }
    }

    private fun reply(r: String) {
        msgs.add(false to r)
        persist()
        lastReply = r
        if (!voiceOn) { phase = "idle"; return }
        phase = "speak"
        scope.launch {
            val pcm = withContext(Dispatchers.IO) { Brain.speechPcm(key, r) }
            if (phase != "speak") return@launch
            if (pcm != null) act.playPcm(pcm) else act.speak(r)
        }
    }
}
