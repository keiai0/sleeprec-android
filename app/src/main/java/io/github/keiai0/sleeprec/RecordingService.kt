package io.github.keiai0.sleeprec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.keiai0.sleeprec.data.AppDatabase
import io.github.keiai0.sleeprec.data.InterruptReason
import io.github.keiai0.sleeprec.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 録音を「画面(Activity)の外」で生かし続けるためのフォアグラウンドサービス。
 * 常時通知を出している間、OS はこのプロセスを簡単には殺さない。
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "io.github.keiai0.sleeprec.START"
        const val ACTION_FINISH = "io.github.keiai0.sleeprec.FINISH"
        const val EXTRA_SAVE = "save"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "RecordingService"
        private const val WAKE_LOCK_TIMEOUT_MS = 14L * 60 * 60 * 1000 // 念のための上限 14 時間
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    private lateinit var store: SessionStore
    // DB 更新などの裏方の仕事用。Service が破棄されるとき cancel() する(Go の context.Cancel に近い)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recorder: WavRecorder? = null
    private var sessionId: Long? = null
    private var heartbeatJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // finishRecording() を通った(=ユーザーが終了を選んだ)か。false のまま破棄されたら「中断」
    private var finished = false
    @Volatile private var recorderError: Throwable? = null

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(AppDatabase.get(this).sessionDao())
    }

    // bind しない(Activity から直接メソッドを呼ばない)ので null。状態共有は RecordingState で行う。
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_FINISH -> finishRecording(save = intent.getBooleanExtra(EXTRA_SAVE, true))
        }
        // kill されても自動再起動しない。Android 14 ではバックグラウンドからの
        // マイク系サービス起動が禁止されており、再起動しても失敗するため。
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return // 二重開始防止

        val startedAt = System.currentTimeMillis()

        // startForegroundService() を受けたら 5 秒以内に startForeground() を呼ぶ決まり。最初にやる。
        try {
            createChannel()
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(startedAt),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: Exception) {
            // RECORD_AUDIO 未許可だと Android 14 以降は SecurityException、
            // バックグラウンド起動だと ForegroundServiceStartNotAllowedException が投げられる
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return
        }

        // 前回の計測が「計測中」のまま残っていれば、中断として記録する。
        // サービスは 1 つだけなので、起動時点で残っている RECORDING は必ず前回の残骸。
        runBlocking(Dispatchers.IO) { InterruptionDetector.markStale(applicationContext, store, force = true) }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startedAt))
        val file = File(getExternalFilesDir(null), "recordings/rec_$name.wav")

        val rec = WavRecorder(file) { error ->
            // 録音スレッドが終わった。エラー由来ならサービスごと止める(onDestroy で中断として記録される)
            if (error != null) {
                recorderError = error
                stopSelf()
            }
        }
        try {
            rec.start()
        } catch (e: Exception) {
            Log.e(TAG, "recorder start failed", e)
            stopSelf()
            return
        }
        recorder = rec

        // DB への書き込みは Room の決まりでメインスレッドでは行えない。
        // 一瞬で終わるので、IO スレッドで実行してここで結果を待つ。
        val id = runBlocking(Dispatchers.IO) { store.start(startedAt, file.absolutePath) }
        sessionId = id
        startHeartbeat(id)

        // 画面オフ+長時間でも CPU を眠らせない
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepRec:recording")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }

        RecordingState.set(true)
    }

    // 約 30 秒ごとに「まだ生きている」印を DB に残す。プロセスごと消えたとき、
    // 次回起動時に「最後に生きていた時刻」から中断を判定できる。
    private fun startHeartbeat(id: Long) {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    store.heartbeat(id, System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.e(TAG, "heartbeat failed", e)
                }
            }
        }
    }

    /** ユーザーが選んだ「保存して終了 / 破棄して終了」を実行する。 */
    private fun finishRecording(save: Boolean) {
        val id = sessionId
        if (recorder == null || id == null) {
            stopSelf()
            return
        }
        recorder?.stop() // ヘッダ更新とファイルクローズが終わるまで待つ
        recorder = null
        heartbeatJob?.cancel()
        runBlocking(Dispatchers.IO) { store.finish(id, save, System.currentTimeMillis()) }
        finished = true
        stopSelf() // → onDestroy() で後片付け
    }

    override fun onDestroy() {
        recorder?.stop()
        heartbeatJob?.cancel()

        val id = sessionId
        if (id != null && !finished) {
            // ユーザー操作を経ずに破棄された = 中断
            val reason = if (recorderError != null) InterruptReason.RECORDER_ERROR else InterruptReason.SERVICE_STOPPED
            runBlocking(Dispatchers.IO) { store.markInterrupted(id, System.currentTimeMillis(), reason) }
        }

        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        RecordingState.set(false)
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        // IMPORTANCE_LOW: 音やヘッドアップ表示なし。寝室で鳴らさないため。
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(startedAtMillis: Long): Notification {
        // 通知タップ・「停止」ボタンは、どちらも画面(Activity)を開く。
        // 終了は保存/破棄の選択が要るので、画面側のダイアログで行う。
        val openApp = PendingIntent.getActivity(
            this, 0, activityIntent(requestStop = false),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getActivity(
            this, 1, activityIntent(requestStop = true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notification_title))
            // 経過時間: setWhen を起点に、システム側が毎秒カウントアップ表示する。
            // アプリが毎秒通知を更新する必要がなく、電池にも優しい。
            .setUsesChronometer(true)
            .setWhen(startedAtMillis)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    private fun activityIntent(requestStop: Boolean): Intent =
        Intent(this, MainActivity::class.java)
            // 既に開いている MainActivity があれば、それを前面に出す(新しく作らない)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_REQUEST_STOP, requestStop)
}
