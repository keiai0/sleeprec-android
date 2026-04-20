package io.github.keiai0.sleeprec

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { WEEK, MONTH }

/** 統計の期間(週または月)。週は月曜始まり。start・end を含む。 */
data class StatsPeriod(val kind: PeriodKind, val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1

    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    fun previous(): StatsPeriod = of(kind, start.minusDays(1))
    fun next(): StatsPeriod = of(kind, end.plusDays(1))

    companion object {
        /** anchor を含む期間。 */
        fun of(kind: PeriodKind, anchor: LocalDate): StatsPeriod = when (kind) {
            PeriodKind.WEEK -> StatsPeriod(
                kind, anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
            )
            PeriodKind.MONTH -> StatsPeriod(kind, anchor.withDayOfMonth(1), anchor.with(TemporalAdjusters.lastDayOfMonth()))
        }
    }
}

/** 夜の記録が、どの日の記録として数えられるか。 */
object NightDay {
    /**
     * 「日の区切り」(cutoffHour 時)をまたいだ翌朝の日付。23:00 に寝た夜も、明け方に寝た夜も、その朝の日付になる。
     * 例(区切り 15:00): 9/18 23:00 開始 → 9/19、9/19 03:00 開始 → 9/19、9/19 16:00 開始 → 9/20。
     */
    fun of(startedAtMs: Long, zoneOffsetMs: Long, cutoffHour: Int = Thresholds.DAY_CUTOFF_HOUR): LocalDate {
        val shifted = startedAtMs + zoneOffsetMs + (24 - cutoffHour) * 3_600_000L
        return LocalDate.ofEpochDay(Math.floorDiv(shifted, 24L * 3_600_000L))
    }
}
