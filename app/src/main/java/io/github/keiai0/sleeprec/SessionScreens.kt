package io.github.keiai0.sleeprec

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.keiai0.sleeprec.data.ApneaCandidate
import io.github.keiai0.sleeprec.data.AudioEvent
import io.github.keiai0.sleeprec.data.EventType
import io.github.keiai0.sleeprec.data.LoudnessSample
import io.github.keiai0.sleeprec.data.Session
import io.github.keiai0.sleeprec.data.SessionStatus
import io.github.keiai0.sleeprec.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun sessionEndMs(s: Session): Long = (s.endedAt ?: s.lastAliveAt) - s.startedAt

private fun statusLabel(status: SessionStatus): Int = when (status) {
    SessionStatus.COMPLETED -> R.string.status_completed
    SessionStatus.SHORT_SLEEP -> R.string.status_short
    SessionStatus.INTERRUPTED -> R.string.status_interrupted
    SessionStatus.RECORDING -> R.string.status_recording
}

/** 「1:05:30」「02:15」のような表示。長さ・位置に使う。 */
private fun formatMs(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return if (t >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", t / 3600, t % 3600 / 60, t % 60)
    else String.format(Locale.ROOT, "%02d:%02d", t / 60, t % 60)
}

private fun formatDateTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

private fun formatTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(ms))

/** セッション一覧(Journal の原型)。新しい順。開始日時、長さ、状態、イベント数を出す。 */
@Composable
fun SessionListScreen(onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SessionStore.create(context) }
    var rows by remember { mutableStateOf<List<Pair<Session, Int>>?>(null) }

    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) { store.finishedSessions().map { it to store.eventCount(it.id) } }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.list_title), style = MaterialTheme.typography.headlineSmall)
        val list = rows
        when {
            list == null -> {}
            list.isEmpty() -> Text(stringResource(R.string.list_empty), Modifier.padding(top = 16.dp))
            else -> LazyColumn {
                items(list, key = { it.first.id }) { (s, count) ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(vertical = 12.dp)) {
                        Text(formatDateTime(s.startedAt), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.list_row_detail,
                                formatMs(sessionEndMs(s)), stringResource(statusLabel(s.status)), count,
                            )
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * セッションの詳細。音量グラフ(タップで最も近いクリップを再生)と、クリップの一覧。
 * 音声が削除済みのイベントは、行だけ残して「音声は削除済み」と出す。
 */
@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SessionStore.create(context) }
    val scope = rememberCoroutineScope()
    val player = remember { ClipPlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    var session by remember { mutableStateOf<Session?>(null) }
    var samples by remember { mutableStateOf<List<LoudnessSample>>(emptyList()) }
    var events by remember { mutableStateOf<List<AudioEvent>>(emptyList()) }
    var apneas by remember { mutableStateOf<List<ApneaCandidate>>(emptyList()) }
    var deleting by remember { mutableStateOf<AudioEvent?>(null) }
    var retyping by remember { mutableStateOf<AudioEvent?>(null) }

    suspend fun load() = withContext(Dispatchers.IO) {
        session = store.session(sessionId)
        samples = store.loudness(sessionId)
        events = store.events(sessionId)
        apneas = store.apneaCandidates(sessionId)
    }
    LaunchedEffect(sessionId) { load() }

    // デバッグビルドのみ: クリップを YAMNet で分類して、上位のクラスと推論時間を出す(Phase 4 のスパイク)
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val classifier = remember { lazy { YamnetClassifier(context) } }
    DisposableEffect(Unit) { onDispose { if (classifier.isInitialized()) classifier.value.close() } }
    val analyze: (suspend (AudioEvent) -> String)? = if (!debuggable) null else { e ->
        withContext(Dispatchers.Default) {
            try {
                val yamnet = classifier.value
                val c = yamnet.classifyWav(File(e.clipPath!!).readBytes())
                val top = AudioWindows.topK(c.scores, 5).joinToString("\n") { (i, sc) -> "%s %.2f".format(yamnet.labels[i], sc) }
                // 分類し直して、種別を保存する(録音時に分類されなかった過去のクリップにも使える)
                val (type, score) = EventTypeMapper.decide(c.scores)
                withContext(Dispatchers.IO) { store.updateAutoType(e.id, type, score) }
                load()
                "$top\n→ %s %.2f\n%d 窓 / %d ms".format(context.getString(type.labelRes), score, c.windows, c.elapsedMs)
            } catch (ex: Throwable) {
                Log.e("Analyze", "failed", ex)
                "失敗: ${ex.message}"
            }
        }
    }

    // 再生中は、位置を 0.2 秒ごとに画面へ反映する
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            player.tick()
            delay(200)
        }
    }

    val s = session ?: return
    val totalMs = maxOf(sessionEndMs(s), (samples.maxOfOrNull { it.second } ?: 0) * 1000L + 1000L)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        // 見出し・グラフ・クリップ一覧を 1 つのリストにして、画面全体をスクロールできるようにする
        LazyColumn {
            item {
                Text(formatDateTime(s.startedAt), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.detail_summary, formatMs(sessionEndMs(s)), stringResource(statusLabel(s.status))),
                    Modifier.padding(bottom = 12.dp),
                )
                LoudnessGraph(
                    samples = samples,
                    events = events,
                    sessionStartedAt = s.startedAt,
                    totalMs = totalMs,
                    playingId = player.currentId,
                    playPositionMs = player.positionMs,
                    onTap = { tapMs -> ClipPlayback.nearestPlayable(events, s.startedAt, tapMs)?.let(player::play) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(s.startedAt), style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(s.startedAt + totalMs), style = MaterialTheme.typography.labelSmall)
                }
                Text(stringResource(R.string.graph_hint), style = MaterialTheme.typography.labelSmall)
                SnoreSummary.of(events)?.let { snore ->
                    Text(
                        stringResource(R.string.snore_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(stringResource(R.string.snore_detail, snore.count, formatMs(snore.totalMs), snore.maxDb, snore.avgDb))
                    Text(
                        stringResource(R.string.snore_times, snore.times.joinToString(" ") { formatTime(it).take(5) }),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (SnoreSummary.of(events) != null || apneas.isNotEmpty()) {
                    ApneaCard(ApneaSummary.of(apneas, sessionEndMs(s)))
                }
            }
            if (apneas.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.apnea_clips_title, apneas.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(apneas, key = { -it.id }) { c ->
                    ApneaRow(c, player)
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            item {
                Text(
                    stringResource(R.string.clips_title, events.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                if (events.isEmpty()) Text(stringResource(R.string.clips_empty))
            }
            items(events, key = { it.id }) { e ->
                ClipRow(e, s.startedAt, player, onDelete = { deleting = e }, onRetype = { retyping = e }, analyze = analyze)
                HorizontalDivider()
            }
        }
    }

    retyping?.let { e ->
        TypeDialog(
            current = e.type,
            onSelect = { type ->
                retyping = null
                scope.launch {
                    withContext(Dispatchers.IO) { store.setUserType(e.id, type) }
                    load()
                }
            },
            onDismiss = { retyping = null },
        )
    }

    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    if (player.currentId == e.id) player.stop()
                    scope.launch {
                        withContext(Dispatchers.IO) { store.deleteEvent(e) }
                        load()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ClipRow(
    e: AudioEvent, sessionStart: Long, player: ClipPlayer, onDelete: () -> Unit, onRetype: () -> Unit,
    analyze: (suspend (AudioEvent) -> String)?,
) {
    val scope = rememberCoroutineScope()
    var analysis by remember { mutableStateOf<String?>(null) }
    val hasAudio = e.clipPath != null
    val current = player.currentId == e.id
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "${formatTime(e.startedAt)}  ${stringResource(e.type.labelRes)}" +
                if (e.typeCorrected) stringResource(R.string.type_corrected_mark) else "",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(
                R.string.clip_detail,
                formatMs(e.durationMs), e.maxDb, formatMs(e.startedAt - sessionStart),
            )
        )
        if (!hasAudio) Text(stringResource(R.string.audio_deleted), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (hasAudio) {
                TextButton(onClick = { if (current) player.togglePause() else player.play(e) }) {
                    Text(stringResource(if (current && player.isPlaying) R.string.pause else R.string.play))
                }
            }
            if (analyze != null && hasAudio) {
                TextButton(onClick = {
                    analysis = "…"
                    scope.launch { analysis = analyze(e) }
                }) { Text(stringResource(R.string.debug_analyze)) }
            }
            TextButton(onClick = onRetype) { Text(stringResource(R.string.change_type)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
        analysis?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (current) {
            Slider(
                value = player.positionMs.toFloat(),
                onValueChange = { player.seekTo(it.toInt()) },
                valueRange = 0f..player.durationMs.coerceAtLeast(1).toFloat(),
            )
            Text(
                "${formatMs(player.positionMs.toLong())} / ${formatMs(player.durationMs.toLong())}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 無呼吸の目安(FR-4.6)。診断ではないことを、常に一緒に表示する。 */
@Composable
private fun ApneaCard(a: ApneaSummary) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(stringResource(R.string.apnea_title), style = MaterialTheme.typography.titleMedium)
        if (a.count == 0) {
            Text(stringResource(R.string.apnea_none))
        } else {
            Text(stringResource(R.string.apnea_count, a.count, formatMs(a.maxSilenceMs), formatMs(a.totalSilenceMs)))
        }
        val level = a.level
        val perHour = a.perHour
        if (level != null && perHour != null) {
            Text(stringResource(R.string.apnea_rate, perHour, stringResource(levelLabel(level))))
        } else if (a.count > 0) {
            Text(stringResource(R.string.apnea_short_session), style = MaterialTheme.typography.labelMedium)
        }
        if (a.showRiskNotice) {
            Text(
                stringResource(R.string.apnea_risk),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            stringResource(R.string.apnea_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun levelLabel(level: ApneaLevel): Int = when (level) {
    ApneaLevel.NORMAL -> R.string.level_normal
    ApneaLevel.MILD -> R.string.level_mild
    ApneaLevel.MODERATE -> R.string.level_moderate
    ApneaLevel.SEVERE -> R.string.level_severe
}

/** 無呼吸の候補 1 件。前後を含むクリップを再生できる。 */
@Composable
private fun ApneaRow(c: ApneaCandidate, player: ClipPlayer) {
    val playId = -c.id // イベントの id と重ならないよう負の値にする
    val current = player.currentId == playId
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(formatTime(c.startedAt), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.apnea_row_detail, formatMs(c.silenceMs)))
        if (c.clipPath == null) {
            Text(stringResource(R.string.audio_deleted), style = MaterialTheme.typography.labelMedium)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { if (current) player.togglePause() else player.play(playId, c.clipPath, c.maxDb) }) {
                    Text(stringResource(if (current && player.isPlaying) R.string.pause else R.string.play))
                }
            }
        }
        if (current) {
            Slider(
                value = player.positionMs.toFloat(),
                onValueChange = { player.seekTo(it.toInt()) },
                valueRange = 0f..player.durationMs.coerceAtLeast(1).toFloat(),
            )
            Text(
                "${formatMs(player.positionMs.toLong())} / ${formatMs(player.durationMs.toLong())}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** FR-4.8: 種別の選び直し。現在の種別に印を付けて、選ぶとすぐ保存する。 */
@Composable
private fun TypeDialog(current: EventType, onSelect: (EventType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_type_title)) },
        text = {
            Column {
                // 未分類は選ぶ対象ではない(自動分類が終わる前の状態)
                EventType.entries.filter { it != EventType.UNCLASSIFIED }.forEach { type ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(type) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == current, onClick = { onSelect(type) })
                        Text(stringResource(type.labelRes))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
