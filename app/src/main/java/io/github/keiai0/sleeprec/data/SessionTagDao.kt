package io.github.keiai0.sleeprec.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<SessionTag>)

    @Query("SELECT tag FROM session_tags WHERE sessionId = :sessionId ORDER BY rowid")
    suspend fun forSession(sessionId: Long): List<String>

    // 最近使ったタグ(新しい計測で使ったものが先)。次の計測で、ワンタップで付けられるようにする
    @Query("SELECT tag FROM session_tags GROUP BY tag ORDER BY MAX(sessionId) DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<String>
}
