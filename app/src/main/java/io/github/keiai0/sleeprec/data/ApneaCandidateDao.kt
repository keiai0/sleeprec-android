package io.github.keiai0.sleeprec.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ApneaCandidateDao {
    @Insert
    suspend fun insert(candidate: ApneaCandidate): Long

    @Query("SELECT * FROM apnea_candidates WHERE sessionId = :sessionId ORDER BY startedAt")
    suspend fun forSession(sessionId: Long): List<ApneaCandidate>

    // 音声が残っているもののうち、無音の長い順で limit 件を飛ばした残り(=音声の削除対象)
    @Query(
        "SELECT * FROM apnea_candidates WHERE sessionId = :sessionId AND clipPath IS NOT NULL " +
            "ORDER BY silenceMs DESC, id LIMIT -1 OFFSET :limit"
    )
    suspend fun clipsOverLimit(sessionId: Long, limit: Int): List<ApneaCandidate>

    @Query("SELECT * FROM apnea_candidates WHERE clipPath IS NOT NULL AND startedAt < :cutoff")
    suspend fun clipsOlderThan(cutoff: Long): List<ApneaCandidate>

    @Query("SELECT clipPath FROM apnea_candidates WHERE sessionId = :sessionId AND clipPath IS NOT NULL")
    suspend fun clipPaths(sessionId: Long): List<String>

    @Query("UPDATE apnea_candidates SET clipPath = NULL WHERE id IN (:ids)")
    suspend fun clearClipPaths(ids: List<Long>)
}
