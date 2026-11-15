package com.sumit.ruhi

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var log: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private val hist = mutableListOf<Pair<String, String>>()
    private val SYSTEM = "Tu Ruhi hai, Sumit ki dost jaisi voice assistant. Hinglish mein baat kar, casual, confident aur thoda funny. Jawab chhote rakh (1-3 lines). Phone ka kaam ho toh jawab ke baad alag line mein likh: ACTION: youtube|query / whatsapp|number|message / maps|jagah (navigation) / call|number / google|query / alarm|HH|MM (24 ghante) / torch|on ya off / battery / volume|0 se 15 / wifi ya data (settings panel khulta hai)"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 70, 24, 24) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "😎 Ruhi"; textSize = 24f }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(Button(this).apply { text = "🔑"; setOnClickListener { keyDialog() } })
        log = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(log) }
        input = EditText(this).apply { hint = "Ruhi se bol…"; maxLines = 3 }
        val mic = Button(this).apply { text = "🎤"; setOnClickListener { listen() } }
        val go = Button(this).apply { text = "➤"; setOnClickListener { send(input.text.toString()) } }
        val row = LinearLayout(this)
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(mic); row.addView(go)
        root.addView(top)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(row)
        setContentView(root)
        bubble("Ruhi: Namaste Sumit! Main tera phone sambhalne ko ready hoon. 🎤 dabake bol.")
        if (key().isEmpty()) keyDialog()
    }

    override fun onInit(s: Int) { tts.language = Locale("hi", "IN") }

    private fun key() = getSharedPreferences("r", 0).getString("k", "") ?: ""

    private fun keyDialog() {
        val e = EditText(this); e.hint = "Gemini API key paste kar"; e.setText(key())
        AlertDialog.Builder(this).setTitle("API key").setView(e)
            .setPositiveButton("Save") { _, _ -> getSharedPreferences("r", 0).edit().putString("k", e.text.toString().trim()).apply() }
            .show()
    }

    private fun bubble(t: String) {
        log.addView(TextView(this).apply { text = t; textSize = 16f; setPadding(8, 14, 8, 14); setTextIsSelectable(true) })
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        try { startActivityForResult(i, 1) } catch (e: Exception) { bubble("Ruhi: Is phone pe voice input nahi mila, type kar.") }
    }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        if (r == 1 && c == RESULT_OK) d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
    }

    private fun send(t: String) {
        val msg = t.trim()
        if (msg.isEmpty()) return
        if (key().isEmpty()) { keyDialog(); return }
        input.setText(""); bubble("Tu: $msg"); hist.add("user" to msg)
        Thread {
            try {
                val out = ask()
                runOnUiThread { handle(out) }
            } catch (e: Exception) {
                runOnUiThread { hist.removeAt(hist.size - 1); bubble("Error: ${e.message}") }
            }
        }.start()
    }

    private fun ask(): String {
        val c = JSONArray()
        hist.takeLast(19).forEach { (r, t) -> c.put(JSONObject().put("role", r).put("parts", JSONArray().put(JSONObject().put("text", t)))) }
        val sys = SYSTEM + " Abhi: " + SimpleDateFormat("EEEE d MMMM HH:mm", Locale.ENGLISH).format(Date())
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", c)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 800).put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        val con = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent").openConnection() as HttpURLConnection
        con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 20000; con.readTimeout = 60000
        con.setRequestProperty("Content-Type", "application/json"); con.setRequestProperty("x-goog-api-key", key())
        con.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = con.responseCode
        val txt = (if (code in 200..299) con.inputStream else con.errorStream).bufferedReader().readText()
        if (code !in 200..299) throw Exception("API $code (key check kar)")
        return JSONObject(txt).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun handle(out: String) {
        hist.add("model" to out)
        var extra = ""
        Regex("ACTION:\\s*(\\w+)\\|?(.*)").findAll(out).forEach {
            extra += " " + (act(it.groupValues[1].lowercase(), it.groupValues[2].split("|").map { x -> x.trim() }) ?: "")
        }
        val say = (out.replace(Regex("ACTION:.*"), "").trim() + extra).trim()
        bubble("Ruhi: $say")
        tts.speak(say, TextToSpeech.QUEUE_FLUSH, null, "r")
    }

    private fun act(k: String, p: List<String>): String? {
        fun arg(i: Int) = p.getOrElse(i) { "" }
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        fun open(i: Intent) { try { startActivity(i) } catch (e: Exception) { } }
        fun view(u: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        try {
            when (k) {
                "youtube" -> view(if (arg(0).isEmpty()) "https://www.youtube.com" else "https://www.youtube.com/results?search_query=" + enc(arg(0)))
                "whatsapp" -> { var n = arg(0).filter { it.isDigit() }; if (n.length == 10) n = "91$n"; view("https://wa.me/$n" + (if (arg(1).isNotEmpty()) "?text=" + enc(arg(1)) else "")) }
                "maps" -> view("google.navigation:q=" + enc(arg(0)))
                "call" -> view("tel:" + arg(0).filter { it.isDigit() || it == '+' })
                "alarm" -> open(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, arg(0).toInt()).putExtra(AlarmClock.EXTRA_MINUTES, arg(1).toInt()).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                "torch" -> { val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager; cm.setTorchMode(cm.cameraIdList[0], arg(0) != "off") }
                "volume" -> { val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager; am.setStreamVolume(AudioManager.STREAM_MUSIC, arg(0).toInt().coerceIn(0, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0) }
                "battery" -> return "Battery " + (getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " percent hai."
                "wifi", "data" -> { open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); return "Panel khul gaya, WiFi ya data ka button tu daba de." }
                else -> view("https://www.google.com/search?q=" + enc(arg(0)))
            }
        } catch (e: Exception) { return "Wo kaam nahi hua." }
        return null
    }
}
