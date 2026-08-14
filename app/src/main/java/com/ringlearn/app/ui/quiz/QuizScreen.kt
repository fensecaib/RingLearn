package com.ringlearn.app.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringlearn.app.R
import com.ringlearn.app.ui.components.EmptyState
import com.ringlearn.app.ui.components.LoadingState
import com.ringlearn.app.ui.components.SakuTopBar
import com.ringlearn.app.ui.components.sakuCardBorder
import com.ringlearn.app.ui.components.sakuCtaButtonColors
import com.ringlearn.app.ui.rememberHapticManager
import com.ringlearn.app.ui.rememberTtsManager
import com.ringlearn.app.util.TtsManager

/** 随机测验：看日文选中文释义（四选一） */
@Composable
fun QuizScreen(
    onExit: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberHapticManager()
    val snackbarHostState = remember { SnackbarHostState() }
    // 应用级 TTS 单例：进出页面不重建、不销毁
    val tts = rememberTtsManager()

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SakuTopBar(
                title = { Text("随机测验") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> LoadingState(message = "正在抽取题目…")

                uiState.error != null && uiState.questions.isEmpty() -> EmptyState(
                    iconRes = R.drawable.ic_quiz,
                    title = "加载题目失败",
                    subtitle = uiState.error ?: "请稍后重试",
                    actionLabel = "重试",
                    onAction = { viewModel.startQuiz() }
                )

                uiState.finished -> QuizResult(
                    score = uiState.score,
                    total = uiState.total,
                    haptic = haptic,
                    onRestart = { viewModel.restart() },
                    onExit = onExit
                )

                else -> {
                    val question = uiState.currentQuestion
                    if (question != null) {
                        QuizQuestionView(
                            uiState = uiState,
                            question = question,
                            tts = tts,
                            haptic = haptic,
                            onSelect = viewModel::selectOption,
                            onNext = viewModel::nextQuestion
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizQuestionView(
    uiState: QuizUiState,
    question: QuizQuestion,
    tts: TtsManager,
    haptic: com.ringlearn.app.util.HapticManager,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit
) {
    val selected = uiState.selectedIndex
    val scrollState = rememberScrollState()
    LaunchedEffect(uiState.currentIndex) { scrollState.scrollTo(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${uiState.currentIndex + 1} / ${uiState.total} 题",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "得分 ${uiState.score}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { uiState.currentIndex.toFloat() / uiState.total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )

        Spacer(Modifier.height(16.dp))

        // 题目卡片（紧凑布局，保证小屏无需滚动即可看到全部选项）
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = sakuCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "请选择正确的中文释义",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = question.word.word,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = question.word.kana,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { tts.speak(question.word.word) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_volume),
                            contentDescription = "播放发音",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 选项
        question.options.forEachIndexed { index, option ->
            OptionButton(
                text = option,
                state = when {
                    selected == null -> OptionState.IDLE
                    index == question.correctIndex -> OptionState.CORRECT
                    index == selected -> OptionState.WRONG
                    else -> OptionState.DISABLED
                },
                onClick = {
                    haptic.click()
                    if (index == question.correctIndex) haptic.tick() else haptic.doubleClick()
                    onSelect(index)
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (selected != null) {
            Button(
                onClick = { haptic.click(); onNext() },
                colors = sakuCtaButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(if (uiState.currentIndex + 1 >= uiState.total) "查看成绩" else "下一题")
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

private enum class OptionState { IDLE, CORRECT, WRONG, DISABLED }

@Composable
private fun OptionButton(
    text: String,
    state: OptionState,
    onClick: () -> Unit
) {
    val container = when (state) {
        OptionState.IDLE -> MaterialTheme.colorScheme.surfaceContainerLow
        OptionState.CORRECT -> MaterialTheme.colorScheme.secondaryContainer
        OptionState.WRONG -> MaterialTheme.colorScheme.errorContainer
        OptionState.DISABLED -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when (state) {
        OptionState.CORRECT -> MaterialTheme.colorScheme.onSecondaryContainer
        OptionState.WRONG -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    OutlinedButton(
        onClick = onClick,
        enabled = state == OptionState.IDLE,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = container,
            disabledContentColor = contentColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuizResult(
    score: Int,
    total: Int,
    haptic: com.ringlearn.app.util.HapticManager,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    val percent = if (total > 0) score * 100 / total else 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (percent >= 80) "🏆 太棒了！" else if (percent >= 50) "👍 不错哦！" else "💪 继续加油！",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "$score / $total",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "正确率 $percent%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { haptic.click(); onRestart() },
            colors = sakuCtaButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("再来一轮")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { haptic.click(); onExit() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("返回首页")
        }
    }
}

