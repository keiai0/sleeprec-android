package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.AudioEvent
import io.github.keiai0.sleeprec.data.EventType
import kotlin.math.max
import kotlin.math.min

enum class Stage { AWAKE, LIGHT, DEEP, REM }

/** 1 晩の指標。分は、計測開始からの経過分。 */
data class SleepMetrics(
    val timeInBedMin: Int,
    val latencyMin: Int,     // 入眠潜時(入眠できなければ、計測全体)
    val tstMin: Int,         // 総睡眠時間(入眠から最後の眠りの終わりまでのうち、眠っていた分)
    val wasoMin: Int,        // 入眠後の覚醒時間
    val wakeCount: Int,      // 5 分以上続いた中途覚醒の回数
    val lightMin: Int,
    val deepMin: Int,
    val remMin: Int,
    val onsetAt: Long?,      // 入眠の時刻(エポックミリ秒)。入眠できなければ null
    val wakeAt: Long?,       // 最後の眠りの終わりの時刻
) {
    val slept: Boolean get() = onsetAt != null && tstMin > 0
    /** 睡眠効率 = 総睡眠時間 / ベッドにいた時間(NSF)。 */
    val efficiency: Double get() = if (timeInBedMin > 0) tstMin.toDouble() / timeInBedMin else 0.0
}

class SleepAnalysis(val stages: List<Stage>, val metrics: SleepMetrics)

/**
 * 音イベントから、1 分ごとの覚醒/睡眠、ステージ、指標を推定する(Android に依存しない純粋な関数)。
 * マイクだけの推定なので精度には限界がある: 静かに起きていても「睡眠」になり、
 * ステージ(特に Deep/REM)は、90 分周期という一般的な傾向による暫定ルールにすぎない。
 */
object SleepAnalyzer {
    // 「活動」とみなさない種別。いびき・歯ぎしりなどは眠っていても出る。動物・環境音は本人の動きではない
    private val NOT_ACTIVITY = setOf(
        EventType.SNORING, EventType.GRINDING, EventType.FART, EventType.ANIMAL, EventType.AMBIENT,
    )

    fun analyze(startedAt: Long, endedAt: Long, events: List<AudioEvent>): SleepAnalysis {
        val totalMin = ((endedAt - startedAt).coerceAtLeast(0) + 59_999) / 60_000
        val n = totalMin.toInt()
        val activityMs = activityPerMinute(startedAt, n, events)
        val awake = BooleanArray(n) { activityMs[it] >= Thresholds.AWAKE_ACTIVITY_MS }

        val onset = findOnset(awake)
        if (onset == null) {
            val m = SleepMetrics(n, n, 0, 0, 0, 0, 0, 0, null, null)
            return SleepAnalysis(List(n) { Stage.AWAKE }, m)
        }
        val end = (n - 1 downTo onset).first { !awake[it] } + 1 // 最後の「睡眠」の分の次

        val stages = MutableList(n) { Stage.AWAKE }
        val w = Thresholds.STAGE_QUIET_WINDOW_MIN
        for (i in onset until end) {
            if (awake[i]) continue
            val near = (max(0, i - w)..min(n - 1, i + w)).any { activityMs[it] > 0 }
            stages[i] = if (near) Stage.LIGHT else quietStage(i - onset)
        }

        var waso = 0
        var wakeCount = 0
        var run = 0
        for (i in onset until end) {
            if (awake[i]) {
                waso++
                run++
            } else {
                if (run >= Thresholds.WAKE_RUN_MINUTES) wakeCount++
                run = 0
            }
        }
        if (run >= Thresholds.WAKE_RUN_MINUTES) wakeCount++ // 区間の最後は必ず睡眠なので通常は到達しない

        fun count(st: Stage) = (onset until end).count { stages[it] == st }
        val tst = (end - onset) - waso
        val metrics = SleepMetrics(
            timeInBedMin = n,
            latencyMin = onset,
            tstMin = tst,
            wasoMin = waso,
            wakeCount = wakeCount,
            lightMin = count(Stage.LIGHT),
            deepMin = count(Stage.DEEP),
            remMin = count(Stage.REM),
            onsetAt = startedAt + onset * 60_000L,
            wakeAt = startedAt + end * 60_000L,
        )
        return SleepAnalysis(stages, metrics)
    }

    /**
     * 静かな睡眠の分を、入眠からの経過分で Deep / REM / Light に割り当てる暫定ルール(90 分周期)。
     * 実際の睡眠ステージを測っているわけではなく、成人の一般的な配分(Deep 約 15〜20%、REM 約 20〜25%)に沿わせるもの。
     */
    internal fun quietStage(minutesSinceOnset: Int): Stage {
        val cycle = Thresholds.SLEEP_CYCLE_MIN
        val c = minutesSinceOnset / cycle
        val p = (minutesSinceOnset % cycle).toDouble() / cycle
        val deep = (Thresholds.DEEP_FIRST_CYCLE_FRACTION - Thresholds.DEEP_DECAY_PER_CYCLE * c).coerceAtLeast(0.0)
        val rem = (Thresholds.REM_FIRST_CYCLE_FRACTION + Thresholds.REM_GROWTH_PER_CYCLE * c).coerceAtMost(0.5)
        return when {
            p >= 1.0 - rem -> Stage.REM
            deep > 0 && p >= Thresholds.DEEP_START_FRACTION && p < Thresholds.DEEP_START_FRACTION + deep -> Stage.DEEP
            else -> Stage.LIGHT
        }
    }

    // 各イベントの [開始, 開始+長さ) が、各分にどれだけ重なるか
    private fun activityPerMinute(startedAt: Long, n: Int, events: List<AudioEvent>): LongArray {
        val out = LongArray(n)
        for (e in events) {
            if (e.type in NOT_ACTIVITY) continue
            val s = e.startedAt - startedAt
            val t = s + e.durationMs
            val first = (s / 60_000).toInt().coerceAtLeast(0)
            val last = ((t - 1) / 60_000).toInt().coerceAtMost(n - 1)
            for (m in first..last) {
                val overlap = min(t, (m + 1) * 60_000L) - max(s, m * 60_000L)
                if (overlap > 0) out[m] += overlap
            }
        }
        return out
    }

    /** 活動のない分が ONSET_QUIET_MINUTES 続く、最初の位置。なければ null。 */
    private fun findOnset(awake: BooleanArray): Int? {
        val need = Thresholds.ONSET_QUIET_MINUTES
        var run = 0
        for (i in awake.indices) {
            run = if (awake[i]) 0 else run + 1
            if (run >= need) return i - need + 1
        }
        return null
    }
}
