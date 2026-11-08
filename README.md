# JARVIS AI – Android (Kotlin + Jetpack Compose)

## Build
1. Open this folder in **Android Studio (Koala / 2024.1 or newer)**. Accept Gradle sync (it downloads Gradle 8.7 and dependencies; JDK 17).
2. Run on a real device (Android 8.0+). Emulators usually lack a microphone/camera feed and Urdu voice data.
3. In the app: **Settings → AI API**, paste a Google Gemini API key (free tier at https://aistudio.google.com/apikey) and Save.
4. On the phone, install/update **Speech Services by Google**, then download the **Urdu (Pakistan)** voice and speech-recognition language
   (Phone Settings → Languages → Text-to-speech / Google → Voice). Then pick a voice in **Settings → Voice** and tap *Test voice*.

## Architecture
- `MainActivity` – edge-to-edge Compose host and simple screen navigation.
- `JarvisViewModel` – single source of truth: voice loop (listen → think → speak → listen), chat, camera analysis, notes, settings.
- `GeminiClient` – REST call to Gemini (text + image input + Google Search grounding for current PKR prices). No third-party HTTP libs.
- `VoiceManager` – Android SpeechRecognizer (ur-PK / en-US) and TextToSpeech.
- `Stores.kt` – API key + settings in EncryptedSharedPreferences; notes in a private JSON file.
- `CameraScreen` – CameraX preview, capture frame, gallery photo, live scan (every ~3 s while idle).
- UI: `Ui.kt`, `ChatScreens.kt`, `CameraScreen.kt`, `NotesScreen.kt`, `SettingsScreen.kt`.

## Notes
- Model name is editable in Settings (default `gemini-2.5-flash`); change it if Google retires it.
- The API key is entered at runtime and never stored in source code. For a shared/public app, proxy calls through your own backend instead.
- Photos are sent to the AI service for analysis and are never saved; only text notes are stored, after you approve.
