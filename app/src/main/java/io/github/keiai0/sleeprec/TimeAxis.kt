package io.github.keiai0.sleeprec

/** タイムラインの時刻の目盛り。Android に依存しない純粋な関数。 */
object TimeAxis {
    class Tick(val offsetMs: Long, val label: String)

    // 目盛りの間隔の候補(分)。画面に収まる数(最大 MAX_TICKS 個)になる、最小の間隔を選ぶ
    private val STEPS_MIN = listOf(5, 10, 15, 30, 60, 120, 180, 240, 360, 720)
    private const val MAX_TICKS = 7
    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 24L * 60 * MINUTE_MS

    /**
     * 開始から totalMs の間にある、ちょうどの時刻(例: 23:00、0:00、1:00)に目盛りを置く。
     * @param zoneOffsetMs 開始時点の、UTC からの時差(ミリ秒)。時刻の区切りを、その土地の時刻に合わせるために使う。
     */
    fun ticks(startMs: Long, totalMs: Long, zoneOffsetMs: Long): List<Tick> {
        if (totalMs <= 0) return emptyList()
        val step = STEPS_MIN.firstOrNull { totalMs / (it * MINUTE_MS) <= MAX_TICKS } ?: STEPS_MIN.last()
        val stepMs = step * MINUTE_MS
        val localStart = startMs + zoneOffsetMs
        // localStart 以降で、最初の「step の倍数の時刻」
        var t = (localStart + stepMs - 1) / stepMs * stepMs
        val out = mutableListOf<Tick>()
        while (t - localStart <= totalMs) {
            val minuteOfDay = (((t % DAY_MS) + DAY_MS) % DAY_MS / MINUTE_MS).toInt()
            out += Tick(t - localStart, "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60))
            t += stepMs
        }
        return out
    }
}
