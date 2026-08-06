package com.ringlearn.app.ui.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.data.repository.SettingsRepository
import com.ringlearn.app.data.repository.WordRepository
import com.ringlearn.app.util.handwriting.HandwritingRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 查词输入模式 */
enum class LookupInputMode { KEYBOARD, HANDWRITING }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LookupViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** 查询关键字（表记/假名/释义） */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 是否使用应用内置键盘（跟随全局设置，可在键盘上一键切换） */
    val useInAppKeyboard: StateFlow<Boolean> = settingsRepository.settings
        .map { it.useInAppKeyboard }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 当前输入模式：键盘 / 手写 */
    private val _inputMode = MutableStateFlow(LookupInputMode.KEYBOARD)
    val inputMode: StateFlow<LookupInputMode> = _inputMode.asStateFlow()

    /** 手写笔画（画板坐标系原始坐标） */
    private val _strokes = MutableStateFlow<List<List<Pair<Float, Float>>>>(emptyList())
    val strokes: StateFlow<List<List<Pair<Float, Float>>>> = _strokes.asStateFlow()

    /** 手写识别候选（最多 8 个字符） */
    private val _candidates = MutableStateFlow<List<Char>>(emptyList())
    val candidates: StateFlow<List<Char>> = _candidates.asStateFlow()

    /** 手写识别器（懒构建：词库字符集 + 字形模板，纯离线） */
    private val _recognizer = MutableStateFlow<HandwritingRecognizer?>(null)
    val recognizer: StateFlow<HandwritingRecognizer?> = _recognizer.asStateFlow()

    /** 识别器是否正在构建模板 */
    private val _recognizerLoading = MutableStateFlow(true)
    val recognizerLoading: StateFlow<Boolean> = _recognizerLoading.asStateFlow()

    /** 识别触发信号（去抖后执行） */
    private val strokeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 内置键盘是否处于组合状态（组合期间不触发主查询） */
    private val _imeComposing = MutableStateFlow(false)
    val imeComposing: StateFlow<Boolean> = _imeComposing.asStateFlow()

    /** 内置键盘组合中的纯假名（用于 IME 词典候选） */
    private val _compositionKana = MutableStateFlow("")
    val compositionKana: StateFlow<String> = _compositionKana.asStateFlow()

    /** IME 词典候选：组合假名前缀匹配词库（去抖 120ms，组合期间不触发主查询） */
    val imeDictionaryCandidates: StateFlow<List<WordEntity>> = _compositionKana
        .debounce(120)
        .distinctUntilChanged()
        .flatMapLatest { kana ->
            if (kana.isBlank()) flowOf(emptyList())
            else flowOf(wordRepository.searchCandidates(kana))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** 搜索结果：输入去抖 300ms 后实时查询 Room（LIKE，已转义）；空查询不查库 */
    val results: StateFlow<List<WordEntity>> = _query
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(emptyList())
            } else {
                wordRepository.observeLookup(q.trim())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 懒构建手写识别器（词库规模 ~1000 词，模板构建 <500ms）
        viewModelScope.launch {
            val chars = wordRepository.getAllCharacters()
            _recognizer.value = withContext(Dispatchers.Default) {
                HandwritingRecognizer(chars)
            }
            _recognizerLoading.value = false
        }
        // 停笔 250ms 后自动识别
        viewModelScope.launch {
            strokeEvents.debounce(250).collect { recognize() }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /**
     * 字段内容变化（内置键盘 / 系统输入法共用）：
     * 组合进行中不更新查询（避免搜索到不完整假名），仅组合提交后才触发搜索。
     */
    fun onFieldChanged(text: String, composing: Boolean) {
        if (!composing) _query.value = text
    }

    /** 内置键盘组合状态变化（候选条数据源 + 查询门控） */
    fun onCompositionChange(composing: Boolean, kana: String) {
        _imeComposing.value = composing
        _compositionKana.value = kana
    }

    fun onModeChange(mode: LookupInputMode) {
        _inputMode.value = mode
    }

    fun onSwitchToSystemIme() {
        viewModelScope.launch { settingsRepository.setUseInAppKeyboard(false) }
    }

    fun onSwitchToInAppKeyboard() {
        viewModelScope.launch { settingsRepository.setUseInAppKeyboard(true) }
    }

    fun onToggleFavorite(word: WordEntity) {
        viewModelScope.launch { wordRepository.setFavorite(word.id, !word.isFavorite) }
    }

    // ---- 手写 ----

    fun onStrokesChange(newStrokes: List<List<Pair<Float, Float>>>) {
        _strokes.value = newStrokes
        if (newStrokes.isNotEmpty()) strokeEvents.tryEmit(Unit)
    }

    fun clearHandwriting() {
        _strokes.value = emptyList()
        _candidates.value = emptyList()
    }

    /** 选中候选字符：追加到查询框并清空画板 */
    fun appendCandidate(char: Char) {
        _query.value = _query.value + char
        clearHandwriting()
    }

    private fun recognize() {
        val recognizer = _recognizer.value ?: return
        val currentStrokes = _strokes.value
        if (currentStrokes.isEmpty()) return
        viewModelScope.launch {
            val list = withContext(Dispatchers.Default) {
                runCatching { recognizer.recognize(currentStrokes) }.getOrDefault(emptyList())
            }
            _candidates.value = list.map { it.char }
        }
    }
}


