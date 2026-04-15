package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.AudioEvent
import kotlin.math.abs

/** クリップの再生まわりの判断(Android に依存しない純粋な関数)。 */
object ClipPlayback {
    // 再生時に目指す音量(1 秒あたりの最大 dB がここに近づくよう持ち上げる)。小さい録音が聞こえにくい対策
    const val TARGET_DB = -12f
    const val MAX_GAIN_DB = 12f

    /** 再生時の増幅量(ミリベル)。すでに十分大きいクリップは 0(下げはしない)。 */
    fun gainMillibels(maxDb: Float): Int =
        ((TARGET_DB - maxDb).coerceIn(0f, MAX_GAIN_DB) * 100).toInt()

    /**
     * グラフのタップ位置(セッション開始からのミリ秒)に最も近い、音声が残っているクリップを返す。
     * 近さは、クリップの中心の時刻で比べる。音声が残っているものがなければ null。
     */
    fun nearestPlayable(events: List<AudioEvent>, sessionStartedAt: Long, tapMs: Long): AudioEvent? =
        events.filter { it.clipPath != null }
            .minByOrNull { abs(it.startedAt - sessionStartedAt + it.durationMs / 2 - tapMs) }
}
