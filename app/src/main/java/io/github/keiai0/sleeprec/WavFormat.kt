package io.github.keiai0.sleeprec

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16kHz / モノラル / 16bit の WAV の形式と、ヘッダの生成。Android に依存しない。 */
object WavFormat {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8 // 32000
    const val HEADER_SIZE = 44

    fun msToBytes(ms: Long): Int = (ms * BYTES_PER_SECOND / 1000).toInt()
    fun bytesToMs(bytes: Int): Long = bytes * 1000L / BYTES_PER_SECOND

    /**
     * 44バイトの標準WAV(PCM)ヘッダ。数値はすべてリトルエンディアン。
     *
     *  offset size 内容
     *   0     4    "RIFF"         … これは RIFF 形式のファイルという印
     *   4     4    ファイル全長-8  … ここから先のバイト数
     *   8     4    "WAVE"         … 中身は WAV
     *  12     4    "fmt "         … フォーマット情報チャンクの開始
     *  16     4    16             … fmt チャンクの長さ(PCMなら16固定)
     *  20     2    1              … 圧縮なしのリニアPCM
     *  22     2    チャンネル数     … 1(モノラル)
     *  24     4    サンプルレート   … 16000
     *  28     4    バイトレート     … レート × ch × 2 = 32000 (1秒あたりのバイト数)
     *  32     2    ブロックサイズ   … ch × 2 = 2 (1サンプル時点のバイト数)
     *  34     2    ビット深度       … 16
     *  36     4    "data"         … 音声データチャンクの開始
     *  40     4    PCMのバイト数   … この後に続く生データの長さ
     *  44     …    PCM 本体
     */
    fun header(dataBytes: Long): ByteArray {
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
        return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + dataBytes).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(BYTES_PER_SECOND)
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes.toInt())
        }.array()
    }

    /** PCM 全体を、ヘッダ付きの WAV ファイルとして書き出す(クリップ用)。 */
    fun writeFile(file: File, pcm: ByteArray) {
        file.parentFile?.mkdirs()
        file.outputStream().use {
            it.write(header(pcm.size.toLong()))
            it.write(pcm)
        }
    }
}
