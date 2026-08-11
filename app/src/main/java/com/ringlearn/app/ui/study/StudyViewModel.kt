package com.ringlearn.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.data.repository.SettingsRepository
import com.ringlearn.app.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyUiState(
    val isLoading: Boolean = true,
    /** 本轮学习队列（先复习、后新词） */
    val words: List<WordEntity> = emptyList(),
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val favoriteCount: Int = 0,
    val sessionStartAt: Long = 0L,
    val roundFinished: Boolean = false,
    val autoSpeakEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val empty: Boolean = false,
    val error: String? = null
) {
    val currentWord: WordEntity? get() = words.getOrNull(currentIndex)
    val total: Int get() = words.size
    /** 正确率（0..1） */
    val accuracy: Float
        get() {
            val answered = correctCount + wrongCount
            return if (answered == 0) 0f else correctCount.toFloat() / answered
        }
}

/**
 * 学习页 ViewModel：管理一轮（默认 20 词）的学习队列，
 * 在每次滑动后调用 SM-2 算法更新调度状态并记录复习日志。
 */
@HiltViewModel
class StudyViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState(isLoading = true))
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    /** 串行化滑动落库：快速连滑时保证每个词只被记录一次、索引有序推进 */
    private val swipeMutex = Mutex()

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.autoSpeakEnabled to it.vibrationEnabled }
                .distinctUntilChanged()
                .collect { (speak, vibrate) ->
                    _uiState.update { it.copy(autoSpeakEnabled = speak, vibrationEnabled = vibrate) }
                }
        }
        startSession()
    }

    /** 开始（或重新开始）一轮学习 */
    fun startSession() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, roundFinished = false, empty = false, error = null)
            }
            runCatching {
                val goal = settingsRepository.settings.first().dailyGoal
                val roundSize = goal.coerceAtMost(ROUND_SIZE).coerceAtLeast(1)
                wordRepository.getStudyRound(roundSize)
            }.onSuccess { words ->
                if (words.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, empty = true) }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            words = words,
                            currentIndex = 0,
                            correctCount = 0,
                            wrongCount = 0,
                            favoriteCount = 0,
                            sessionStartAt = System.currentTimeMillis(),
                            roundFinished = false,
                            empty = false,
                            error = null
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    /** 右滑：认识（SM-2 quality=5） */
    fun onSwipeKnown() = recordSwipe(quality = Sm2Quality.KNOWN, favorite = false)

    /** 左滑：不认识（SM-2 quality=2） */
    fun onSwipeUnknown() = recordSwipe(quality = Sm2Quality.UNKNOWN, favorite = false)

    /** 上滑：收进生词本（按“不认识”处理，并标记收藏） */
    fun onSwipeToWordbook() = recordSwipe(quality = Sm2Quality.UNKNOWN, favorite = true)

    private fun recordSwipe(quality: Int, favorite: Boolean) {
        viewModelScope.launch {
            // 在锁内读取当前卡片：快速连滑时第二个请求等待锁释放后读到新卡片，
            // 杜绝对同一词重复 recordReview（reviewCount/日志双记）
            swipeMutex.withLock {
                val state = _uiState.value
                val word = state.currentWord ?: return@withLock
                runCatching { wordRepository.recordReview(word, quality, favorite) }
                    .onSuccess {
                        val fresh = _uiState.value
                        // 防重入：只有当被记录的单词仍是当前卡片时才推进
                        if (fresh.words.getOrNull(fresh.currentIndex)?.id != word.id) return@onSuccess
                        val correct = quality >= 3
                        val next = fresh.copy(
                            currentIndex = fresh.currentIndex + 1,
                            correctCount = fresh.correctCount + if (correct) 1 else 0,
                            wrongCount = fresh.wrongCount + if (correct) 0 else 1,
                            favoriteCount = fresh.favoriteCount + if (favorite) 1 else 0
                        )
                        _uiState.value = next.copy(roundFinished = next.currentIndex >= next.words.size)
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = e.message ?: "保存失败") }
                    }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 已用秒数（用于统计弹窗） */
    fun elapsedSeconds(): Long {
        val start = _uiState.value.sessionStartAt
        return if (start == 0L) 0L else (System.currentTimeMillis() - start) / 1000
    }

    private companion object {
        const val ROUND_SIZE = 20
        object Sm2Quality {
            const val KNOWN = 5
            const val UNKNOWN = 2
        }
    }
}
