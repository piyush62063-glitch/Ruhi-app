package com.sumit.ruhi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {
    private const val SYS = "Tum Ruhi ho, Sumit ki friendly aur samajhdaar dost aur AI assistant. Ek normal dost ki tarah baat karo: simple, natural aur sidhi, kabhi kabhi halka sa mazaak. " +
        "Kabhi bhi 'babu', 'baccha', 'bachha', 'baby', 'jaan', 'sweetheart' jaise pyaar bhare ya bachkane shabd mat bolo, aur girlfriend ki tarah mat bolo. Sumit ko uske naam se bulao. " +
        "Jawab sirf 1 ya 2 chhote vaakya mein do, kyunki tum bolti ho. " +
        "Hindi shabd Devanagari lipi mein likho aur English shabd English mein likho, jaise: 'मैं YouTube खोल देती हूँ'. " +
        "Tum ek ladki ho, to apne liye feminine form use karo (करती हूँ, रही हूँ). Emoji, markdown aur list kabhi mat likho."
    private fun sys() = SYS + " Abhi ka samay: " + java.text.SimpleDateFormat("EEEE, hh:mm a", java.util.Locale.ENGLISH).format(java.util.Date()) + "."
    private val hist = ArrayList<Pair<String, String>>()
    private var rot = 0
    private val bad = Regex("(?i)\\b(babu|baccha|bachha|baby)\\b|बाबू|बच्चा")
    private fun clean(s: String) = s.replace(bad, "").replace(Regex("\\s{2,}"), " ")
    private val models = listOf("llama-3.1-8b-instant", "llama-3.3-70b-versatile")

    fun ask(q: String, onSent: (String) -> Unit, onDone: () -> Unit, onErr: (String) -> Unit) {
        Thread {
            try {
                val keys = Store.groq.split(",", "\n", " ").map { it.trim() }.filter { it.isNotEmpty() }
                if (keys.isEmpty()) { onErr("Settings > Connections mein Groq key daalo"); return@Thread }
                var out: String? = null
                var last = "net check karo"
                for (i in keys.indices) {
                    val k = keys[(rot + i) % keys.size]
                    try { out = groq(k, q, onSent); rot = (rot + i + 1) % keys.size; break } catch (e: Exception) { last = e.message ?: last }
                }
                if (out == null) { onErr("Groq error: $last"); return@Thread }
                hist.add("user" to q); hist.add("assistant" to out)
                while (hist.size > 12) hist.removeAt(0)
                onDone()
            } catch (e: Exception) { onErr(e.message ?: "error") }
        }.start()
    }

    private fun groq(key: String, q: String, cb: (String) -> Unit): String {
        val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 6000; c.readTimeout = 15000
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        val m = JSONArray().put(JSONObject().put("role", "system").put("content", sys()))
        for ((r, t) in hist) m.put(JSONObject().put("role", r).put("content", t))
        m.put(JSONObject().put("role", "user").put("content", q))
        val body = JSONObject().put("model", models[Store.model.coerceIn(0, 1)]).put("stream", true)
            .put("max_tokens", 90).put("temperature", 0.7).put("messages", m)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode != 200) throw Exception("groq ${c.responseCode}")
        val all = StringBuilder(); val buf = StringBuilder()
        c.inputStream.bufferedReader().forEachLine { line ->
            if (line.startsWith("data:") && !line.contains("[DONE]")) {
                try {
                    val d = JSONObject(line.substring(5).trim()).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("delta").optString("content", "")
                    all.append(d); buf.append(d)
                    // pehla chunk jaldi bolne ke liye: vaakya ya comma pe flush
                    var i = buf.indexOfLast { ch -> ch in ".!?।\n" }
                    if (i < 0 && all.length == buf.length && buf.length >= 28) i = buf.indexOfLast { ch -> ch == ',' || ch == '،' }
                    if (i >= 0) { val s = clean(buf.substring(0, i + 1)); if (s.isNotBlank()) cb(s); buf.delete(0, i + 1) }
                } catch (e: Exception) { }
            }
        }
        if (buf.isNotBlank()) { val s = clean(buf.toString()); if (s.isNotBlank()) cb(s) }
        if (all.isBlank()) throw Exception("empty")
        return all.toString()
    }
}
