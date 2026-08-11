package com.ringlearn.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringlearn.app.data.repository.SettingsRepository
import com.ringlearn.app.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 根级 ViewModel：暴露主题模式用于全局动态换肤 */
@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.settings
        .map { it.themeMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM
        )

    /** 是否使用应用内置键盘（用于根层 dock 显隐门控） */
    val useInAppKeyboard: StateFlow<Boolean> = settingsRepository.settings
        .map { it.useInAppKeyboard }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )
}
