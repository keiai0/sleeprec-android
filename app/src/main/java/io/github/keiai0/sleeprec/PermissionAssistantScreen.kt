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
 * 手順を案内し、設定したら自分でチェックを付ける。
 */
@Composable
fun PermissionAssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val status = rememberDeviceStatus()
    val settings by AppSettings.state.collectAsState()
    val vendor = remember(Build.MANUFACTURER, Build.BRAND) { VendorGuide.vendorOf(Build.MANUFACTURER, Build.BRAND) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.pa_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.pa_intro), style = MaterialTheme.typography.bodyMedium)

        PermissionCard(
            title = stringResource(R.string.pa_notifications),
            ok = status.notificationsGranted,
            okText = stringResource(R.string.pa_notifications_ok),
            ngText = stringResource(R.string.pa_notifications_ng),
            actionLabel = stringResource(R.string.pf_open_notification_settings),
            onAction = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
        )
        PermissionCard(
            title = stringResource(R.string.pa_mic),
            ok = status.micGranted,
            okText = stringResource(R.string.pa_mic_ok),
            ngText = stringResource(R.string.pa_mic_ng),
            actionLabel = stringResource(R.string.pa_open_app_info),
            onAction = { openAppInfo(context) },
        )
        PermissionCard(
            title = stringResource(R.string.pa_battery_opt),
            ok = status.batteryOptimizationIgnored,
            okText = stringResource(R.string.pf_battery_opt_ok),
            ngText = stringResource(R.string.pf_battery_opt_ng),
            actionLabel = stringResource(R.string.pf_open_battery_settings),
            onAction = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        )

        // メーカー独自の設定。状態は判定できないので、手順を案内し、設定したら自分でチェックを付ける
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pa_background), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pa_background_device, stringResource(vendor.nameRes)), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(vendor.guideRes), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { openVendorSettings(context, vendor) }) { Text(stringResource(R.string.pa_open_device_settings)) }
                Row(
                    Modifier.fillMaxWidth().clickable { AppSettings.update(context) { it.copy(backgroundSetupDone = !it.backgroundSetupDone) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = settings.backgroundSetupDone, onCheckedChange = { c -> AppSettings.update(context) { it.copy(backgroundSetupDone = c) } })
                    Text(stringResource(R.string.pa_background_done))
                }
                Text(stringResource(R.string.pa_background_caveat), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, ok: Boolean, okText: String, ngText: String, actionLabel: String, onAction: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            // 状態は、色だけでなく、記号と言葉でも示す
            Text(
                (if (ok) "✓ " else "! ") + (if (ok) okText else ngText),
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!ok) OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
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
