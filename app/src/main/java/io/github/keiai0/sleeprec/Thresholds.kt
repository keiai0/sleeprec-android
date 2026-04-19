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

    // --- 睡眠の推定(Phase 5)。音だけからの暫定ルール。実データで調整する ---
    const val AWAKE_ACTIVITY_MS = 10_000L   // 1 分のうち、活動の音がこれ以上あれば「覚醒」
    const val ONSET_QUIET_MINUTES = 10      // 活動のない状態がこの分数続いた最初の時点を「入眠」とする
    const val WAKE_RUN_MINUTES = 5          // これ以上続く覚醒を、1 回の「中途覚醒」と数える(NSF の 5 分)
    const val STAGE_QUIET_WINDOW_MIN = 5    // 前後この分数に活動がなければ「静か」(Deep / REM)。あれば Light
    // 静かな分は、90 分周期のモデルで Deep / REM / Light に割り当てる(一般的な傾向: 前半の周期ほど Deep が多く、後半ほど REM が多い)。
    // 周期の中で、[DEEP_START, DEEP_START + Deep 割合) が Deep、周期の終わりの REM 割合が REM、残りは Light。
    const val SLEEP_CYCLE_MIN = 90
    const val DEEP_START_FRACTION = 0.10
    const val DEEP_FIRST_CYCLE_FRACTION = 0.32 // 最初の周期の Deep の割合。周期ごとに DEEP_DECAY_PER_CYCLE ずつ減る(下限 0)
    const val DEEP_DECAY_PER_CYCLE = 0.08
    const val REM_FIRST_CYCLE_FRACTION = 0.10  // 最初の周期の REM の割合。周期ごとに REM_GROWTH_PER_CYCLE ずつ増える(上限 0.5)
    const val REM_GROWTH_PER_CYCLE = 0.06

    // --- 睡眠スコア(RESEARCH.md §A-3)。配点・閾値は実データで調整する ---
    const val GOAL_SLEEP_MS = 450L * 60_000 // 目標睡眠時間の既定(7.5 時間)。設定画面ができるまで固定
    const val SCORE_MIN_TST_MS = 4L * 60 * 60_000 // これ未満はスコアを出さない
    const val DURATION_FULL_UPPER_MS = 9L * 60 * 60_000 // これを超えると、緩やかに減点
    const val SCORE_DURATION = 40
    const val SCORE_RESTORATION = 30
    const val SCORE_CONSISTENCY = 20
    const val SCORE_FEELING = 10
    const val CONSISTENCY_MIN_NIGHTS = 3    // 規則性を出すのに必要な夜数
    const val CONSISTENCY_NIGHTS = 7        // 規則性の計算に使う直近の夜数
    const val CONSISTENCY_SD_FULL_MIN = 30.0 // 就寝・起床時刻の標準偏差(分)がこれ以下で 100%
    const val CONSISTENCY_SD_ZERO_MIN = 90.0 // これ以上で 0%

    // --- いびきの強度区分(Phase 7)。最大 dBFS による目安。端末のマイク感度で変わる相対値なので、暫定 ---
    const val SNORE_LIGHT_DB = -33f      // これ以上で「軽い」(未満は「静か」)
    const val SNORE_LOUD_DB = -25f       // これ以上で「大きい」
    const val SNORE_VERY_LOUD_DB = -15f  // これ以上で「非常に大きい」

    // --- 保持ポリシー(PLAN §6 #6) ---
    const val MAX_CLIPS_PER_SESSION = 20 // 1晩に音声を残す数。超えた分は最大 dB の大きい順に残す
    const val AUDIO_RETENTION_DAYS = 7L  // 音声(クリップ、全録音)を自動削除するまでの日数
}
