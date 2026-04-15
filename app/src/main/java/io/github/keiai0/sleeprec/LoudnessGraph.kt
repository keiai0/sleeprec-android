package io.github.keiai0.sleeprec

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import io.github.keiai0.sleeprec.data.AudioEvent
import io.github.keiai0.sleeprec.data.LoudnessSample

// グラフの縦軸(dBFS)。実機で静かな部屋は約 -62 なので、その少し下から 0 までを描く
private const val GRAPH_MIN_DB = -70f
private const val GRAPH_MAX_DB = 0f

/**
 * 1 秒ごとの最大音量を棒グラフで描く。イベントの区間は色を変え、再生中のクリップの位置に線を引く。
 * タップした位置(セッション開始からのミリ秒)を onTap に渡す。
 * 8 時間分(約 2.9 万点)でも、画面の幅の分だけの柱にまとめて描くので軽い。
 */
@Composable
fun LoudnessGraph(
    samples: List<LoudnessSample>,
    events: List<AudioEvent>,
    sessionStartedAt: Long,
    totalMs: Long,
    playingId: Long?,
    playPositionMs: Int,
    onTap: (tapMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.outline
    val eventColor = MaterialTheme.colorScheme.primary
    val playColor = MaterialTheme.colorScheme.error
    val total = totalMs.coerceAtLeast(1L)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(total) {
                detectTapGestures { p -> onTap((p.x / size.width * total).toLong().coerceIn(0L, total)) }
            },
    ) {
        val w = size.width
        val h = size.height
        val columns = w.toInt().coerceAtLeast(1)

        // 柱ごとに、その時間帯の最大値を取る
        val colMax = FloatArray(columns) { Loudness.FLOOR_DB }
        for (s in samples) {
            val col = (s.second * 1000L * columns / total).toInt().coerceIn(0, columns - 1)
            if (s.maxDb > colMax[col]) colMax[col] = s.maxDb
        }

        // イベントの区間(背景)。細すぎて見えなくならないよう、最低 3px の幅を持たせる
        for (e in events) {
            val x0 = (e.startedAt - sessionStartedAt).toFloat() / total * w
            val x1 = x0 + e.durationMs.toFloat() / total * w
            drawRect(
                color = eventColor.copy(alpha = if (e.clipPath != null) 0.25f else 0.10f),
                topLeft = Offset(x0, 0f),
                size = Size((x1 - x0).coerceAtLeast(3f), h),
            )
        }

        for (i in 0 until columns) {
            val ratio = ((colMax[i] - GRAPH_MIN_DB) / (GRAPH_MAX_DB - GRAPH_MIN_DB)).coerceIn(0f, 1f)
            if (ratio <= 0f) continue
            drawLine(barColor, Offset(i + 0.5f, h), Offset(i + 0.5f, h * (1f - ratio)), strokeWidth = 1f)
        }

        // 再生位置
        val playing = events.firstOrNull { it.id == playingId }
        if (playing != null) {
            val x = ((playing.startedAt - sessionStartedAt) + playPositionMs).toFloat() / total * w
            drawLine(playColor, Offset(x, 0f), Offset(x, h), strokeWidth = 3f)
        }
    }
}
