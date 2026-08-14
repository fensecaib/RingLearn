package com.ringlearn.app.ui.study

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringlearn.app.R
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.ui.components.EmptyState
import com.ringlearn.app.ui.components.LoadingState
import com.ringlearn.app.ui.components.SakuTopBar
import com.ringlearn.app.ui.LocalActiveRoute
import com.ringlearn.app.ui.navigation.StudyKey
import com.ringlearn.app.ui.rememberHapticManager
import com.ringlearn.app.ui.rememberTtsManager
import com.ringlearn.app.util.TtsManager
import com.ringlearn.app.util.HapticManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 单词学习页：3D 翻转卡片 + 滑动手势 + TTS 发音 + 本轮统计弹窗。
 */
@Composable
fun StudyScreen(
    onExit: () -> Unit,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberHapticManager()
    val snackbarHostState = remember { SnackbarHostState() }
    // 应用级 TTS 单例：进出页面不重建、不销毁，避免切换卡顿
    val tts = rememberTtsManager()

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    // 实时计时器：仅在学习 Tab 处于激活态时更新，避免 keep-alive 下隐藏时每秒重组
    val activeRoute = LocalActiveRoute.current.value
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(uiState.sessionStartAt, uiState.roundFinished, activeRoute) {
        while (uiState.sessionStartAt > 0 && !uiState.roundFinished) {
            if (activeRoute == StudyKey) {
                elapsedSeconds = (System.currentTimeMillis() - uiState.sessionStartAt) / 1000
            }
            delay(1000)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SakuTopBar(
                title = { Text("开始背词") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (!uiState.isLoading && !uiState.roundFinished && uiState.sessionStartAt > 0) {
                        Text(
                            text = formatDuration(elapsedSeconds),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> LoadingState(message = "正在准备本轮单词…")

                uiState.empty -> EmptyState(
                    iconRes = R.drawable.ic_check,
                    title = "今日任务已完成",
                    subtitle = "没有待学习的单词了。\n可以先去生词本复习收藏的词条，或做一轮随机测验。",
                    actionLabel = "返回首页",
                    onAction = onExit
                )

                else -> {
                    val word = uiState.currentWord
                    if (word != null) {
                        StudyContent(
                            state = uiState,
                            word = word,
                            tts = tts,
                            haptic = haptic,
                            onKnown = viewModel::onSwipeKnown,
                            onUnknown = viewModel::onSwipeUnknown,
                            onToWordbook = viewModel::onSwipeToWordbook
                        )
                    }
                }
            }
        }
    }

    if (uiState.roundFinished) {
        RoundStatsDialog(
            state = uiState,
            elapsedSeconds = elapsedSeconds,
            onNextRound = { viewModel.startSession() },
            onExit = onExit
        )
    }
}

@Composable
private fun StudyContent(
    state: StudyUiState,
    word: WordEntity,
    tts: TtsManager,
    haptic: HapticManager,
    onKnown: () -> Unit,
    onUnknown: () -> Unit,
    onToWordbook: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // 顶部进度
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${state.currentIndex + 1} / ${state.total} 个",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "认识 ${state.correctCount} · 不认识 ${state.wrongCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { if (state.total > 0) state.currentIndex.toFloat() / state.total else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )

        Spacer(Modifier.height(8.dp))

        // 卡片
        SwipeableWordCard(
            word = word,
            autoSpeakEnabled = state.autoSpeakEnabled,
            tts = tts,
            haptic = haptic,
            onKnown = onKnown,
            onUnknown = onUnknown,
            onToWordbook = onToWordbook,
            modifier = Modifier.weight(1f)
        )

        // 底部手势提示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = "← 不认识",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "↑ 生词本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "认识 →",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun RoundStatsDialog(
    state: StudyUiState,
    elapsedSeconds: Long,
    onNextRound: () -> Unit,
    onExit: () -> Unit
) {
    val accuracy = (state.accuracy * 100).roundToInt()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("本轮学习完成 🎉") },
        text = {
            Column {
                StatsLine("本轮单词", "${state.total} 个")
                StatsLine("认识", "${state.correctCount}", color = MaterialTheme.colorScheme.secondary)
                StatsLine("不认识", "${state.wrongCount}", color = MaterialTheme.colorScheme.error)
                StatsLine("收进生词本", "${state.favoriteCount}", color = MaterialTheme.colorScheme.tertiary)
                StatsLine("正确率", "$accuracy%")
                StatsLine("用时", formatDuration(elapsedSeconds))
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "每个单词的下次复习时间已按 SM-2 算法自动安排到 Room 数据库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onNextRound) { Text("再来一组") }
        },
        dismissButton = {
            TextButton(onClick = onExit) { Text("返回首页") }
        }
    )
}

@Composable
private fun StatsLine(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

