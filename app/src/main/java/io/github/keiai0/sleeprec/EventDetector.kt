package io.github.keiai0.sleeprec

import java.io.ByteArrayOutputStream

/** 検出した1件の音声イベント。pcm は前後を含むクリップの音声(16kHz/モノラル/16bit)。 */
class DetectedEvent(
    val offsetMs: Long,   // クリップの先頭が、録音開始から何ミリ秒か(前の2〜3秒を含む)
    val durationMs: Long, // クリップ全体の長さ
    val maxDb: Float,
    val avgDb: Float,
    val pcm: ByteArray,
)

/**
 * 音量の閾値超えを1件のイベントとして切り出す状態機械(Android に依存しない)。
 * 録音ループが読み取ったブロック(約0.1秒)と、その dB を feed() に順に渡す。
 *
 * - START 以上で開始。開始の前 PRE_ROLL 分もクリップに含める(リングバッファ)。
 * - END 以上の間は継続。END を下回った状態が HOLD 続いたら終了(短い途切れで分割しない)。
 * - 音の出ている区間が MIN 未満なら捨てる。クリップが MAX に達したら、そこで区切る。
 */
class EventDetector(
    private val startDb: Float = Thresholds.EVENT_START_DB,
    private val endDb: Float = Thresholds.EVENT_END_DB,
    holdMs: Long = Thresholds.EVENT_HOLD_MS,
    preRollMs: Long = Thresholds.PRE_ROLL_MS,
    minEventMs: Long = Thresholds.MIN_EVENT_MS,
    maxClipMs: Long = Thresholds.MAX_CLIP_MS,
) {
    private val holdBytes = WavFormat.msToBytes(holdMs)
    private val preRollBytes = WavFormat.msToBytes(preRollMs)
    private val minBytes = WavFormat.msToBytes(minEventMs)
    private val maxBytes = WavFormat.msToBytes(maxClipMs)

    private val ring = ArrayDeque<ByteArray>()
    private var ringBytes = 0

    private var totalBytes = 0L // これまでに feed された総量(録音開始からの位置)

    private var inEvent = false
    private var clip = ByteArrayOutputStream()
    private var clipOffsetBytes = 0L
    private var preBytes = 0
    private var quietBytes = 0
    private var maxDb = 0f
    private var sumDb = 0.0
    private var blocks = 0

    /** イベントが確定したら返す。それ以外は null。pcm は呼び出し側が再利用してよい(中でコピーする)。 */
    fun feed(pcm: ByteArray, length: Int, db: Float): DetectedEvent? {
        val block = pcm.copyOf(length)
        var result: DetectedEvent? = null

        if (!inEvent) {
            if (db >= startDb) {
                begin(block, db)
            } else {
                ring.addLast(block)
                ringBytes += block.size
                while (ring.size > 1 && ringBytes - ring.first().size >= preRollBytes) {
                    ringBytes -= ring.removeFirst().size
                }
            }
        } else {
            append(block, db)
            if (db < endDb) quietBytes += block.size else quietBytes = 0
            if (quietBytes >= holdBytes || clip.size() >= maxBytes) result = finish()
        }
        totalBytes += block.size
        return result
    }

    /** 録音が終わったとき、イベントの途中なら、そこまでを確定させる。 */
    fun flush(): DetectedEvent? = if (inEvent) finish() else null

    private fun begin(block: ByteArray, db: Float) {
        inEvent = true
        clip = ByteArrayOutputStream()
        preBytes = ringBytes
        clipOffsetBytes = totalBytes - ringBytes
        ring.forEach { clip.write(it) }
        ring.clear()
        ringBytes = 0
        quietBytes = 0
        maxDb = db
        sumDb = 0.0
        blocks = 0
        append(block, db)
    }

    private fun append(block: ByteArray, db: Float) {
        clip.write(block)
        if (db > maxDb) maxDb = db
        sumDb += db
        blocks++
    }

    private fun finish(): DetectedEvent? {
        inEvent = false
        val pcm = clip.toByteArray()
        val activeBytes = pcm.size - preBytes - quietBytes
        val event = if (activeBytes < minBytes) null else DetectedEvent(
            offsetMs = WavFormat.bytesToMs(clipOffsetBytes.toInt()),
            durationMs = WavFormat.bytesToMs(pcm.size),
            maxDb = maxDb,
            avgDb = (sumDb / blocks).toFloat(),
            pcm = pcm,
        )
        clip = ByteArrayOutputStream()
        return event
    }
}
