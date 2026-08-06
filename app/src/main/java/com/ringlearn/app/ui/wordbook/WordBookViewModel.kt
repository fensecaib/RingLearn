package com.ringlearn.app.ui.wordbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.data.repository.SettingsRepository
import com.ringlearn.app.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 生词本 ViewModel：展示收藏词条，支持搜索（内置键盘/系统输入法）与移除。 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WordBookViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 是否使用应用内置键盘（跟随全局设置） */
    val useInAppKeyboard: StateFlow<Boolean> = settingsRepository.settings
        .map { it.useInAppKeyboard }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val favorites: StateFlow<List<WordEntity>> = _query
        .flatMapLatest { wordRepository.observeFavorites(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onSwitchToSystemIme() {
        viewModelScope.launch { settingsRepository.setUseInAppKeyboard(false) }
    }

    fun onSwitchToInAppKeyboard() {
        viewModelScope.launch { settingsRepository.setUseInAppKeyboard(true) }
    }

    fun removeFromBook(wordId: Long) {
        viewModelScope.launch { wordRepository.setFavorite(wordId, false) }
    }
}
