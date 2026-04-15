package io.github.keiai0.sleeprec.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AudioEventDao {
    @Insert
    suspend fun insert(event: AudioEvent): Long

    @Query("SELECT * FROM audio_events WHERE sessionId = :sessionId ORDER BY startedAt")
    suspend fun forSession(sessionId: Long): List<AudioEvent>

    @Query("SELECT COUNT(*) FROM audio_events WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    // 音声が残っているクリップのうち、最大 dB の大きい順で limit 件を飛ばした残り(=削除対象)
    @Query(
        "SELECT * FROM audio_events WHERE sessionId = :sessionId AND clipPath IS NOT NULL " +
            "ORDER BY maxDb DESC, id LIMIT -1 OFFSET :limit"
    )
    suspend fun clipsOverLimit(sessionId: Long, limit: Int): List<AudioEvent>

    @Query("SELECT * FROM audio_events WHERE clipPath IS NOT NULL AND startedAt < :cutoff")
    suspend fun clipsOlderThan(cutoff: Long): List<AudioEvent>

    @Query("DELETE FROM audio_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT clipPath FROM audio_events WHERE sessionId = :sessionId AND clipPath IS NOT NULL")
    suspend fun clipPaths(sessionId: Long): List<String>

    @Query("UPDATE audio_events SET clipPath = NULL WHERE id IN (:ids)")
    suspend fun clearClipPaths(ids: List<Long>)
}
