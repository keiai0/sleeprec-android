package io.github.keiai0.sleeprec

import android.app.DatePickerDialog
import android.content.pm.ApplicationInfo
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.keiai0.sleeprec.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 設定タブ(FR-8.1、8.2、8.6)。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SessionStore.create(context) }
    val scope = rememberCoroutineScope()
    val settings by AppSettings.state.collectAsState()
    val recording by RecordingState.isRecording.collectAsState()
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun change(f: (Settings) -> Settings) = AppSettings.update(context, f)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall)

        // --- 睡眠 ---
        SectionTitle(R.string.settings_sleep)
        StepperRow(
            title = stringResource(R.string.setting_goal),
            value = stringResource(R.string.duration_hm_long, settings.goalSleepMin / 60, settings.goalSleepMin % 60),
            onMinus = { change { it.copy(goalSleepMin = SettingsRules.stepGoal(it.goalSleepMin, -1)) } },
            onPlus = { change { it.copy(goalSleepMin = SettingsRules.stepGoal(it.goalSleepMin, +1)) } },
        )
        Text(stringResource(R.string.setting_goal_note), style = MaterialTheme.typography.labelSmall)
        StepperRow(
            title = stringResource(R.string.setting_cutoff),
            value = "%d:00".format(settings.dayCutoffHour),
            onMinus = { change { it.copy(dayCutoffHour = SettingsRules.stepCutoff(it.dayCutoffHour, -1)) } },
            onPlus = { change { it.copy(dayCutoffHour = SettingsRules.stepCutoff(it.dayCutoffHour, +1)) } },
        )
        Text(stringResource(R.string.setting_cutoff_note), style = MaterialTheme.typography.labelSmall)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // --- プロフィール ---
        SectionTitle(R.string.settings_profile)
        Text(stringResource(R.string.profile_note), style = MaterialTheme.typography.labelSmall)
        ProfileEditor(settings, ::change)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // --- 権限 ---
        SectionTitle(R.string.settings_permissions)
        Text(stringResource(R.string.settings_permissions_note), style = MaterialTheme.typography.labelSmall)
        OutlinedButton(onClick = onOpenPermissions) { Text(stringResource(R.string.pa_title)) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // --- データ ---
        SectionTitle(R.string.settings_data)
        var cacheBytes by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) { cacheBytes = withContext(Dispatchers.IO) { DataCleaner.cacheSize(context) } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_clear_cache))
                Text(stringResource(R.string.cache_size, Formatter.formatFileSize(context, cacheBytes)), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(enabled = cacheBytes > 0, onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { DataCleaner.clearCache(context) }
                    cacheBytes = 0
                    Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.clear)) }
        }

        var confirmDeleteAll by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_delete_all))
                Text(
                    stringResource(if (recording) R.string.delete_all_blocked else R.string.delete_all_note),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(enabled = !recording, onClick = { confirmDeleteAll = true }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
        if (confirmDeleteAll) {
            AlertDialog(
                onDismissRequest = { confirmDeleteAll = false },
                title = { Text(stringResource(R.string.delete_all_title)) },
                text = { Text(stringResource(R.string.delete_all_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDeleteAll = false
                        scope.launch(Dispatchers.IO + NonCancellable) {
                            DataCleaner.deleteAll(context, store)
                            withContext(Dispatchers.Main) { Toast.makeText(context, R.string.delete_all_done, Toast.LENGTH_LONG).show() }
                        }
                    }) { Text(stringResource(R.string.delete_all_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }

        if (debuggable) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DebugTools(store)
        }
    }
}

@Composable
private fun SectionTitle(res: Int) = Text(stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))

/** 「− 値 +」で値を変える。ラベルは 1 行目、操作は 2 行目に分けて、文字が折り返さないようにする。 */
@Composable
private fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onMinus) { Text("−") }
            Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onPlus) { Text("＋") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileEditor(settings: Settings, change: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val imperial = settings.units == Units.IMPERIAL

    Text(stringResource(R.string.profile_gender), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Gender.entries.forEach { g ->
            FilterChip(selected = settings.gender == g, onClick = { change { it.copy(gender = g) } }, label = { Text(stringResource(g.labelRes)) })
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.profile_birth), Modifier.weight(1f))
        OutlinedButton(onClick = {
            val initial = settings.birthEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().minusYears(30)
            DatePickerDialog(context, { _, y, m, d ->
                val picked = LocalDate.of(y, m + 1, d)
                if (SettingsRules.isValidBirthDate(picked.toEpochDay(), LocalDate.now().toEpochDay())) {
                    change { it.copy(birthEpochDay = picked.toEpochDay()) }
                } else {
                    Toast.makeText(context, R.string.profile_birth_invalid, Toast.LENGTH_SHORT).show()
                }
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }) {
            Text(settings.birthEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: stringResource(R.string.profile_unset))
        }
        if (settings.birthEpochDay != null) TextButton(onClick = { change { it.copy(birthEpochDay = null) } }) { Text(stringResource(R.string.clear)) }
    }

    Text(stringResource(R.string.profile_units), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Units.entries.forEach { u ->
            FilterChip(selected = settings.units == u, onClick = { change { it.copy(units = u) } }, label = { Text(stringResource(u.labelRes)) })
        }
    }

    // 身長・体重。入力は選んだ単位で、保存はメートル法。範囲外の値は保存しない
    NumberField(
        label = stringResource(if (imperial) R.string.profile_height_in else R.string.profile_height_cm),
        initial = settings.heightCm?.let { if (imperial) "%.1f".format(SettingsRules.cmToInches(it)) else it.toString() } ?: "",
        keyOf = settings.units,
        parse = { text -> text.toDoubleOrNull()?.let { v -> (if (imperial) SettingsRules.inchesToCm(v) else v.toInt()).takeIf { it in 50..250 } } },
        onValue = { cm -> change { it.copy(heightCm = cm) } },
        errorRes = R.string.profile_height_invalid,
    )
    NumberField(
        label = stringResource(if (imperial) R.string.profile_weight_lb else R.string.profile_weight_kg),
        initial = settings.weightKg?.let { if (imperial) "%.1f".format(SettingsRules.kgToPounds(it)) else "%.1f".format(it) } ?: "",
        keyOf = settings.units,
        parse = { text -> text.toDoubleOrNull()?.let { v -> (if (imperial) SettingsRules.poundsToKg(v) else v.toFloat()).takeIf { it in 20f..300f } } },
        onValue = { kg -> change { it.copy(weightKg = kg) } },
        errorRes = R.string.profile_weight_invalid,
    )
}

/** 数値の入力欄。空にすると未設定に戻る。妥当でない値のときは、エラーを出して保存しない。 */
@Composable
private fun <T : Any> NumberField(label: String, initial: String, keyOf: Any, parse: (String) -> T?, onValue: (T?) -> Unit, errorRes: Int) {
    var text by remember(keyOf) { mutableStateOf(initial) }
    var error by remember(keyOf) { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length <= 6 && input.all { it.isDigit() || it == '.' }) {
                text = input
                if (input.isBlank()) {
                    error = false
                    onValue(null)
                } else {
                    val v = parse(input)
                    error = v == null
                    if (v != null) onValue(v)
                }
            }
        },
        label = { Text(label) },
        singleLine = true,
        isError = error,
        supportingText = if (error) { { Text(stringResource(errorRes)) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
