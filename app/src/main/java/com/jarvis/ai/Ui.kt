package com.jarvis.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.cos
import kotlin.math.sin

val Cyan = Color(0xFF00E5FF)
val CyanDim = Color(0xFF1AA7C9)
val DeepBlue = Color(0xFF0A2A5E)
val Navy = Color(0xFF06142B)
val Ink = Color(0xFF02060F)
val TextLight = Color(0xFFD8F6FF)
val Danger = Color(0xFFFF5470)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, onPrimary = Ink, secondary = CyanDim, background = Ink,
            onBackground = TextLight, surface = Navy, onSurface = TextLight,
            surfaceVariant = Color(0xFF0B1F3F), outline = CyanDim, error = Danger
        ),
        content = content
    )
}

// ---------- Permissions ----------
class PermState(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberPerm(permission: String): PermState {
    val ctx = LocalContext.current
    fun check() = ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(check()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = check() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return PermState(granted) { launcher.launch(permission) }
}

fun openAppSettings(ctx: android.content.Context) {
    val i = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", ctx.packageName, null)
    )
    ctx.startActivity(i)
}

// ---------- Components ----------
@Composable
fun AiCore(status: Status, level: Float, modifier: Modifier = Modifier.size(240.dp)) {
    val t = rememberInfiniteTransition(label = "core")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "r1")
    val rot2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "r2")
    val pulse by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
    )
    val target = when (status) {
        Status.LISTENING -> 0.25f + level * 0.75f
        Status.SPEAKING -> 0.3f + pulse * 0.5f
        Status.THINKING -> pulse
        Status.IDLE -> pulse * 0.2f
    }
    val amp by animateFloatAsState(target, tween(120), label = "amp")

    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(
            Brush.radialGradient(listOf(Cyan.copy(alpha = 0.18f + 0.3f * amp), Color.Transparent), c, r),
            radius = r, center = c
        )
        val inner = r * 0.52f * (0.75f + 0.3f * amp)
        drawCircle(
            Brush.radialGradient(listOf(Cyan.copy(alpha = 0.55f), DeepBlue.copy(alpha = 0.25f), Color.Transparent), c, inner),
            radius = inner, center = c
        )
        drawCircle(Cyan, radius = r * 0.62f, center = c, style = Stroke(3.dp.toPx()))
        drawCircle(CyanDim.copy(alpha = 0.6f), radius = r * 0.68f, center = c, style = Stroke(1.dp.toPx()))
        val a1 = r * 0.78f
        rotate(rot, c) {
            drawArc(Cyan, 0f, 80f, false, Offset(c.x - a1, c.y - a1), Size(a1 * 2, a1 * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Cyan, 180f, 80f, false, Offset(c.x - a1, c.y - a1), Size(a1 * 2, a1 * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        val a2 = r * 0.88f
        rotate(rot2, c) {
            drawArc(CyanDim, 40f, 50f, false, Offset(c.x - a2, c.y - a2), Size(a2 * 2, a2 * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawArc(CyanDim, 220f, 50f, false, Offset(c.x - a2, c.y - a2), Size(a2 * 2, a2 * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        rotate(rot / 2f, c) {
            for (i in 0 until 48) {
                val a = Math.toRadians(i * 7.5).toFloat()
                val ri = r * 0.94f
                val ro = r * (if (i % 4 == 0) 1.0f else 0.97f)
                drawLine(
                    Cyan.copy(alpha = 0.5f),
                    Offset(c.x + cos(a) * ri, c.y + sin(a) * ri),
                    Offset(c.x + cos(a) * ro, c.y + sin(a) * ro),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun GlowButton(icon: ImageVector, size: Dp, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .shadow(if (active) 24.dp else 10.dp, CircleShape, ambientColor = Cyan, spotColor = Cyan)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(if (active) Cyan else DeepBlue, Navy)))
            .border(2.dp, Cyan, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (active) Ink else Cyan, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun HudCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Navy.copy(alpha = 0.95f), Color(0xFF040C1C))))
            .border(1.dp, Cyan.copy(alpha = 0.45f), shape)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun HudTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Cyan) }
        Text(
            title.uppercase(), color = Cyan, fontSize = 18.sp, letterSpacing = 3.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        actions()
    }
}

fun statusLabel(s: Status) = when (s) {
    Status.IDLE -> "STANDBY"
    Status.LISTENING -> "LISTENING…"
    Status.THINKING -> "PROCESSING…"
    Status.SPEAKING -> "SPEAKING…"
}

@Composable
fun Bubble(m: ChatMessage) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (m.fromUser) DeepBlue else Navy)
                .border(1.dp, if (m.fromUser) Cyan else CyanDim.copy(alpha = 0.5f), shape)
                .padding(12.dp)
        ) {
            Text(m.text, color = TextLight, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
fun ChatList(vm: JarvisViewModel, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val n = vm.messages.size
    val thinking = vm.status == Status.THINKING
    LaunchedEffect(n, thinking) {
        val last = n - 1 + (if (thinking) 1 else 0)
        if (last >= 0) state.animateScrollToItem(last)
    }
    LazyColumn(
        modifier, state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(vm.messages) { Bubble(it) }
        if (thinking) item { Text("●  ●  ●   JARVIS is thinking", color = Cyan, fontSize = 13.sp) }
    }
}
