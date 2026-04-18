package io.github.keiai0.sleeprec.data

import android.content.Context
import io.github.keiai0.sleeprec.SecondLoudness
import io.github.keiai0.sleeprec.SessionPolicy
import io.github.keiai0.sleeprec.SyntheticNight
import io.github.keiai0.sleeprec.Thresholds
import java.io.File

/** セッションの作成・終了・中断の扱いをまとめる。保存するかどうかの判断は SessionPolicy に従う。 */
class SessionStore(
    private val dao: SessionDao,
    private val loudnessDao: LoudnessDao,
    private val eventDao: AudioEventDao,
    private val apneaDao: ApneaCandidateDao,
    private val tagDao: SessionTagDao,
    private val pauseDao: SessionPauseDao,
) {

    companion object {
        fun create(context: Context): SessionStore =
            AppDatabase.get(context).let { SessionStore(it.sessionDao(), it.loudnessDao(), it.audioEventDao(), it.apneaCandidateDao(), it.sessionTagDao(), it.sessionPauseDao()) }
    }

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

    suspend fun recentCompleted(before: Long, limit: Int): List<Session> = dao.recentCompleted(before, limit)

    /** デバッグ用: 合成した 1 晩を、通常の記録として保存する(音声ファイルはない)。 */
    suspend fun insertSyntheticNight(night: SyntheticNight): Long {
        val id = dao.insert(
            Session(
                startedAt = night.startedAt, endedAt = night.endedAt, status = SessionStatus.COMPLETED,
                lastAliveAt = night.endedAt, wavPath = "debug://synthetic",
            )
        )
        loudnessDao.insertAll(night.samples.map { LoudnessSample(id, it.second, it.avgDb, it.maxDb) })
        night.events.forEach { eventDao.insert(it.copy(sessionId = id)) }
        night.apneas.forEach { apneaDao.insert(it.copy(sessionId = id)) }
        return id
    }

    suspend fun deleteSyntheticNights() = dao.deleteSynthetic()

    suspend fun finishedSessions(): List<Session> = dao.finished()

    suspend fun session(id: Long): Session? = dao.get(id)

    /** クリップの音声ファイルと、イベントの行の両方を削除する。 */
    suspend fun deleteEvent(event: AudioEvent) {
        event.clipPath?.let { File(it).delete() }
        eventDao.delete(event.id)
    }

    /** 自動分類の結果を反映する。ユーザーが直した種別は上書きしない。 */
    suspend fun updateAutoType(id: Long, type: EventType, score: Float) = eventDao.updateAutoType(id, type, score)

    /** 無呼吸の候補を保存する。音声は 1 晩 MAX_APNEA_CLIPS_PER_SESSION 件まで、無音の長いものを残す。 */
    suspend fun saveApnea(candidate: ApneaCandidate) {
        apneaDao.insert(candidate)
        val over = apneaDao.clipsOverLimit(candidate.sessionId, Thresholds.MAX_APNEA_CLIPS_PER_SESSION)
        if (over.isEmpty()) return
        over.forEach { it.clipPath?.let(::File)?.delete() }
        apneaDao.clearClipPaths(over.map { it.id })
    }

    suspend fun apneaCandidates(id: Long): List<ApneaCandidate> = apneaDao.forSession(id)

    /** ユーザーによる再分類(FR-4.8)。 */
    suspend fun setUserType(id: Long, type: EventType) = eventDao.updateUserType(id, type)

    suspend fun eventCount(id: Long): Int = eventDao.count(id)

    suspend fun events(id: Long): List<AudioEvent> = eventDao.forSession(id)

    /**
     * 保持期間(AUDIO_RETENTION_DAYS)を過ぎた音声を削除する。対象はクリップと全録音の WAV。
     * イベントの行、音量、スコアなどは消さない。起動時とセッション終了時に呼ぶ。
     */
    suspend fun expireOldAudio(now: Long) {
        val cutoff = now - Thresholds.AUDIO_RETENTION_DAYS * 24 * 60 * 60 * 1000
        deleteClips(eventDao.clipsOlderThan(cutoff))
        apneaDao.clipsOlderThan(cutoff).let { old ->
            if (old.isNotEmpty()) {
                old.forEach { it.clipPath?.let(::File)?.delete() }
                apneaDao.clearClipPaths(old.map { it.id })
            }
        }
        dao.finishedBefore(cutoff).forEach { File(it.wavPath).delete() }
    }

    private suspend fun deleteClips(events: List<AudioEvent>) {
        if (events.isEmpty()) return
        events.forEach { it.clipPath?.let(::File)?.delete() }
        eventDao.clearClipPaths(events.map { it.id })
    }

    /** 睡眠前のメモとタグ(FR-2.10)を保存する。タグは、検証済み(20 文字以内・重複なし)のものを渡す。 */
    suspend fun saveMemoAndTags(id: Long, memo: String?, tags: List<String>) {
        dao.get(id)?.let { dao.update(it.copy(memo = memo?.takeIf { m -> m.isNotBlank() })) }
        tagDao.insertAll(tags.map { SessionTag(id, it) })
    }

    suspend fun tags(id: Long): List<String> = tagDao.forSession(id)

    suspend fun recentTags(limit: Int): List<String> = tagDao.recent(limit)

    /** 一時停止の開始を記録して、その id を返す。再開・終了時に endPause で閉じる。 */
    suspend fun beginPause(sessionId: Long, startedAt: Long, reason: PauseReason): Long =
        pauseDao.insert(SessionPause(sessionId = sessionId, startedAt = startedAt, reason = reason))

    suspend fun endPause(pauseId: Long, endedAt: Long) = pauseDao.end(pauseId, endedAt)

    suspend fun pauses(sessionId: Long): List<SessionPause> = pauseDao.forSession(sessionId)

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
        apneaDao.clipPaths(s.id).forEach { File(it).delete() }
        eventDao.clipPaths(s.id).forEach { File(it).delete() } // 行は cascade で消えるが、ファイルは自分で消す
        dao.delete(s)
    }
}
