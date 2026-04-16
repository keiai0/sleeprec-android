package io.github.keiai0.sleeprec

/**
 * 解析の閾値をここに集約する(PLAN §3)。いずれも暫定値で、実データを見て調整する。
 * dB は dBFS の相対値(Loudness.kt を参照)。実機で静かな部屋は約 -60、話し声や拍手は -30〜-8 だった。
 */
object Thresholds {
    // --- 音声イベントの検出(Phase 3) ---
    const val EVENT_START_DB = -40f     // これ以上でイベント開始
    const val EVENT_END_DB = -45f       // これを下回ると「静か」とみなす(開始より低くしてヒステリシスを作る)
    const val EVENT_HOLD_MS = 2_000L    // 静かな状態がこの時間続いたらイベント終了
    const val PRE_ROLL_MS = 3_000L      // イベント開始の前から含める音の長さ
    const val MIN_EVENT_MS = 500L       // 音の出ている区間がこれ未満なら、イベントとして扱わない
    const val MAX_CLIP_MS = 60_000L     // 1クリップの上限。超えたら、そこで区切って次のイベントにする

    // --- 音の分類(Phase 4) ---
    const val CLASSIFY_MIN_SCORE = 0.3f // 種別に決めるための、最低のスコア。届かなければ「その他」

    // --- 保持ポリシー(PLAN §6 #6) ---
    const val MAX_CLIPS_PER_SESSION = 20 // 1晩に音声を残す数。超えた分は最大 dB の大きい順に残す
    const val AUDIO_RETENTION_DAYS = 7L  // 音声(クリップ、全録音)を自動削除するまでの日数
}
