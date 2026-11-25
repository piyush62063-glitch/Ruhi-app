package com.sumit.ruhi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("ruhi", Context.MODE_PRIVATE)
    private fun kf(c: Context) = File(c.filesDir, "groq.key")

    /** Key phone mein 2 jagah save hoti hai (prefs + file) taaki dobara na maange. */
    fun key(c: Context): String {
        val a = sp(c).getString("groq", "") ?: ""
        if (a.isNotBlank()) return a
        val b = try { if (kf(c).exists()) kf(c).readText().trim() else "" } catch (e: Exception) { "" }
        if (b.isNotBlank()) sp(c).edit().putString("groq", b).apply()
        return b
    }

    fun setKey(c: Context, k: String) {
        val t = k.trim()
        sp(c).edit().putString("groq", t).apply()
        try { kf(c).writeText(t) } catch (e: Exception) { }
    }

    fun model(c: Context): String = sp(c).getString("model", "") ?: ""
    fun setModel(c: Context, m: String) { sp(c).edit().putString("model", m).apply() }
    fun speed(c: Context): Float = sp(c).getFloat("speed", 1.0f)
    fun pitch(c: Context): Float = sp(c).getFloat("pitch", 1.1f)
    fun setVoice(c: Context, speed: Float, pitch: Float) { sp(c).edit().putFloat("speed", speed).putFloat("pitch", pitch).apply() }

    fun wake(c: Context): Boolean = sp(c).getBoolean("wake", true)
    fun setWake(c: Context, on: Boolean) { sp(c).edit().putBoolean("wake", on).apply() }

    fun overlayAsked(c: Context): Boolean = sp(c).getBoolean("ovAsked", false)
    fun setOverlayAsked(c: Context) { sp(c).edit().putBoolean("ovAsked", true).apply() }

    fun hist(c: Context): List<Pair<Boolean, String>> {
        val out = ArrayList<Pair<Boolean, String>>()
        try {
            val a = JSONArray(sp(c).getString("hist", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Pair(o.getBoolean("u"), o.getString("t")))
            }
        } catch (e: Exception) { }
        return out
    }

    fun addHist(c: Context, user: Boolean, text: String) {
        if (text.isBlank()) return
        try {
            val a = JSONArray(sp(c).getString("hist", "[]"))
            a.put(JSONObject().put("u", user).put("t", text))
            val n = JSONArray()
            val from = maxOf(0, a.length() - 120)
            for (i in from until a.length()) n.put(a.get(i))
            sp(c).edit().putString("hist", n.toString()).apply()
        } catch (e: Exception) { }
    }

    fun clearHist(c: Context) { sp(c).edit().putString("hist", "[]").apply() }
}
