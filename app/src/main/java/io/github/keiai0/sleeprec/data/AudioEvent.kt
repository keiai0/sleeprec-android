package io.github.keiai0.sleeprec.data

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.keiai0.sleeprec.R

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
    // 自動分類のときの、その種別のスコア(0〜1)。再分類・閾値の調整に使う
    @ColumnInfo(defaultValue = "0") val typeScore: Float = 0f,
    // ユーザーが種別を直したか(FR-4.8)。true の種別は、自動分類で上書きしない
    @ColumnInfo(defaultValue = "0") val typeCorrected: Boolean = false,
    // null なら音声は残っていない(上限超え、または 7 日経過で削除)
    val clipPath: String? = null,
)

// SPEC の種別。歯ぎしり・環境音はモデルが対応しないので、自動分類では OTHER になり、ユーザーが手動で選ぶ。
// Room は名前の文字列で保存するので、並べ替え・追加をしても既存の行は壊れない。
enum class EventType(@StringRes val labelRes: Int) {
    UNCLASSIFIED(R.string.type_unclassified),
    SNORING(R.string.type_snoring),
    SLEEP_TALK(R.string.type_sleep_talk),
    COUGH(R.string.type_cough),
    FART(R.string.type_fart),
    FOOTSTEPS(R.string.type_footsteps),
    ANIMAL(R.string.type_animal),
    GRINDING(R.string.type_grinding),
    AMBIENT(R.string.type_ambient),
    OTHER(R.string.type_other),
}
