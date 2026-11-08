package com.jarvis.ai

import android.graphics.Bitmap

enum class Status { IDLE, LISTENING, THINKING, SPEAKING }

enum class Screen { HOME, VOICE, CAMERA, TEXT, NOTES, SETTINGS }

data class ChatMessage(val text: String, val fromUser: Boolean)

data class ObjectInfo(
    val name: String,
    val brand: String,
    val description: String,
    val uses: String,
    val features: String,
    val price: String,
    val spoken: String,
    val sensitive: Boolean
)

data class CameraUi(
    val loading: Boolean = false,
    val speaking: Boolean = false,
    val preview: Bitmap? = null,
    val info: ObjectInfo? = null,
    val error: String? = null
)

data class Note(
    val id: Long,
    val name: String,
    val brand: String,
    val description: String,
    val uses: String,
    val features: String,
    val price: String,
    val savedAt: Long
)

object TextUtil {
    /** Removes markdown symbols so text is clean on screen and in TTS. */
    fun clean(s: String): String =
        s.replace(Regex("[*#`]+"), "").replace(Regex("\\n{3,}"), "\n\n").trim()

    /** True if most letters are Latin (so an English voice should read it). */
    fun mostlyLatin(s: String): Boolean {
        var latin = 0
        var other = 0
        for (c in s) {
            if (!c.isLetter()) continue
            if (c.code < 128) latin++ else other++
        }
        return latin + other > 0 && latin > other * 1.5
    }
}
