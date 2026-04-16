package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.ApneaCandidate
import io.github.keiai0.sleeprec.data.AudioEvent
import io.github.keiai0.sleeprec.data.EventType
import kotlin.random.Random

/**
 * デバッグ用の合成データ。数時間の実データがなくても、睡眠の推定・スコア・画面を確認できるようにする。
 * 同じ seed なら同じ夜になる(テストで再現できる)。sessionId は 0(保存時に差し替える)。
 */
class SyntheticNight(
    val startedAt: Long,
    val endedAt: Long,
    val samples: List<SecondLoudness>,
    val events: List<AudioEvent>,
    val apneas: List<ApneaCandidate>,
)

object SyntheticNightGenerator {
    /** 寝つくまでの覚醒、いびき、中途覚醒 2 回(6〜12 分)、短い覚醒 1 回、起床前の覚醒、数回の無呼吸候補を持つ 1 晩。 */
    fun generate(startedAt: Long, durationMin: Int = 480, seed: Int = 1): SyntheticNight {
        val rnd = Random(seed)
        val events = mutableListOf<AudioEvent>()
        fun add(type: EventType, atMin: Int, sec: Int, maxDb: Float, offsetSec: Int = 0) {
            events += AudioEvent(
                sessionId = 0, startedAt = startedAt + atMin * 60_000L + offsetSec * 1000L, durationMs = sec * 1000L,
                maxDb = maxDb, avgDb = maxDb - 12f, type = type,
            )
        }
        val activity = listOf(EventType.FOOTSTEPS, EventType.COUGH, EventType.OTHER, EventType.SLEEP_TALK)

        // 寝つくまで(スマホをいじる音・寝返り)
        val latency = rnd.nextInt(8, 21)
        for (m in 1 until latency) add(EventType.OTHER, m, sec = rnd.nextInt(15, 30), maxDb = -28f)

        // いびき: 15〜35 分おきに 20〜70 秒
        val snoreAt = mutableListOf<Int>()
        var t = latency + rnd.nextInt(15, 36)
        while (t < durationMin - 30) {
            add(EventType.SNORING, t, sec = rnd.nextInt(20, 71), maxDb = -30f + rnd.nextInt(0, 9))
            snoreAt += t
            t += rnd.nextInt(15, 36)
        }

        // 中途覚醒 2 回(6〜12 分)と、短い覚醒(3 分)
        fun awakening(fromMin: Int, minutes: Int) {
            for (m in fromMin until fromMin + minutes) add(activity[rnd.nextInt(activity.size)], m, sec = rnd.nextInt(15, 30), maxDb = -25f)
        }
        awakening(rnd.nextInt(90, 171), rnd.nextInt(6, 13))
        awakening(rnd.nextInt(250, 381), rnd.nextInt(6, 13))
        awakening(rnd.nextInt(200, 240), 3)

        // 起床前の覚醒
        awakening(durationMin - rnd.nextInt(6, 13), 6)

        // 睡眠には関係しない音
        repeat(4) { add(EventType.ANIMAL, rnd.nextInt(60, durationMin - 20), sec = 8, maxDb = -35f) }
        repeat(3) { add(EventType.AMBIENT, rnd.nextInt(60, durationMin - 20), sec = 10, maxDb = -38f) }

        // 無呼吸の候補(いびきの直後の 12〜40 秒の無音)
        val apneas = snoreAt.shuffled(rnd).take(3).map { at ->
            ApneaCandidate(
                sessionId = 0, startedAt = startedAt + (at + 1) * 60_000L, silenceMs = rnd.nextLong(12_000, 40_000),
                maxDb = -30f,
            )
        }

        // 1 秒ごとの音量: 静けさ(約 -62)に、イベントの区間だけ上乗せする
        val n = durationMin * 60
        val avg = FloatArray(n) { -62f + rnd.nextFloat() * 3f }
        val max = FloatArray(n) { avg[it] + 1f + rnd.nextFloat() * 2f }
        for (e in events) {
            val from = ((e.startedAt - startedAt) / 1000).toInt().coerceIn(0, n - 1)
            val to = ((e.startedAt - startedAt + e.durationMs) / 1000).toInt().coerceIn(from + 1, n)
            for (s in from until to) {
                avg[s] = maxOf(avg[s], e.avgDb)
                max[s] = maxOf(max[s], e.maxDb - rnd.nextFloat() * 4f)
            }
        }
        val samples = List(n) { SecondLoudness(it, avg[it], max[it]) }
        return SyntheticNight(startedAt, startedAt + durationMin * 60_000L, samples, events.sortedBy { it.startedAt }, apneas.sortedBy { it.startedAt })
    }
}
