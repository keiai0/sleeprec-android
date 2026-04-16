package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.EventType

/**
 * 無呼吸の目安の判定(Android に依存しない純粋な関数)。医療機器ではなく、
 * 「いびき → 一定時間以上の無音 → 呼吸音での再開」というパターンを拾うヒューリスティック。
 */
object ApneaRules {
    /** 連続する 2 つのイベントの、判定に必要な情報。位置は録音開始からのミリ秒。 */
    class Mark(val type: EventType, val loudStartMs: Long, val loudEndMs: Long)

    // YAMNet のクラス番号: Breathing, Wheeze, Snoring, Gasp, Snort
    private val RESUME_CLASSES = intArrayOf(36, 37, 38, 39, 41)

    /** 直前がいびきで、その後の無音が範囲内なら、無音の長さ(ミリ秒)を返す。候補でなければ null。 */
    fun silenceMs(
        prev: Mark?, next: Mark, nextScores: FloatArray,
        min: Long = Thresholds.APNEA_MIN_SILENCE_MS, max: Long = Thresholds.APNEA_MAX_SILENCE_MS,
    ): Long? {
        if (prev == null || prev.type != EventType.SNORING) return null
        if (!resumes(nextScores)) return null
        val silence = next.loudStartMs - prev.loudEndMs
        return if (silence in min..max) silence else null
    }

    /** 次の音が、呼吸・いびき系(再開の音)か。話し声や物音だけなら、寝返りや覚醒とみなして候補にしない。 */
    fun resumes(scores: FloatArray, minScore: Float = Thresholds.APNEA_RESUME_MIN_SCORE): Boolean =
        RESUME_CLASSES.any { scores[it] >= minScore }

    /**
     * 無音区間のクリップの音声: [前のいびきの末尾] + [間の無音] + [後の音の先頭]。
     * @param prevPcm 前のイベントのクリップ(末尾から tailMs 分を使う)
     * @param prevEndMs 前のクリップの終わりの位置 / nextStartMs 後のクリップの始まりの位置(いずれも録音開始から)
     * @param lead 後のクリップの直前の音(間の無音の材料。足りなければ、あるだけ使う)
     */
    fun buildClip(
        prevPcm: ByteArray, prevEndMs: Long, nextPcm: ByteArray, nextStartMs: Long, lead: ByteArray,
        tailMs: Long = Thresholds.APNEA_CLIP_TAIL_MS, headMs: Long = Thresholds.APNEA_CLIP_HEAD_MS,
    ): ByteArray {
        val tail = prevPcm.takeLastAligned(WavFormat.msToBytes(tailMs))
        val gapBytes = WavFormat.msToBytes((nextStartMs - prevEndMs).coerceAtLeast(0))
        val gap = lead.takeLastAligned(gapBytes)
        val head = nextPcm.copyOfRange(0, minOf(nextPcm.size, WavFormat.msToBytes(headMs)) and 1.inv())
        return tail + gap + head
    }

    // 16bit サンプルの区切り(2 バイト)を壊さずに、末尾から n バイト(以下)を取る
    private fun ByteArray.takeLastAligned(n: Int): ByteArray {
        val len = minOf(n, size) and 1.inv()
        return copyOfRange(size - len, size)
    }
}
