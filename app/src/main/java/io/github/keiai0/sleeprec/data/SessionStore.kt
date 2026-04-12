package io.github.keiai0.sleeprec.data

import io.github.keiai0.sleeprec.SessionPolicy
import java.io.File

/** セッションの作成・終了・中断の扱いをまとめる。保存するかどうかの判断は SessionPolicy に従う。 */
class SessionStore(private val dao: SessionDao) {

    suspend fun start(startedAt: Long, wavPath: String): Long =
        dao.insert(
            Session(startedAt = startedAt, status = SessionStatus.RECORDING, lastAliveAt = startedAt, wavPath = wavPath)
        )

    suspend fun heartbeat(id: Long, now: Long) = dao.updateLastAlive(id, now)

    suspend fun activeSession(): Session? = dao.recording().lastOrNull()

    suspend fun recordingSessions(): List<Session> = dao.recording()

    suspend fun unacknowledgedInterrupted(): List<Session> = dao.unacknowledgedInterrupted()

    suspend fun acknowledge(id: Long) {
        dao.get(id)?.let { dao.update(it.copy(interruptionAcknowledged = true)) }
    }

    /**
     * ユーザー操作による終了。save=false、または 10 分未満なら、行も WAV も削除する。
     */
    suspend fun finish(id: Long, save: Boolean, now: Long) {
        val s = dao.get(id) ?: return
        val outcome = SessionPolicy.classify(now - s.startedAt)
        if (!save || outcome == SessionPolicy.Outcome.NOT_SAVED) {
            discard(s)
            return
        }
        val status = if (outcome == SessionPolicy.Outcome.SHORT_SLEEP) SessionStatus.SHORT_SLEEP else SessionStatus.COMPLETED
        dao.update(s.copy(status = status, endedAt = now, lastAliveAt = now))
    }

    /**
     * 中断(意図しない終了)。10 分未満は、通常終了と同じく保存しない(ユーザーへの案内もなし)。
     * それ以上は WAV を残し、INTERRUPTED として次回起動時に案内する。
     */
    suspend fun markInterrupted(id: Long, endedAt: Long, reason: InterruptReason) {
        val s = dao.get(id) ?: return
        if (s.status != SessionStatus.RECORDING) return
        if (SessionPolicy.classify(endedAt - s.startedAt) == SessionPolicy.Outcome.NOT_SAVED) {
            discard(s)
            return
        }
        dao.update(
            s.copy(
                status = SessionStatus.INTERRUPTED,
                endedAt = endedAt,
                interruptReason = reason,
                interruptionAcknowledged = false,
            )
        )
    }

    private suspend fun discard(s: Session) {
        File(s.wavPath).delete()
        dao.delete(s)
    }
}
