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

    fun guard(c: Context): Boolean = sp(c).getBoolean("guard", true)
    fun setGuard(c: Context, on: Boolean) { sp(c).edit().putBoolean("guard", on).apply() }
    fun held(c: Context): Boolean = sp(c).getBoolean("held", false)
    fun setHeld(c: Context, on: Boolean) { sp(c).edit().putBoolean("held", on).apply() }

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

    // ---------------------------------------------------------------- Part 4A
    fun gate(c: Context): Boolean = sp(c).getBoolean("gate", true)
    fun setGate(c: Context, on: Boolean) { sp(c).edit().putBoolean("gate", on).apply() }
    fun passHash(c: Context): String = sp(c).getString("passHash", "") ?: ""
    fun passN(c: Context): Int = sp(c).getInt("passN", 1)
    fun setPass(c: Context, hash: String, n: Int) { sp(c).edit().putString("passHash", hash).putInt("passN", n).apply() }

    fun pvKey(c: Context): String = sp(c).getString("pvKey", "") ?: ""
    fun setPvKey(c: Context, k: String) { sp(c).edit().putString("pvKey", k.trim()).apply() }

    fun city(c: Context): String = sp(c).getString("city", "") ?: ""
    fun setCity(c: Context, s: String) { sp(c).edit().putString("city", s.trim()).apply() }

    fun notes(c: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val a = JSONArray(sp(c).getString("notes", "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Exception) { }
        return out
    }

    fun addNote(c: Context, t: String) {
        try {
            val a = JSONArray(sp(c).getString("notes", "[]"))
            a.put(t)
            val n = JSONArray()
            for (i in maxOf(0, a.length() - 50) until a.length()) n.put(a.get(i))
            sp(c).edit().putString("notes", n.toString()).apply()
        } catch (e: Exception) { }
    }

    fun clearNotes(c: Context) { sp(c).edit().putString("notes", "[]").apply() }

    fun reminders(c: Context): List<Triple<Int, Long, String>> {
        val out = ArrayList<Triple<Int, Long, String>>()
        try {
            val a = JSONArray(sp(c).getString("rem", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Triple(o.getInt("id"), o.getLong("at"), o.getString("t")))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun putReminders(c: Context, l: List<Triple<Int, Long, String>>) {
        val a = JSONArray()
        for (r in l) a.put(JSONObject().put("id", r.first).put("at", r.second).put("t", r.third))
        sp(c).edit().putString("rem", a.toString()).apply()
    }

    fun nextReminderId(c: Context): Int {
        val n = sp(c).getInt("remId", 100) + 1
        sp(c).edit().putInt("remId", n).apply()
        return n
    }

    fun addReminder(c: Context, id: Int, at: Long, t: String) {
        val l = reminders(c).toMutableList()
        l.add(Triple(id, at, t))
        putReminders(c, l)
    }

    fun removeReminder(c: Context, id: Int) { putReminders(c, reminders(c).filter { it.first != id }) }
    fun clearReminders(c: Context) { putReminders(c, emptyList()) }

    // ---------------------------------------------------------------- Part 4B
    fun handoff(c: Context): Boolean = sp(c).getBoolean("handoff", true)
    fun setHandoff(c: Context, on: Boolean) { sp(c).edit().putBoolean("handoff", on).apply() }
}
