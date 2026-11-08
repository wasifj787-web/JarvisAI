package com.jarvis.ai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private val vm: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { JarvisTheme { JarvisApp(vm) } }
    }
}

@Composable
fun JarvisApp(vm: JarvisViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val mic = rememberPerm(Manifest.permission.RECORD_AUDIO)
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.notice) {
        vm.notice?.let { snack.showSnackbar(it); vm.consumeNotice() }
    }

    fun go(s: Screen) {
        if (s == Screen.CAMERA) vm.stopVoice()
        screen = s
    }
    val onMic = { if (mic.granted) vm.toggleVoice() else mic.request() }

    BackHandler(enabled = screen != Screen.HOME) { go(Screen.HOME) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Color(0xFF031230), Ink)))
            .systemBarsPadding()
            .imePadding()
    ) {
        Crossfade(screen, label = "nav") { s ->
            when (s) {
                Screen.HOME -> HomeScreen(vm, onMic) { go(it) }
                Screen.VOICE -> VoiceScreen(vm, mic, onMic, { go(Screen.HOME) }) { go(Screen.TEXT) }
                Screen.TEXT -> TextScreen(vm, { go(Screen.HOME) }) { go(Screen.VOICE) }
                Screen.CAMERA -> CameraScreen(vm) { go(Screen.HOME) }
                Screen.NOTES -> NotesScreen(vm) { go(Screen.HOME) }
                Screen.SETTINGS -> SettingsScreen(vm) { go(Screen.HOME) }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}
