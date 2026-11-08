package com.jarvis.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiException(val code: Int, message: String) : Exception(message)

/** Minimal Gemini REST client (text + vision + Google Search grounding). No extra libraries. */
object GeminiClient {

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        history: List<ChatMessage>,
        userText: String,
        imageBase64: String?,
        useSearch: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            call(apiKey, model, system, history, userText, imageBase64, useSearch)
        } catch (e: AiException) {
            // If the search tool is rejected for this model/key, retry without it.
            if (useSearch && e.code == 400 && !e.message.orEmpty().contains("API key", true)) {
                call(apiKey, model, system, history, userText, imageBase64, false)
            } else throw e
        }
    }

    private fun call(
        apiKey: String, model: String, system: String, history: List<ChatMessage>,
        userText: String, imageBase64: String?, useSearch: Boolean
    ): String {
        val contents = JSONArray()
        history.forEach { m ->
            contents.put(
                JSONObject().put("role", if (m.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
            )
        }
        val parts = JSONArray().put(JSONObject().put("text", userText))
        if (imageBase64 != null) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64)
                )
            )
        }
        contents.put(JSONObject().put("role", "user").put("parts", parts))

        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("generationConfig", JSONObject().put("temperature", 0.7))
        if (useSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    text.take(200)
                }
                throw AiException(code, msg)
            }
            val ps = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw AiException(0, "Empty response")
            val sb = StringBuilder()
            for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text", ""))
            val out = TextUtil.clean(sb.toString())
            if (out.isBlank()) throw AiException(0, "Empty response")
            return out
        } finally {
            conn.disconnect()
        }
    }
}
