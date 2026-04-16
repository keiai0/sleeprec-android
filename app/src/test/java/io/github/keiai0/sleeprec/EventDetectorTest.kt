package io.github.keiai0.sleeprec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {
    private val block = ByteArray(3200) // 0.1秒。中身は不問(dB は別に渡す)
    private val quiet = -60f
    private val loud = -20f

    private fun detector() = EventDetector(
        startDb = -40f, endDb = -45f, holdMs = 1000, preRollMs = 500, minEventMs = 300, maxClipMs = 10_000,
    )

    /** 指定した dB を並べて流し込み、確定したイベントをすべて返す。 */
    private fun run(d: EventDetector, vararg dbs: Pair<Float, Int>): List<DetectedEvent> {
        val out = mutableListOf<DetectedEvent>()
        for ((db, n) in dbs) repeat(n) { d.feed(block, block.size, db)?.let(out::add) }
        return out
    }

    @Test fun silenceProducesNoEvents() = assertEquals(0, run(detector(), quiet to 600).size)

    @Test fun burstBecomesOneEvent() {
        val d = detector()
        val events = run(d, quiet to 20, loud to 10, quiet to 20)
        assertEquals(1, events.size)
        val e = events[0]
        assertEquals(loud, e.maxDb, 0f)
        assertEquals(e.pcm.size.toLong(), e.durationMs * 32)
    }

    @Test fun clipIncludesPreRollAndHold() {
        // 前 0.5 秒 + 音 1 秒 + 静かな 1 秒(終了までの待ち)= 2.5 秒
        val e = run(detector(), quiet to 20, loud to 10, quiet to 20).single()
        assertEquals(2500, e.durationMs)
        // 開始は、音が出た 2.0 秒の位置の 0.5 秒前
        assertEquals(1500, e.offsetMs)
    }

    @Test fun shortGapDoesNotSplit() {
        // 0.5 秒の途切れは、待ち時間(1 秒)より短いので 1 件のまま
        val events = run(detector(), quiet to 10, loud to 5, quiet to 5, loud to 5, quiet to 20)
        assertEquals(1, events.size)
    }

    @Test fun longGapSplitsIntoTwoEvents() {
        val events = run(detector(), loud to 5, quiet to 30, loud to 5, quiet to 30)
        assertEquals(2, events.size)
    }

    @Test fun hysteresisKeepsEventBetweenEndAndStart() {
        // -42 dB は開始(-40)には届かないが、終了(-45)よりは大きいので、イベントは続く
        val events = run(detector(), loud to 5, -42f to 30, quiet to 20)
        assertEquals(1, events.size)
        assertTrue(events[0].durationMs > 3000)
    }

    @Test fun belowStartDoesNotTrigger() =
        assertEquals(0, run(detector(), -42f to 100).size)

    @Test fun tooShortBurstIsDropped() {
        // 0.1 秒だけの音は MIN(0.3 秒)未満なので捨てる
        assertEquals(0, run(detector(), quiet to 10, loud to 1, quiet to 30).size)
    }

    @Test fun clipIsCappedAtMax() {
        // 20 秒鳴り続けても、10 秒で区切られる
        val events = run(detector(), loud to 200, quiet to 20)
        assertEquals(2, events.size)
        assertEquals(10_000, events[0].durationMs)
    }

    @Test fun flushClosesEventInProgress() {
        val d = detector()
        run(d, loud to 10)
        assertNotNull(d.flush())
        assertNull(d.flush())
    }

    @Test fun flushWithoutEventReturnsNull() {
        val d = detector()
        run(d, quiet to 10)
        assertNull(d.flush())
    }

    @Test fun preRollContainsRecentAudio() {
        // ブロックごとに目印のバイトを入れ、前の音が正しい順序で入っていることを確認する
        val d = detector()
        fun blockOf(v: Int) = ByteArray(3200) { v.toByte() }
        for (i in 1..10) d.feed(blockOf(i), 3200, quiet)
        repeat(4) { d.feed(blockOf(99), 3200, loud) }
        val e = (1..20).firstNotNullOf { d.feed(blockOf(0), 3200, quiet) }
        // 前 0.5 秒 = 5 ブロック(6〜10)の後に、音のブロック(99)が続く
        assertEquals(6, e.pcm[0].toInt())
        assertEquals(10, e.pcm[4 * 3200].toInt())
        assertEquals(99, e.pcm[5 * 3200].toInt())
    }

    @Test fun loudRangeExcludesPaddingAndTail() {
        // 静か 2 秒 → 音 1 秒 → 静か 2 秒: 音は 2.0〜3.0 秒
        val e = run(detector(), quiet to 20, loud to 10, quiet to 20).single()
        assertEquals(2000, e.loudStartMs)
        assertEquals(3000, e.loudEndMs)
    }

    @Test fun leadIsAudioBeforeTheClip() {
        val d = EventDetector(startDb = -40f, endDb = -45f, holdMs = 1000, preRollMs = 500, minEventMs = 300, maxClipMs = 10_000, leadMs = 2000)
        fun blockOf(v: Int) = ByteArray(3200) { v.toByte() }
        for (i in 1..30) d.feed(blockOf(i), 3200, quiet) // 3 秒分(1〜30)
        repeat(4) { d.feed(blockOf(99), 3200, loud) }
        val e = (1..20).firstNotNullOf { d.feed(blockOf(0), 3200, quiet) }
        // クリップの頭 = 直前 5 ブロック(26〜30)。lead はその前の 20 ブロック(6〜25)
        assertEquals(26, e.pcm[0].toInt())
        assertEquals(20 * 3200, e.leadPcm.size)
        assertEquals(6, e.leadPcm[0].toInt())
        assertEquals(25, e.leadPcm[e.leadPcm.size - 1].toInt())
    }

    @Test fun leadIsEmptyWhenEventStartsRightAway() {
        val e = run(detector(), loud to 10, quiet to 20).single()
        assertEquals(0, e.leadPcm.size)
    }
}
