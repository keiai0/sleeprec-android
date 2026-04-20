package io.github.keiai0.sleeprec

/** 時刻の計算(Android に依存しない純粋な関数)。 */
object TimeMath {
    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 24L * 60 * MINUTE_MS

    /**
     * 「正午からの経過分」(0〜1439)。真夜中をまたぐ就寝時刻(23:30 と 0:30 など)を、連続した値として平均・比較できるようにする。
     * @param zoneOffsetMs その時点の、UTC からの時差(ミリ秒)
     */
    fun minutesFromNoon(epochMs: Long, zoneOffsetMs: Long): Double {
        val local = epochMs + zoneOffsetMs
        val minOfDay = (((local % DAY_MS) + DAY_MS) % DAY_MS) / MINUTE_MS.toDouble()
        return (minOfDay - 720 + 1440) % 1440
    }

    /** minutesFromNoon の値を、その日の 0 時からの分(0〜1439)に戻す。 */
    fun toMinuteOfDay(minutesFromNoon: Double): Int = (((minutesFromNoon + 720) % 1440).toInt() + 1440) % 1440

    /** 就寝・起床時刻の平均(その日の 0 時からの分)。データがなければ null。真夜中をまたいでも正しく平均する。 */
    fun meanMinuteOfDay(epochs: List<Long>, zoneOffsetsMs: List<Long>): Int? {
        if (epochs.isEmpty()) return null
        val mean = epochs.indices.map { minutesFromNoon(epochs[it], zoneOffsetsMs[it]) }.average()
        return toMinuteOfDay(mean)
    }

    fun format(minuteOfDay: Int): String = "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
