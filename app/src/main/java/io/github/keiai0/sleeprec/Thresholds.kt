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

    // --- 無呼吸の目安(Phase 4)。医療機器ではなく、いびきの後の無音区間によるヒューリスティック ---
    const val APNEA_MIN_SILENCE_MS = 10_000L // これ以上の無音で候補(無呼吸の定義の 10 秒)
    const val APNEA_MAX_SILENCE_MS = 90_000L // これを超える無音は、寝返り・離席などとみなして候補にしない
    const val APNEA_LEAD_MS = 30_000L        // 各イベントの直前に、余分に持っておく音の長さ(無音区間のクリップ用)
    const val APNEA_CLIP_TAIL_MS = 5_000L    // クリップに含める、無音の前のいびきの長さ
    const val APNEA_CLIP_HEAD_MS = 8_000L    // クリップに含める、無音の後の音の長さ
    const val APNEA_RESUME_MIN_SCORE = 0.3f  // 再開の音(いびき・Snort・Gasp など)とみなす最低スコア
    const val MAX_APNEA_CLIPS_PER_SESSION = 10
    // 重症度の境界(1 時間あたりの候補数)。AHI の分類(5/15/30)を借りているが、AHI そのものではない
    const val APNEA_MILD_PER_HOUR = 5.0
    const val APNEA_MODERATE_PER_HOUR = 15.0
    const val APNEA_SEVERE_PER_HOUR = 30.0
    const val APNEA_MIN_SESSION_MS = 60L * 60 * 1000 // これ未満の計測では、1 時間あたりの回数(重症度)を出さない

    // --- 保持ポリシー(PLAN §6 #6) ---
    const val MAX_CLIPS_PER_SESSION = 20 // 1晩に音声を残す数。超えた分は最大 dB の大きい順に残す
    const val AUDIO_RETENTION_DAYS = 7L  // 音声(クリップ、全録音)を自動削除するまでの日数
}
