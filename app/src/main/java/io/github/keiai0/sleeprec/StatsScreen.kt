package io.github.keiai0.sleeprec

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.keiai0.sleeprec.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

private val WEEKDAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "月", DayOfWeek.TUESDAY to "火", DayOfWeek.WEDNESDAY to "水", DayOfWeek.THURSDAY to "木",
    DayOfWeek.FRIDAY to "金", DayOfWeek.SATURDAY to "土", DayOfWeek.SUNDAY to "日",
)

/** 統計タブ(FR-5.1、5.2)。週 / 月を切り替え、前後の期間へ移動できる。日ごとの一覧は、「記録」タブ。 */
@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val store = remember { SessionStore.create(context) }
    var kindName by rememberSaveable { mutableStateOf(PeriodKind.WEEK.name) }
    var anchorDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val kind = PeriodKind.valueOf(kindName)
    val period = remember(kind, anchorDay) { StatsPeriod.of(kind, LocalDate.ofEpochDay(anchorDay)) }

    var nights by remember { mutableStateOf<List<NightSummary>?>(null) }
    LaunchedEffect(period) {
        nights = null
        nights = withContext(Dispatchers.IO) { StatsLoader.load(store, period) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_stats), style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == PeriodKind.WEEK, onClick = { kindName = PeriodKind.WEEK.name; anchorDay = LocalDate.now().toEpochDay() }, label = { Text(stringResource(R.string.period_week)) })
            FilterChip(selected = kind == PeriodKind.MONTH, onClick = { kindName = PeriodKind.MONTH.name; anchorDay = LocalDate.now().toEpochDay() }, label = { Text(stringResource(R.string.period_month)) })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { anchorDay = period.previous().start.toEpochDay() }) { Text(stringResource(R.string.period_prev)) }
            Text(periodLabel(period), style = MaterialTheme.typography.titleMedium)
            // 今の期間より先には進めない
            TextButton(enabled = period.end < LocalDate.now(), onClick = { anchorDay = period.next().start.toEpochDay() }) { Text(stringResource(R.string.period_next)) }
        }

        val list = nights
        if (list != null) {
            val stats = PeriodStats.of(list, period)
            if (stats.nights == 0) {
                Text(stringResource(R.string.stats_empty), modifier = Modifier.padding(top = 16.dp))
            } else {
                // 画面に出ている集計とグラフを、そのまま画像にする(FR-5.4)
                val layer = rememberGraphicsLayer()
                val background = MaterialTheme.colorScheme.background
                ExportMenu(layer, period)
                Column(
                    Modifier
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                        .background(background)
                        .padding(8.dp),
                ) {
                    Text(stringResource(R.string.export_image_title, periodLabel(period)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    StatsContent(period, stats)
                }
            }
            Text(stringResource(R.string.stats_note), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 「書き出し」メニュー: 統計を画像として、端末に保存 / 共有する。 */
@Composable
private fun ExportMenu(layer: GraphicsLayer, period: StatsPeriod) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.export_menu)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.export_save_image)) },
            onClick = {
                open = false
                scope.launch {
                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                    val uri = withContext(Dispatchers.IO) { ImageExport.saveToPictures(context, bitmap, ImageExport.fileName(period)) }
                    Toast.makeText(context, if (uri != null) R.string.export_image_saved else R.string.export_failed, Toast.LENGTH_LONG).show()
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.export_share_image)) },
            onClick = {
                open = false
                scope.launch {
                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                    runCatching { withContext(Dispatchers.IO) { ImageExport.share(context, bitmap, ImageExport.fileName(period)) } }
                        .onFailure { Toast.makeText(context, R.string.share_failed, Toast.LENGTH_LONG).show() }
                }
            },
        )
    }
}

private fun periodLabel(p: StatsPeriod): String = when (p.kind) {
    PeriodKind.WEEK -> "%d/%d〜%d/%d".format(p.start.monthValue, p.start.dayOfMonth, p.end.monthValue, p.end.dayOfMonth)
    PeriodKind.MONTH -> "%d年%d月".format(p.start.year, p.start.monthValue)
}

/** 集計のカードとグラフ。 */
@Composable
fun StatsContent(period: StatsPeriod, stats: PeriodStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val goalMin = (Thresholds.GOAL_SLEEP_MS / 60_000).toInt()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.stat_avg_sleep), stats.avgTstMin?.let { fmtHM(it) } ?: "-", stringResource(R.string.stat_goal, fmtHM(goalMin)), Modifier.weight(1f))
            StatCard(
                stringResource(R.string.stat_avg_score), stats.avgScore?.toString() ?: "-",
                stats.avgScore?.let { stringResource(bandLabel(SleepScore.bandOf(it))) } ?: stringResource(R.string.stat_no_score), Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.stat_avg_bed), stats.avgBedMinuteOfDay?.let { TimeMath.format(it) } ?: "-", null, Modifier.weight(1f))
            StatCard(stringResource(R.string.stat_avg_wake), stats.avgWakeMinuteOfDay?.let { TimeMath.format(it) } ?: "-", null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                stringResource(R.string.stat_avg_snore),
                stats.avgSnoreCount?.let { stringResource(R.string.stat_snore_value, it) } ?: "-",
                stats.avgSnoreMs?.let { stringResource(R.string.stat_snore_time, fmtMs(it)) }, Modifier.weight(1f),
            )
            StatCard(stringResource(R.string.stat_max_db), stats.maxDb?.let { "%.1f".format(it) } ?: "-", stringResource(R.string.stat_max_db_note), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.stat_nights), stringResource(R.string.stat_nights_value, stats.nights, period.days), null, Modifier.weight(1f))
            StatCard(stringResource(R.string.stat_efficiency), stats.avgEfficiency?.let { "%.0f%%".format(it * 100) } ?: "-", null, Modifier.weight(1f))
        }

        Text(stringResource(R.string.chart_sleep_title), style = MaterialTheme.typography.titleMedium)
        BarChart(
            values = stats.points.map { it.tstMin?.let { m -> m / 60f } },
            labels = xLabels(period),
            max = 10f,
            goal = goalMin / 60f,
            goalLabel = stringResource(R.string.chart_goal_label),
            axisLabel = "10h",
        )
        Text(stringResource(R.string.chart_score_title), style = MaterialTheme.typography.titleMedium)
        BarChart(
            values = stats.points.map { it.score?.toFloat() },
            labels = xLabels(period),
            max = 100f,
            goal = null,
            goalLabel = "",
            axisLabel = "100",
        )
    }
}

private fun xLabels(p: StatsPeriod): List<String?> = (0 until p.days).map { i ->
    val d = p.start.plusDays(i.toLong())
    when (p.kind) {
        PeriodKind.WEEK -> WEEKDAY_LABELS.getValue(d.dayOfWeek)
        // 月は、混み合わないよう、1・5・10・15… だけにラベルを付ける
        PeriodKind.MONTH -> if (d.dayOfMonth == 1 || d.dayOfMonth % 5 == 0) d.dayOfMonth.toString() else null
    }
}

@Composable
private fun StatCard(title: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 日ごとの棒グラフ。値のない日は、棒を描かない。目標があれば、破線で示す。 */
@Composable
private fun BarChart(values: List<Float?>, labels: List<String?>, max: Float, goal: Float?, goalLabel: String, axisLabel: String) {
    val bar = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, color = textColor)
    Canvas(Modifier.fillMaxWidth().height(166.dp)) {
        val labelH = 18.dp.toPx()
        val topPad = 16.dp.toPx() // 上限の目盛りの文字を置く余白(棒に重ならないように)
        val chartH = size.height - labelH - topPad
        val n = values.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val base = topPad + chartH // 棒の下端(基準線)
        drawLine(grid, Offset(0f, base), Offset(size.width, base), strokeWidth = 1f)
        drawLine(grid.copy(alpha = 0.5f), Offset(0f, topPad), Offset(size.width, topPad), strokeWidth = 1f)
        drawText(measurer.measure(axisLabel, style), topLeft = Offset(2f, 0f))
        values.forEachIndexed { i, v ->
            if (v != null) {
                val h = (v / max).coerceIn(0f, 1f) * chartH
                drawRect(bar, Offset(i * slot + (slot - barW) / 2, base - h), Size(barW, h))
            }
            labels.getOrNull(i)?.let { l ->
                val layout = measurer.measure(l, style)
                drawText(layout, topLeft = Offset(i * slot + (slot - layout.size.width) / 2, base + 3.dp.toPx()))
            }
        }
        if (goal != null) {
            val y = base - (goal / max).coerceIn(0f, 1f) * chartH
            drawLine(goalColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            val layout = measurer.measure(goalLabel, TextStyle(fontSize = 11.sp, color = goalColor))
            drawText(layout, topLeft = Offset(size.width - layout.size.width - 2f, y - layout.size.height - 1f))
        }
    }
}

private fun fmtHM(min: Int) = "%d:%02d".format(min / 60, min % 60)

// 「1 時間 5 分」「11 分」「40 秒」のように、単位付きで表す(分秒か時分かで迷わないように)
private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return when {
        s >= 3600 -> "%d 時間 %d 分".format(s / 3600, s % 3600 / 60)
        s >= 60 -> "%d 分".format(s / 60)
        else -> "%d 秒".format(s)
    }
}
