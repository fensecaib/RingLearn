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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
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

    /** 内置键盘是否处于组合状态（组合期间不触发搜索） */
    private val _imeComposing = MutableStateFlow(false)
    val imeComposing: StateFlow<Boolean> = _imeComposing.asStateFlow()

    /** 内置键盘组合中的纯假名（用于 IME 词典候选） */
    private val _compositionKana = MutableStateFlow("")
    val compositionKana: StateFlow<String> = _compositionKana.asStateFlow()

    /** IME 词典候选：组合假名前缀匹配词库（去抖 120ms） */
    val imeDictionaryCandidates: StateFlow<List<WordEntity>> = _compositionKana
        .debounce(120)
        .distinctUntilChanged()
        .flatMapLatest { kana ->
            if (kana.isBlank()) flowOf(emptyList())
            else flowOf(wordRepository.searchCandidates(kana))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<WordEntity>> = _query
        .flatMapLatest { wordRepository.observeFavorites(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** 字段内容变化：组合进行中不更新搜索词，仅组合提交后才触发搜索 */
    fun onFieldChanged(text: String, composing: Boolean) {
        if (!composing) _query.value = text
    }

    /** 内置键盘组合状态变化（候选条数据源 + 搜索门控） */
    fun onCompositionChange(composing: Boolean, kana: String) {
        _imeComposing.value = composing
        _compositionKana.value = kana
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
