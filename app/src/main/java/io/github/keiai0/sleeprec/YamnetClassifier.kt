package io.github.keiai0.sleeprec

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 分類の結果。scores はクラスごとの最大スコア(全窓の中での最大)。 */
class Classification(val scores: FloatArray, val windows: Int, val elapsedMs: Long)

/**
 * YAMNet(LiteRT)で、クリップの音を 521 クラスに分類する。モデルは assets/yamnet.tflite(約 4MB)。
 * スレッドセーフではない。使う側で 1 つのスレッドから呼ぶか、排他すること。
 */
class YamnetClassifier(context: Context) : AutoCloseable {
    val labels: List<String> = context.assets.open(LABELS_FILE).bufferedReader().readLines()
    private val interpreter: Interpreter
    private val outputIsBatched: Boolean

    init {
        val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buf.rewind()
        interpreter = Interpreter(buf)
        val inShape = interpreter.getInputTensor(0).shape()
        val outShape = interpreter.getOutputTensor(0).shape()
        Log.i(TAG, "input=${inShape.toList()} output=${outShape.toList()} labels=${labels.size}")
        check(inShape.last() == AudioWindows.WINDOW_SAMPLES) { "想定外の入力形状: ${inShape.toList()}" }
        check(outShape.last() == labels.size) { "出力(${outShape.last()})とラベル数(${labels.size})が一致しない" }
        outputIsBatched = outShape.size == 2
    }

    /** WAV ファイルの内容(ヘッダ 44 バイト + PCM)を分類する。 */
    fun classifyWav(wav: ByteArray): Classification {
        val pcm = wav.copyOfRange(WavFormat.HEADER_SIZE.coerceAtMost(wav.size), wav.size)
        return classifyPcm(pcm)
    }

    fun classifyPcm(pcm: ByteArray): Classification {
        val t0 = System.nanoTime()
        val starts = AudioWindows.starts(pcm.size / 2)
        val results = starts.map { start ->
            val input = AudioWindows.toFloats(pcm, start, AudioWindows.WINDOW_SAMPLES)
            if (outputIsBatched) {
                val out = Array(1) { FloatArray(labels.size) }
                interpreter.run(input, out)
                out[0]
            } else {
                val out = FloatArray(labels.size)
                interpreter.run(input, out)
                out
            }
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        return Classification(AudioWindows.maxPerClass(results), starts.size, ms)
    }

    override fun close() = interpreter.close()

    companion object {
        private const val TAG = "YamnetClassifier"
        private const val MODEL_FILE = "yamnet.tflite"
        private const val LABELS_FILE = "yamnet_labels.txt"
    }
}
