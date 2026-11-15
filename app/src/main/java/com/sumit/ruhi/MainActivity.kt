package com.sumit.ruhi

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech

    private val prefs by lazy { getSharedPreferences("ruhi", 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) {
            tts.language = Locale("hi", "IN")
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 25, 18, 15)
        }

        root.addView(TextView(this).apply {
            text = "✨ Ruhi AI"
            textSize = 28f
            gravity = Gravity.CENTER
        })

        val settings = Button(this).apply {
            text = "⚙ API Key Settings"
            setOnClickListener { apiDialog() }
        }
        root.addView(settings)

        val scroll = ScrollView(this)
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this)
        input = EditText(this).apply {
            hint = "Ruhi se kuch pucho..."
        }

        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        row.addView(Button(this).apply {
            text = "🎙"
            setOnClickListener { voiceInput() }
        })

        row.addView(Button(this).apply {
            text = "➤"
            setOnClickListener {
                val question = input.text.toString().trim()
                if (question.isNotEmpty()) {
                    input.setText("")
                    send(question)
                }
            }
        })

        root.addView(row)
        setContentView(root)

        bubble("Ruhi: Namaste! Main tumhari AI assistant hoon. ❤️")
    }

    private fun bubble(message: String) {
        chat.addView(TextView(this).apply {
            text = message
            textSize = 16f
            setPadding(12, 14, 12, 14)
        })
    }

    private fun apiDialog() {
        val edit = EditText(this).apply {
            hint = "Gemini API key paste karo"
            setText(prefs.getString("key", ""))
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Gemini API Key")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("key", edit.text.toString().trim()).apply()
                Toast.makeText(this, "API Key Saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun send(question: String) {
        bubble("Tum: $question")

        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) {
            bubble("Ruhi: Pehle API key save karo.")
            return
        }

        bubble("Ruhi: Soch rahi hoon...")

        thread {
            try {
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=$key"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")

                val body = JSONObject().apply {
                    put("system_instruction", JSONObject().put(
                        "parts", org.json.JSONArray().put(
                            JSONObject().put(
                                "text",
                                "Tum Ruhi ho, Sumit ki Hindi Hinglish AI assistant. Friendly aur concise jawab do."
                            )
                        )
                    ))
                    put("contents", org.json.JSONArray().put(
                        JSONObject().put(
                            "parts", org.json.JSONArray().put(
                                JSONObject().put("text", question)
                            )
                        )
                    ))
                }

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val stream = if (code in 200..299)
                    connection.inputStream else connection.errorStream

                val response = stream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (code !in 200..299) {
                    throw Exception("API Error $code: $response")
                }

                val answer = JSONObject(response)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                runOnUiThread {
                    bubble("Ruhi: $answer")
                    tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "ruhi")
                }

            } catch (e: Exception) {
                runOnUiThread {
                    bubble("Ruhi: Error - ${e.message}")
                }
            }
        }
    }

    private fun voiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            startActivityForResult(intent, 101)
        } catch (e: Exception) {
            bubble("Voice input available nahi hai.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            input.setText(result?.firstOrNull() ?: "")
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
