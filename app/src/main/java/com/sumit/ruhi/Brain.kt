package com.sumit.ruhi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {
    private const val SYS = "Tum Ruhi ho, Sumit ki pyaari aur caring girlfriend jaisi dost. Hinglish mein, mithi pyaar bhari awaaz mein baat karo, thodi masti aur chhedkhaani ke saath. Sumit ka naam lo, uska mood aur din poochho. Jawab bahut chhota rakho: sirf 1 ya 2 vaakya, kyunki tum bolti ho. Emoji ya markdown mat likho. Possessive ya jealous mat bano; Sumit ko doston, family aur apne kaam ke liye bhi encourage karo."
    private fun sys() = SYS + " Abhi ka samay: " + java.text.SimpleDateFormat("EEEE, hh:mm a", java.util.Locale.ENGLISH).format(java.util.Date()) + ". Samay ke hisaab se baat karo (subah, raat, wagairah)."
    private val hist = ArrayList<Pair<String, String>>()
    private var rot = 0

    fun warm() {
        Thread {
            try {
                val k = Store.groq.split(",").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return@Thread
                val c = URL("https://api.groq.com/openai/v1/models").openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000
                c.setRequestProperty("Authorization", "Bearer $k")
                c.responseCode
            } catch (e: Exception) { }
        }.start()
    }

    fun ask(q: String, onSent: (String) -> Unit, onDone: () -> Unit, onErr: (String) -> Unit) {
        Thread {
            try {
                val keys = Store.groq.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                var out: String? = null
                for (i in keys.indices) {
                    val k = keys[(rot + i) % keys.size]
                    try { out = groq(k, q, onSent); rot = (rot + i + 1) % keys.size; break } catch (e: Exception) { }
                }
                if (out == null && Store.gemini.isNotBlank()) { out = gemini(q); onSent(out) }
                if (out == null) {
                    onErr(if (keys.isEmpty() && Store.gemini.isBlank()) "Settings > Connections mein key daalo" else "Net ya key check karo")
                    return@Thread
                }
                hist.add("user" to q); hist.add("assistant" to out)
                while (hist.size > 12) hist.removeAt(0)
                onDone()
            } catch (e: Exception) { onErr(e.message ?: "error") }
        }.start()
    }

    private fun groq(key: String, q: String, cb: (String) -> Unit): String {
        val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 20000
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        val m = JSONArray().put(JSONObject().put("role", "system").put("content", sys()))
        for ((r, t) in hist) m.put(JSONObject().put("role", r).put("content", t))
        m.put(JSONObject().put("role", "user").put("content", q))
        val body = JSONObject().put("model", "llama-3.1-8b-instant").put("stream", true)
            .put("max_tokens", 100).put("temperature", 0.8).put("messages", m)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode != 200) throw Exception("groq ${c.responseCode}")
        val all = StringBuilder(); val buf = StringBuilder(); var first = true
        c.inputStream.bufferedReader().forEachLine { line ->
            if (line.startsWith("data:") && !line.contains("[DONE]")) {
                try {
                    val d = JSONObject(line.substring(5).trim()).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("delta").optString("content", "")
                    all.append(d); buf.append(d)
                    var i = buf.indexOfLast { ch -> ch in ".!?।\n" }
                    if (i < 0 && first && buf.length >= 16) i = buf.indexOfLast { ch -> ch == ',' }
                    if (i >= 0) { cb(buf.substring(0, i + 1)); buf.delete(0, i + 1); first = false }
                } catch (e: Exception) { }
            }
        }
        if (buf.isNotBlank()) cb(buf.toString())
        if (all.isBlank()) throw Exception("empty")
        return all.toString()
    }

    private fun gemini(q: String): String {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + Store.gemini)
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 20000
        c.setRequestProperty("Content-Type", "application/json")
        val b = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys()))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", q)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 150))
        c.outputStream.use { it.write(b.toString().toByteArray()) }
        if (c.responseCode != 200) throw Exception("gemini ${c.responseCode}")
        val r = JSONObject(c.inputStream.bufferedReader().readText())
        return r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text")
    }
}
