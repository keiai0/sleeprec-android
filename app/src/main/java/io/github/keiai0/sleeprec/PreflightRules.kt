package io.github.keiai0.sleeprec

/** 開始前の確認の判定(FR-2.4)。Android に依存しない純粋な関数。 */
object PreflightRules {
    const val BATTERY_WARN_BELOW = 50 // 未満で警告
    const val BATTERY_RECOMMENDED = 30 // 以上を推奨。未満は強い警告

    enum class Battery {
        OK,             // 50% 以上
        OK_CHARGING,    // 充電中(残量が少なくても、途中で切れる心配は小さい)
        LOW,            // 30〜49%、充電していない
        VERY_LOW,       // 30% 未満、充電していない
    }

    fun battery(percent: Int, charging: Boolean): Battery = when {
        charging -> Battery.OK_CHARGING
        percent >= BATTERY_WARN_BELOW -> Battery.OK
        percent >= BATTERY_RECOMMENDED -> Battery.LOW
        else -> Battery.VERY_LOW
    }

    fun batteryIsWarning(b: Battery): Boolean = b == Battery.LOW || b == Battery.VERY_LOW
}
