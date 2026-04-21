package io.github.keiai0.sleeprec

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private fun fmtMin(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

private fun fmtClock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

internal fun stageOrder(s: Stage) = when (s) { Stage.AWAKE -> 0; Stage.REM -> 1; Stage.LIGHT -> 2; Stage.DEEP -> 3 }

@Composable
internal fun stageColor(s: Stage): Color = when (s) {
    Stage.AWAKE -> MaterialTheme.colorScheme.error
    Stage.REM -> MaterialTheme.colorScheme.tertiary
    Stage.LIGHT -> MaterialTheme.colorScheme.outline
    Stage.DEEP -> MaterialTheme.colorScheme.primary
}

internal fun stageLabel(s: Stage) = when (s) {
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

internal fun bandLabel(b: ScoreBand) = when (b) {
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

private fun skipReason(n: ScorePart.Name) = when (n) {
    ScorePart.Name.CONSISTENCY -> R.string.skip_consistency
    else -> R.string.skip_feeling
}

/** 睡眠スコア(FR-4.3、4.4)。点数を大きく、その内訳を、要素ごとの棒で見せる。スコアを出せないときは、その理由を 1 行で示す。 */
@Composable
fun ScoreCard(score: ScoreResult) {
    var showExplain by remember { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.score_title),
        trailing = { TextButton(onClick = { showExplain = true }) { Text(stringResource(R.string.score_explain_button)) } },
    ) {
        if (score.total != null && score.band != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(score.total.toString(), style = MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.score_out_of), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                Column(Modifier.weight(1f)) {}
                Pill(stringResource(bandLabel(score.band)), Tone.GOOD, Modifier.padding(bottom = 10.dp))
            }
            score.parts.forEach { p ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow(stringResource(partLabel(p.name)), stringResource(R.string.part_points, p.points, p.max))
                    ValueBar((p.points / p.max).toFloat())
                }
            }
            if (score.skipped.isNotEmpty()) {
                val skippedNames = score.skipped.map { stringResource(skipReason(it)) }
                MutedText(stringResource(R.string.score_skipped, skippedNames.joinToString("、")))
            }
        } else {
            Text(stringResource(R.string.no_score), style = MaterialTheme.typography.titleLarge)
            score.reason?.let { MutedText(stringResource(reasonLabel(it))) }
        }
    }

    if (showExplain) {
        AlertDialog(
            onDismissRequest = { showExplain = false },
            title = { Text(stringResource(R.string.explain_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    score.reason?.let { Text(stringResource(R.string.explain_reason, stringResource(reasonLabel(it))), style = MaterialTheme.typography.titleSmall) }
                    ExplainItem(R.string.part_duration, R.string.explain_duration)
                    ExplainItem(R.string.part_restoration, R.string.explain_restoration)
                    ExplainItem(R.string.part_consistency, R.string.explain_consistency)
                    ExplainItem(R.string.part_feeling, R.string.explain_feeling)
                    MutedText(stringResource(R.string.explain_footer))
                }
            },
            confirmButton = { TextButton(onClick = { showExplain = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun ExplainItem(titleRes: Int, bodyRes: Int) {
    Column {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 睡眠の指標(FR-4.2)。値を大きくした小さなタイルを 2 列に並べ、ステージの内訳を 1 本の帯で見せる。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricsCard(analysis: SleepAnalysis) {
    val m = analysis.metrics
    val goalMin = AppSettings.state.collectAsState().value.goalSleepMin

    SectionCard(title = stringResource(R.string.metrics_title)) {
        if (!m.slept) {
            MutedText(stringResource(R.string.metrics_none))
            return@SectionCard
        }
        val debt = goalMin - m.tstMin
        val rows = listOf(
            Triple(stringResource(R.string.metric_tst_label), fmtMin(m.tstMin), stringResource(R.string.metric_tib, fmtMin(m.timeInBedMin))) to
                Triple(stringResource(R.string.metric_eff_label), "%.0f%%".format(m.efficiency * 100), null as String?),
            Triple(stringResource(R.string.metric_onset_label), fmtClock(m.onsetAt!!), null as String?) to
                Triple(stringResource(R.string.metric_wake_label), fmtClock(m.wakeAt!!), null as String?),
            Triple(stringResource(R.string.metric_latency_label), stringResource(R.string.metric_minutes, m.latencyMin), null as String?) to
                Triple(stringResource(R.string.metric_wakes_label), stringResource(R.string.metric_times, m.wakeCount), stringResource(R.string.metric_wakes_sub)),
            Triple(stringResource(R.string.metric_waso_label), stringResource(R.string.metric_minutes, m.wasoMin), null as String?) to
                Triple(
                    stringResource(R.string.metric_debt_label),
                    if (debt > 0) fmtMin(debt) else stringResource(R.string.metric_debt_zero),
                    stringResource(R.string.metric_goal, fmtMin(goalMin)),
                ),
        )
        rows.forEach { (l, r) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(l.first, l.second, Modifier.weight(1f), l.third)
                MetricTile(r.first, r.second, Modifier.weight(1f), r.third)
            }
        }

        // ステージの内訳: 1 本の帯と、割合の一覧
        if (m.tstMin > 0) {
            Text(stringResource(R.string.stage_share_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            val parts = listOf(Stage.LIGHT to m.lightMin, Stage.DEEP to m.deepMin, Stage.REM to m.remMin)
            Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                parts.forEach { (st, min) ->
                    if (min > 0) Row(Modifier.weight(min.toFloat()).height(12.dp).background(stageColor(st))) {}
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                parts.forEach { (st, min) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StageDot(st)
                        Text(
                            stringResource(R.string.stage_share_item, stringResource(stageLabel(st)), min * 100 / m.tstMin),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            ExpandableNote(stringResource(R.string.stage_caveat_summary), stringResource(R.string.stage_caveat))
        }
    }
}

@Composable
private fun StageDot(st: Stage) {
    val c = stageColor(st)
    Canvas(Modifier.size(10.dp)) { drawCircle(c) }
}
