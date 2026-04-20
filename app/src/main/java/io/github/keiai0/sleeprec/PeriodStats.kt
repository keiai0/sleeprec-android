package io.github.keiai0.sleeprec

import java.time.LocalDate

/** 1 晩ぶんの要約(統計の入力)。入眠できなかった夜は tstMin = 0。 */
class NightSummary(
    val day: LocalDate,
    val bedAt: Long,          // 計測の開始(就寝の目安)
    val wakeAt: Long,         // 計測の終了(起床の目安)
    val zoneOffsetMs: Long,
    val tstMin: Int,
    val efficiency: Double,
    val score: Int?,
    val snoreCount: Int,
    val snoreMs: Long,
    val maxDb: Float?,        // この夜のイベントの最大音量(dBFS)。イベントがなければ null
) {
    val slept: Boolean get() = tstMin > 0
}

/** グラフの 1 日ぶん。記録のない日は、値が null。同じ日に複数の記録があれば、睡眠時間が最長のもの。 */
class DayPoint(val date: LocalDate, val tstMin: Int?, val score: Int?)

/** 期間の集計(FR-5.2)。眠れた夜だけを対象にする。 */
class PeriodStats(
    val nights: Int,
    val avgTstMin: Int?,
    val avgEfficiency: Double?,
    val avgScore: Int?,
    val avgBedMinuteOfDay: Int?,
    val avgWakeMinuteOfDay: Int?,
    val avgSnoreCount: Double?,
    val avgSnoreMs: Long?,
    val maxDb: Float?,
    val points: List<DayPoint>,
) {
    companion object {
        fun of(all: List<NightSummary>, period: StatsPeriod): PeriodStats {
            val nights = all.filter { it.slept && it.day in period }
            val points = (0 until period.days).map { i ->
                val date = period.start.plusDays(i.toLong())
                val best = nights.filter { it.day == date }.maxByOrNull { it.tstMin }
                DayPoint(date, best?.tstMin, best?.score)
            }
            if (nights.isEmpty()) return PeriodStats(0, null, null, null, null, null, null, null, null, points)
            val scores = nights.mapNotNull { it.score }
            return PeriodStats(
                nights = nights.size,
                avgTstMin = nights.map { it.tstMin }.average().let { Math.round(it).toInt() },
                avgEfficiency = nights.map { it.efficiency }.average(),
                avgScore = if (scores.isEmpty()) null else Math.round(scores.average()).toInt(),
                avgBedMinuteOfDay = TimeMath.meanMinuteOfDay(nights.map { it.bedAt }, nights.map { it.zoneOffsetMs }),
                avgWakeMinuteOfDay = TimeMath.meanMinuteOfDay(nights.map { it.wakeAt }, nights.map { it.zoneOffsetMs }),
                avgSnoreCount = nights.map { it.snoreCount }.average(),
                avgSnoreMs = nights.map { it.snoreMs }.average().toLong(),
                maxDb = nights.mapNotNull { it.maxDb }.maxOrNull(),
                points = points,
            )
        }
    }
}
