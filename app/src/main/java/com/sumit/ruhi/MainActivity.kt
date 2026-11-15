package com.sumit.ruhi

import android.Manifest
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    lateinit var vm: Vm
    var foreground = false

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.setAvatar(u)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val t = this@MainActivity.tts
                val r = t?.setLanguage(Locale("hi", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t?.setLanguage(Locale.getDefault())
                }
                t?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { runOnUiThread { vm.afterSpeak() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { runOnUiThread { vm.afterSpeak() } }
                })
            }
        }
        vm = Vm(this)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE), 1)
        setContent { RuhiRoot(vm) }
    }

    fun pickPhoto() { picker.launch("image/*") }
    fun speak(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "ruhi") }
    fun stopSpeak() { tts?.stop() }

    override fun onResume() { super.onResume(); foreground = true }
    override fun onPause() { foreground = false; super.onPause() }

    override fun onDestroy() {
        tts?.shutdown()
        vm.stop()
        super.onDestroy()
    }
}
