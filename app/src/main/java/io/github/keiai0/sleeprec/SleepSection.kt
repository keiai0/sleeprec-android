package io.github.keiai0.sleeprec

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private fun fmtMin(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

private fun fmtClock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

private fun stageOrder(s: Stage) = when (s) { Stage.AWAKE -> 0; Stage.REM -> 1; Stage.LIGHT -> 2; Stage.DEEP -> 3 }

@Composable
private fun stageColor(s: Stage): Color = when (s) {
    Stage.AWAKE -> MaterialTheme.colorScheme.error
    Stage.REM -> MaterialTheme.colorScheme.tertiary
    Stage.LIGHT -> MaterialTheme.colorScheme.outline
    Stage.DEEP -> MaterialTheme.colorScheme.primary
}

private fun stageLabel(s: Stage) = when (s) {
    Stage.AWAKE -> R.string.stage_awake
    Stage.REM -> R.string.stage_rem
    Stage.LIGHT -> R.string.stage_light
    Stage.DEEP -> R.string.stage_deep
}

private fun partLabel(n: ScorePart.Name) = when (n) {
    ScorePart.Name.DURATION -> R.string.part_duration
    ScorePart.Name.RESTORATION -> R.string.part_restoration
    ScorePart.Name.CONSISTENCY -> R.string.part_consistency
    ScorePart.Name.FEELING -> R.string.part_feeling
}

private fun bandLabel(b: ScoreBand) = when (b) {
    ScoreBand.EXCELLENT -> R.string.band_excellent
    ScoreBand.PRETTY_GOOD -> R.string.band_pretty_good
    ScoreBand.FAIR -> R.string.band_fair
    ScoreBand.POOR -> R.string.band_poor
}

private fun reasonLabel(r: NoScoreReason) = when (r) {
    NoScoreReason.SHORT_SLEEP -> R.string.no_score_short
    NoScoreReason.INTERRUPTED -> R.string.no_score_interrupted
    NoScoreReason.TOO_SHORT -> R.string.no_score_too_short
    NoScoreReason.NO_SLEEP -> R.string.no_score_no_sleep
}

/** 睡眠スコア、指標、睡眠曲線(FR-4.1〜4.4)。ステージは音だけからの大まかな推定であることを、画面に明記する。 */
@Composable
fun SleepSection(analysis: SleepAnalysis, score: ScoreResult) {
    var showExplain by remember { mutableStateOf(false) }
    val m = analysis.metrics

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(stringResource(R.string.sleep_title), style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (score.total != null && score.band != null) {
                Text(score.total.toString(), style = MaterialTheme.typography.displayMedium)
                Column {
                    Text(stringResource(bandLabel(score.band)), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.score_out_of), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Column {
                    Text(stringResource(R.string.no_score), style = MaterialTheme.typography.titleMedium)
                    score.reason?.let { Text(stringResource(reasonLabel(it))) }
                }
            }
        }
        TextButton(onClick = { showExplain = true }) { Text(stringResource(R.string.score_explain_button)) }

        if (score.total != null) {
            score.parts.forEach { p ->
                Text(stringResource(R.string.part_row, stringResource(partLabel(p.name)), p.points, p.max))
            }
            score.skipped.forEach { n ->
                Text(
                    stringResource(R.string.part_skipped, stringResource(partLabel(n)), stringResource(skipReason(n))),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // 指標
        if (m.slept) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.metric_bed_wake, fmtClock(m.onsetAt!!), fmtClock(m.wakeAt!!)))
                Text(stringResource(R.string.metric_tst, fmtMin(m.tstMin), fmtMin(m.timeInBedMin)))
                Text(stringResource(R.string.metric_latency_efficiency, m.latencyMin, m.efficiency * 100))
                Text(stringResource(R.string.metric_waso, m.wasoMin, m.wakeCount))
                val debt = (Thresholds.GOAL_SLEEP_MS / 60_000).toInt() - m.tstMin
                Text(
                    if (debt > 0) stringResource(R.string.metric_debt, fmtMin(debt), fmtMin((Thresholds.GOAL_SLEEP_MS / 60_000).toInt()))
                    else stringResource(R.string.metric_debt_none, fmtMin((Thresholds.GOAL_SLEEP_MS / 60_000).toInt()))
                )
                Text(
                    stringResource(
                        R.string.metric_stages,
                        m.lightMin, pct(m.lightMin, m.tstMin), m.deepMin, pct(m.deepMin, m.tstMin), m.remMin, pct(m.remMin, m.tstMin),
                    )
                )
            }
            Hypnogram(analysis.stages)
            Text(stringResource(R.string.stage_caveat), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }

    if (showExplain) {
        AlertDialog(
            onDismissRequest = { showExplain = false },
            title = { Text(stringResource(R.string.explain_title)) },
            text = {
                Column {
                    score.reason?.let {
                        Text(stringResource(R.string.explain_reason, stringResource(reasonLabel(it))), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(stringResource(R.string.explain_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showExplain = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

private fun pct(part: Int, whole: Int) = if (whole > 0) part * 100.0 / whole else 0.0

private fun skipReason(n: ScorePart.Name) = when (n) {
    ScorePart.Name.CONSISTENCY -> R.string.skip_consistency
    else -> R.string.skip_feeling
}

/** 睡眠曲線。縦は上から Awake / REM / Light / Deep、横は計測開始からの時間(1 分 = 1 マス)。 */
@Composable
private fun Hypnogram(stages: List<Stage>) {
    val colors = Stage.entries.associateWith { stageColor(it) }
    Column(Modifier.padding(top = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = stages.size.coerceAtLeast(1)
            val rowH = size.height / 4f
            val colW = size.width / n
            stages.forEachIndexed { i, s ->
                drawRect(
                    color = colors.getValue(s),
                    topLeft = Offset(i * colW, stageOrder(s) * rowH + rowH * 0.1f),
                    size = Size(colW + 0.5f, rowH * 0.8f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (s in listOf(Stage.AWAKE, Stage.REM, Stage.LIGHT, Stage.DEEP)) {
                Text(stringResource(stageLabel(s)), color = colors.getValue(s), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
