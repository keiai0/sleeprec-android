package io.github.keiai0.sleeprec.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LoudnessDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<LoudnessSample>)

    @Query("SELECT * FROM loudness_samples WHERE sessionId = :sessionId ORDER BY second")
    suspend fun forSession(sessionId: Long): List<LoudnessSample>
}
