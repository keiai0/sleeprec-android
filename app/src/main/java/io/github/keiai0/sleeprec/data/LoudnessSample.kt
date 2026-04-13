package io.github.keiai0.sleeprec.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// 1秒ごとの音量(dBFS)。8時間で約2.9万行。セッションを消すと一緒に消える。
@Entity(
    tableName = "loudness_samples",
    primaryKeys = ["sessionId", "second"],
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
data class LoudnessSample(
    val sessionId: Long,
    // 計測開始からの経過秒
    val second: Int,
    val avgDb: Float,
    val maxDb: Float,
)
