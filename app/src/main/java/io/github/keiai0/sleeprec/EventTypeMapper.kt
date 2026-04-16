package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.EventType

/**
 * YAMNet の 521 クラスのスコアから、SPEC の種別を決める(Android に依存しない純粋な関数)。
 * 種別ごとに、対応するクラスの最大スコアを取り、最も高い種別を採用する。
 * どれも MIN 未満なら「その他」。`Silence` などは対応表に入れないので、無音の余白の影響を受けない。
 */
object EventTypeMapper {
    // クラス番号は、YAMNet の yamnet_class_map.csv(assets/yamnet_labels.txt の行番号 - 1)
    val groups: Map<EventType, IntArray> = mapOf(
        EventType.SNORING to intArrayOf(38),                    // Snoring
        EventType.SLEEP_TALK to intArrayOf(0, 12),              // Speech, Whispering
        EventType.COUGH to intArrayOf(42, 43),                  // Cough, Throat clearing
        EventType.FART to intArrayOf(55),                       // Fart
        EventType.FOOTSTEPS to intArrayOf(48),                  // Walk, footsteps
        EventType.ANIMAL to intArrayOf(67, 68, 69, 70, 76, 78), // Animal, Domestic animals, Dog, Bark, Cat, Meow
    )

    fun decide(scores: FloatArray, minScore: Float = Thresholds.CLASSIFY_MIN_SCORE): Pair<EventType, Float> {
        var best: EventType = EventType.OTHER
        var bestScore = 0f
        for ((type, indices) in groups) {
            val score = indices.maxOf { scores[it] }
            if (score >= minScore && score > bestScore) {
                best = type
                bestScore = score
            }
        }
        return best to bestScore
    }
}
