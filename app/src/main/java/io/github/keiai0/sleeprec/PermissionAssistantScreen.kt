package io.github.keiai0.sleeprec

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * 権限アシスタント(FR-8.4): 一晩の計測が止められないための設定を、状態つきで案内する。
 * 通知・マイク・バッテリー最適化は状態を判定できる。メーカー独自のバックグラウンド動作の設定は判定できないので、
 * 手順を番号つきで案内し、設定したら自分でチェックを付ける。
 */
@Composable
fun PermissionAssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val status = rememberDeviceStatus()
    val settings by AppSettings.state.collectAsState()
    val vendor = remember(Build.MANUFACTURER, Build.BRAND) { VendorGuide.vendorOf(Build.MANUFACTURER, Build.BRAND) }
    val ok = stringResource(R.string.status_ok)
    val check = stringResource(R.string.status_check)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.cards),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        PageTitle(stringResource(R.string.pa_title))
        MutedText(stringResource(R.string.pa_intro))

        // 状態を判定できる 3 項目は、1 枚のカードにまとめる
        SectionCard(title = stringResource(R.string.pa_basic_title)) {
            if (status.notificationsGranted) {
                StatusRow(stringResource(R.string.pa_notifications), Tone.GOOD, ok, stringResource(R.string.pa_notifications_sub_ok))
            } else {
                StatusRow(
                    stringResource(R.string.pa_notifications), Tone.WARN, check, stringResource(R.string.pa_notifications_sub_ng),
                    stringResource(R.string.pf_open_settings),
                ) {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            CardDivider()
            if (status.micGranted) {
                StatusRow(stringResource(R.string.pa_mic), Tone.GOOD, ok)
            } else {
                StatusRow(stringResource(R.string.pa_mic), Tone.WARN, check, stringResource(R.string.pa_mic_sub_ng), stringResource(R.string.pa_open_app_info)) { openAppInfo(context) }
            }
            CardDivider()
            if (status.batteryOptimizationIgnored) {
                StatusRow(stringResource(R.string.pa_battery_opt), Tone.GOOD, ok, stringResource(R.string.pf_battery_opt_sub_ok))
            } else {
                StatusRow(
                    stringResource(R.string.pa_battery_opt), Tone.WARN, check, stringResource(R.string.pf_battery_opt_sub_ng),
                    stringResource(R.string.pf_open_settings),
                ) { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }

        // メーカー独自の設定。状態は判定できないので、番号つきの手順で案内し、設定したら自分でチェックを付ける
        SectionCard(title = stringResource(R.string.pa_background)) {
            // 手順が、この端末で確認したものか、一般的なものかを、はっきり示す
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vendor == Vendor.COLOROS) Pill(stringResource(R.string.pa_verified), Tone.GOOD) else Pill(stringResource(R.string.pa_general), Tone.INFO)
                MutedText(stringResource(R.string.pa_background_device, stringResource(vendor.nameRes)), modifier = Modifier.weight(1f))
            }
            stringResource(vendor.guideRes).split("\n").forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            OutlinedButton(onClick = { openVendorSettings(context, vendor) }) { Text(stringResource(R.string.pa_open_device_settings)) }
            CardDivider()
            Row(
                Modifier.fillMaxWidth().clickable { AppSettings.update(context) { it.copy(backgroundSetupDone = !it.backgroundSetupDone) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = settings.backgroundSetupDone, onCheckedChange = { c -> AppSettings.update(context) { it.copy(backgroundSetupDone = c) } })
                Text(stringResource(R.string.pa_background_done), style = MaterialTheme.typography.bodyLarge)
            }
            ExpandableNote(stringResource(R.string.pa_caveat_summary), stringResource(R.string.pa_background_caveat))
        }
        Spacer(Modifier.height(Spacing.screen))
    }
}

private fun openAppInfo(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

/** そのメーカーの設定画面を開く。開けなければ、アプリ情報の画面を開く(そこから手順で案内する)。 */
private fun openVendorSettings(context: Context, vendor: Vendor) {
    for (t in VendorGuide.targets(vendor)) {
        val intent = Intent().setComponent(ComponentName(t.pkg, t.cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) continue
        try {
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // 公開されていない画面は、開けないことがある(SecurityException など)。次の候補へ
        }
    }
    openAppInfo(context)
}
