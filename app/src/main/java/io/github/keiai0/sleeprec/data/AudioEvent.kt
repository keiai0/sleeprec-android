package io.github.keiai0.sleeprec.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 検出した音声イベント 1 件。クリップの音声が削除されても(上限超え・7 日経過)、この行は残す。
@Entity(
    tableName = "audio_events",
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
data class AudioEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startedAt: Long,   // クリップの先頭の時刻(エポックミリ秒)
    val durationMs: Long,
    val maxDb: Float,
    val avgDb: Float,
    val type: EventType = EventType.UNCLASSIFIED,
    // null なら音声は残っていない(上限超え、または 7 日経過で削除)
    val clipPath: String? = null,
)

// Phase 4 で分類する。今は UNCLASSIFIED のみ
enum class EventType { UNCLASSIFIED }
