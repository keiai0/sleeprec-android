package io.github.keiai0.sleeprec

import kotlin.math.abs

/** クリップの波形表示用の間引き(Android に依存しない純粋な関数)。 */
object Waveform {
    private const val FULL_SCALE = 32768f

    /**
     * 16bit リトルエンディアン PCM を、buckets 個の区間に分け、区間ごとの最大振幅(0〜1)を返す。
     * 数十万サンプルでも、画面の幅の分だけの値にまとめて描けるようにする。
     */
    fun envelope(pcm: ByteArray, buckets: Int): FloatArray {
        require(buckets > 0)
        val samples = pcm.size / 2
        val out = FloatArray(buckets)
        if (samples == 0) return out
        for (b in 0 until buckets) {
            val from = (b.toLong() * samples / buckets).toInt()
            val to = maxOf(from + 1, ((b + 1).toLong() * samples / buckets).toInt()).coerceAtMost(samples)
            var peak = 0
            for (i in from until to) {
                val v = (pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF) // 符号付き 16bit
                val a = abs(v)
                if (a > peak) peak = a
            }
            out[b] = peak / FULL_SCALE
        }
        return out
    }

    /** 最大が 1 になるよう、全体を拡大する(小さい録音でも、形が見えるように)。無音なら、そのまま。 */
    fun normalize(envelope: FloatArray): FloatArray {
        val max = envelope.maxOrNull() ?: return envelope
        return if (max <= 0f) envelope else FloatArray(envelope.size) { envelope[it] / max }
    }
}
