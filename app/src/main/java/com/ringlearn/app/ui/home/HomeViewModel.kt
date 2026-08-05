package com.ringlearn.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringlearn.app.data.repository.SettingsRepository
import com.ringlearn.app.data.repository.WordRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val stats: WordRepository.HomeStats = WordRepository.HomeStats(),
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(wordRepository.homeStats, settingsRepository.settings) { stats, settings ->
                HomeUiState(
                    stats = stats,
                    settings = settings,
                    isLoading = !stats.isReady,
                    error = null
                )
            }
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
                }
                .collect { _uiState.value = it }
        }
    }

    fun setDailyGoal(goal: Int) {
        viewModelScope.launch { settingsRepository.setDailyGoal(goal) }
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

    fun resetProgress() {
        viewModelScope.launch {
            wordRepository.resetAllProgress()
            _message.emit("所有学习进度已重置")
        }
    }
}
