package io.github.keiai0.sleeprec

import kotlin.math.roundToInt

/** 設定の入力の制限と単位換算(Android に依存しない純粋な関数)。 */
object SettingsRules {
    const val GOAL_MIN = 4 * 60        // 目標睡眠時間の下限(分)
    const val GOAL_MAX = 12 * 60       // 上限
    const val GOAL_STEP = 15           // 刻み
    const val GOAL_DEFAULT = 450       // 7 時間 30 分

    const val SPAN_MIN = 60            // 就寝〜起床の睡眠時間の下限(FR-1.4: 1 時間)
    const val SPAN_MAX = 20 * 60       // 上限(20 時間)

    const val MAX_AGE_YEARS = 120

    /** 目標睡眠時間を、範囲内・刻みに揃える。 */
    fun clampGoal(minutes: Int): Int = ((minutes.coerceIn(GOAL_MIN, GOAL_MAX) + GOAL_STEP / 2) / GOAL_STEP * GOAL_STEP).coerceIn(GOAL_MIN, GOAL_MAX)

    fun stepGoal(current: Int, direction: Int): Int = clampGoal(current + direction * GOAL_STEP)

    /** 日の区切りの時刻(0〜23)。範囲外は循環させる(23 の次は 0)。 */
    fun stepCutoff(current: Int, direction: Int): Int = Math.floorMod(current + direction, 24)

    /** 就寝から起床までの時間(分)。日をまたぐ(23:00 → 7:00 = 8 時間)。同じ時刻なら 0。 */
    fun sleepSpanMinutes(bedMinuteOfDay: Int, wakeMinuteOfDay: Int): Int = Math.floorMod(wakeMinuteOfDay - bedMinuteOfDay, 1440)

    /** 就寝〜起床が 1〜20 時間か(FR-1.4)。 */
    fun isValidSpan(spanMinutes: Int): Boolean = spanMinutes in SPAN_MIN..SPAN_MAX

    /** 就寝〜起床の時間から、目標睡眠時間の初期値を決める(範囲外は範囲内に収める)。 */
    fun goalFromSpan(spanMinutes: Int): Int = clampGoal(spanMinutes)

    /** 生年月日が妥当か。未来ではなく、年齢が MAX_AGE_YEARS 以下。 */
    fun isValidBirthDate(birthEpochDay: Long, todayEpochDay: Long): Boolean =
        birthEpochDay <= todayEpochDay && (todayEpochDay - birthEpochDay) / 365.25 <= MAX_AGE_YEARS

    // --- 単位(内部は常にメートル法で持つ) ---
    private const val CM_PER_INCH = 2.54
    private const val KG_PER_LB = 0.45359237

    fun cmToInches(cm: Int): Double = cm / CM_PER_INCH
    fun inchesToCm(inches: Double): Int = (inches * CM_PER_INCH).roundToInt()
    fun kgToPounds(kg: Float): Double = kg / KG_PER_LB
    fun poundsToKg(pounds: Double): Float = (pounds * KG_PER_LB).toFloat()
}
