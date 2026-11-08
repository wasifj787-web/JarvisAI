package com.jarvis.ai

import android.Manifest
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = Cyan, fontSize = 13.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    HudCard(Modifier.fillMaxWidth(), content)
}

@Composable
private fun PermRow(name: String, granted: Boolean, onGrant: (() -> Unit)?, ctxOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = TextLight, fontSize = 16.sp)
            Text(if (granted) "Granted" else "Not granted", color = if (granted) Cyan else Danger, fontSize = 12.sp)
        }
        if (!granted && onGrant != null) {
            Button(onClick = onGrant, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Allow") }
            TextButton(onClick = ctxOpen) { Text("Settings") }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextLight, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: JarvisViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mic = rememberPerm(Manifest.permission.RECORD_AUDIO)
    val cam = rememberPerm(Manifest.permission.CAMERA)
    var keyDraft by remember { mutableStateOf(vm.apiKey) }
    var modelDraft by remember { mutableStateOf(vm.model) }
    var showKey by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val online = remember {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    LaunchedEffect(Unit) { delay(600); vm.refreshVoices() }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Cyan, unfocusedBorderColor = CyanDim, cursorColor = Cyan,
        focusedTextColor = TextLight, unfocusedTextColor = TextLight
    )

    Column(Modifier.fillMaxSize()) {
        HudTopBar("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {

            Section("Permissions") {
                PermRow("Microphone", mic.granted, mic.request) { openAppSettings(ctx) }
                PermRow("Camera", cam.granted, cam.request) { openAppSettings(ctx) }
                PermRow("Internet (${if (online) "online" else "offline"})", true, null) {}
            }

            Section("AI API") {
                Text("Google Gemini API key (stored encrypted on this device). Get one at aistudio.google.com/apikey", color = TextLight.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyDraft, onValueChange = { keyDraft = it }, singleLine = true,
                    label = { Text("API key") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelDraft, onValueChange = { modelDraft = it }, singleLine = true,
                    label = { Text("Model name") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow("Use Google Search for live PKR prices", vm.useSearch) { vm.updateSearch(it) }
                Button(onClick = {
                    vm.updateApiKey(keyDraft); vm.updateModel(modelDraft)
                    vm.postNotice("Saved.")
                }) { Text("Save API settings") }
            }

            Section("Language") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.urdu, onClick = { vm.updateLanguage("ur-PK") }, label = { Text("اردو (Pakistan)") })
                    FilterChip(selected = !vm.urdu, onClick = { vm.updateLanguage("en-US") }, label = { Text("English") })
                }
                Text("Roman Urdu and English are understood in both modes.", color = TextLight.copy(alpha = 0.7f), fontSize = 12.sp)
            }

            Section("Voice") {
                SwitchRow("Speak replies aloud", vm.speakReplies) { vm.updateSpeak(it) }
                Spacer(Modifier.height(6.dp))
                if (vm.voiceList.isEmpty()) {
                    Text(
                        "No Urdu voice found. Install “Speech Services by Google” and download the Urdu voice in your phone’s text-to-speech settings.",
                        color = Danger, fontSize = 13.sp
                    )
                } else {
                    Text("Urdu voices (pick one and tap Test to find the female voice):", color = TextLight.copy(alpha = 0.7f), fontSize = 13.sp)
                    VoiceOption("Default", vm.voiceName.isEmpty()) { vm.updateVoice("") }
                    vm.voiceList.forEach { v -> VoiceOption(v, vm.voiceName == v) { vm.updateVoice(v) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.testVoice() }) { Text("Test voice") }
                    OutlinedButton(onClick = { vm.refreshVoices() }) { Text("Refresh") }
                    OutlinedButton(onClick = {
                        try { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (e: Exception) { vm.postNotice("Open Settings > Languages > Text-to-speech.") }
                    }) { Text("TTS settings") }
                }
            }

            Section("Saved history") {
                Text("${vm.notes.size} saved notes · ${vm.messages.size} chat messages", color = TextLight)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.clearChat(); vm.postNotice("Chat cleared.") }) { Text("Clear chat") }
                    Button(
                        onClick = { confirmClear = true }, enabled = vm.notes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Danger)
                    ) { Text("Delete all notes") }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all notes?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteAllNotes(); confirmClear = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VoiceOption(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(name, color = TextLight, fontSize = 14.sp)
    }
}
