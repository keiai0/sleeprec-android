package com.example.sleeprec

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioRecord で生PCMを読み取り、WAVファイルに書き続ける。
 * UI や Service には依存しない。将来、音量判定はこのクラスの読み取りループに足す。
 */
class WavRecorder(
    private val outFile: File,
    // 録音スレッドが終了したとき(正常停止でも異常でも)に、そのスレッド上で呼ばれる
    private val onFinished: (error: Throwable?) -> Unit,
) {
    companion object {
        private const val TAG = "WavRecorder"
        const val SAMPLE_RATE = 16_000
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8 // 32000
        private const val HEADER_SIZE = 44
    }

    @Volatile private var running = false
    private var thread: Thread? = null

    /** マイクを開いて録音スレッドを起動する。失敗時は例外(呼び出し側で捕捉)。 */
    @SuppressLint("MissingPermission") // 権限チェックは Activity 側で済んでいる前提
    fun start() {
        // --- バッファサイズの決め方 ---
        // getMinBufferSize: このデバイスで AudioRecord が動作できる「最小」の内部バッファ(バイト)。
        // これぎりだと、読み取りが少し遅れただけで音が欠けるので、余裕を持たせる。
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuf > 0) { "getMinBufferSize failed: $minBuf" }
        // 内部バッファ = max(最小の2倍, 1秒分)。1秒分 = 32000 バイト。
        // 画面オフ中に CPU が一瞬遅れても、1秒までは音が失われない。
        val internalBufSize = maxOf(minBuf * 2, BYTES_PER_SECOND)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            internalBufSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("AudioRecord init failed (マイクが他アプリに使われている等)")
        }

        outFile.parentFile?.mkdirs()
        running = true
        thread = Thread({ runLoop(record, internalBufSize) }, "wav-recorder").also { it.start() }
    }

    /** 停止を指示し、ファイルが閉じられるまで待つ。 */
    fun stop() {
        running = false
        thread?.join(3000)
    }

    private fun runLoop(record: AudioRecord, internalBufSize: Int) {
        var error: Throwable? = null
        var dataBytes = 0L
        // 1回の read() で読む量。0.1秒分(3200バイト)。内部バッファ(>=1秒)より十分小さく、
        // 後で音量判定をするときの「1フレーム」の単位にもちょうどよい。
        val readBuf = ByteArray(BYTES_PER_SECOND / 10)
        var lastHeaderUpdate = 0L

        try {
            RandomAccessFile(outFile, "rw").use { raf ->
                raf.setLength(0)
                // データ長がまだ分からないので、サイズ欄 0 のヘッダを先頭に置いて始める
                raf.write(buildHeader(0))
                record.startRecording()

                while (running) {
                    // ブロッキング読み取り。データが溜まるまで待ち、読めたバイト数を返す(負ならエラー)
                    val n = record.read(readBuf, 0, readBuf.size)
                    if (n < 0) throw IllegalStateException("AudioRecord.read error: $n")
                    if (n == 0) continue
                    raf.write(readBuf, 0, n) // ← 将来ここで readBuf を見て音量判定する
                    dataBytes += n

                    // 約1秒ごとにヘッダのサイズ欄を更新。アプリが kill されても再生可能なファイルが残る
                    if (dataBytes - lastHeaderUpdate >= BYTES_PER_SECOND) {
                        updateHeaderSizes(raf, dataBytes)
                        lastHeaderUpdate = dataBytes
                    }
                }
                // 正常停止: 最終的なサイズをヘッダに書く
                updateHeaderSizes(raf, dataBytes)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "recording failed", t)
            error = t
        } finally {
            try { record.stop() } catch (_: IllegalStateException) {}
            record.release()
            running = false
            onFinished(error)
        }
    }

    /** ヘッダのサイズ欄(4バイト目と40バイト目)だけを書き換え、書き込み位置をファイル末尾に戻す。 */
    private fun updateHeaderSizes(raf: RandomAccessFile, dataBytes: Long) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        raf.seek(4)
        raf.write(b.putInt(0, (36 + dataBytes).toInt()).array()) // RIFFチャンクサイズ = ファイル全長 - 8
        raf.seek(40)
        raf.write(b.putInt(0, dataBytes.toInt()).array())        // dataチャンクサイズ = PCMのバイト数
        raf.seek(HEADER_SIZE + dataBytes)                        // 続きは PCM の末尾から
    }

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
    private fun buildHeader(dataBytes: Long): ByteArray {
        val byteRate = BYTES_PER_SECOND
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
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes.toInt())
        }.array()
    }
}
