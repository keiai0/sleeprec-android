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
import androidx.compose.runtime.collectAsState
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
import io.github.keiai0.sleeprec.data.SessionPause
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

/** 「23:55」のような、秒なしの時刻。 */
private fun formatClockShort(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

private fun formatTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(ms))

/** 記録一覧。1 件 = 1 枚のカード(日時・状態のラベル・計測時間・音声イベント数)。新しい順。 */
@Composable
fun SessionListScreen(onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val store = remember { SessionStore.create(context) }
    var rows by remember { mutableStateOf<List<Pair<Session, Int>>?>(null) }

    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) { store.finishedSessions().map { it to store.eventCount(it.id) } }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
        PageTitle(stringResource(R.string.list_title), Modifier.padding(top = Spacing.screen, bottom = 12.dp))
        val list = rows
        when {
            list == null -> {}
            list.isEmpty() -> MutedText(stringResource(R.string.list_empty))
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.screen),
            ) {
                items(list, key = { it.first.id }) { (s, count) ->
                    SectionCard(onClick = { onOpen(s.id) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDateTime(s.startedAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            Pill(stringResource(statusLabel(s.status)), statusTone(s.status))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MutedText(stringResource(R.string.list_row_duration, formatDurationJa(sessionEndMs(s))))
                            MutedText(stringResource(R.string.list_row_events, count))
                        }
                    }
                }
            }
        }
    }
}

private fun statusTone(status: SessionStatus): Tone = when (status) {
    SessionStatus.COMPLETED -> Tone.GOOD
    SessionStatus.SHORT_SLEEP -> Tone.NEUTRAL
    SessionStatus.INTERRUPTED -> Tone.WARN
    SessionStatus.RECORDING -> Tone.INFO
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
    var previousNights by remember { mutableStateOf<List<Session>>(emptyList()) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var pauses by remember { mutableStateOf<List<SessionPause>>(emptyList()) }
    var editingMood by remember { mutableStateOf(false) }
    var deletingSession by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AudioEvent?>(null) }
    var retyping by remember { mutableStateOf<AudioEvent?>(null) }

    suspend fun load() = withContext(Dispatchers.IO) {
        session = store.session(sessionId)
        samples = store.loudness(sessionId)
        events = store.events(sessionId)
        apneas = store.apneaCandidates(sessionId)
        tags = store.tags(sessionId)
        pauses = store.pauses(sessionId)
        previousNights = session?.let { store.recentCompleted(it.startedAt, Thresholds.CONSISTENCY_NIGHTS - 1) } ?: emptyList()
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

    // 睡眠の推定・スコア。音イベントから計算するので、イベントの種別を直すと結果も変わる
    val analysis = remember(s, events, pauses) {
        SleepAnalyzer.analyze(s.startedAt, s.endedAt ?: s.lastAliveAt, events, pauses.map { it.startedAt to it.endedAt })
    }
    val goalMs = AppSettings.state.collectAsState().value.goalSleepMs
    val score = remember(analysis, previousNights, s.mood, goalMs) {
        val nights = (previousNights + s).filter { it.status == SessionStatus.COMPLETED }
            .map { NightTimes(it.startedAt, it.endedAt ?: it.lastAliveAt) }
        SleepScore.compute(s.status, analysis.metrics, nights, mood = s.mood, goalMs = goalMs)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            // 計測中の記録は消せない(計測画面から終了する)
            if (s.status != SessionStatus.RECORDING) {
                TextButton(onClick = { deletingSession = true }) {
                    Text(stringResource(R.string.session_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        // 1 つのまとまり = 1 枚のカード。全体を 1 つのリストにして、画面全体をスクロールできるようにする
        val snore = SnoreSummary.of(events)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.cards),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.screen),
        ) {
            // 1. この夜の記録
            item {
                SectionCard(
                    title = formatDateTime(s.startedAt),
                    trailing = { Pill(stringResource(statusLabel(s.status)), statusTone(s.status)) },
                ) {
                    InfoRow(stringResource(R.string.overview_duration), formatDurationJa(sessionEndMs(s)))
                    // 起床時の気分(FR-2.11)。保存された記録(短時間睡眠を含む)だけ、入力・変更できる
                    if (s.status == SessionStatus.COMPLETED || s.status == SessionStatus.SHORT_SLEEP) {
                        val mood = Mood.of(s.mood)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.overview_mood), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(
                                if (mood != null) "${mood.emoji} ${stringResource(mood.labelRes)}" else stringResource(R.string.overview_none),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TextButton(onClick = { editingMood = true }) { Text(stringResource(if (mood != null) R.string.mood_change else R.string.mood_enter)) }
                        }
                    }
                    if (tags.isNotEmpty()) InfoRow(stringResource(R.string.overview_tags), tags.joinToString("、"))
                    s.memo?.let { memo ->
                        Column {
                            MutedText(stringResource(R.string.overview_memo))
                            Text(memo, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (pauses.isNotEmpty()) {
                        val end = s.endedAt ?: s.lastAliveAt
                        val pausedMs = pauses.sumOf { (it.endedAt ?: end) - it.startedAt }
                        InfoRow(stringResource(R.string.overview_pauses), stringResource(R.string.overview_pauses_value, pauses.size, formatDurationJa(pausedMs)))
                    }
                }
            }
            // 2. 睡眠スコア
            item { ScoreCard(score) }
            // 3. 睡眠の指標
            item { MetricsCard(analysis) }
            // 4. タイムライン(睡眠曲線・音量・音のマーカーを、同じ時間軸に重ねる)
            item {
                SectionCard(title = stringResource(R.string.timeline_title)) {
                    val targets = remember(events, apneas, s) { ClipPlayback.targets(events, apneas, s.startedAt) }
                    SleepTimeline(
                        stages = analysis.stages,
                        samples = samples,
                        events = events,
                        apneas = apneas,
                        pauses = pauses.map { it.startedAt to it.endedAt },
                        sessionStartedAt = s.startedAt,
                        totalMs = totalMs,
                        targets = targets,
                        playingId = player.currentId,
                        playPositionMs = player.positionMs,
                        onTap = { t -> player.play(t.id, t.path, t.maxDb) },
                    )
                    ExpandableNote(stringResource(R.string.timeline_hint_summary), stringResource(R.string.graph_hint))
                }
            }
            // 5. いびき
            snore?.let { item { SnoreCard(it) } }
            // 6. 無呼吸の目安
            if (snore != null || apneas.isNotEmpty()) {
                item { ApneaCard(ApneaSummary.of(apneas, sessionEndMs(s)), apneas, player) }
            }
            // 7. 音声イベント
            item {
                SectionCard(title = stringResource(R.string.clips_title, events.size)) {
                    if (events.isEmpty()) MutedText(stringResource(R.string.clips_empty))
                    events.forEachIndexed { i, e ->
                        if (i > 0) CardDivider()
                        ClipRow(e, s.startedAt, player, onDelete = { deleting = e }, onRetype = { retyping = e }, analyze = analyze)
                    }
                }
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

    if (deletingSession) {
        AlertDialog(
            onDismissRequest = { deletingSession = false },
            title = { Text(stringResource(R.string.session_delete_title)) },
            text = { Text(stringResource(R.string.session_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingSession = false
                    player.stop()
                    scope.launch {
                        withContext(Dispatchers.IO) { store.deleteSession(sessionId) }
                        onBack()
                    }
                }) { Text(stringResource(R.string.session_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingSession = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (editingMood) {
        MoodDialog(
            current = session?.mood,
            onSelect = { mood ->
                editingMood = false
                scope.launch {
                    withContext(Dispatchers.IO) { store.setMood(sessionId, mood) }
                    load()
                }
            },
            onDismiss = { editingMood = false },
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
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(e.startedAt), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(end = 8.dp))
            // 種別を状態のラベルで示す。いびきは強度、修正した種別は「修正済み」を添える
            Pill(
                stringResource(e.type.labelRes) +
                    (if (e.type == EventType.SNORING) "・${stringResource(SnoreLevel.of(e.maxDb).labelRes)}" else "") +
                    (if (e.typeCorrected) "・${stringResource(R.string.type_corrected_short)}" else ""),
                if (e.type == EventType.SNORING) Tone.GOOD else Tone.NEUTRAL,
            )
        }
        MutedText(stringResource(R.string.clip_detail, formatDurationJa(e.durationMs), e.maxDb))
        if (!hasAudio) MutedText(stringResource(R.string.audio_deleted))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (hasAudio) {
                TextButton(onClick = { if (current) player.togglePause() else player.play(e) }) {
                    Text(stringResource(if (current && player.isPlaying) R.string.pause else R.string.play))
                }
            }
            TextButton(onClick = onRetype) { Text(stringResource(R.string.change_type)) }
            // 保存・共有・削除(デバッグビルドでは分析も)は、「操作」メニューにまとめる
            ClipMenu(
                path = e.clipPath,
                fileName = ClipExport.fileName(e.startedAt, e.type.name.lowercase()),
                onDelete = onDelete,
                onAnalyze = analyze?.let { run -> { analysis = "…"; scope.launch { analysis = run(e) } } },
            )
        }
        analysis?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (current && e.clipPath != null) PlaybackPanel(player, e.clipPath)
    }
}

/** いびき(FR-4.5)。回数・時間・音量と、強度ごとの回数。強度は端末のマイクの感度による目安。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SnoreCard(snore: SnoreSummary) {
    SectionCard(title = stringResource(R.string.snore_title)) {
        InfoRow(stringResource(R.string.snore_count_label), stringResource(R.string.metric_times, snore.count))
        InfoRow(stringResource(R.string.snore_total_label), formatDurationJa(snore.totalMs))
        InfoRow(stringResource(R.string.snore_db_label), "%.1f / %.1f dBFS".format(snore.maxDb, snore.avgDb))
        Text(stringResource(R.string.snore_level_title), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SnoreLevel.entries.forEach { lv ->
                val n = snore.levelCounts.getValue(lv)
                Pill("${stringResource(lv.labelRes)} $n", if (lv == snore.maxLevel) Tone.GOOD else Tone.INFO)
            }
        }
        MutedText(stringResource(R.string.snore_times_line, snore.times.take(8).joinToString("  ") { formatClockShort(it) } + if (snore.times.size > 8) " …" else ""))
        ExpandableNote(stringResource(R.string.snore_note_summary), stringResource(R.string.snore_note_detail))
    }
}

/** 無呼吸の目安(FR-4.6)。診断ではないことを、常に見える 1 行で示し、詳しい説明は「詳しく」に置く。候補のクリップも、このカードの中に並べる。 */
@Composable
private fun ApneaCard(a: ApneaSummary, candidates: List<ApneaCandidate>, player: ClipPlayer) {
    SectionCard(
        title = stringResource(R.string.apnea_title),
        trailing = {
            val level = a.level
            if (level != null) Pill(stringResource(levelLabel(level)), if (a.showRiskNotice) Tone.WARN else if (level == ApneaLevel.NORMAL) Tone.GOOD else Tone.INFO)
        },
    ) {
        if (a.count == 0) {
            MutedText(stringResource(R.string.apnea_none))
        } else {
            InfoRow(stringResource(R.string.apnea_count_label), stringResource(R.string.metric_times, a.count))
            InfoRow(stringResource(R.string.apnea_silence_label), "${formatDurationJa(a.maxSilenceMs)} / ${formatDurationJa(a.totalSilenceMs)}")
            val perHour = a.perHour
            if (perHour != null) InfoRow(stringResource(R.string.apnea_rate_label), stringResource(R.string.apnea_rate_value, perHour))
            else MutedText(stringResource(R.string.apnea_short_session))
        }
        if (a.showRiskNotice) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.apnea_risk_pill), Tone.WARN)
                Text(stringResource(R.string.apnea_risk), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        ExpandableNote(stringResource(R.string.apnea_disclaimer_summary), stringResource(R.string.apnea_disclaimer))
        if (candidates.isNotEmpty()) {
            CardDivider()
            Text(stringResource(R.string.apnea_clips_title, candidates.size), style = MaterialTheme.typography.titleSmall)
            candidates.forEachIndexed { i, c ->
                if (i > 0) CardDivider()
                ApneaRow(c, player)
            }
        }
    }
}

private fun levelLabel(level: ApneaLevel): Int = when (level) {
    ApneaLevel.NORMAL -> R.string.level_normal
    ApneaLevel.MILD -> R.string.level_mild
    ApneaLevel.MODERATE -> R.string.level_moderate
    ApneaLevel.SEVERE -> R.string.level_severe
}

/** 無呼吸の候補 1 件。前後を含むクリップを再生・保存・共有できる。 */
@Composable
private fun ApneaRow(c: ApneaCandidate, player: ClipPlayer) {
    val playId = -c.id // イベントの id と重ならないよう負の値にする
    val current = player.currentId == playId
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatClockShort(c.startedAt), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Pill(stringResource(R.string.apnea_row_pill, formatDurationJa(c.silenceMs)), Tone.WARN)
        }
        if (c.clipPath == null) {
            MutedText(stringResource(R.string.audio_deleted))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (current) player.togglePause() else player.play(playId, c.clipPath, c.maxDb) }) {
                    Text(stringResource(if (current && player.isPlaying) R.string.pause else R.string.play))
                }
                ClipMenu(path = c.clipPath, fileName = ClipExport.fileName(c.startedAt, "apnea"), onDelete = null)
            }
        }
        if (current && c.clipPath != null) PlaybackPanel(player, c.clipPath)
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
