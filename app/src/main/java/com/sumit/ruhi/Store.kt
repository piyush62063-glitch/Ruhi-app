package com.sumit.ruhi

import android.content.Context
import android.content.SharedPreferences

object Store {
    private var sp: SharedPreferences? = null

    fun init(c: Context) {
        if (sp == null) sp = c.applicationContext.getSharedPreferences("ruhi", Context.MODE_PRIVATE)
    }

    private fun s(k: String, d: String): String = sp?.getString(k, d) ?: d
    private fun put(k: String, v: String) { sp?.edit()?.putString(k, v)?.apply() }

    var userName: String
        get() = s("name", "Sumit")
        set(v) { put("name", v) }
    var groqKeys: String
        get() = s("groq", "")
        set(v) { put("groq", v) }
    var geminiKey: String
        get() = s("gemini", "")
        set(v) { put("gemini", v) }
    var elevenKey: String
        get() = s("eleven", "")
        set(v) { put("eleven", v) }
    var geminiVoice: String
        get() = s("gvoice", "Kore")
        set(v) { put("gvoice", v) }
    var elevenVoiceId: String
        get() = s("evoice", "EXAVITQu4vr4xnSDxMzb")
        set(v) { put("evoice", v) }
    var lang: String
        get() = s("lang", "hi-IN")
        set(v) { put("lang", v) }
    var orbStyle: Int
        get() = sp?.getInt("orb", 0) ?: 0
        set(v) { sp?.edit()?.putInt("orb", v)?.apply() }
    // 0 = phone voice (fastest), 1 = Gemini voice, 2 = ElevenLabs
    var voiceEngine: Int
        get() = sp?.getInt("engine", 0) ?: 0
        set(v) { sp?.edit()?.putInt("engine", v)?.apply() }
    var permAsked: Boolean
        get() = sp?.getBoolean("permAsked", false) ?: false
        set(v) { sp?.edit()?.putBoolean("permAsked", v)?.apply() }
}
