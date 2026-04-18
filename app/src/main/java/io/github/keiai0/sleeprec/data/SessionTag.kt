package io.github.keiai0.sleeprec.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// 睡眠前のタグ(FR-2.10)。1 回の計測に対して、同じタグは 1 つだけ(主キーで重複を防ぐ)。
@Entity(
    tableName = "session_tags",
    primaryKeys = ["sessionId", "tag"],
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
data class SessionTag(
    val sessionId: Long,
    val tag: String,
)
