package com.example.sleeprec.data

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sleeprec.R

// 1 回の計測 = 1 行。Go でいう、DB の 1 レコードに対応する struct。
// Room は enum を名前の文字列(例 "RECORDING")として保存する。
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus,
    // サービスが約 30 秒ごとに更新する「最後に生きていた時刻」。中断の検知に使う
    val lastAliveAt: Long,
    val wavPath: String,
    val interruptReason: InterruptReason? = null,
    // 中断をユーザーに案内済みか
    val interruptionAcknowledged: Boolean = false,
)

enum class SessionStatus {
    RECORDING,   // 計測中(終了していない)
    COMPLETED,   // 30 分以上。通常分析の対象
    SHORT_SLEEP, // 10〜30 分。分析対象外
    INTERRUPTED, // 中断された(データが不正確な可能性あり)
}

enum class InterruptReason(@StringRes val messageRes: Int) {
    SERVICE_STOPPED(R.string.reason_service_stopped),
    RECORDER_ERROR(R.string.reason_recorder_error),
    OS_LOW_MEMORY(R.string.reason_os_low_memory),
    USER_KILLED(R.string.reason_user_killed),
    CRASH(R.string.reason_crash),
    SYSTEM_KILLED(R.string.reason_system_killed),
    PERMISSION_CHANGED(R.string.reason_permission_changed),
    OTHER(R.string.reason_other),
    NO_RECORD(R.string.reason_no_record), // OS に終了の記録がない(電池切れ・再起動の可能性)
    UNKNOWN(R.string.reason_unknown),     // Android 10 など、終了理由を取得できない
}
