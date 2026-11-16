package com.sumit.ruhi

import android.content.Context
import android.content.SharedPreferences

object Store {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("ruhi7", 0) }
    var gemini: String
        get() = sp.getString("gem", "") ?: ""
        set(v) { sp.edit().putString("gem", v).apply() }
    var groq: String
        get() = sp.getString("groq", "") ?: ""
        set(v) { sp.edit().putString("groq", v).apply() }
    var orb: Int
        get() = sp.getInt("orb", 0)
        set(v) { sp.edit().putInt("orb", v).apply() }
    var chr: Int
        get() = sp.getInt("chr", 100)
        set(v) { sp.edit().putInt("chr", v).apply() }
    var voice: Int
        get() = sp.getInt("voice2", 1)
        set(v) { sp.edit().putInt("voice2", v).apply() }
    var chars: Int
        get() = sp.getInt("chars", 0)
        set(v) { sp.edit().putInt("chars", v).apply() }
    fun hist(): List<String> = (sp.getString("hist", "") ?: "").split("\n").filter { it.isNotBlank() }
    fun addHist(s: String) {
        val l = (listOf(s.replace("\n", " ")) + hist()).take(50)
        sp.edit().putString("hist", l.joinToString("\n")).apply()
    }
}
