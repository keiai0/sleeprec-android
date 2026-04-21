package io.github.keiai0.sleeprec

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor

/**
 * AudioRecord で生PCMを読み取り、WAVファイルに書き続ける。
 * UI や Service には依存しない。音量は読み取りループで0.1秒フレームごとに計算し、1秒ごとに onSecond へ渡す。
 */
class WavRecorder(
    // 全録音を書き出す WAV ファイル。null なら、ファイルは作らない(音量・イベントの解析だけを行う。既定)
    private val outFile: File?,
    // 1秒分の音量がそろうたびに、録音スレッド上で呼ばれる(重い処理は入れない)
    private val onSecond: (SecondLoudness) -> Unit = {},
    // 音声イベントが確定するたびに、録音スレッド上で呼ばれる(重い処理は入れない)
    private val onEvent: (DetectedEvent) -> Unit = {},
    // 0.1秒フレームごとの音量。デバッグ表示用
    private val onFrame: (Float) -> Unit = {},
    // 他のアプリにマイクを取られて無音にされた(true)/ 戻った(false)とき、callbackExecutor 上で呼ばれる(FR-2.5)
    private val callbackExecutor: Executor? = null,
    private val onSilenceChanged: (silenced: Boolean) -> Unit = {},
    // 録音スレッドが終了したとき(正常停止でも異常でも)に、そのスレッド上で呼ばれる
    private val onFinished: (error: Throwable?) -> Unit,
) {
    companion object {
        private const val TAG = "WavRecorder"
        private const val SAMPLE_RATE = WavFormat.SAMPLE_RATE
        private const val BYTES_PER_SECOND = WavFormat.BYTES_PER_SECOND
        private const val HEADER_SIZE = WavFormat.HEADER_SIZE
        private const val FRAME_MS = 100L // 1 フレーム(readBuf)の長さ

        // ファイルを作らないときの、何もしない Closeable(use のために必要)
        private val NoFile = java.io.Closeable {}
    }

    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null
    @Volatile private var audioRecord: AudioRecord? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private var lastSilenced = false

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

        audioRecord = record
        registerSilenceCallback(record)

        outFile?.parentFile?.mkdirs()
        running = true
        thread = Thread({ runLoop(record, internalBufSize) }, "wav-recorder").also { it.start() }
    }

    /**
     * 一時停止する。マイクを手放し、音は録らず・解析もしない。
     * 時間軸(イベントの位置・1 秒ごとの音量)がずれないよう、止めている間は無音のフレームを流し続ける。
     */
    fun pause() { paused = true }

    /** 一時停止を解除して、マイクを再び開く。 */
    fun resume() { paused = false }

    /** いま、他のアプリにマイクを取られて無音にされているか。 */
    fun isSilenced(): Boolean = audioRecord?.activeRecordingConfiguration?.isClientSilenced == true

    // Android 10 以降、マイクは 1 つのアプリにしか実際の音を渡さない(通話や、前面のアプリの録音が優先される)。
    // 取られた側は無音のデータを受け取り続けるので、AudioRecord 自身の状態(isClientSilenced)の変化で検知する
    private fun registerSilenceCallback(record: AudioRecord) {
        val executor = callbackExecutor ?: return
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                val silenced = record.activeRecordingConfiguration?.isClientSilenced ?: return
                if (silenced != lastSilenced) {
                    lastSilenced = silenced
                    onSilenceChanged(silenced)
                }
            }
        }
        recordingCallback = cb
        record.registerAudioRecordingCallback(executor, cb)
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
        val aggregator = Loudness.SecondAggregator()
        val detector = EventDetector()

        try {
            // 全録音を残さないときは、ファイルを開かない(raf が null)。解析は、ファイルの有無と関係なく行う
            (outFile?.let { RandomAccessFile(it, "rw") } ?: NoFile).use { closeable ->
                val raf = closeable as? RandomAccessFile // 全録音を書き出さないときは null
                // データ長がまだ分からないので、サイズ欄 0 のヘッダを先頭に置いて始める
                raf?.setLength(0)
                raf?.write(WavFormat.header(0))
                var micOpen = false
                val silence = ByteArray(readBuf.size) // 一時停止中に流す無音

                while (running) {
                    if (paused) {
                        if (micOpen) {
                            record.stop()
                            micOpen = false
                        }
                        // 0.1 秒ぶんの無音を、時間軸を保つためだけに解析側へ流す(WAV には書かない)
                        Thread.sleep(FRAME_MS)
                        onFrame(Loudness.FLOOR_DB)
                        aggregator.add(Loudness.FLOOR_DB)?.let(onSecond)
                        detector.feed(silence, silence.size, Loudness.FLOOR_DB)?.let(onEvent)
                        continue
                    }
                    if (!micOpen) {
                        record.startRecording()
                        micOpen = true
                    }
                    // ブロッキング読み取り。データが溜まるまで待ち、読めたバイト数を返す(負ならエラー)
                    val n = record.read(readBuf, 0, readBuf.size)
                    if (n < 0) throw IllegalStateException("AudioRecord.read error: $n")
                    if (n == 0) continue
                    raf?.write(readBuf, 0, n)
                    val db = Loudness.frameDb(readBuf, n)
                    onFrame(db)
                    aggregator.add(db)?.let(onSecond)
                    detector.feed(readBuf, n, db)?.let(onEvent)
                    dataBytes += n

                    // 約1秒ごとにヘッダのサイズ欄を更新。アプリが kill されても再生可能なファイルが残る
                    if (dataBytes - lastHeaderUpdate >= BYTES_PER_SECOND) {
                        raf?.let { updateHeaderSizes(it, dataBytes) }
                        lastHeaderUpdate = dataBytes
                    }
                }
                // 正常停止: イベントの途中なら確定させ、最終的なサイズをヘッダに書く
                detector.flush()?.let(onEvent)
                raf?.let { updateHeaderSizes(it, dataBytes) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "recording failed", t)
            error = t
        } finally {
            recordingCallback?.let { record.unregisterAudioRecordingCallback(it) }
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
}
