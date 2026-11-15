package com.sumit.ruhi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Net {
    fun post(url: String, body: String, headers: Map<String, String>, readTimeout: Int = 20000): Pair<Int, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 8000
        c.readTimeout = readTimeout
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val data = stream?.use { it.readBytes() } ?: ByteArray(0)
        c.disconnect()
        return Pair(code, data)
    }
}

object Brain {
    private val hist = ArrayList<Pair<String, String>>()
    private var keyIdx = 0

    private fun system(): String =
        "Tum Ruhi ho: ${Store.userName} ki pyaari, caring, thodi masti-bhari dost aur AI assistant. " +
        "Hinglish (Roman Hindi + English mix) me bolti ho, ladki ke tone me (\"rahi hoon\", \"karungi\"). " +
        "Jawab bahut chhote rakho: 1-2 chhote sentences, bolne layak. Koi markdown, emoji, list ya star nahi. " +
        "Warm, seedhi baat, thoda funny. Jo nahi pata wo imaandari se bolo. " +
        "PHONE ACTIONS: agar user kuch karwana chahe (gaana/video, call, WhatsApp, navigation, app kholna, google search), " +
        "to reply ke bilkul shuru me ek tag likho, uske baad ek chhota confirm sentence. Tags: " +
        "[[youtube|gaane ya video ka naam]] [[call|contact ka naam ya number]] [[whatsapp|contact ka naam|message]] " +
        "[[nav|jagah ka naam]] [[open|app ka naam]] [[search|google query]]. " +
        "Naam, app aur query hamesha Roman (English) letters me likho. " +
        "Example: user \"Kundan bhai ko call lagao\" -> [[call|Kundan bhai]] Lo, abhi call laga rahi hoon. " +
        "Agar koi action nahi chahiye to tag bilkul mat likho."

    private class Streamer(val onSentence: (String) -> Unit, val onAction: (String) -> Unit) {
        val buf = StringBuilder()
        val full = StringBuilder()
        var tag = ""
        var headDone = false

        fun flush() {
            val t = buf.toString().trim()
            buf.setLength(0)
            if (t.isNotEmpty()) {
                full.append(t).append(' ')
                onSentence(t)
            }
        }

        fun feed(piece: String) {
            for (ch in piece) {
                buf.append(ch)
                if (!headDone) {
                    val s = buf.toString().trimStart()
                    if (s.isEmpty() || s == "[") continue
                    if (s.startsWith("[[")) {
                        val e = s.indexOf("]]")
                        if (e >= 0) {
                            tag = s.substring(2, e)
                            onAction(tag)
                            buf.setLength(0)
                            buf.append(s.substring(e + 2))
                            headDone = true
                        }
                        continue
                    }
                    headDone = true
                }
                if (ch in ".?!।\n" && buf.length >= 16) flush()
            }
        }

        fun finish() {
            flush()
        }
    }

    fun ask(text: String, onSentence: (String) -> Unit, onAction: (String) -> Unit): String {
        val keys = Store.groqKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty() && Store.geminiKey.isBlank()) throw IllegalStateException("nokey")
        val st = Streamer(onSentence, onAction)
        var ok = false
        for (i in keys.indices) {
            val idx = (keyIdx + i) % keys.size
            val code = groq(keys[idx], text, st)
            if (code == 200) {
                keyIdx = idx
                ok = true
                break
            }
            if (st.full.isNotEmpty() || st.tag.isNotEmpty()) {
                ok = true
                break
            }
        }
        if (!ok && Store.geminiKey.isNotBlank()) {
            if (gemini(text, st) == 200) ok = true
        }
        if (!ok) throw RuntimeException("fail")
        val reply = st.full.toString().trim()
        synchronized(hist) {
            hist.add(Pair("user", text))
            hist.add(Pair("assistant", (if (st.tag.isNotEmpty()) "[[" + st.tag + "]] " else "") + reply))
            while (hist.size > 12) hist.removeAt(0)
        }
        return reply
    }

    private fun groq(key: String, text: String, st: Streamer): Int {
        return try {
            val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system()))
            synchronized(hist) {
                for (h in hist) msgs.put(JSONObject().put("role", h.first).put("content", h.second))
            }
            msgs.put(JSONObject().put("role", "user").put("content", text))
            val body = JSONObject()
                .put("model", "llama-3.3-70b-versatile")
                .put("messages", msgs)
                .put("stream", true)
                .put("max_tokens", 180)
                .put("temperature", 0.8)
                .toString()
            val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $key")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code != 200) {
                c.disconnect()
                return code
            }
            c.inputStream.bufferedReader().use { br ->
                while (true) {
                    val line = br.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val d = line.substring(5).trim()
                    if (d == "[DONE]") break
                    val piece = try {
                        val delta = JSONObject(d).getJSONArray("choices").getJSONObject(0).getJSONObject("delta")
                        if (delta.isNull("content")) "" else delta.getString("content")
                    } catch (e: Exception) {
                        ""
                    }
                    if (piece.isNotEmpty()) st.feed(piece)
                }
            }
            st.finish()
            c.disconnect()
            200
        } catch (e: Exception) {
            -1
        }
    }

    private fun gemini(text: String, st: Streamer): Int {
        return try {
            val contents = JSONArray()
            synchronized(hist) {
                for (h in hist) {
                    contents.put(
                        JSONObject()
                            .put("role", if (h.first == "user") "user" else "model")
                            .put("parts", JSONArray().put(JSONObject().put("text", h.second)))
                    )
                }
            }
            contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text))))
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system()))))
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("maxOutputTokens", 400))
                .toString()
            for (m in listOf("gemini-flash-lite-latest", "gemini-flash-latest")) {
                val res = Net.post(
                    "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=${Store.geminiKey}",
                    body, emptyMap()
                )
                if (res.first == 200) {
                    val t = JSONObject(String(res.second))
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    st.feed(t)
                    st.finish()
                    return 200
                }
            }
            -1
        } catch (e: Exception) {
            -1
        }
    }
}
