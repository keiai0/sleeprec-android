package io.github.keiai0.sleeprec.data

import io.github.keiai0.sleeprec.SecondLoudness
import io.github.keiai0.sleeprec.SessionPolicy
import io.github.keiai0.sleeprec.Thresholds
import java.io.File

/** セッションの作成・終了・中断の扱いをまとめる。保存するかどうかの判断は SessionPolicy に従う。 */
class SessionStore(
    private val dao: SessionDao,
    private val loudnessDao: LoudnessDao,
    private val eventDao: AudioEventDao,
) {

    suspend fun start(startedAt: Long, wavPath: String): Long =
        dao.insert(
            Session(startedAt = startedAt, status = SessionStatus.RECORDING, lastAliveAt = startedAt, wavPath = wavPath)
        )

    suspend fun saveLoudness(id: Long, samples: List<SecondLoudness>) {
        if (samples.isEmpty()) return
        loudnessDao.insertAll(samples.map { LoudnessSample(id, it.second, it.avgDb, it.maxDb) })
    }

    suspend fun loudness(id: Long): List<LoudnessSample> = loudnessDao.forSession(id)

    /**
     * 検出したイベントを保存する。クリップの音声は 1 晩 MAX_CLIPS_PER_SESSION 件まで残し、
     * 超えたら最大 dB の小さいものから音声だけを削除する(行=メタデータは残す)。
     */
    suspend fun saveEvent(event: AudioEvent) {
        eventDao.insert(event)
        val over = eventDao.clipsOverLimit(event.sessionId, Thresholds.MAX_CLIPS_PER_SESSION)
        deleteClips(over)
    }

    suspend fun eventCount(id: Long): Int = eventDao.count(id)

    suspend fun events(id: Long): List<AudioEvent> = eventDao.forSession(id)

    /**
     * 保持期間(AUDIO_RETENTION_DAYS)を過ぎた音声を削除する。対象はクリップと全録音の WAV。
     * イベントの行、音量、スコアなどは消さない。起動時とセッション終了時に呼ぶ。
     */
    suspend fun expireOldAudio(now: Long) {
        val cutoff = now - Thresholds.AUDIO_RETENTION_DAYS * 24 * 60 * 60 * 1000
        deleteClips(eventDao.clipsOlderThan(cutoff))
        dao.finishedBefore(cutoff).forEach { File(it.wavPath).delete() }
    }

    private suspend fun deleteClips(events: List<AudioEvent>) {
        if (events.isEmpty()) return
        events.forEach { it.clipPath?.let(::File)?.delete() }
        eventDao.clearClipPaths(events.map { it.id })
    }

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
        eventDao.clipPaths(s.id).forEach { File(it).delete() } // 行は cascade で消えるが、ファイルは自分で消す
        dao.delete(s)
    }
}
