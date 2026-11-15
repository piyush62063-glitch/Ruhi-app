package com.sumit.ruhi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class RuhiService : Service() {
    companion object {
        const val A_START = "ruhi.start"
        const val A_STOP = "ruhi.stop"
        const val A_MUTE = "ruhi.mute"
        const val A_SKIP = "ruhi.skip"

        fun cmd(c: Context, a: String) {
            val i = Intent(c, RuhiService::class.java).setAction(a)
            if (a == A_START) c.startForegroundService(i) else c.startService(i)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var speaker: Speaker? = null
    private var running = false
    private var muted = false
    private var busy = false
    private var errs = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        when (i?.action) {
            A_STOP -> shutdown()
            A_MUTE -> {
                muted = !muted
                Hub.muted = muted
                if (muted) {
                    stopListen()
                    Hub.setState(Hub.IDLE)
                } else {
                    listen()
                }
                Hub.changed()
            }
            A_SKIP -> {
                speaker?.newTurn()
                busy = false
                stopListen()
                Hub.setState(Hub.IDLE)
                listen()
            }
            else -> if (!running) boot()
        }
        return START_NOT_STICKY
    }

    private fun boot() {
        running = true
        muted = false
        Hub.running = true
        Hub.muted = false
        Hub.status = ""
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ruhi", "Ruhi", NotificationManager.IMPORTANCE_LOW))
        val n = notif()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
        speaker = Speaker(this) {
            busy = false
            Hub.setState(Hub.IDLE)
            listen()
        }
        Hub.changed()
        listen()
    }

    private fun notif(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RuhiService::class.java).setAction(A_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "ruhi")
            .setContentTitle("Ruhi sun rahi hai")
            .setContentText("Wapas aane ke liye tap karo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop", stop
                ).build()
            )
            .build()
    }

    private fun listen() {
        main.post {
            if (!running || muted || busy) return@post
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    Hub.status = "Speech service nahi mili. Google app update/enable karo."
                    Hub.changed()
                    return@post
                }
                if (rec == null) {
                    rec = SpeechRecognizer.createSpeechRecognizer(this)
                    rec?.setRecognitionListener(listener)
                }
                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Store.lang)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                Hub.partial = ""
                Hub.setState(Hub.LISTENING)
                rec?.startListening(i)
            } catch (e: Exception) {
                resetRec()
                main.postDelayed({ listen() }, 1500)
            }
        }
    }

    private fun stopListen() {
        try { rec?.cancel() } catch (e: Exception) {}
    }

    private fun resetRec() {
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) { Hub.level = ((v + 2f) / 12f).coerceIn(0f, 1f) }
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() { Hub.level = 0f }
        override fun onError(e: Int) { handleError(e) }
        override fun onResults(b: Bundle?) {
            val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            errs = 0
            Hub.status = ""
            if (t.isEmpty()) listen() else handle(t)
        }
        override fun onPartialResults(b: Bundle?) {
            val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) {
                Hub.partial = t
                Hub.changed()
            }
        }
        override fun onEvent(t: Int, b: Bundle?) {}
    }

    private fun handleError(e: Int) {
        if (!running || busy || muted) return
        when (e) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                main.postDelayed({ listen() }, 250)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                Hub.status = "Mic permission do (phone Settings > Apps > Ruhi)."
                Hub.changed()
                shutdown()
            }
            else -> {
                resetRec()
                errs++
                if (errs >= 3) {
                    Hub.status = "Voice error $e. Internet ya Google voice service check karo."
                    Hub.changed()
                }
                main.postDelayed({ listen() }, if (errs >= 3) 2500L else 600L)
            }
        }
    }

    private fun handle(text: String) {
        val sp = speaker ?: return
        busy = true
        Hub.partial = ""
        Hub.level = 0f
        val g = sp.newTurn()
        Hub.setState(Hub.THINKING)
        val appCtx = applicationContext
        Thread {
            Core.process(appCtx, text, sp, g)
            sp.endReply(g)
        }.start()
    }

    private fun shutdown() {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        Hub.running = false
        Hub.muted = false
        Hub.partial = ""
        Hub.state = Hub.IDLE
        main.removeCallbacksAndMessages(null)
        resetRec()
        speaker?.shutdown()
        speaker = null
        Hub.changed()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) {
            running = false
            Hub.running = false
            resetRec()
            speaker?.shutdown()
            speaker = null
            Hub.changed()
        }
        super.onDestroy()
    }
}
