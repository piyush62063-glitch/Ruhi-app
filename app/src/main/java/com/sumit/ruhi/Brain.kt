package com.sumit.ruhi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GroqErr(val code: Int, msg: String) : Exception(msg)

object Brain {
    private const val BASE = "https://api.groq.com/openai/v1"
    val DEFAULTS = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-20b")

    private fun open(path: String, key: String, post: Boolean): HttpURLConnection {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 40000
        c.setRequestProperty("Authorization", "Bearer " + key.trim())
        c.setRequestProperty("Content-Type", "application/json")
        if (post) { c.requestMethod = "POST"; c.doOutput = true } else c.requestMethod = "GET"
        return c
    }

    private fun err(c: HttpURLConnection): GroqErr {
        val code = c.responseCode
        val body = try { (c.errorStream ?: c.inputStream).bufferedReader().readText() } catch (e: Exception) { "" }
        val m = try { JSONObject(body).getJSONObject("error").getString("message") } catch (e: Exception) { body.take(120) }
        return GroqErr(code, m)
    }

    fun models(key: String): List<String> {
        val c = open("/models", key, false)
        if (c.responseCode != 200) throw err(c)
        val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("data")
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.getJSONObject(i).getString("id"))
        val bad = Regex("whisper|guard|tts|orpheus|playai|safeguard|embed", RegexOption.IGNORE_CASE)
        return out.filter { !bad.containsMatchIn(it) }.sorted()
    }

    private fun stream(key: String, model: String, msgs: JSONArray, onDelta: (String) -> Unit) {
        val body = JSONObject().put("model", model).put("messages", msgs).put("stream", true)
            .put("temperature", 0.7).put("max_tokens", 350)
        val c = open("/chat/completions", key, true)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode != 200) throw err(c)
        c.inputStream.bufferedReader().useLines { lines ->
            for (l in lines) {
                if (!l.startsWith("data:")) continue
                val d = l.substring(5).trim()
                if (d == "[DONE]") break
                try {
                    val t = JSONObject(d).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("delta").optString("content", "")
                    if (t.isNotEmpty()) onDelta(t)
                } catch (e: Exception) { }
            }
        }
    }

    // 404/400 (model hata diya gaya) ho to agla model try karta hai, phir Groq ki live model list se khud chun leta hai
    fun ask(key: String, saved: String, msgs: JSONArray, onModel: (String) -> Unit, onDelta: (String) -> Unit) {
        if (key.isBlank()) throw GroqErr(0, "Settings mein Groq key daalo")
        val order = ArrayList<String>()
        if (saved.isNotBlank()) order.add(saved.trim())
        order.addAll(DEFAULTS)
        val tried = HashSet<String>()
        var refreshed = false
        var last: GroqErr? = null
        var i = 0
        while (true) {
            if (i >= order.size) {
                if (refreshed) break
                refreshed = true
                try { order.addAll(models(key)) } catch (e: GroqErr) { throw e } catch (e: Exception) { }
                if (i >= order.size) break
            }
            val m = order[i++]
            if (!tried.add(m)) continue
            try {
                stream(key, m, msgs, onDelta)
                if (m != saved) onModel(m)
                return
            } catch (e: GroqErr) {
                last = e
                if (e.code == 401 || e.code == 403) throw GroqErr(e.code, "Key galat ya band hai: " + e.message)
                if (e.code != 404 && e.code != 400 && e.code != 429 && e.code != 503 && e.code != 413) throw e
            }
        }
        throw last ?: GroqErr(404, "Koi chalne wala model nahi mila")
    }
}
