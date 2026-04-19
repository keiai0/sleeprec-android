package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.AudioEvent
import kotlin.math.abs

/**
 * タイムラインのタップで再生できる音(音声イベントのクリップ、または無呼吸の候補のクリップ)。
 * id は ClipPlayer が「どれを再生中か」を見分けるための値(イベントは正、無呼吸の候補は負)。
 * 位置は、セッション開始からのミリ秒。
 */
class PlayTarget(val id: Long, val path: String, val maxDb: Float, val startMs: Long, val centerMs: Long)

/** クリップの再生まわりの判断(Android に依存しない純粋な関数)。 */
object ClipPlayback {
    // 再生時に目指す音量(1 秒あたりの最大 dB がここに近づくよう持ち上げる)。小さい録音が聞こえにくい対策
    const val TARGET_DB = -12f
    const val MAX_GAIN_DB = 12f

    /** 再生時の増幅量(ミリベル)。すでに十分大きいクリップは 0(下げはしない)。 */
    fun gainMillibels(maxDb: Float): Int =
        ((TARGET_DB - maxDb).coerceIn(0f, MAX_GAIN_DB) * 100).toInt()

    /** タップした時刻に最も近い再生対象(中心の時刻で比べる)。なければ null。 */
    fun nearestTarget(targets: List<PlayTarget>, tapMs: Long): PlayTarget? =
        targets.minByOrNull { abs(it.centerMs - tapMs) }

    /** 音声イベントと無呼吸の候補から、音声が残っているものを再生対象にする。 */
    fun targets(
        events: List<AudioEvent>, apneas: List<io.github.keiai0.sleeprec.data.ApneaCandidate>, sessionStartedAt: Long,
    ): List<PlayTarget> {
        val fromEvents = events.mapNotNull { e ->
            val path = e.clipPath ?: return@mapNotNull null
            val start = e.startedAt - sessionStartedAt
            PlayTarget(e.id, path, e.maxDb, start, start + e.durationMs / 2)
        }
        val fromApneas = apneas.mapNotNull { c ->
            val path = c.clipPath ?: return@mapNotNull null
            val silenceStart = c.startedAt - sessionStartedAt
            // クリップは、無音の直前のいびきの末尾から始まる(おおよそ 3 秒前)
            PlayTarget(-c.id, path, c.maxDb, silenceStart - 3_000, silenceStart + c.silenceMs / 2)
        }
        return fromEvents + fromApneas
    }

    /**
     * グラフのタップ位置(セッション開始からのミリ秒)に最も近い、音声が残っているクリップを返す。
     * 近さは、クリップの中心の時刻で比べる。音声が残っているものがなければ null。
     */
    fun nearestPlayable(events: List<AudioEvent>, sessionStartedAt: Long, tapMs: Long): AudioEvent? =
        events.filter { it.clipPath != null }
            .minByOrNull { abs(it.startedAt - sessionStartedAt + it.durationMs / 2 - tapMs) }
}
