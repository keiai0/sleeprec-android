package io.github.keiai0.sleeprec

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
        setContent {
            MaterialTheme {
                RecorderScreen(
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
private fun RecorderScreen(stopRequested: Boolean, onStopRequestConsumed: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val store = remember { AppDatabase.get(context).let { SessionStore(it.sessionDao(), it.loudnessDao()) } }

    val isRecording by RecordingState.isRecording.collectAsState()
    var permissionUi by remember { mutableStateOf(PermissionUi.None) }
    // 終了ダイアログ: null なら非表示。値は、ダイアログを開いた時点の経過時間(ミリ秒)
    var stopElapsedMs by remember { mutableStateOf<Long?>(null) }
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
            store.unacknowledgedInterrupted()
        }
    }

    fun requestStop() {
        scope.launch {
            val active = withContext(Dispatchers.IO) { store.activeSession() } ?: return@launch
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

    fun startService() {
        val intent = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START)
        // フォアグラウンドサービスは startForegroundService() で起動する。
        // 起動後 5 秒以内にサービス側が startForeground() を呼ぶ約束。
        ContextCompat.startForegroundService(context, intent)
    }

    fun finishService(save: Boolean) {
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_FINISH)
            .putExtra(RecordingService.EXTRA_SAVE, save)
        context.startService(intent)
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (isRecording) R.string.state_recording else R.string.state_stopped),
            style = MaterialTheme.typography.headlineMedium,
        )

        startedAt?.let { start ->
            Text(text = formatClock(nowMs - start), style = MaterialTheme.typography.displayMedium)
        }

        if (isRecording) {
            Button(onClick = ::requestStop) { Text(stringResource(R.string.button_stop)) }
        } else {
            Button(onClick = ::onStartClicked) { Text(stringResource(R.string.button_start)) }
        }

        when (permissionUi) {
            PermissionUi.None -> {}
            PermissionUi.Denied -> Text(stringResource(R.string.permission_denied))
            PermissionUi.PermanentlyDenied -> {
                Text(stringResource(R.string.permission_permanently_denied))
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text(stringResource(R.string.open_settings)) }
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

    // 中断は 1 件ずつ案内する。閉じたら「案内済み」にして、次の 1 件へ
    interrupted.firstOrNull()?.let { session ->
        InterruptionDialog(session) {
            interrupted = interrupted - session
            scope.launch(Dispatchers.IO) { store.acknowledge(session.id) }
        }
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
