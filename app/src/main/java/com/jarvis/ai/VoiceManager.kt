package com.jarvis.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Wraps Android SpeechRecognizer (speech-to-text) and TextToSpeech. */
class VoiceManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())

    var onLevel: (Float) -> Unit = {}

    init {
        tts = TextToSpeech(context) { status -> ready = status == TextToSpeech.SUCCESS }
    }

    fun urduVoices(): List<String> = try {
        tts?.voices?.filter { it.locale.language == "ur" }?.map { it.name }?.sorted().orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** Speaks text and suspends until finished. Returns false if the language/voice is unavailable. */
    suspend fun speak(text: String, lang: String, voiceName: String): Boolean {
        val engine = tts ?: return false
        var waited = 0
        while (!ready && waited < 3000) { delay(100); waited += 100 }
        if (!ready) return false
        val r = engine.setLanguage(Locale.forLanguageTag(lang))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return false
        if (lang.startsWith("ur") && voiceName.isNotEmpty()) {
            try {
                engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
            } catch (_: Exception) {}
        }
        engine.setPitch(1.08f)
        engine.setSpeechRate(1.0f)
        val parts = chunks(text)
        if (parts.isEmpty()) return true
        speakAll(engine, parts)
        return true
    }

    private suspend fun speakAll(engine: TextToSpeech, parts: List<String>) =
        suspendCancellableCoroutine<Unit> { cont ->
            val base = UUID.randomUUID().toString()
            val lastId = "$base-${parts.size - 1}"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == lastId && cont.isActive) cont.resume(Unit)
                }
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == lastId && cont.isActive) cont.resume(Unit)
                }
            })
            cont.invokeOnCancellation { engine.stop() }
            parts.forEachIndexed { i, p ->
                engine.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$base-$i")
            }
        }

    private fun chunks(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (s in text.split(Regex("(?<=[۔.!?؟\\n])"))) {
            if (sb.isNotEmpty() && sb.length + s.length > 350) {
                out.add(sb.toString()); sb.clear()
            }
            sb.append(s)
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out.filter { it.isNotBlank() }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        return recognizer
    }

    /** Must be called on the main thread. */
    fun listen(lang: String, onPartial: (String) -> Unit, onResult: (String) -> Unit, onErr: (Int) -> Unit) {
        val r = ensureRecognizer()
        if (r == null) { onErr(-1); return }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onLevel(0f) }
            override fun onError(error: Int) { onLevel(0f); onErr(error) }
            override fun onResults(results: Bundle?) {
                onLevel(0f)
                onResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    fun cancelListening() {
        main.post { try { recognizer?.cancel() } catch (_: Exception) {} }
        onLevel(0f)
    }

    fun shutdown() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
    }
}
