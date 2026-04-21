package io.github.keiai0.sleeprec

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.net.Uri

/** 開始前に確認する端末の状態(FR-2.4)。 */
data class DeviceStatus(
    val batteryPercent: Int,
    val charging: Boolean,
    val notificationsGranted: Boolean,
    val micGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
)

private fun readStatus(context: Context): DeviceStatus {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
    val charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return DeviceStatus(percent, charging, notifications, mic, pm.isIgnoringBatteryOptimizations(context.packageName))
}

/** 端末の状態。電池の変化と、設定画面から戻ってきたとき(再開時)に更新する。 */
@Composable
fun rememberDeviceStatus(): DeviceStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readStatus(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { status = readStatus(context) }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readStatus(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return status
}

/**
 * 開始前のチェックリスト(FR-2.4)。項目ごとに 1 行(名前・1 行の補足・状態のラベル)で、対処が要るものだけボタンを出す。
 * 警告が出ていても、計測は開始できる(ブロックしない)。状態は、色だけでなく、言葉でも示す。
 */
@Composable
fun PreflightChecklist(status: DeviceStatus, backgroundSetupDone: Boolean, onOpenAssistant: () -> Unit) {
    val context = LocalContext.current
    val ok = stringResource(R.string.status_ok)
    val check = stringResource(R.string.status_check)
    val info = stringResource(R.string.status_info)

    SectionCard(title = stringResource(R.string.preflight_title)) {
        // 電池(残量と充電)
        val battery = PreflightRules.battery(status.batteryPercent, status.charging)
        val warn = PreflightRules.batteryIsWarning(battery)
        StatusRow(
            title = stringResource(R.string.pf_battery_title, status.batteryPercent),
            tone = if (warn) Tone.WARN else if (status.charging) Tone.GOOD else Tone.INFO,
            statusText = if (warn) check else if (status.charging) ok else info,
            sub = stringResource(
                when (battery) {
                    PreflightRules.Battery.OK_CHARGING -> R.string.pf_battery_sub_charging
                    PreflightRules.Battery.OK -> R.string.pf_battery_sub_ok
                    PreflightRules.Battery.LOW -> R.string.pf_battery_sub_low
                    PreflightRules.Battery.VERY_LOW -> R.string.pf_battery_sub_very_low
                }
            ),
        )
        CardDivider()
        // 通知
        if (status.notificationsGranted) {
            StatusRow(stringResource(R.string.pa_notifications), Tone.GOOD, ok)
        } else {
            StatusRow(
                stringResource(R.string.pa_notifications), Tone.WARN, check, stringResource(R.string.pf_notifications_sub),
                stringResource(R.string.pf_open_settings),
            ) {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
        CardDivider()
        // バッテリー最適化
        if (status.batteryOptimizationIgnored) {
            StatusRow(stringResource(R.string.pa_battery_opt), Tone.GOOD, ok, stringResource(R.string.pf_battery_opt_sub_ok))
        } else {
            StatusRow(
                stringResource(R.string.pa_battery_opt), Tone.WARN, check, stringResource(R.string.pf_battery_opt_sub_ng),
                stringResource(R.string.pf_open_settings),
            ) {
                // 「除外」を直接求める Intent は Play の制限対象なので、除外を設定できる一覧の画面を開く
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        CardDivider()
        // メーカー独自のバックグラウンド動作の設定は、端末から判定できない。済ませたと申告されるまで、案内を出す
        if (backgroundSetupDone) {
            StatusRow(stringResource(R.string.pf_background_title), Tone.GOOD, ok, stringResource(R.string.pf_background_sub_ok))
        } else {
            StatusRow(
                stringResource(R.string.pf_background_title), Tone.WARN, check, stringResource(R.string.pf_background_sub_ng),
                stringResource(R.string.pf_open_assistant), onOpenAssistant,
            )
        }
        CardDivider()
        // マイク
        if (status.micGranted) {
            StatusRow(stringResource(R.string.pa_mic), Tone.GOOD, ok)
        } else {
            StatusRow(stringResource(R.string.pa_mic), Tone.INFO, info, stringResource(R.string.pf_mic_sub))
        }
        CardDivider()
        ExpandableNote(stringResource(R.string.pf_placement_summary), stringResource(R.string.pf_placement_detail))
    }
}

/** 睡眠前のメモとタグ(FR-2.10)。タグは 1 つ 20 文字まで、同じタグは付けられない。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    tags: List<String>, onTagsChange: (List<String>) -> Unit,
    memo: String, onMemoChange: (String) -> Unit,
    recent: List<String>,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<TagRules.Error?>(null) }

    fun add(text: String) {
        val e = TagRules.validate(text, tags)
        error = e
        if (e == null) {
            onTagsChange(tags + TagRules.clean(text))
            input = ""
        }
    }

    SectionCard(title = stringResource(R.string.tags_title)) {
        MutedText(stringResource(R.string.tags_hint))

        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tag_remove, tag)) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            label = { Text(stringResource(R.string.tag_input_label)) },
            singleLine = true,
            isError = error != null,
            supportingText = {
                Text(
                    error?.let { stringResource(tagErrorRes(it)) }
                        ?: stringResource(R.string.tag_counter, input.trim().codePointCount(0, input.trim().length), TagRules.MAX_LENGTH),
                )
            },
            trailingIcon = { TextButton(onClick = { add(input) }) { Text(stringResource(R.string.tag_add)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(input) }),
            modifier = Modifier.fillMaxWidth(),
        )

        // 最近使ったタグ(まだ付けていないもの)
        val suggestions = recent.filter { r -> tags.none { it == r } }
        if (suggestions.isNotEmpty()) {
            MutedText(stringResource(R.string.tags_recent))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { tag -> AssistChip(onClick = { add(tag) }, label = { Text(tag) }) }
            }
        }

        OutlinedTextField(
            value = memo,
            onValueChange = { if (it.length <= MEMO_MAX_LENGTH) onMemoChange(it) },
            label = { Text(stringResource(R.string.memo_label)) },
            supportingText = { Text(stringResource(R.string.memo_counter, memo.length, MEMO_MAX_LENGTH)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

const val MEMO_MAX_LENGTH = 200 // メモの上限(SPEC に定めはない。タグの 20 文字とは別)

private fun tagErrorRes(e: TagRules.Error): Int = when (e) {
    TagRules.Error.EMPTY -> R.string.tag_error_empty
    TagRules.Error.TOO_LONG -> R.string.tag_error_too_long
    TagRules.Error.DUPLICATE -> R.string.tag_error_duplicate
    TagRules.Error.TOO_MANY -> R.string.tag_error_too_many
}
