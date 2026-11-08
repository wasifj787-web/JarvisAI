package com.jarvis.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Settings, with the API key kept in encrypted preferences (Android Keystore). */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "jarvis_secure_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) { prefs.edit().putString("api_key", v).apply() }

    var model: String
        get() = prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) { prefs.edit().putString("model", v).apply() }

    var language: String
        get() = prefs.getString("language", "ur-PK") ?: "ur-PK"
        set(v) { prefs.edit().putString("language", v).apply() }

    var voiceName: String
        get() = prefs.getString("voice_name", "") ?: ""
        set(v) { prefs.edit().putString("voice_name", v).apply() }

    var useSearch: Boolean
        get() = prefs.getBoolean("use_search", true)
        set(v) { prefs.edit().putBoolean("use_search", v).apply() }

    var speakReplies: Boolean
        get() = prefs.getBoolean("speak_replies", true)
        set(v) { prefs.edit().putBoolean("speak_replies", v).apply() }
}

/** Notes stored as a small JSON file in app-private storage. */
class NotesStore(context: Context) {
    private val file = File(context.filesDir, "notes.json")

    @Synchronized
    fun load(): List<Note> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(
                    o.getLong("id"), o.optString("name"), o.optString("brand"),
                    o.optString("description"), o.optString("uses"), o.optString("features"),
                    o.optString("price"), o.optLong("savedAt")
                )
            }.sortedByDescending { it.savedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(
                JSONObject().put("id", n.id).put("name", n.name).put("brand", n.brand)
                    .put("description", n.description).put("uses", n.uses)
                    .put("features", n.features).put("price", n.price).put("savedAt", n.savedAt)
            )
        }
        val tmp = File(file.parentFile, "notes.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}
