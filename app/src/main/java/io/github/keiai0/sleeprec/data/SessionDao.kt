package io.github.keiai0.sleeprec.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// DAO(Data Access Object): SQL とメソッドの対応表。
// interface を書くだけで、実装は Room がビルド時に生成する(KSP)。
@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Delete
    suspend fun delete(session: Session)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): Session?

    // 規則性(Consistency)の計算用。この夜より前の、通常の(30 分以上の)記録を新しい順に
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND startedAt < :before ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentCompleted(before: Long, limit: Int): List<Session>

    // デバッグ用の合成データ(wavPath が debug:// で始まる)だけを削除する。音量・イベントは cascade で消える
    @Query("DELETE FROM sessions WHERE wavPath LIKE 'debug://%'")
    suspend fun deleteSynthetic()

    // 一覧用。計測中を除いて、新しい順
    @Query("SELECT * FROM sessions WHERE status != 'RECORDING' ORDER BY startedAt DESC")
    suspend fun finished(): List<Session>

    @Query("SELECT * FROM sessions WHERE status = 'RECORDING'")
    suspend fun recording(): List<Session>

    @Query("SELECT * FROM sessions WHERE status = 'INTERRUPTED' AND interruptionAcknowledged = 0 ORDER BY startedAt")
    suspend fun unacknowledgedInterrupted(): List<Session>

    // 録音が終わっていて、cutoff より前に始まったセッション(全録音 WAV の自動削除の対象)
    @Query("SELECT * FROM sessions WHERE status != 'RECORDING' AND startedAt < :cutoff")
    suspend fun finishedBefore(cutoff: Long): List<Session>

    // 気分だけを更新する(行全体を書き換えると、終了処理との競合で、他の項目を古い値に戻してしまうため)
    @Query("UPDATE sessions SET mood = :mood WHERE id = :id")
    suspend fun setMood(id: Long, mood: Int?)

    @Query("UPDATE sessions SET lastAliveAt = :time WHERE id = :id")
    suspend fun updateLastAlive(id: Long, time: Long)
}
