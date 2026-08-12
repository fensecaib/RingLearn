package com.ringlearn.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringlearn.app.data.ai.AiChatConfig
import com.ringlearn.app.data.ai.AiChatConfigRepository
import com.ringlearn.app.data.ai.AiChatRepository
import com.ringlearn.app.data.local.entity.AiChatEntity
import com.ringlearn.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** AI 对话页 ViewModel：消息/配置/发送/停止/重试/重置/连接测试。 */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val repository: AiChatRepository,
    private val configRepository: AiChatConfigRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** 全量历史（用于窗口同步与全量上下文统计；内存开销小，仅组合量按页控制） */
    private val fullMessages: StateFlow<List<AiChatEntity>> = repository.observeMessages(SESSION_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val window = ChatHistoryWindow()
    private val _messages = MutableStateFlow<List<AiChatEntity>>(emptyList())
    /** 当前显示的消息窗口（最近一页 + 已上滑加载的更早消息） */
    val messages: StateFlow<List<AiChatEntity>> = _messages

    private val _hasMoreOlder = MutableStateFlow(false)
    /** 是否还有更早的历史可加载 */
    val hasMoreOlder: StateFlow<Boolean> = _hasMoreOlder

    private val loadMutex = Mutex()

    init {
        // 全量变化（新消息追加/重置清空）时同步窗口回到最近一页
        viewModelScope.launch {
            fullMessages.collect { full ->
                window.sync(full)
                _messages.value = window.items
                _hasMoreOlder.value = window.hasMoreOlder
            }
        }
    }

    /** 上滑到顶部时加载更早一页（防重入）。 */
    fun loadOlder() {
        viewModelScope.launch {
            loadMutex.withLock {
                if (!_hasMoreOlder.value) return@withLock
                if (window.appendOlder(fullMessages.value)) {
                    _messages.value = window.items
                    _hasMoreOlder.value = window.hasMoreOlder
                }
            }
        }
    }

    val config: StateFlow<AiChatConfig> = configRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiChatConfig())

    /** 是否使用内置键盘（沿用全局设置，用于输入区与键盘绑定） */
    val useInAppKeyboard: StateFlow<Boolean> = settingsRepository.settings
        .map { it.useInAppKeyboard }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText

    /** 上下文统计：轮数 + 字符数 */
    val contextStats: StateFlow<ContextStats> = combine(fullMessages, streamingText) { msgs, stream ->
        ContextStats(
            rounds = msgs.count { it.role == "user" },
            chars = msgs.sumOf { it.content.length } + (stream?.length ?: 0)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContextStats())

    private var sendJob: Job? = null

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending.value) return
        sendJob = viewModelScope.launch {
            _sending.value = true
            _streamingText.value = ""
            try {
                repository.sendMessage(SESSION_ID, t) { acc -> _streamingText.value = acc }
            } finally {
                _sending.value = false
                _streamingText.value = null
            }
        }
    }

    /** 停止生成（取消协程；仓库侧把占位行标记为错误/已停止）。 */
    fun stop() {
        sendJob?.cancel()
        sendJob = null
    }

    /** 重试上一次（复用已有历史，不重复插入用户消息）。 */
    fun retry() {
        if (sending.value) return
        sendJob = viewModelScope.launch {
            _sending.value = true
            _streamingText.value = ""
            try {
                repository.resendLast(SESSION_ID) { acc -> _streamingText.value = acc }
            } finally {
                _sending.value = false
                _streamingText.value = null
            }
        }
    }

    /** 重置会话：先停止生成，再清空当前会话消息。 */
    fun resetSession() {
        stop()
        viewModelScope.launch { repository.clearSession(SESSION_ID) }
    }

    fun updateConfig(
        baseUrl: String,
        apiKey: String,
        model: String,
        maxTokens: Int,
        systemPrompt: String,
        thinkingEnabled: Boolean,
        chatFontScale: Float
    ) {
        viewModelScope.launch {
            configRepository.update(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt,
                thinkingEnabled = thinkingEnabled,
                chatFontScale = chatFontScale
            )
        }
    }

    /** 连接测试：用对话框中的候选配置（可能未保存）发起最小请求。 */
    fun testConnection(candidate: AiChatConfig, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.testConnection(candidate))
        }
    }

    /** 一键切到系统输入法。 */
    fun switchToSystemIme() {
        viewModelScope.launch { settingsRepository.setUseInAppKeyboard(false) }
    }

    /** 切回内置键盘（与其他页面一致）。 */
    fun switchToInAppKeyboard() {
        viewModelScope.launch { settingsRepository.setUseInAppKeyboard(true) }
    }

    companion object {
        const val SESSION_ID = "default"
    }
}



