package io.github.keiai0.sleeprec

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 計測中の画面モード(FR-2.3、UX.md §7)。既定は自動ロック。 */
enum class ScreenMode(val titleRes: Int, val descriptionRes: Int) {
    AUTO_LOCK(R.string.mode_auto_lock, R.string.mode_auto_lock_desc),
    DARK_CLOCK(R.string.mode_dark_clock, R.string.mode_dark_clock_desc),
    DARK_OFF(R.string.mode_dark_off, R.string.mode_dark_off_desc),
}

/** 選んだ画面モードを覚えておく(次回の起動でも同じモードにする)。Activity と画面で共有する。 */
object ScreenModeState {
    private const val PREFS = "app"
    private const val KEY = "screen_mode"

    private val _mode = MutableStateFlow(ScreenMode.AUTO_LOCK)
    val mode: StateFlow<ScreenMode> = _mode

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _mode.value = ScreenMode.entries.firstOrNull { it.name == saved } ?: ScreenMode.AUTO_LOCK
    }

    fun set(context: Context, mode: ScreenMode) {
        _mode.value = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}
