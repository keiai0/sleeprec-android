package io.github.keiai0.sleeprec

import kotlin.math.log10
import kotlin.math.sqrt

/** 1秒分の音量。値は dBFS(フルスケール基準の相対値。0 が最大、無音は FLOOR_DB)。 */
data class SecondLoudness(val second: Int, val avgDb: Float, val maxDb: Float)

/**
 * 音量の計算。Android に依存しない純粋な関数なので、JVM のユニットテストで検証できる。
 * 端末ごとにマイク感度が違うため、絶対的な音圧(SPL)ではなく dBFS の相対値として扱う(PLAN §2)。
 */
object Loudness {
    const val FLOOR_DB = -90.0f
    private const val FULL_SCALE = 32768.0

    /** 16bit リトルエンディアン PCM の先頭 length バイトの RMS を dBFS で返す。 */
    fun frameDb(pcm: ByteArray, length: Int = pcm.size): Float {
        val samples = length / 2
        if (samples == 0) return FLOOR_DB
        var sumSq = 0.0
        for (i in 0 until samples) {
            val lo = pcm[2 * i].toInt() and 0xFF
            val hi = pcm[2 * i + 1].toInt() // 符号付き
            val v = ((hi shl 8) or lo).toDouble()
            sumSq += v * v
        }
        val rms = sqrt(sumSq / samples) / FULL_SCALE
        if (rms <= 0.0) return FLOOR_DB
        return (20 * log10(rms)).toFloat().coerceAtLeast(FLOOR_DB)
    }

    /**
     * 0.1秒フレームの dB を受け取り、framesPerSecond 個たまるごとに (平均, 最大) を1件返す集約器。
     * 平均は dB 値の単純平均(暫定)。
     */
    class SecondAggregator(private val framesPerSecond: Int = 10) {
        private var second = 0
        private var count = 0
        private var sum = 0.0
        private var max = FLOOR_DB

        /** 1秒分たまったら SecondLoudness を返し、そうでなければ null。 */
        fun add(frameDb: Float): SecondLoudness? {
            sum += frameDb
            if (frameDb > max) max = frameDb
            count++
            if (count < framesPerSecond) return null
            val out = SecondLoudness(second, (sum / count).toFloat(), max)
            second++
            count = 0
            sum = 0.0
            max = FLOOR_DB
            return out
        }
    }
}
