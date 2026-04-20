package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.SessionStatus
import io.github.keiai0.sleeprec.data.SessionStore
import java.util.TimeZone

/** 期間内の記録から、1 晩ぶんの要約を作る(統計の入力)。30 分以上の通常の記録だけが対象。 */
object StatsLoader {
    suspend fun load(store: SessionStore, period: StatsPeriod): List<NightSummary> {
        val zone = TimeZone.getDefault()
        return store.finishedSessions()
            .filter { it.status == SessionStatus.COMPLETED }
            .filter { NightDay.of(it.startedAt, zone.getOffset(it.startedAt).toLong()) in period }
            .sortedBy { it.startedAt }
            .map { s ->
                val end = s.endedAt ?: s.lastAliveAt
                val events = store.events(s.id)
                val pauses = store.pauses(s.id).map { it.startedAt to it.endedAt }
                val analysis = SleepAnalyzer.analyze(s.startedAt, end, events, pauses)
                // 規則性(Consistency)は、この夜より前の記録も使う
                val nights = (store.recentCompleted(s.startedAt, Thresholds.CONSISTENCY_NIGHTS - 1) + s)
                    .map { NightTimes(it.startedAt, it.endedAt ?: it.lastAliveAt) }
                val score = SleepScore.compute(s.status, analysis.metrics, nights, s.mood).total
                val snore = SnoreSummary.of(events)
                val offset = zone.getOffset(s.startedAt).toLong()
                NightSummary(
                    day = NightDay.of(s.startedAt, offset),
                    bedAt = s.startedAt,
                    wakeAt = end,
                    zoneOffsetMs = offset,
                    tstMin = analysis.metrics.tstMin,
                    efficiency = analysis.metrics.efficiency,
                    score = score,
                    snoreCount = snore?.count ?: 0,
                    snoreMs = snore?.totalMs ?: 0L,
                    maxDb = events.maxOfOrNull { it.maxDb },
                )
            }
    }
}
