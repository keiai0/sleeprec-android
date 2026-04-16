package io.github.keiai0.sleeprec

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.keiai0.sleeprec.data.AudioEvent

/**
 * クリップを 1 つずつ再生する。状態は Compose の state なので、画面はそのまま読める。
 * 音が小さい録音は LoudnessEnhancer で持ち上げる(補正量は ClipPlayback.gainMillibels)。
 */
class ClipPlayer {
    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null

    var currentId by mutableStateOf<Long?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set

    /** 指定のクリップを最初から再生する。別のクリップを再生中なら止めて切り替える。 */
    fun play(event: AudioEvent) {
        val path = event.clipPath ?: return
        play(event.id, path, event.maxDb)
    }

    /** id は画面が「どれを再生中か」を見分けるための値(イベントと無呼吸の候補で重ならないようにする)。 */
    fun play(id: Long, path: String, maxDb: Float) {
        stop()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    // apply の中では this が MediaPlayer なので、ClipPlayer 側を明示する
                    this@ClipPlayer.isPlaying = false
                    this@ClipPlayer.positionMs = 0
                }
            }
            enhancer = try {
                LoudnessEnhancer(mp.audioSessionId).apply {
                    setTargetGain(ClipPlayback.gainMillibels(maxDb))
                    enabled = true
                }
            } catch (e: Exception) { // 端末が対応していなければ、補正なしで再生する
                Log.w("ClipPlayer", "LoudnessEnhancer unavailable", e)
                null
            }
            player = mp
            currentId = id
            durationMs = mp.duration
            mp.start()
            isPlaying = true
        } catch (e: Exception) {
            Log.e("ClipPlayer", "play failed: $path", e)
            stop()
        }
    }

    fun togglePause() {
        val mp = player ?: return
        if (mp.isPlaying) {
            mp.pause()
            isPlaying = false
        } else {
            mp.start()
            isPlaying = true
        }
    }

    fun seekTo(ms: Int) {
        player?.seekTo(ms)
        positionMs = ms
    }

    /** 再生位置を画面用の state に反映する。再生中に定期的に呼ぶ。 */
    fun tick() {
        val mp = player ?: return
        if (isPlaying) positionMs = mp.currentPosition
    }

    fun stop() {
        enhancer?.release()
        enhancer = null
        player?.release()
        player = null
        currentId = null
        isPlaying = false
        positionMs = 0
        durationMs = 0
    }
}
