package io.github.keiai0.sleeprec

/**
 * 録音時間による、セッションの扱いのルール(SPEC FR-2.7)。
 * Android に依存しない純粋な関数なので、JVM のユニットテストで検証できる。
 */
object SessionPolicy {
    const val MIN_SAVE_MS = 10L * 60 * 1000   // 10 分未満は保存しない
    const val NORMAL_MIN_MS = 30L * 60 * 1000 // 30 分以上が通常分析の対象

    enum class Outcome { NOT_SAVED, SHORT_SLEEP, NORMAL }

    fun classify(durationMs: Long): Outcome = when {
        durationMs < MIN_SAVE_MS -> Outcome.NOT_SAVED
        durationMs < NORMAL_MIN_MS -> Outcome.SHORT_SLEEP
        else -> Outcome.NORMAL
    }
}
