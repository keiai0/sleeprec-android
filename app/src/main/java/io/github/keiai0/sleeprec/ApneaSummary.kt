package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.ApneaCandidate

enum class ApneaLevel { NORMAL, MILD, MODERATE, SEVERE }

/**
 * 無呼吸の目安の集計(FR-4.6)。回数、1 時間あたりの回数、重症度、無音の最大/合計。
 * 1 時間未満の計測では、1 時間あたりの回数は信頼できないので出さない(perHour と level が null)。
 */
data class ApneaSummary(
    val count: Int,
    val totalSilenceMs: Long,
    val maxSilenceMs: Long,
    val perHour: Double?,
    val level: ApneaLevel?,
) {
    /** 受診を検討する目安のメッセージを出すか(中等度以上)。 */
    val showRiskNotice: Boolean get() = level == ApneaLevel.MODERATE || level == ApneaLevel.SEVERE

    companion object {
        fun levelOf(perHour: Double): ApneaLevel = when {
            perHour >= Thresholds.APNEA_SEVERE_PER_HOUR -> ApneaLevel.SEVERE
            perHour >= Thresholds.APNEA_MODERATE_PER_HOUR -> ApneaLevel.MODERATE
            perHour >= Thresholds.APNEA_MILD_PER_HOUR -> ApneaLevel.MILD
            else -> ApneaLevel.NORMAL
        }

        fun of(candidates: List<ApneaCandidate>, sessionMs: Long): ApneaSummary {
            val perHour = if (sessionMs >= Thresholds.APNEA_MIN_SESSION_MS) candidates.size * 3_600_000.0 / sessionMs else null
            return ApneaSummary(
                count = candidates.size,
                totalSilenceMs = candidates.sumOf { it.silenceMs },
                maxSilenceMs = candidates.maxOfOrNull { it.silenceMs } ?: 0L,
                perHour = perHour,
                level = perHour?.let(::levelOf),
            )
        }
    }
}
