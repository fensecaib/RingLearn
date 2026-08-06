package com.ringlearn.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ringlearn.app.domain.model.AppSettings
import com.ringlearn.app.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ringlearn_settings")

/** 使用 DataStore Preferences 持久化用户设置，并以 Flow 对外暴露。 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val AUTO_SPEAK_ENABLED = booleanPreferencesKey("auto_speak_enabled")
        val USE_IN_APP_KEYBOARD = booleanPreferencesKey("use_in_app_keyboard")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e ->
            Log.e("SettingsRepository", "读取设置失败", e)
            emit(emptyPreferences())
        }
        .map { p ->
            AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }
                    .getOrDefault(ThemeMode.SYSTEM),
                dailyGoal = (p[Keys.DAILY_GOAL] ?: 20).coerceIn(10, 100),
                reminderEnabled = p[Keys.REMINDER_ENABLED] ?: false,
                reminderHour = (p[Keys.REMINDER_HOUR] ?: 20).coerceIn(0, 23),
                reminderMinute = (p[Keys.REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
                soundEnabled = p[Keys.SOUND_ENABLED] ?: true,
                vibrationEnabled = p[Keys.VIBRATION_ENABLED] ?: true,
                autoSpeakEnabled = p[Keys.AUTO_SPEAK_ENABLED] ?: false,
                useInAppKeyboard = p[Keys.USE_IN_APP_KEYBOARD] ?: true
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDailyGoal(goal: Int) {
        context.settingsDataStore.edit { it[Keys.DAILY_GOAL] = goal.coerceIn(10, 100) }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit {
            it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setAutoSpeakEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_SPEAK_ENABLED] = enabled }
    }

    suspend fun setUseInAppKeyboard(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_IN_APP_KEYBOARD] = enabled }
    }
}
