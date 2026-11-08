package com.jarvis.ai

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(vm: JarvisViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cam = rememberPerm(Manifest.permission.CAMERA)
    val ui = vm.camera
    var live by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }

    // COMPATIBLE mode (TextureView) is required so previewView.bitmap can grab frames.
    val previewView = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { live = false; vm.analyzeUri(uri) }
    }

    DisposableEffect(cam.granted) {
        var provider: ProcessCameraProvider? = null
        if (cam.granted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    provider?.unbindAll()
                    provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                } catch (e: Exception) {
                    vm.postNotice("Camera could not start: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { try { provider?.unbindAll() } catch (_: Exception) {} }
    }

    DisposableEffect(Unit) { onDispose { vm.stopCameraWork() } }

    LaunchedEffect(live, cam.granted) {
        while (live && cam.granted) {
            if (!vm.cameraBusy) previewView.bitmap?.let { vm.analyze(it) }
            delay(3000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        HudTopBar("Camera Vision", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier.fillMaxWidth().height(340.dp).clip(shape).background(Ink).border(1.5.dp, Cyan.copy(alpha = 0.7f), shape)
            ) {
                if (cam.granted) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Camera permission is required to scan objects.", color = TextLight, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { cam.request() }) { Text("Allow camera") }
                        TextButton(onClick = { openAppSettings(ctx) }) { Text("Open app settings") }
                    }
                }
                if (!live && ui.preview != null) {
                    Image(ui.preview.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (ui.loading) ScanLine()
                Text(
                    if (live) "● LIVE" else "", color = Danger, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                GlowButton(Icons.Default.PhotoLibrary, 52.dp, false, { picker.launch("image/*") })
                GlowButton(
                    Icons.Default.CenterFocusStrong, 76.dp, ui.loading,
                    {
                        if (!cam.granted) cam.request()
                        else {
                            live = false
                            if (ui.preview != null && ui.info != null || ui.error != null) vm.clearCamera()
                            previewView.bitmap?.let { vm.analyze(it) }
                        }
                    }
                )
                GlowButton(Icons.Default.Videocam, 52.dp, live, {
                    if (!cam.granted) cam.request() else { if (live) vm.clearCamera(); live = !live }
                })
            }
            Text(
                "Capture · Gallery photo · Live scan", color = CyanDim, fontSize = 11.sp,
                letterSpacing = 2.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (ui.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Analyzing…", color = Cyan)
                }
            }
            ui.error?.let {
                HudCard(Modifier.fillMaxWidth()) { Text(it, color = Danger, fontSize = 15.sp, lineHeight = 22.sp) }
            }
            ui.info?.let { info ->
                ResultCard(info, ui.speaking, onSpeak = { vm.speakInfo() }, onStop = { vm.stopCameraWork() }, onSave = { askSave = true })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askSave && ui.info != null) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = { Text("Save to notes?") },
            text = {
                Text(
                    "Jarvis will store the object name, description, uses and price on this device only. The photo itself is not saved." +
                        if (ui.info.sensitive) "\n\n⚠ This scan may contain sensitive or private information (such as documents, cards or faces). Only save it if you are sure." else ""
                )
            },
            confirmButton = { TextButton(onClick = { vm.saveNote(ui.info); askSave = false }) { Text("Allow & save") } },
            dismissButton = { TextButton(onClick = { askSave = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ScanLine() {
    val t = rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0f, 330f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    Box(Modifier.fillMaxWidth().padding(top = y.dp).height(3.dp).background(Cyan.copy(alpha = 0.8f)))
}

@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = CyanDim, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(value, color = TextLight, fontSize = 16.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun ResultCard(info: ObjectInfo, speaking: Boolean, onSpeak: () -> Unit, onStop: () -> Unit, onSave: () -> Unit) {
    HudCard(Modifier.fillMaxWidth()) {
        if (info.name.isNotBlank()) Text(info.name, color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Field("BRAND / MODEL", info.brand)
        Field("WHAT IT IS", info.description)
        Field("PURPOSE & USES", info.uses)
        Field("KEY FEATURES", info.features)
        Field("ESTIMATED PRICE (PKR)", info.price)
        if (info.sensitive) {
            Text("⚠ Possible sensitive content detected.", color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = if (speaking) onStop else onSpeak) {
                Icon(if (speaking) Icons.Default.Stop else Icons.Default.VolumeUp, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (speaking) "Stop" else "Speak")
            }
            Button(onClick = onSave) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save note")
            }
        }
    }
}
