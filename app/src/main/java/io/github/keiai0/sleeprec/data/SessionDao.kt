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

    @Query("SELECT * FROM sessions WHERE status = 'RECORDING'")
    suspend fun recording(): List<Session>

    @Query("SELECT * FROM sessions WHERE status = 'INTERRUPTED' AND interruptionAcknowledged = 0 ORDER BY startedAt")
    suspend fun unacknowledgedInterrupted(): List<Session>

    @Query("UPDATE sessions SET lastAliveAt = :time WHERE id = :id")
    suspend fun updateLastAlive(id: Long, time: Long)
}
