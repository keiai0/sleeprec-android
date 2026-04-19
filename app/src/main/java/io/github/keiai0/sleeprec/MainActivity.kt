package io.github.keiai0.sleeprec

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.keiai0.sleeprec.data.AppDatabase
import io.github.keiai0.sleeprec.data.PauseReason
import io.github.keiai0.sleeprec.data.Session
import io.github.keiai0.sleeprec.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    companion object {
        // 通知の「停止」ボタンから開かれたことを示す印
        const val EXTRA_REQUEST_STOP = "request_stop"
    }

    // Compose の「状態」。値が変わると、これを読んでいる画面が自動で描き直される
    private var stopRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionPolicy.configure(this)
        stopRequested = intent?.getBooleanExtra(EXTRA_REQUEST_STOP, false) == true
        ScreenModeState.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            SleepRecTheme {
                SleepRecApp(
                    stopRequested = stopRequested,
                    onStopRequestConsumed = {
                        stopRequested = false
                        intent?.removeExtra(EXTRA_REQUEST_STOP) // 画面の作り直しで、ダイアログが再表示されないように
                    },
                )
            }
        }
    }

    // launchMode="singleTop" により、既に開いている画面が通知から再度開かれると、ここが呼ばれる
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_STOP, false)) stopRequested = true
    }
}

private enum class PermissionUi { None, Denied, PermanentlyDenied }

@Composable
internal fun RecorderScreen(stopRequested: Boolean, onStopRequestConsumed: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore.create(context) }

    val isRecording by RecordingState.isRecording.collectAsState()
    val pauseReason by RecordingState.pauseReason.collectAsState()
    val screenMode by ScreenModeState.mode.collectAsState()
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    var permissionUi by remember { mutableStateOf(PermissionUi.None) }
    // 終了ダイアログ: null なら非表示。値は、ダイアログを開いた時点の経過時間(ミリ秒)
    var stopElapsedMs by remember { mutableStateOf<Long?>(null) }
    var stopSessionId by remember { mutableStateOf<Long?>(null) }   // 終了しようとしている計測の id
    var moodSessionId by remember { mutableStateOf<Long?>(null) }   // 気分を尋ねる記録の id(保存して終了した後)
    // 案内待ちの中断セッション
    var interrupted by remember { mutableStateOf<List<Session>>(emptyList()) }

    // 計測中の経過時間の表示用。開始時刻は DB のセッションから取る
    // (画面を開き直したり、プロセスが作り直されたりしても、正しい経過時間が出る)
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isRecording) {
        startedAt = if (isRecording) withContext(Dispatchers.IO) { store.activeSession()?.startedAt } else null
    }
    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) { // startedAt が変わる/画面が消えると、この effect ごとキャンセルされる
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    // 起動時: 前回の中断を判定し、未案内のものを取得する
    LaunchedEffect(Unit) {
        interrupted = withContext(Dispatchers.IO) {
            // サービスが今動いているなら、計測中のセッションは正常なので触らない
            if (!RecordingState.isRecording.value) {
                InterruptionDetector.markStale(context, store, force = false)
            }
            store.expireOldAudio(System.currentTimeMillis()) // 保持期間を過ぎた音声を削除
            store.unacknowledgedInterrupted()
        }
    }

    fun requestStop() {
        scope.launch {
            val active = withContext(Dispatchers.IO) { store.activeSession() } ?: return@launch
            stopSessionId = active.id
            stopElapsedMs = System.currentTimeMillis() - active.startedAt
        }
    }

    // 通知の「停止」ボタンから開かれたときも、同じダイアログを出す
    LaunchedEffect(stopRequested) {
        if (stopRequested) {
            requestStop()
            onStopRequestConsumed()
        }
    }

    // 開始前に入力した睡眠前のメモ・タグ(FR-2.10)。開始したら、次の計測のために空に戻す
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var memo by remember { mutableStateOf("") }
    var recentTags by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            tags = emptyList()
            memo = ""
        } else {
            recentTags = withContext(Dispatchers.IO) { store.recentTags(8) }
        }
    }
    val deviceStatus = rememberDeviceStatus()

    fun startService() {
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_TAGS, tags.toTypedArray())
            .putExtra(RecordingService.EXTRA_MEMO, memo)
        // フォアグラウンドサービスは startForegroundService() で起動する。
        // 起動後 5 秒以内にサービス側が startForeground() を呼ぶ約束。
        ContextCompat.startForegroundService(context, intent)
    }

    // 一時停止 / 再開(夜中に起きたときなど)。サービスが、マイクの解放・再開と、記録の管理を行う
    fun togglePause() {
        val action = if (RecordingState.pauseReason.value != null) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE
        context.startService(Intent(context, RecordingService::class.java).setAction(action))
    }

    fun finishService(save: Boolean) {
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_FINISH)
            .putExtra(RecordingService.EXTRA_SAVE, save)
        context.startService(intent)
        // 保存して終了したときは、起床時の気分を尋ねる(FR-2.11)。破棄・10 分未満で保存されない場合は尋ねない
        val elapsed = stopElapsedMs
        if (save && elapsed != null && SessionPolicy.classify(elapsed) != SessionPolicy.Outcome.NOT_SAVED) {
            moodSessionId = stopSessionId
        }
        stopElapsedMs = null
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            permissionUi = PermissionUi.None
            // 通知権限が拒否されても録音は動く(通知がシェードに出ないだけ)ので、そのまま開始する
            startService()
        } else {
            // 拒否後に「説明を出すべき」がfalseなら、「今後表示しない」相当で永続拒否とみなす
            val canAskAgain = activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            permissionUi = if (canAskAgain) PermissionUi.Denied else PermissionUi.PermanentlyDenied
        }
    }

    fun onStartClicked() {
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            startService()
        } else {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(perms.toTypedArray())
        }
    }

    if (isRecording) {
        val elapsed = startedAt?.let { nowMs - it } ?: 0L
        if (screenMode == ScreenMode.AUTO_LOCK) {
            // 自動ロック: 通常の画面。画面は端末の設定どおりに消える(動作は通知の経過時間でも確認できる)
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (pauseReason != null) R.string.state_paused else R.string.state_recording),
                    style = MaterialTheme.typography.headlineMedium,
                )
                startedAt?.let { Text(text = formatClock(elapsed), style = MaterialTheme.typography.displayMedium) }
                when (pauseReason) {
                    PauseReason.USER -> Text(stringResource(R.string.paused_note), style = MaterialTheme.typography.bodyMedium)
                    PauseReason.MIC_BUSY -> Text(stringResource(R.string.mic_busy_note), style = MaterialTheme.typography.bodyMedium)
                    null -> {}
                }
                if (debuggable) DebugPanel()
                // マイクを他のアプリに取られている間は、自動で再開するので、ボタンは出さない
                if (pauseReason != PauseReason.MIC_BUSY) {
                    OutlinedButton(onClick = ::togglePause) {
                        Text(stringResource(if (pauseReason != null) R.string.action_resume else R.string.action_pause))
                    }
                }
                LongPressButton(stringResource(R.string.hold_to_finish), MaterialTheme.colorScheme.primary, onComplete = ::requestStop)
            }
        } else {
            DarkMeasuringScreen(screenMode, elapsed, pauseReason, onTogglePause = ::togglePause, onFinishHold = ::requestStop)
        }
    } else {
        // 停止中: 確認事項とタグをスクロールで見られるようにし、開始ボタンは常に下に出しておく
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                HomeHeader()
                ScreenModeSelector(screenMode) { ScreenModeState.set(context, it) }
                PreflightChecklist(deviceStatus)
                TagEditor(tags, { tags = it }, memo, { memo = it }, recentTags)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (permissionUi) {
                    PermissionUi.None -> {}
                    PermissionUi.Denied -> Text(stringResource(R.string.permission_denied), modifier = Modifier.padding(bottom = 8.dp))
                    PermissionUi.PermanentlyDenied -> {
                        Text(stringResource(R.string.permission_permanently_denied))
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        }) { Text(stringResource(R.string.open_settings)) }
                    }
                }
                Button(onClick = ::onStartClicked, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.button_start))
                }
            }
        }
    }

    stopElapsedMs?.let { elapsed ->
        StopDialog(
            elapsedMs = elapsed,
            onFinish = ::finishService,
            onContinue = { stopElapsedMs = null },
        )
    }

    moodSessionId?.let { id ->
        MoodDialog(
            current = null,
            onSelect = { mood ->
                moodSessionId = null
                scope.launch(Dispatchers.IO) { store.setMood(id, mood) }
            },
            onDismiss = { moodSessionId = null },
        )
    }

    // 中断は 1 件ずつ案内する。閉じたら「案内済み」にして、次の 1 件へ
    interrupted.firstOrNull()?.let { session ->
        InterruptionDialog(session) {
            interrupted = interrupted - session
            scope.launch(Dispatchers.IO) { store.acknowledge(session.id) }
        }
    }
}

/** 計測中の画面モード(FR-2.3)。開始前に選ぶ。 */
@Composable
private fun ScreenModeSelector(selected: ScreenMode, onSelect: (ScreenMode) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleMedium)
        ScreenMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                Column(Modifier.padding(top = 12.dp)) {
                    Text(stringResource(mode.titleRes), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(mode.descriptionRes), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** ホームの見出し: 今の時刻と、目標睡眠時間で寝た場合の起床時刻の目安。 */
@Composable
private fun HomeHeader() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val goalMin = (Thresholds.GOAL_SLEEP_MS / 60_000).toInt()
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.state_stopped), style = MaterialTheme.typography.titleMedium)
        Text(fmt.format(Date(now)), style = MaterialTheme.typography.displayMedium)
        Text(
            stringResource(R.string.home_wake_hint, goalMin / 60, goalMin % 60, fmt.format(Date(now + Thresholds.GOAL_SLEEP_MS))),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 閾値調整用(デバッグビルドのみ)。現在の dB、イベントの開始/終了の閾値、検出したイベント数を出す。 */
@Composable
private fun DebugPanel() {
    val db by RecordingState.currentDb.collectAsState()
    val count by RecordingState.eventCount.collectAsState()
    val active = db >= Thresholds.EVENT_START_DB
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.debug_current_db, db), style = MaterialTheme.typography.titleLarge)
        // 0.1 秒ごとの値が閾値を超えているか(超えていればイベント開始の条件を満たす)
        Text(stringResource(if (active) R.string.debug_above else R.string.debug_below))
        Text(stringResource(R.string.debug_thresholds, Thresholds.EVENT_START_DB, Thresholds.EVENT_END_DB))
        Text(stringResource(R.string.debug_event_count, count))
    }
}

/** FR-2.8: Discard / Short Sleep として保存 / 計測継続。経過時間に応じて選択肢が変わる。 */
@Composable
private fun StopDialog(elapsedMs: Long, onFinish: (save: Boolean) -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val outcome = SessionPolicy.classify(elapsedMs)
    val message = when (outcome) {
        SessionPolicy.Outcome.NOT_SAVED -> R.string.stop_msg_not_saved
        SessionPolicy.Outcome.SHORT_SLEEP -> R.string.stop_msg_short
        SessionPolicy.Outcome.NORMAL -> R.string.stop_msg_normal
    }
    AlertDialog(
        onDismissRequest = onContinue, // 外側タップ・戻るボタンは「計測を続ける」と同じ(誤操作で止めない)
        title = { Text(stringResource(R.string.stop_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.stop_dialog_elapsed, formatDuration(context, elapsedMs)))
                Text(stringResource(message))
            }
        },
        // ボタンは 3 つ必要なので、confirmButton の枠に縦に並べる
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (outcome != SessionPolicy.Outcome.NOT_SAVED) {
                    TextButton(onClick = { onFinish(true) }) {
                        Text(
                            stringResource(
                                if (outcome == SessionPolicy.Outcome.SHORT_SLEEP) R.string.action_save_short else R.string.action_save
                            )
                        )
                    }
                }
                TextButton(onClick = { onFinish(false) }) { Text(stringResource(R.string.action_discard)) }
                TextButton(onClick = onContinue) { Text(stringResource(R.string.action_continue)) }
            }
        },
    )
}

/** FR-2.9: 中断とその原因、データが不正確な旨を案内する。 */
@Composable
private fun InterruptionDialog(session: Session, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val recordedMs = (session.endedAt ?: session.lastAliveAt) - session.startedAt
    val started = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startedAt))
    val reason = session.interruptReason?.let { context.getString(it.messageRes) }
        ?: context.getString(R.string.reason_unknown)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.interrupted_title)) },
        text = { Text(stringResource(R.string.interrupted_body, started, formatDuration(context, recordedMs), reason)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** 経過時間を「時:分:秒」で表示する(例 03:12:45)。 */
private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60)
}

private fun formatDuration(context: Context, ms: Long): String {
    val totalMinutes = (ms / 60_000L).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) context.getString(R.string.duration_hm, h, m) else context.getString(R.string.duration_m, m)
}
