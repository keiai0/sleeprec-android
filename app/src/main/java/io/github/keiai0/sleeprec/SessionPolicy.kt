package io.github.keiai0.sleeprec

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * 録音時間による、セッションの扱いのルール(SPEC FR-2.7)。
 * Android に依存しない純粋な関数なので、JVM のユニットテストで検証できる。
 */
object SessionPolicy {
    const val MIN_SAVE_MS = 10L * 60 * 1000   // 10 分未満は保存しない
    const val NORMAL_MIN_MS = 30L * 60 * 1000 // 30 分以上が通常分析の対象

    // 実機での動作確認用。デバッグビルドのときだけ configure() で短くする(ユニットテストは既定値のまま)
    private const val DEBUG_MIN_SAVE_MS = 20L * 1000   // 20 秒
    private const val DEBUG_NORMAL_MIN_MS = 60L * 1000 // 60 秒
    @Volatile private var minSaveMs = MIN_SAVE_MS
    @Volatile private var normalMinMs = NORMAL_MIN_MS

    /** Activity と Service の onCreate で呼ぶ。debuggable なビルドなら、基準を短くする。 */
    fun configure(context: Context) {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        minSaveMs = if (debuggable) DEBUG_MIN_SAVE_MS else MIN_SAVE_MS
        normalMinMs = if (debuggable) DEBUG_NORMAL_MIN_MS else NORMAL_MIN_MS
    }

    enum class Outcome { NOT_SAVED, SHORT_SLEEP, NORMAL }

    fun classify(durationMs: Long): Outcome = when {
        durationMs < minSaveMs -> Outcome.NOT_SAVED
        durationMs < normalMinMs -> Outcome.SHORT_SLEEP
        else -> Outcome.NORMAL
    }
}
