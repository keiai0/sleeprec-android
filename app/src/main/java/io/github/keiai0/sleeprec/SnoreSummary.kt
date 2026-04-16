package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.AudioEvent
import io.github.keiai0.sleeprec.data.EventType

/**
 * いびきの集計(FR-4.6)。種別が SNORING のイベントだけを対象にする。
 * 長さはクリップの長さ(前後の余白を含む)なので、実際のいびきの時間より少し長めの目安。
 */
data class SnoreSummary(
    val count: Int,
    val totalMs: Long,
    val maxDb: Float,
    val avgDb: Float, // 長さで重みを付けた平均
    val times: List<Long>, // 各イベントの開始時刻(エポックミリ秒)
) {
    companion object {
        fun of(events: List<AudioEvent>): SnoreSummary? {
            val snores = events.filter { it.type == EventType.SNORING }
            if (snores.isEmpty()) return null
            val total = snores.sumOf { it.durationMs }
            val avg = if (total > 0) snores.sumOf { it.avgDb * it.durationMs.toDouble() } / total else snores.map { it.avgDb }.average()
            return SnoreSummary(
                count = snores.size,
                totalMs = total,
                maxDb = snores.maxOf { it.maxDb },
                avgDb = avg.toFloat(),
                times = snores.map { it.startedAt }.sorted(),
            )
        }
    }
}
