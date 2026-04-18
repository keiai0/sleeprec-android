package io.github.keiai0.sleeprec.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 計測の一時停止 1 回分。この間は音を録らず、解析もしない。睡眠の推定では「起きていた」時間として扱う。
@Entity(
    tableName = "session_pauses",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SessionPause(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long? = null, // null なら、まだ再開していない(または、再開前に中断された)
    val reason: PauseReason,
)

enum class PauseReason {
    USER,      // ユーザーが一時停止した
    MIC_BUSY,  // 他のアプリがマイクを使ったため、自動で一時停止した(FR-2.5)
}
