package io.github.keiai0.sleeprec

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/** 下部タブ 4 つ(UX.md §7)。アイコンだけにせず、必ずラベルを付ける。 */
private enum class Tab(val route: String, val labelRes: Int) {
    Record("record", R.string.tab_record),
    Journal("journal", R.string.tab_journal),
    Stats("stats", R.string.tab_stats),
    Settings("settings", R.string.tab_settings),
}

private const val DETAIL_ROUTE = "journal/{id}"

@Composable
fun SleepRecApp(stopRequested: Boolean, onStopRequestConsumed: () -> Unit) {
    val nav = rememberNavController()
    // NavHost の中身(composable の lambda)は、表示中の画面では更新されないことがある。
    // 変わる値は、State 経由で中で読む(そのままキャプチャすると、古い値のままになる)
    val stopRequestedState = rememberUpdatedState(stopRequested)
    val consumedState = rememberUpdatedState(onStopRequestConsumed)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }
    // 暗いモードで計測中は、タブも出さない全画面(暗い計測画面)にする
    val recording by RecordingState.isRecording.collectAsState()
    val screenMode by ScreenModeState.mode.collectAsState()
    val fullscreen = recording && screenMode != ScreenMode.AUTO_LOCK && (route == null || route == Tab.Record.route)

    fun goToTab(tab: Tab) {
        // すでにそのタブにいるときは何もしない。移動すると、同じ画面が作り直されて、
        // 画面の中の状態(開いたばかりのダイアログなど)が消えてしまう
        if (tab == currentTab) return
        nav.navigate(tab.route) {
            // タブを切り替えても、履歴が積み上がらないようにする
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 通知の「停止」から開かれたときは、どの画面にいても、計測タブ(終了ダイアログがある)へ移す
    LaunchedEffect(stopRequested) {
        if (stopRequested) goToTab(Tab.Record)
    }

    Scaffold(
        bottomBar = {
            // 詳細画面でもタブを出す(Journal を選択中として表示し、他のタブへすぐ移れるようにする)
            if (!fullscreen && (currentTab != null || route == DETAIL_ROUTE)) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab || (route == DETAIL_ROUTE && tab == Tab.Journal),
                            onClick = { goToTab(tab) },
                            icon = { Icon(tabIcon(tab), contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Record.route, modifier = if (fullscreen) Modifier else Modifier.padding(padding)) {
            composable(Tab.Record.route) {
                RecorderScreen(stopRequested = stopRequestedState.value, onStopRequestConsumed = { consumedState.value() })
            }
            composable(Tab.Journal.route) {
                SessionListScreen(onOpen = { nav.navigate("journal/$it") })
            }
            composable(DETAIL_ROUTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                SessionDetailScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable(Tab.Stats.route) { StatsScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
        }
    }
}

/** 設定は Phase 9 で作る。今は、デバッグビルドだけ、確認用の道具を置く。 */
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { io.github.keiai0.sleeprec.data.SessionStore.create(context) }
    val scope = rememberCoroutineScope()
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.placeholder_settings))
        if (debuggable) {
            Text(stringResource(R.string.debug_tools), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            // 数時間の実データがなくても、睡眠の推定・スコア・画面を確認するための合成データ(デバッグビルドのみ)
            TextButton(enabled = !busy, onClick = {
                busy = true
                message = R.string.debug_creating
                // タブを切り替えても、途中で止まって中途半端なデータが残らないようにする(NonCancellable)
                scope.launch(Dispatchers.IO + NonCancellable) {
                    for (i in 1..7) {
                        // 昨日から 7 日前まで。就寝は 23:00 前後で、日ごとに数十分ずつずれる
                        val cal = java.util.Calendar.getInstance().apply {
                            add(java.util.Calendar.DAY_OF_YEAR, -i)
                            set(java.util.Calendar.HOUR_OF_DAY, 23)
                            set(java.util.Calendar.MINUTE, (i * 17) % 50)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        store.insertSyntheticNight(
                            SyntheticNightGenerator.generate(cal.timeInMillis, durationMin = 420 + i * 10, seed = i)
                        )
                    }
                    busy = false
                    message = R.string.debug_created
                }
            }) { Text(stringResource(R.string.debug_create_nights)) }
            TextButton(enabled = !busy, onClick = {
                scope.launch(Dispatchers.IO + NonCancellable) {
                    store.deleteSyntheticNights()
                    message = R.string.debug_deleted
                }
            }) {
                Text(stringResource(R.string.debug_delete_nights))
            }
            message?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

// --- タブのアイコン。標準アイコン集にないものは、単純な図形で自前に描く ---

private fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.Record -> RecordIcon
    Tab.Journal -> Icons.Filled.DateRange
    Tab.Stats -> BarsIcon
    Tab.Settings -> Icons.Filled.Settings
}

// 録音(丸に中の点)
private val RecordIcon: ImageVector = ImageVector.Builder("Record", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) {
        moveTo(12f, 2f); arcToRelative(10f, 10f, 0f, true, false, 0.01f, 0f); close()
        moveTo(12f, 7f); arcToRelative(5f, 5f, 0f, true, true, -0.01f, 0f); close()
    }
}.build()

// 棒グラフ
private val BarsIcon: ImageVector = ImageVector.Builder("Bars", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) {
        moveTo(4f, 12f); horizontalLineToRelative(4f); verticalLineToRelative(8f); horizontalLineToRelative(-4f); close()
        moveTo(10f, 4f); horizontalLineToRelative(4f); verticalLineToRelative(16f); horizontalLineToRelative(-4f); close()
        moveTo(16f, 9f); horizontalLineToRelative(4f); verticalLineToRelative(11f); horizontalLineToRelative(-4f); close()
    }
}.build()
