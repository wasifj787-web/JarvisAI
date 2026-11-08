package com.jarvis.ai

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FeatureTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Navy.copy(alpha = 0.85f))
            .border(1.dp, Cyan.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextLight, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun HomeScreen(vm: JarvisViewModel, onMic: () -> Unit, nav: (Screen) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text("J.A.R.V.I.S", color = Cyan, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        Text("AI PERSONAL ASSISTANT", color = CyanDim, fontSize = 12.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(20.dp))
        AiCore(vm.status, vm.level, Modifier.size(250.dp).clickable { vm.interrupt() })
        Spacer(Modifier.height(8.dp))
        Text(statusLabel(vm.status), color = Cyan, fontSize = 14.sp, letterSpacing = 3.sp)
        Text(
            vm.partial.ifBlank { vm.messages.lastOrNull { !it.fromUser }?.text.orEmpty() },
            color = TextLight.copy(alpha = 0.8f), fontSize = 15.sp, maxLines = 3,
            textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp).heightIn(min = 60.dp)
        )
        GlowButton(if (vm.voiceActive) Icons.Default.Stop else Icons.Default.Mic, 84.dp, vm.voiceActive, onMic)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureTile(Icons.Default.Mic, "VOICE", Modifier.weight(1f)) { nav(Screen.VOICE) }
            FeatureTile(Icons.Default.CameraAlt, "CAMERA", Modifier.weight(1f)) { nav(Screen.CAMERA) }
            FeatureTile(Icons.Default.ChatBubble, "TEXT", Modifier.weight(1f)) { nav(Screen.TEXT) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureTile(Icons.Default.History, "NOTES", Modifier.weight(1f)) { nav(Screen.NOTES) }
            FeatureTile(Icons.Default.Settings, "SETTINGS", Modifier.weight(1f)) { nav(Screen.SETTINGS) }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun VoiceScreen(vm: JarvisViewModel, mic: PermState, onMic: () -> Unit, onBack: () -> Unit, toText: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HudTopBar("Voice Assistant", onBack)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AiCore(vm.status, vm.level, Modifier.size(190.dp).clickable { vm.interrupt() })
            Text(statusLabel(vm.status), color = Cyan, fontSize = 13.sp, letterSpacing = 3.sp)
            if (vm.partial.isNotBlank()) {
                Text(vm.partial, color = TextLight, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
            }
            if (!mic.granted) {
                Text(
                    "Microphone permission is needed. Tap the mic to allow.",
                    color = Danger, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)
                )
            }
        }
        ChatList(vm, Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            GlowButton(Icons.Default.Keyboard, 52.dp, false, toText)
            GlowButton(if (vm.voiceActive) Icons.Default.Stop else Icons.Default.Mic, 76.dp, vm.voiceActive, onMic)
            GlowButton(Icons.Default.VolumeOff, 52.dp, false, { vm.interrupt() })
        }
    }
}

@Composable
fun TextScreen(vm: JarvisViewModel, onBack: () -> Unit, toVoice: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val send = {
        if (input.isNotBlank()) { vm.sendText(input); input = "" }
    }
    Column(Modifier.fillMaxSize()) {
        HudTopBar("Text Assistant", onBack) {
            IconButton(onClick = { vm.updateSpeak(!vm.speakReplies) }) {
                Icon(if (vm.speakReplies) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Speak replies", tint = Cyan)
            }
            IconButton(onClick = toVoice) { Icon(Icons.Default.Mic, "Voice mode", tint = Cyan) }
        }
        ChatList(vm, Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type in Urdu, Roman Urdu or English…", color = CyanDim) },
                shape = RoundedCornerShape(24.dp), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan, unfocusedBorderColor = CyanDim, cursorColor = Cyan,
                    focusedTextColor = TextLight, unfocusedTextColor = TextLight
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            Spacer(Modifier.width(8.dp))
            GlowButton(Icons.AutoMirrored.Filled.Send, 52.dp, input.isNotBlank(), { send() })
        }
    }
}
