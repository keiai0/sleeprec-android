package io.github.keiai0.sleeprec

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Gender(val labelRes: Int) {
    UNSET(R.string.gender_unset), FEMALE(R.string.gender_female), MALE(R.string.gender_male), OTHER(R.string.gender_other)
}

/** 身長・体重の表示単位(FR-8.1)。保存は常にメートル法。 */
enum class Units(val labelRes: Int) { METRIC(R.string.units_metric), IMPERIAL(R.string.units_imperial) }

/** 利用目的(FR-1.2)。複数選択できる。 */
enum class Purpose(val labelRes: Int) {
    RECORD(R.string.purpose_record), FALL_ASLEEP(R.string.purpose_fall_asleep), QUALITY(R.string.purpose_quality),
    MORNING(R.string.purpose_morning), RHYTHM(R.string.purpose_rhythm),
}

/** アプリの設定と、初回の質問の答え。 */
data class Settings(
    val goalSleepMin: Int = SettingsRules.GOAL_DEFAULT,
    val dayCutoffHour: Int = Thresholds.DAY_CUTOFF_HOUR,
    val onboardingDone: Boolean = false,
    val purposes: Set<Purpose> = emptySet(),
    val bedMinuteOfDay: Int? = null,
    val wakeMinuteOfDay: Int? = null,
    val gender: Gender = Gender.UNSET,
    val birthEpochDay: Long? = null,
    val heightCm: Int? = null,
    val weightKg: Float? = null,
    val units: Units = Units.METRIC,
    // メーカー独自のバックグラウンド動作の設定を、ユーザーが済ませたと申告したか(状態を端末から判定できないため)
    val backgroundSetupDone: Boolean = false,
    // 全録音の WAV を残すか(PLAN 決定 #2)。既定はオフ: 音声は、検出したクリップだけを残す(容量のため)
    val keepFullRecording: Boolean = false,
) {
    val goalSleepMs: Long get() = goalSleepMin * 60_000L
}

/**
 * 設定の保存(SharedPreferences)と、画面への通知(StateFlow)。プロフィールは端末内にだけ保存し、外へは送らない(NFR-3)。
 */
object AppSettings {
    private const val PREFS = "settings"

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state

    fun init(context: Context) {
        _state.value = read(context)
    }

    /** 設定を変えて保存する。 */
    fun update(context: Context, change: (Settings) -> Settings) {
        val next = change(_state.value)
        _state.value = next
        write(context, next)
    }

    /** すべての設定を初期状態に戻す(全データ削除のとき)。 */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _state.value = Settings()
    }

    private fun read(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun <E : Enum<E>> enumOf(key: String, values: Array<E>, default: E) = values.firstOrNull { it.name == p.getString(key, null) } ?: default
        return Settings(
            goalSleepMin = SettingsRules.clampGoal(p.getInt("goal_min", SettingsRules.GOAL_DEFAULT)),
            dayCutoffHour = p.getInt("cutoff_hour", Thresholds.DAY_CUTOFF_HOUR).coerceIn(0, 23),
            onboardingDone = p.getBoolean("onboarding_done", false),
            purposes = (p.getString("purposes", "") ?: "").split(",").mapNotNull { n -> Purpose.entries.firstOrNull { it.name == n } }.toSet(),
            bedMinuteOfDay = if (p.contains("bed_min")) p.getInt("bed_min", 0) else null,
            wakeMinuteOfDay = if (p.contains("wake_min")) p.getInt("wake_min", 0) else null,
            gender = enumOf("gender", Gender.entries.toTypedArray(), Gender.UNSET),
            birthEpochDay = if (p.contains("birth_day")) p.getLong("birth_day", 0) else null,
            heightCm = if (p.contains("height_cm")) p.getInt("height_cm", 0) else null,
            weightKg = if (p.contains("weight_kg")) p.getFloat("weight_kg", 0f) else null,
            units = enumOf("units", Units.entries.toTypedArray(), Units.METRIC),
            backgroundSetupDone = p.getBoolean("bg_setup_done", false),
            keepFullRecording = p.getBoolean("keep_full_recording", false),
        )
    }

    private fun write(context: Context, s: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt("goal_min", s.goalSleepMin)
            putInt("cutoff_hour", s.dayCutoffHour)
            putBoolean("onboarding_done", s.onboardingDone)
            putString("purposes", s.purposes.joinToString(",") { it.name })
            s.bedMinuteOfDay?.let { putInt("bed_min", it) } ?: remove("bed_min")
            s.wakeMinuteOfDay?.let { putInt("wake_min", it) } ?: remove("wake_min")
            putString("gender", s.gender.name)
            s.birthEpochDay?.let { putLong("birth_day", it) } ?: remove("birth_day")
            s.heightCm?.let { putInt("height_cm", it) } ?: remove("height_cm")
            s.weightKg?.let { putFloat("weight_kg", it) } ?: remove("weight_kg")
            putString("units", s.units.name)
            putBoolean("bg_setup_done", s.backgroundSetupDone)
            putBoolean("keep_full_recording", s.keepFullRecording)
        }.apply()
    }
}
