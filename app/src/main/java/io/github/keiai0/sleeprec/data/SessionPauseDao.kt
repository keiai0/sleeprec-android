package io.github.keiai0.sleeprec.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionPauseDao {
    @Insert
    suspend fun insert(pause: SessionPause): Long

    @Query("UPDATE session_pauses SET endedAt = :endedAt WHERE id = :id AND endedAt IS NULL")
    suspend fun end(id: Long, endedAt: Long)

    @Query("SELECT * FROM session_pauses WHERE sessionId = :sessionId ORDER BY startedAt")
    suspend fun forSession(sessionId: Long): List<SessionPause>
}
