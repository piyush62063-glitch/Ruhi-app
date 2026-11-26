package com.sumit.ruhi

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import java.io.File

/**
 * "Hello Ruhi" ko phone ke andar hi (offline) pehchanta hai, isme koi beep nahi bajti.
 * Chalne ke liye 2 cheezein chahiye: Picovoice Access Key aur "Hello Ruhi" ki .ppn file.
 * Dono na hon to WakeService purane tarike (speech recognizer) se chalta rehta hai.
 */
class PorcupineEngine(private val ctx: Context, private val onHit: () -> Unit) {
    companion object {
        const val FILE = "hello_ruhi.ppn"
        @Volatile var lastError: String = ""

        fun keywordFile(c: Context): File = File(c.filesDir, FILE)

        @Volatile private var assetState = 0   // 0 = pata nahi, 1 = hai, 2 = nahi

        private fun hasAsset(c: Context): Boolean {
            if (assetState == 0) assetState = try { c.assets.open(FILE).close(); 1 } catch (e: Exception) { 2 }
            return assetState == 1
        }

        /** Access Key hai aur .ppn file (phone mein ya app ke assets mein) mili. */
        fun ready(c: Context): Boolean = Store.pvKey(c).isNotBlank() && (keywordFile(c).exists() || hasAsset(c))

        /** Assets mein rakhi .ppn ko app ke folder mein copy karta hai (agar pehle se na ho). */
        fun prepareFile(c: Context): Boolean {
            val f = keywordFile(c)
            if (f.exists() && f.length() > 0) return true
            return try {
                c.assets.open(FILE).use { ins -> f.outputStream().use { ins.copyTo(it) } }
                true
            } catch (e: Exception) { false }
        }
    }

    private var pm: PorcupineManager? = null
    private var running = false
    private var failUntil = 0L

    fun start(): Boolean {
        if (System.currentTimeMillis() < failUntil) return false
        if (!ready(ctx) || !prepareFile(ctx)) return false
        try {
            if (pm == null) {
                pm = PorcupineManager.Builder()
                    .setAccessKey(Store.pvKey(ctx))
                    .setKeywordPath(keywordFile(ctx).absolutePath)
                    .setSensitivity(0.7f)
                    .build(ctx, PorcupineManagerCallback { _ -> onHit() })
            }
            if (!running) {
                pm?.start()
                running = true
            }
            lastError = ""
            return true
        } catch (e: Exception) {
            lastError = e.message ?: "Porcupine start nahi hua"
            failUntil = System.currentTimeMillis() + 60000L
            running = false
            release()
            return false
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try { pm?.stop() } catch (e: Exception) { }
    }

    fun release() {
        running = false
        try { pm?.stop() } catch (e: Exception) { }
        try { pm?.delete() } catch (e: Exception) { }
        pm = null
    }
}
