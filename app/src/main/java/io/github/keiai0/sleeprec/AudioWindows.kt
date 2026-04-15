package io.github.keiai0.sleeprec

/** 音声の分類で使う、窓の切り出しと集計。Android に依存しない純粋な関数。 */
object AudioWindows {
    /** YAMNet の入力長(約 0.975 秒 @16kHz)。モデルの仕様で固定。 */
    const val WINDOW_SAMPLES = 15_600
    /** 窓をずらす量(約 0.5 秒)。窓の半分より少し長く、取りこぼしを減らしつつ推論回数を抑える。 */
    const val HOP_SAMPLES = 8_000

    /** 総サンプル数から、各窓の開始位置を返す。窓より短い音は、1 つの窓(足りない分は 0 で埋める)。末尾も必ず覆う。 */
    fun starts(totalSamples: Int, window: Int = WINDOW_SAMPLES, hop: Int = HOP_SAMPLES): List<Int> {
        if (totalSamples <= window) return listOf(0)
        val out = ArrayList<Int>()
        var s = 0
        while (s + window < totalSamples) {
            out.add(s)
            s += hop
        }
        out.add(totalSamples - window) // 最後の窓は末尾にそろえる
        return out
    }

    /** 16bit リトルエンディアン PCM から、[start, start+count) サンプルを -1〜1 の float にする。範囲外は 0。 */
    fun toFloats(pcm: ByteArray, start: Int, count: Int): FloatArray {
        val total = pcm.size / 2
        return FloatArray(count) { i ->
            val idx = start + i
            if (idx >= total) 0f else {
                val lo = pcm[2 * idx].toInt() and 0xFF
                val hi = pcm[2 * idx + 1].toInt()
                ((hi shl 8) or lo) / 32768f
            }
        }
    }

    /** 窓ごとのスコアを、クラスごとの最大値にまとめる。 */
    fun maxPerClass(windows: List<FloatArray>): FloatArray {
        val out = FloatArray(windows.first().size)
        for (w in windows) for (i in w.indices) if (w[i] > out[i]) out[i] = w[i]
        return out
    }

    /** スコアの大きい順に上位 k 件(クラス番号とスコア)。 */
    fun topK(scores: FloatArray, k: Int): List<Pair<Int, Float>> =
        scores.indices.sortedByDescending { scores[it] }.take(k).map { it to scores[it] }
}
