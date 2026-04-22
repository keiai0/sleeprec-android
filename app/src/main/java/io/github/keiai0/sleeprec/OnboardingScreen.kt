package io.github.keiai0.sleeprec

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private const val STEPS = 5

/**
 * 初回の質問(FR-1)。必須の入力はなく、いつでも「あとで設定する」で飛ばせる。
 * 1 利用目的 → 2 就寝・起床時刻 → 3 権限の用途 → 4 プロフィール(任意) → 5 完了。
 * リマインダーの自動設定(FR-1.1)は、通知の予約が要るため、アラーム(FR-3)と一緒に拡充フェーズで対応する。
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var purposes by remember { mutableStateOf(AppSettings.state.value.purposes) }
    var bed by rememberSaveable { mutableIntStateOf(23 * 60) }
    var wake by rememberSaveable { mutableIntStateOf(7 * 60) }
    val span = SettingsRules.sleepSpanMinutes(bed, wake)
    val spanOk = SettingsRules.isValidSpan(span)

    fun finish(answered: Boolean) {
        AppSettings.update(context) { s ->
            if (!answered) s.copy(onboardingDone = true)
            else s.copy(
                onboardingDone = true, purposes = purposes, bedMinuteOfDay = bed, wakeMinuteOfDay = wake,
                // 就寝〜起床の時間を、目標睡眠時間の初期値にする(設定でいつでも変えられる)
                goalSleepMin = if (spanOk) SettingsRules.goalFromSpan(span) else s.goalSleepMin,
            )
        }
    }

    BackHandler(enabled = step > 0) { step-- }

    // 背景と文字色は、Surface が決める(Scaffold の外では、文字色が既定の黒になってしまう)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.screen, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ob_step, step + 1, STEPS), style = MaterialTheme.typography.labelLarge)
            if (step < STEPS - 1) TextButton(onClick = { finish(answered = false) }) { Text(stringResource(R.string.ob_skip)) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.cards)) {
            when (step) {
                0 -> PurposeStep(purposes) { purposes = it }
                1 -> TimeStep(bed, wake, span, spanOk, onBed = { bed = it }, onWake = { wake = it })
                2 -> PermissionStep()
                3 -> ProfileStep()
                else -> DoneStep()
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ob_back)) }
            val last = step == STEPS - 1
            Button(
                onClick = { if (last) finish(answered = true) else step++ },
                enabled = step != 1 || spanOk, // 睡眠時間が 1〜20 時間でないと、次へ進めない(FR-1.4)
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(if (last) R.string.ob_start else R.string.ob_next)) }
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurposeStep(selected: Set<Purpose>, onChange: (Set<Purpose>) -> Unit) {
    Text(stringResource(R.string.ob_welcome), style = MaterialTheme.typography.headlineMedium)
    MutedText(stringResource(R.string.ob_welcome_body))
    SectionCard(title = stringResource(R.string.ob_purpose_title)) {
        MutedText(stringResource(R.string.ob_purpose_hint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Purpose.entries.forEach { p ->
                FilterChip(
                    selected = p in selected,
                    onClick = { onChange(if (p in selected) selected - p else selected + p) },
                    label = { Text(stringResource(p.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun TimeStep(bed: Int, wake: Int, span: Int, spanOk: Boolean, onBed: (Int) -> Unit, onWake: (Int) -> Unit) {
    val context = LocalContext.current
    fun pick(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
    }
    Text(stringResource(R.string.ob_time_title), style = MaterialTheme.typography.headlineMedium)
    MutedText(stringResource(R.string.ob_time_body))
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ob_bedtime), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pick(bed, onBed) }) { Text(TimeMath.format(bed), style = MaterialTheme.typography.titleLarge) }
        }
        CardDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ob_waketime), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pick(wake, onWake) }) { Text(TimeMath.format(wake), style = MaterialTheme.typography.titleLarge) }
        }
        CardDivider()
        if (spanOk) {
            InfoRow(stringResource(R.string.ob_span_label), stringResource(R.string.duration_hm_long, span / 60, span % 60))
            MutedText(stringResource(R.string.ob_span_goal))
        } else {
            // 1〜20 時間でないときは、理由を示して、次へ進ませない
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.status_check), Tone.WARN)
                Text(stringResource(R.string.ob_span_invalid), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
    MutedText(stringResource(R.string.ob_reminder_note))
}

@Composable
private fun PermissionStep() {
    val status = rememberDeviceStatus()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    Text(stringResource(R.string.ob_perm_title), style = MaterialTheme.typography.headlineMedium)
    MutedText(stringResource(R.string.ob_perm_body))

    SectionCard {
        StatusRow(
            stringResource(R.string.pa_mic), if (status.micGranted) Tone.GOOD else Tone.INFO,
            stringResource(if (status.micGranted) R.string.status_ok else R.string.status_info),
            stringResource(R.string.ob_perm_mic),
        )
        CardDivider()
        StatusRow(
            stringResource(R.string.pa_notifications), if (status.notificationsGranted) Tone.GOOD else Tone.INFO,
            stringResource(if (status.notificationsGranted) R.string.status_ok else R.string.status_info),
            stringResource(R.string.ob_perm_notif),
        )
        CardDivider()
        MutedText(stringResource(R.string.ob_perm_storage))
    }

    if (status.micGranted && status.notificationsGranted) {
        Pill(stringResource(R.string.ob_perm_granted), Tone.GOOD)
    } else {
        Button(onClick = {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(perms.toTypedArray())
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ob_perm_request)) }
        MutedText(stringResource(R.string.ob_perm_later))
    }
}

@Composable
private fun ProfileStep() {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsState()
    Text(stringResource(R.string.ob_profile_title), style = MaterialTheme.typography.headlineMedium)
    MutedText(stringResource(R.string.ob_profile_body))
    SectionCard { ProfileEditor(settings) { f -> AppSettings.update(context, f) } }
}

@Composable
private fun DoneStep() {
    Text(stringResource(R.string.ob_done_title), style = MaterialTheme.typography.headlineMedium)
    SectionCard {
        Text(stringResource(R.string.ob_done_body), style = MaterialTheme.typography.bodyLarge)
        CardDivider()
        MutedText(stringResource(R.string.ob_done_privacy))
    }
}
