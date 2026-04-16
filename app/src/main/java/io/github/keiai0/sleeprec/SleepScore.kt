package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.SessionStatus
import kotlin.math.sqrt

enum class ScoreBand { EXCELLENT, PRETTY_GOOD, FAIR, POOR }

/** スコアを出さない理由(FR-4.4)。 */
enum class NoScoreReason { SHORT_SLEEP, INTERRUPTED, TOO_SHORT, NO_SLEEP }

/** 1 要素の得点。max は、その夜に採用された配点(再配分の前)。 */
data class ScorePart(val name: Name, val points: Double, val max: Int) {
    enum class Name { DURATION, RESTORATION, CONSISTENCY, FEELING }
}

class ScoreResult(
    val total: Int?,
    val band: ScoreBand?,
    val parts: List<ScorePart>,            // 採用した要素
    val skipped: List<ScorePart.Name>,     // データ不足で除いた要素(Consistency, Feeling)
    val reason: NoScoreReason?,
)

/** 就寝・起床の時刻(エポックミリ秒)。規則性(Consistency)の入力。 */
class NightTimes(val bedAt: Long, val wakeAt: Long)

/**
 * 睡眠スコア(RESEARCH.md §A-3 の暫定式)。合計 100 点。Android に依存しない純粋な関数。
 * Consistency(3 夜未満)と Feeling(未入力)は、データがなければ除き、残りを 100 点に換算し直す。
 */
object SleepScore {
    fun compute(
        status: SessionStatus,
        metrics: SleepMetrics,
        nights: List<NightTimes>, // 直近の夜(この夜を含む)。新しい順でも古い順でもよい
        mood: Int?,               // 起床時の気分 1〜5。未入力は null
        goalMs: Long = Thresholds.GOAL_SLEEP_MS,
    ): ScoreResult {
        val reason = when {
            status == SessionStatus.SHORT_SLEEP -> NoScoreReason.SHORT_SLEEP
            status != SessionStatus.COMPLETED -> NoScoreReason.INTERRUPTED
            !metrics.slept -> NoScoreReason.NO_SLEEP
            metrics.tstMin * 60_000L < Thresholds.SCORE_MIN_TST_MS -> NoScoreReason.TOO_SHORT
            else -> null
        }
        if (reason != null) return ScoreResult(null, null, emptyList(), emptyList(), reason)

        val parts = mutableListOf(
            ScorePart(ScorePart.Name.DURATION, durationRatio(metrics.tstMin * 60_000L, goalMs) * Thresholds.SCORE_DURATION, Thresholds.SCORE_DURATION),
            ScorePart(ScorePart.Name.RESTORATION, restorationRatio(metrics) * Thresholds.SCORE_RESTORATION, Thresholds.SCORE_RESTORATION),
        )
        val skipped = mutableListOf<ScorePart.Name>()

        val cons = consistencyRatio(nights)
        if (cons != null) parts += ScorePart(ScorePart.Name.CONSISTENCY, cons * Thresholds.SCORE_CONSISTENCY, Thresholds.SCORE_CONSISTENCY)
        else skipped += ScorePart.Name.CONSISTENCY

        if (mood != null) parts += ScorePart(ScorePart.Name.FEELING, ((mood - 1) / 4.0).coerceIn(0.0, 1.0) * Thresholds.SCORE_FEELING, Thresholds.SCORE_FEELING)
        else skipped += ScorePart.Name.FEELING

        val total = Math.round(parts.sumOf { it.points } / parts.sumOf { it.max } * 100).toInt()
        return ScoreResult(total, bandOf(total), parts, skipped, null)
    }

    fun bandOf(score: Int): ScoreBand = when {
        score >= 85 -> ScoreBand.EXCELLENT
        score >= 70 -> ScoreBand.PRETTY_GOOD
        score >= 55 -> ScoreBand.FAIR
        else -> ScoreBand.POOR
    }

    /** 4 時間以下は 0、目標まで線形に増え、目標〜9 時間は 100%、9 時間超は 1 時間あたり 10% 減(下限 70%)。 */
    fun durationRatio(tstMs: Long, goalMs: Long): Double {
        val lower = Thresholds.SCORE_MIN_TST_MS
        val upper = Thresholds.DURATION_FULL_UPPER_MS
        val goal = goalMs.coerceIn(lower + 1, upper)
        return when {
            tstMs <= lower -> 0.0
            tstMs < goal -> (tstMs - lower).toDouble() / (goal - lower)
            tstMs <= upper -> 1.0
            else -> (1.0 - 0.10 * (tstMs - upper) / 3_600_000.0).coerceAtLeast(0.70)
        }
    }

    /** 睡眠効率 0.4、WASO 0.2、5 分超の覚醒回数 0.2、入眠潜時 0.2 の重み付き平均(NSF の境界)。 */
    fun restorationRatio(m: SleepMetrics): Double {
        val eff = linear(m.efficiency, zeroAt = 0.74, fullAt = 0.85)
        val waso = linear(m.wasoMin.toDouble(), zeroAt = 51.0, fullAt = 20.0)
        val wakes = when {
            m.wakeCount <= 1 -> 1.0
            m.wakeCount <= 3 -> 0.5
            else -> 0.0
        }
        val latency = linear(m.latencyMin.toDouble(), zeroAt = 46.0, fullAt = 30.0)
        return 0.4 * eff + 0.2 * waso + 0.2 * wakes + 0.2 * latency
    }

    /** 直近の就寝・起床時刻の標準偏差(分)から 0〜1。3 夜未満は null(データ不足)。 */
    fun consistencyRatio(nights: List<NightTimes>): Double? {
        val recent = nights.sortedByDescending { it.bedAt }.take(Thresholds.CONSISTENCY_NIGHTS)
        if (recent.size < Thresholds.CONSISTENCY_MIN_NIGHTS) return null
        val bedSd = sd(recent.map { minutesFromNoon(it.bedAt) })
        val wakeSd = sd(recent.map { minutesFromNoon(it.wakeAt) })
        val f = { x: Double -> linear(x, zeroAt = Thresholds.CONSISTENCY_SD_ZERO_MIN, fullAt = Thresholds.CONSISTENCY_SD_FULL_MIN) }
        return (f(bedSd) + f(wakeSd)) / 2
    }

    // 「正午からの経過分」にして、真夜中をまたぐ就寝時刻(23:30 と 00:30 など)を連続した値として扱う
    private fun minutesFromNoon(epochMs: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Double {
        val local = epochMs + zone.getOffset(epochMs)
        val minOfDay = ((local % DAY_MS) + DAY_MS) % DAY_MS / 60_000.0
        return (minOfDay - 720 + 1440) % 1440
    }

    private const val DAY_MS = 24L * 60 * 60_000

    private fun sd(xs: List<Double>): Double {
        val mean = xs.average()
        return sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
    }

    /** value が zeroAt のとき 0、fullAt のとき 1、その間は線形。fullAt < zeroAt(小さいほど良い指標)にも使える。 */
    private fun linear(value: Double, zeroAt: Double, fullAt: Double): Double =
        ((value - zeroAt) / (fullAt - zeroAt)).coerceIn(0.0, 1.0)
}
