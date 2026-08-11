package com.ringlearn.app.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizQuestion(
    val word: WordEntity,
    val options: List<String>,
    val correctIndex: Int
)

data class QuizUiState(
    val isLoading: Boolean = true,
    val questions: List<QuizQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val score: Int = 0,
    val selectedIndex: Int? = null,
    val finished: Boolean = false,
    val error: String? = null
) {
    val currentQuestion: QuizQuestion? get() = questions.getOrNull(currentIndex)
    val total: Int get() = questions.size
}

/** 随机测验 ViewModel：看日文选中文释义，四选一。 */
@HiltViewModel
class QuizViewModel @Inject constructor(
    private val wordRepository: WordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        startQuiz()
    }

    fun startQuiz(questionCount: Int = 10) {
        // 快速重启时取消上一次仍在进行的加载，避免新旧题目交错
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // 一次随机查询（questionCount*5 词）同时提供目标题与干扰项池，
                // 避免两次 ORDER BY RANDOM() 全表扫描（弱机首访提速）
                val words = wordRepository.getRandomWords(questionCount * 5)
                val targets = words.take(questionCount)
                buildQuestions(targets, words)
            }.onSuccess { questions ->
                _uiState.value = QuizUiState(isLoading = false, questions = questions)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载题目失败") }
            }
        }
    }

    private fun buildQuestions(
        targets: List<WordEntity>,
        pool: List<WordEntity>
    ): List<QuizQuestion> {
        val targetIds = targets.mapTo(mutableSetOf()) { it.id }
        val distractorPool = pool
            .filter { it.id !in targetIds }
            .map { it.meaning }
            .distinct()
        return targets.map { target ->
            val wrong = distractorPool.filter { it != target.meaning }.shuffled().take(3)
            val options = (listOf(target.meaning) + wrong).shuffled()
            QuizQuestion(
                word = target,
                options = options,
                correctIndex = options.indexOf(target.meaning)
            )
        }
    }

    fun selectOption(index: Int) {
        val state = _uiState.value
        if (state.selectedIndex != null || state.finished) return
        val question = state.currentQuestion ?: return
        if (index !in question.options.indices) return
        _uiState.update {
            it.copy(
                selectedIndex = index,
                score = it.score + if (index == question.correctIndex) 1 else 0
            )
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.selectedIndex == null) return
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.questions.size) {
            _uiState.update { it.copy(finished = true) }
        } else {
            _uiState.update { it.copy(currentIndex = nextIndex, selectedIndex = null) }
        }
    }

    fun restart() = startQuiz()

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}
