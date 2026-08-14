package com.ringlearn.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringlearn.app.data.repository.SettingsRepository
import com.ringlearn.app.domain.model.AppSettings
import com.ringlearn.app.domain.model.ThemeMode
import com.ringlearn.app.util.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 设置页 ViewModel：读取并修改全局设置，提醒开关/时间同步到 AlarmManager。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettings())
    val uiState: StateFlow<AppSettings> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .catch { e ->
                    Log.e("SettingsViewModel", "读取设置失败", e)
                    emit(AppSettings())
                }
                .collect { _uiState.value = it }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrationEnabled(enabled) }
    }

    fun setAutoSpeakEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSpeakEnabled(enabled) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            val settings = settingsRepository.settings.first()
            if (enabled) {
                reminderScheduler.schedule(settings.reminderHour, settings.reminderMinute)
                _message.emit("学习提醒已开启（每天 ${settings.reminderTimeText}）")
            } else {
                reminderScheduler.cancel()
                _message.emit("学习提醒已关闭")
            }
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(hour, minute)
            val settings = settingsRepository.settings.first()
            if (settings.reminderEnabled) {
                reminderScheduler.schedule(hour, minute)
                _message.emit("提醒时间已更新为 ${settings.reminderTimeText}")
            }
        }
    }

    /** 向 Snackbar 推送一条提示 */
    fun postMessage(message: String) {
        viewModelScope.launch { _message.emit(message) }
    }
}
