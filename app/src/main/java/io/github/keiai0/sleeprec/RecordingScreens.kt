package io.github.keiai0.sleeprec

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.github.keiai0.sleeprec.data.PauseReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * 押し続けると輪が満ちて、満ちきったら onComplete を呼ぶボタン(誤操作の防止。UX.md §3)。
 * 途中で指を離すと、最初に戻る。読み上げ(TalkBack)では、長押しの操作として扱える。
 */
@Composable
fun LongPressButton(text: String, color: Color, onComplete: () -> Unit, modifier: Modifier = Modifier, holdMs: Int = 1500) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .size(128.dp)
            .semantics { onLongClick(label = text) { onComplete(); true } }
            .pointerInput(holdMs) {
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        progress.animateTo(1f, tween(holdMs, easing = LinearEasing))
                        onComplete()
                        progress.snapTo(0f)
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    scope.launch { progress.snapTo(0f) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val s = Size(size.width - stroke, size.height - stroke)
            drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), s, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress.value, false, Offset(inset, inset), s, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(text, color = color, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

/**
 * 暗い計測画面(全画面)。画面の明るさを最小にし、点灯したままにして、必要最低限だけ表示する(UX.md §3)。
 * - 時計付き: 現在時刻と経過時間。
 * - 完全に暗く: 小さな点だけ。画面に触れると、操作ボタンが数秒だけ現れる。
 */
@Composable
fun DarkMeasuringScreen(
    mode: ScreenMode,
    elapsedMs: Long,
    pauseReason: PauseReason?,
    onTogglePause: () -> Unit,
    onFinishHold: () -> Unit,
) {
    val activity = LocalContext.current as Activity
    DisposableEffect(Unit) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = MIN_BRIGHTNESS }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000L)
        }
    }

    // 完全に暗いモードでは、触れたときだけ操作ボタンを出す(6 秒で消える)
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(6_000L)
            revealed = false
        }
    }
    val showControls = mode == ScreenMode.DARK_CLOCK || revealed
    val dim = Color(0xFF6B7185)
    val dimmer = Color(0xFF474D61)

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { revealed = true },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (mode == ScreenMode.DARK_CLOCK) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)), color = dim, style = MaterialTheme.typography.displayLarge)
            }
            if (mode == ScreenMode.DARK_CLOCK || pauseReason != null) {
                Text(
                    when (pauseReason) {
                        PauseReason.USER -> stringResource(R.string.dark_paused)
                        PauseReason.MIC_BUSY -> stringResource(R.string.dark_mic_busy)
                        null -> stringResource(R.string.dark_recording, formatElapsed(elapsedMs))
                    },
                    color = dim,
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text("●", color = dimmer, style = MaterialTheme.typography.titleLarge) // 動作中を示す、ごく小さな印
            }
            if (showControls) {
                if (pauseReason != PauseReason.MIC_BUSY) {
                    TextButton(onClick = onTogglePause) {
                        Text(stringResource(if (pauseReason != null) R.string.action_resume else R.string.action_pause), color = dim, style = MaterialTheme.typography.titleMedium)
                    }
                }
                LongPressButton(stringResource(R.string.hold_to_finish), dim, onFinishHold)
            }
        }
    }
}

private const val MIN_BRIGHTNESS = 0.02f // 0 に近いほど暗い(端末の最小に近い明るさ)

/** 「3 時間 12 分」のような経過時間。 */
fun formatElapsed(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return if (m >= 60) "${m / 60} 時間 ${m % 60} 分" else "$m 分"
}
