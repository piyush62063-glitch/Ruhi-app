package com.sumit.ruhi

import android.content.Context
import android.content.SharedPreferences

object Store {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("ruhi7", 0) }
    var groq: String
        get() = sp.getString("groq", "") ?: ""
        set(v) { sp.edit().putString("groq", v).apply() }
    var model: Int
        get() = sp.getInt("model", 0)
        set(v) { sp.edit().putInt("model", v).apply() }
    var orb: Int
        get() = sp.getInt("orb14", 0)
        set(v) { sp.edit().putInt("orb14", v).apply() }
    var chr: Int
        get() = sp.getInt("chr14", 103)
        set(v) { sp.edit().putInt("chr14", v).apply() }
    var chars: Int
        get() = sp.getInt("chars", 0)
        set(v) { sp.edit().putInt("chars", v).apply() }
    var voiceName: String
        get() = sp.getString("vname", "") ?: ""
        set(v) { sp.edit().putString("vname", v).apply() }
    var rate: Float
        get() = sp.getFloat("rate", 1.05f)
        set(v) { sp.edit().putFloat("rate", v).apply() }
    var pitch: Float
        get() = sp.getFloat("pitch", 1.12f)
        set(v) { sp.edit().putFloat("pitch", v).apply() }
    fun hist(): List<String> = (sp.getString("hist", "") ?: "").split("\n").filter { it.isNotBlank() }
    fun addHist(s: String) {
        val l = (listOf(s.replace("\n", " ")) + hist()).take(50)
        sp.edit().putString("hist", l.joinToString("\n")).apply()
    }
}
