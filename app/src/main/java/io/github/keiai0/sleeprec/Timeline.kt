package io.github.keiai0.sleeprec

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.keiai0.sleeprec.data.ApneaCandidate
import io.github.keiai0.sleeprec.data.AudioEvent
import io.github.keiai0.sleeprec.data.EventType
import io.github.keiai0.sleeprec.data.LoudnessSample
import java.util.TimeZone

/** タイムラインに置くマーカーの種類。形と色の両方で区別し、凡例に言葉のラベルを付ける。 */
enum class MarkerKind(val labelRes: Int) {
    SNORE(R.string.marker_snore),
    TALK(R.string.marker_talk),
    COUGH(R.string.marker_cough),
    OTHER(R.string.marker_other),
    APNEA(R.string.marker_apnea);

    companion object {
        fun of(type: EventType): MarkerKind = when (type) {
            EventType.SNORING -> SNORE
            EventType.SLEEP_TALK -> TALK
            EventType.COUGH -> COUGH
            else -> OTHER
        }
    }
}

// グラフの縦軸(dBFS)。実機で静かな部屋は約 -62 なので、その少し下から 0 までを描く
private const val GRAPH_MIN_DB = -70f
private const val GRAPH_MAX_DB = 0f

/**
 * 結果画面のタイムライン(UX.md §7)。睡眠曲線・音量・音のマーカーを、同じ時間軸(横)に縦に重ねる。
 * タップした時刻に最も近い音(音声イベント、または無呼吸の候補)を再生し、再生位置を全段にまたがる線で示す。
 * 8 時間ぶん(約 2.9 万点)でも、画面の幅の分だけの柱にまとめて描くので軽い。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimeline(
    stages: List<Stage>,
    samples: List<LoudnessSample>,
    events: List<AudioEvent>,
    apneas: List<ApneaCandidate>,
    pauses: List<Pair<Long, Long?>>,
    sessionStartedAt: Long,
    totalMs: Long,
    targets: List<PlayTarget>,
    playingId: Long?,
    playPositionMs: Int,
    onTap: (PlayTarget) -> Unit,
) {
    val total = totalMs.coerceAtLeast(1L)
    val cs = MaterialTheme.colorScheme
    val stageColors = Stage.entries.associateWith { stageColor(it) }
    val kindColors = mapOf(
        MarkerKind.SNORE to cs.primary, MarkerKind.TALK to cs.tertiary, MarkerKind.COUGH to cs.secondary,
        MarkerKind.OTHER to cs.onSurfaceVariant, MarkerKind.APNEA to cs.error,
    )
    val barColor = cs.outline
    val eventShade = cs.primary
    val gridColor = cs.outlineVariant
    val playColor = cs.error
    val pauseColor = cs.onSurface
    val labelColor = cs.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val ticks = TimeAxis.ticks(sessionStartedAt, total, TimeZone.getDefault().getOffset(sessionStartedAt).toLong())

    Column(Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TIMELINE_HEIGHT_DP.dp)
                .pointerInput(total, targets) {
                    detectTapGestures { p ->
                        val tapMs = (p.x / size.width * total).toLong().coerceIn(0L, total)
                        ClipPlayback.nearestTarget(targets, tapMs)?.let(onTap)
                    }
                },
        ) {
            val w = size.width
            val hypH = HYP_H.dp.toPx()
            val loudTop = hypH + GAP.dp.toPx()
            val loudH = LOUD_H.dp.toPx()
            val markTop = loudTop + loudH + GAP.dp.toPx()
            val markH = MARK_H.dp.toPx()
            val axisTop = markTop + markH + 2.dp.toPx()
            fun xOf(ms: Long) = ms.toFloat() / total * w

            // 目盛りの縦線(睡眠曲線と音量の両方を貫く)
            for (t in ticks) {
                val x = xOf(t.offsetMs)
                drawLine(gridColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, markTop), strokeWidth = 1f)
            }

            // 1 段目: 睡眠曲線(上から 覚醒 / REM / 浅い / 深い)
            val rowH = hypH / 4f
            val colW = 60_000f / total * w
            stages.forEachIndexed { i, s ->
                // 最後の 1 分は、計測の終わりで切る(短い計測で、右端をはみ出さないように)
                val x0 = xOf(i * 60_000L)
                val x1 = (x0 + colW + 0.5f).coerceAtMost(w)
                if (x1 > x0) {
                    drawRect(
                        color = stageColors.getValue(s),
                        topLeft = Offset(x0, stageOrder(s) * rowH + rowH * 0.1f),
                        size = Size(x1 - x0, rowH * 0.8f),
                    )
                }
            }

            // 2 段目: 音量(1 秒ごとの最大値)。柱ごとに、その時間帯の最大を取る
            val columns = w.toInt().coerceAtLeast(1)
            val colMax = FloatArray(columns) { Loudness.FLOOR_DB }
            for (s in samples) {
                val col = (s.second * 1000L * columns / total).toInt().coerceIn(0, columns - 1)
                if (s.maxDb > colMax[col]) colMax[col] = s.maxDb
            }
            for (e in events) {
                val x0 = xOf(e.startedAt - sessionStartedAt)
                val x1 = xOf(e.startedAt - sessionStartedAt + e.durationMs)
                drawRect(
                    eventShade.copy(alpha = if (e.clipPath != null) 0.25f else 0.10f),
                    Offset(x0, loudTop), Size((x1 - x0).coerceAtLeast(3f), loudH),
                )
            }
            for (i in 0 until columns) {
                val ratio = ((colMax[i] - GRAPH_MIN_DB) / (GRAPH_MAX_DB - GRAPH_MIN_DB)).coerceIn(0f, 1f)
                if (ratio > 0f) drawLine(barColor, Offset(i + 0.5f, loudTop + loudH), Offset(i + 0.5f, loudTop + loudH * (1f - ratio)), strokeWidth = 1f)
            }

            // 一時停止した時間帯は、両方の段に薄い帯を重ねる(この間は録音していない)
            for ((ps, pe) in pauses) {
                val x0 = xOf(ps - sessionStartedAt)
                val x1 = xOf((pe ?: (sessionStartedAt + total)) - sessionStartedAt)
                drawRect(pauseColor.copy(alpha = 0.18f), Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(2f), loudTop + loudH))
            }

            // 3 段目: 音のマーカー(音声が残っているものは塗りつぶし、削除済みは輪郭だけ)
            val cy = markTop + markH / 2f
            val r = MARK_R.dp.toPx()
            for (e in events) {
                val cx = xOf(e.startedAt - sessionStartedAt + e.durationMs / 2)
                val kind = MarkerKind.of(e.type)
                drawMarker(kind, kindColors.getValue(kind), cx, cy, r, filled = e.clipPath != null)
            }
            for (c in apneas) {
                val cx = xOf(c.startedAt - sessionStartedAt + c.silenceMs / 2)
                drawMarker(MarkerKind.APNEA, kindColors.getValue(MarkerKind.APNEA), cx, cy, r, filled = c.clipPath != null)
            }

            // 目盛りの時刻
            for (t in ticks) {
                val layout = measurer.measure(t.label, tickStyle)
                val x = (xOf(t.offsetMs) - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
                drawText(layout, topLeft = Offset(x, axisTop))
            }

            // 再生位置(全段にまたがる線)
            targets.firstOrNull { it.id == playingId }?.let { p ->
                val x = xOf(p.startMs + playPositionMs)
                drawLine(playColor, Offset(x, 0f), Offset(x, markTop + markH), strokeWidth = 3f)
            }
        }

        // 凡例(アイコンだけにしない。UX.md §6-3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
            for (s in listOf(Stage.AWAKE, Stage.REM, Stage.LIGHT, Stage.DEEP)) {
                LegendItem(stringResource(stageLabel(s)), stageColors.getValue(s)) { c -> drawRect(c) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (k in MarkerKind.entries) {
                LegendItem(stringResource(k.labelRes), kindColors.getValue(k)) { c ->
                    drawMarker(k, c, size.width / 2, size.height / 2, size.minDimension * 0.45f, filled = true)
                }
            }
        }
    }
}

private const val HYP_H = 72
private const val LOUD_H = 90
private const val MARK_H = 22
private const val GAP = 6
private const val MARK_R = 5
private const val TIMELINE_HEIGHT_DP = HYP_H + GAP + LOUD_H + GAP + MARK_H + 2 + 18

@Composable
private fun LegendItem(label: String, color: Color, draw: DrawScope.(Color) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) { draw(color) }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
    }
}

/** マーカーの形: いびき=丸、寝言=ひし形、咳=三角、その他=四角、無呼吸の候補=×。 */
private fun DrawScope.drawMarker(kind: MarkerKind, color: Color, cx: Float, cy: Float, r: Float, filled: Boolean) {
    val style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = 2f)
    when (kind) {
        MarkerKind.SNORE -> drawCircle(color, r, Offset(cx, cy), style = style)
        MarkerKind.TALK -> drawPath(Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
        }, color, style = style)
        MarkerKind.COUGH -> drawPath(Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r, cy + r); lineTo(cx - r, cy + r); close()
        }, color, style = style)
        MarkerKind.OTHER -> drawRect(color, Offset(cx - r * 0.85f, cy - r * 0.85f), Size(r * 1.7f, r * 1.7f), style = style)
        MarkerKind.APNEA -> {
            drawLine(color, Offset(cx - r, cy - r), Offset(cx + r, cy + r), strokeWidth = if (filled) 4f else 2f)
            drawLine(color, Offset(cx - r, cy + r), Offset(cx + r, cy - r), strokeWidth = if (filled) 4f else 2f)
        }
    }
}
