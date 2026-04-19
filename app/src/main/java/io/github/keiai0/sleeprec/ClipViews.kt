package io.github.keiai0.sleeprec

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val WAVE_BUCKETS = 120

/**
 * 再生中のクリップの、波形・再生位置・シークバー。
 * 波形は WAV から最大値を間引いて描く(UX.md §3)。波形のタップやドラッグで、再生位置を動かせる。
 */
@Composable
fun PlaybackPanel(player: ClipPlayer, path: String) {
    val envelope by produceState<FloatArray?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val wav = File(path).readBytes()
                Waveform.normalize(Waveform.envelope(wav.copyOfRange(WavFormat.HEADER_SIZE.coerceAtMost(wav.size), wav.size), WAVE_BUCKETS))
            }.getOrNull()
        }
    }
    val duration = player.durationMs.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth()) {
        envelope?.let { env ->
            WaveformSeek(env, progress = player.positionMs.toFloat() / duration, onSeek = { f -> player.seekTo((f * duration).toInt()) })
        }
        Slider(
            value = player.positionMs.toFloat(),
            onValueChange = { player.seekTo(it.toInt()) },
            valueRange = 0f..duration.toFloat(),
        )
        Text(
            "${formatMs(player.positionMs.toLong())} / ${formatMs(player.durationMs.toLong())}",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatMs(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(t / 60, t % 60)
}

@Composable
private fun WaveformSeek(envelope: FloatArray, progress: Float, onSeek: (Float) -> Unit) {
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outline
    Canvas(
        Modifier.fillMaxWidth().height(56.dp).padding(vertical = 2.dp)
            .pointerInput(Unit) { detectTapGestures { p -> onSeek((p.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val n = envelope.size
        val step = size.width / n
        val mid = size.height / 2
        val stroke = (step * 0.65f).coerceAtLeast(1f)
        for (i in 0 until n) {
            val h = (envelope[i] * size.height).coerceAtLeast(3f)
            val x = i * step + step / 2
            drawLine(if ((i + 0.5f) / n <= progress) played else rest, Offset(x, mid - h / 2), Offset(x, mid + h / 2), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * クリップの「操作」メニュー(FR-4.8): 端末に保存 / 共有 / 削除 / (デバッグ)分析。
 * 音声が残っていないクリップは、保存・共有を出さない。
 */
@Composable
fun ClipMenu(path: String?, fileName: String, onDelete: (() -> Unit)?, onAnalyze: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val hasAudio = path != null && File(path).exists()
    if (!hasAudio && onDelete == null) return

    TextButton(onClick = { open = true }) { Text(stringResource(R.string.menu_more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (hasAudio) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_save)) },
                onClick = {
                    open = false
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { ClipExport.saveToMusic(context, path!!, fileName) }
                        Toast.makeText(context, if (uri != null) R.string.export_saved else R.string.export_failed, Toast.LENGTH_LONG).show()
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_share)) },
                onClick = {
                    open = false
                    runCatching { ClipExport.share(context, path!!, fileName) }
                        .onFailure { Toast.makeText(context, R.string.share_failed, Toast.LENGTH_LONG).show() }
                },
            )
        }
        if (onAnalyze != null && hasAudio) {
            DropdownMenuItem(text = { Text(stringResource(R.string.debug_analyze)) }, onClick = { open = false; onAnalyze() })
        }
        if (onDelete != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { open = false; onDelete() })
        }
    }
}
