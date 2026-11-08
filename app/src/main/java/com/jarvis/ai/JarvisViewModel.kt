package com.jarvis.ai

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class UserMessage(msg: String) : Exception(msg)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val settings = SettingsStore(ctx)
    private val notesStore = NotesStore(ctx)
    val voice = VoiceManager(ctx)

    val messages = mutableStateListOf<ChatMessage>()
    val notes = mutableStateListOf<Note>()

    var status by mutableStateOf(Status.IDLE); private set
    var level by mutableStateOf(0f); private set
    var partial by mutableStateOf(""); private set
    var voiceActive by mutableStateOf(false); private set
    var camera by mutableStateOf(CameraUi()); private set
    var notice by mutableStateOf<String?>(null); private set
    var voiceList by mutableStateOf<List<String>>(emptyList()); private set

    var apiKey by mutableStateOf(settings.apiKey); private set
    var model by mutableStateOf(settings.model); private set
    var language by mutableStateOf(settings.language); private set
    var voiceName by mutableStateOf(settings.voiceName); private set
    var useSearch by mutableStateOf(settings.useSearch); private set
    var speakReplies by mutableStateOf(settings.speakReplies); private set

    val urdu: Boolean get() = language.startsWith("ur")
    val cameraBusy: Boolean get() = camera.loading || camera.speaking

    private var workJob: Job? = null
    private var cameraJob: Job? = null
    private var idleRetries = 0

    private fun tr(ur: String, en: String) = if (urdu) ur else en

    init {
        voice.onLevel = { level = it }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { notesStore.load() }
            notes.addAll(loaded)
        }
        introduce()
    }

    // ---------- Startup ----------
    private fun introduce() {
        val intro = "Introducing myself. I am Jarvis."
        val greet = tr(
            "السلام علیکم! میں جارویس ہوں، آپ کی ذاتی اسسٹنٹ۔ بتائیے، میں آپ کی کیا مدد کر سکتی ہوں؟",
            "Hello! I am ready. How can I help you?"
        )
        messages.add(ChatMessage(intro, false))
        messages.add(ChatMessage(greet, false))
        workJob = viewModelScope.launch {
            status = Status.SPEAKING
            voice.speak(intro, "en-US", "")
            say(greet)
            status = Status.IDLE
        }
    }

    // ---------- Speaking ----------
    private fun speechLang(text: String): String =
        if (!urdu || TextUtil.mostlyLatin(text)) "en-US" else language

    /** Speaks text; shows a hint if the needed voice isn't installed. */
    private suspend fun say(text: String) {
        val ok = voice.speak(text, speechLang(text), voiceName)
        if (!ok) notice = tr(
            "اردو آواز انسٹال نہیں۔ سیٹنگز میں Voice حصہ دیکھیں۔",
            "Voice not available. See Settings > Voice."
        )
    }

    fun interrupt() {
        if (status == Status.SPEAKING) {
            workJob?.cancel()
            status = Status.IDLE
            if (voiceActive) listenOnce()
        }
    }

    // ---------- Voice conversation ----------
    fun toggleVoice() { if (voiceActive) stopVoice() else startVoice() }

    private fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            notice = tr("اس فون پر آواز پہچاننے کی سروس موجود نہیں۔", "Speech recognition is not available on this phone.")
            return
        }
        workJob?.cancel()
        voiceActive = true
        idleRetries = 0
        listenOnce()
    }

    fun stopVoice() {
        voiceActive = false
        voice.cancelListening()
        workJob?.cancel()
        status = Status.IDLE
        level = 0f
        partial = ""
    }

    private fun listenOnce() {
        if (!voiceActive) return
        status = Status.LISTENING
        partial = ""
        voice.listen(language, { partial = it }, { text ->
            partial = ""
            if (text.isBlank()) onSttError(SpeechRecognizer.ERROR_NO_MATCH)
            else { idleRetries = 0; submit(text, true) }
        }, { onSttError(it) })
    }

    private fun onSttError(code: Int) {
        status = Status.IDLE
        if (!voiceActive) return
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                if (++idleRetries >= 3) {
                    stopVoice()
                    notice = tr("کوئی آواز نہیں آئی، مائیک بند کر دیا۔", "No speech heard. Mic stopped.")
                } else viewModelScope.launch {
                    voice.cancelListening()
                    delay(400)
                    listenOnce()
                }
            }
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                stopVoice()
                notice = tr("آواز پہچاننے کے لیے انٹرنیٹ چیک کریں۔", "Check your internet for speech recognition.")
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                stopVoice()
                notice = tr("مائیک کی اجازت درکار ہے۔", "Microphone permission is required.")
            }
            else -> {
                stopVoice()
                notice = tr("آواز پہچاننے میں مسئلہ آیا۔", "Speech recognition error ($code).")
            }
        }
    }

    // ---------- Chat ----------
    fun sendText(text: String) = submit(text, speakReplies || voiceActive)

    private fun submit(text: String, speak: Boolean) {
        val t = text.trim()
        if (t.isEmpty()) return
        workJob?.cancel()
        voice.cancelListening()
        messages.add(ChatMessage(t, true))
        status = Status.THINKING
        workJob = viewModelScope.launch {
            try {
                val reply = ask(t)
                messages.add(ChatMessage(reply, false))
                if (speak) {
                    status = Status.SPEAKING
                    say(reply)
                }
                status = Status.IDLE
                if (voiceActive) listenOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.add(ChatMessage(friendly(e), false))
                status = Status.IDLE
                voiceActive = false
            }
        }
    }

    private suspend fun ask(text: String): String {
        requireReady()
        val history = messages.dropLast(1).takeLast(12).dropWhile { !it.fromUser }
        return GeminiClient.generate(apiKey, model, systemPrompt(), history, text, null, false)
    }

    private fun systemPrompt(): String {
        val lang = if (urdu)
            "Reply in natural, polite Pakistani Urdu written in Urdu script, even if the user writes Roman Urdu or English. " +
                "Reply in English only if the user clearly wants English. Keep technical words and brand names in English."
        else "Reply in clear English unless the user writes in Urdu or Roman Urdu, then reply in Urdu script."
        return "You are JARVIS, a futuristic female AI personal assistant on an Android phone. $lang " +
            "You are a woman: use feminine Urdu grammar for yourself (کر سکتی ہوں). " +
            "Your answers are spoken aloud, so keep them short, natural and friendly. " +
            "Never use markdown, bullet symbols, asterisks or emojis. Never invent facts; say when you are unsure."
    }

    fun clearChat() {
        stopVoice()
        messages.clear()
    }

    // ---------- Camera ----------
    fun analyze(bmp: Bitmap) {
        if (camera.loading) return
        cameraJob?.cancel()
        cameraJob = viewModelScope.launch {
            camera = CameraUi(loading = true, preview = bmp)
            try {
                requireReady()
                val b64 = withContext(Dispatchers.Default) { ImageUtils.toBase64(bmp) }
                val raw = GeminiClient.generate(apiKey, model, systemPrompt(), emptyList(), cameraPrompt(), b64, useSearch)
                val info = parseInfo(raw)
                camera = CameraUi(preview = bmp, info = info)
                if (speakReplies) {
                    camera = camera.copy(speaking = true)
                    say(info.spoken)
                    camera = camera.copy(speaking = false)
                }
            } catch (e: CancellationException) {
                camera = camera.copy(loading = false, speaking = false)
                throw e
            } catch (e: Exception) {
                camera = CameraUi(preview = bmp, error = friendly(e))
            }
        }
    }

    fun analyzeUri(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageUtils.load(ctx, uri) }
            if (bmp == null) notice = tr("تصویر نہیں کھل سکی۔", "Could not open the image.")
            else analyze(bmp)
        }
    }

    private fun cameraPrompt(): String {
        val l = if (urdu) "Write all values in Urdu script (keep brand and model names in English)."
        else "Write all values in English."
        return "Analyze the main object in this image. Respond ONLY with one JSON object (no markdown, no code fences) " +
            "with these keys: object_name; brand_model (brand and model if visible or identifiable, otherwise say unknown); " +
            "description (what it is); purpose_and_uses; key_features; " +
            "price_pkr (estimated current market price range in Pakistani Rupees; use Google Search for current Pakistani prices when available; " +
            "mention it is an estimate; if it is not a purchasable item say so); " +
            "spoken_summary (3 to 5 short natural sentences summarising object, use and price, speaker is female, no symbols); " +
            "contains_sensitive_info (true if the image shows ID cards, bank cards, documents, people's faces, passwords or other private data). " +
            "$l Never fabricate; say clearly when you are uncertain."
    }

    private fun parseInfo(raw: String): ObjectInfo {
        val s = raw.indexOf('{')
        val e = raw.lastIndexOf('}')
        if (s >= 0 && e > s) {
            try {
                val o = JSONObject(raw.substring(s, e + 1))
                fun g(k: String): String {
                    val v = o.opt(k)
                    return when (v) {
                        null, JSONObject.NULL -> ""
                        is JSONArray -> (0 until v.length()).joinToString("، ") { v.optString(it) }
                        else -> v.toString().trim()
                    }
                }
                val name = g("object_name")
                val desc = g("description")
                val spoken = g("spoken_summary").ifBlank { "$name. $desc" }
                return ObjectInfo(
                    name, g("brand_model"), desc, g("purpose_and_uses"), g("key_features"),
                    g("price_pkr"), spoken, o.optBoolean("contains_sensitive_info", false)
                )
            } catch (_: Exception) {}
        }
        val c = TextUtil.clean(raw)
        return ObjectInfo("", "", c, "", "", "", c, false)
    }

    fun speakInfo() {
        val info = camera.info ?: return
        if (cameraBusy) return
        cameraJob = viewModelScope.launch {
            camera = camera.copy(speaking = true)
            try { say(info.spoken) } finally { camera = camera.copy(speaking = false) }
        }
    }

    fun stopCameraWork() {
        cameraJob?.cancel()
        camera = camera.copy(loading = false, speaking = false)
    }

    fun clearCamera() {
        stopCameraWork()
        camera = CameraUi()
    }

    // ---------- Notes ----------
    fun saveNote(info: ObjectInfo) {
        val n = Note(
            System.currentTimeMillis(),
            info.name.ifBlank { tr("نامعلوم چیز", "Unknown object") },
            info.brand, info.description, info.uses, info.features, info.price, System.currentTimeMillis()
        )
        notes.add(0, n)
        persist()
        notice = tr("نوٹ محفوظ ہو گیا۔", "Note saved.")
    }

    fun deleteNote(id: Long) {
        notes.removeAll { it.id == id }
        persist()
    }

    fun deleteAllNotes() {
        notes.clear()
        persist()
    }

    fun speakNote(n: Note) {
        workJob?.cancel()
        workJob = viewModelScope.launch {
            status = Status.SPEAKING
            try { say("${n.name}. ${n.description} ${n.uses} ${n.price}") } finally { status = Status.IDLE }
        }
    }

    private fun persist() {
        val snapshot = notes.toList()
        viewModelScope.launch(Dispatchers.IO) { notesStore.save(snapshot) }
    }

    // ---------- Settings ----------
    fun updateApiKey(v: String) { apiKey = v.trim(); settings.apiKey = apiKey }
    fun updateModel(v: String) { model = v.trim().ifBlank { "gemini-2.5-flash" }; settings.model = model }
    fun updateLanguage(v: String) { language = v; settings.language = v }
    fun updateVoice(v: String) { voiceName = v; settings.voiceName = v }
    fun updateSearch(v: Boolean) { useSearch = v; settings.useSearch = v }
    fun updateSpeak(v: Boolean) { speakReplies = v; settings.speakReplies = v }
    fun refreshVoices() { voiceList = voice.urduVoices() }
    fun postNotice(m: String) { notice = m }
    fun consumeNotice() { notice = null }

    fun testVoice() {
        workJob?.cancel()
        workJob = viewModelScope.launch {
            status = Status.SPEAKING
            try { say(tr("میں جارویس ہوں۔ یہ میری آواز ہے۔", "I am Jarvis. This is my voice.")) } finally { status = Status.IDLE }
        }
    }

    // ---------- Helpers ----------
    private fun isOnline(): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val n = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun requireReady() {
        if (apiKey.isBlank()) throw UserMessage(
            tr("پہلے سیٹنگز میں AI API Key درج کریں۔", "Please add your AI API key in Settings first.")
        )
        if (!isOnline()) throw UserMessage(
            tr("انٹرنیٹ کنکشن نہیں ہے۔ براہ کرم نیٹ ورک چیک کریں۔", "No internet connection. Please check your network.")
        )
    }

    private fun friendly(e: Exception): String = when {
        e is UserMessage -> e.message.orEmpty()
        e is AiException && (e.code == 401 || e.code == 403 || e.message.orEmpty().contains("API key", true)) ->
            tr("API Key درست نہیں یا اجازت نہیں۔ سیٹنگز میں چیک کریں۔", "API key invalid or not permitted. Check Settings.")
        e is AiException && e.code == 429 ->
            tr("استعمال کی حد پوری ہو گئی۔ تھوڑی دیر بعد کوشش کریں۔", "Rate limit reached. Try again shortly.")
        e is AiException && e.code == 404 ->
            tr("یہ AI ماڈل نہیں ملا۔ سیٹنگز میں ماڈل کا نام چیک کریں۔", "Model not found. Check the model name in Settings.")
        e is AiException -> tr("AI سروس میں مسئلہ: ", "AI service error: ") + e.message.orEmpty().take(120)
        e is IOException -> tr("نیٹ ورک کا مسئلہ۔ دوبارہ کوشش کریں۔", "Network problem. Please try again.")
        else -> tr("کچھ غلط ہو گیا۔ دوبارہ کوشش کریں۔", "Something went wrong. Please try again.")
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }
}
