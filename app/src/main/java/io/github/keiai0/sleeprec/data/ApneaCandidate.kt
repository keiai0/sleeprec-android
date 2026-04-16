package io.github.keiai0.sleeprec.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 無呼吸の候補 1 件(いびき → 無音 → 呼吸音での再開)。診断ではなく、目安。
// クリップの音声が削除されても(上限超え・7 日経過)、この行は残す。
@Entity(
    tableName = "apnea_candidates",
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
data class ApneaCandidate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startedAt: Long,  // 無音の始まりの時刻(エポックミリ秒)
    val silenceMs: Long,  // 無音の長さ
    val maxDb: Float,     // 前後のイベントの最大 dB(再生時の音量補正に使う)
    // null なら音声は残っていない
    val clipPath: String? = null,
)
