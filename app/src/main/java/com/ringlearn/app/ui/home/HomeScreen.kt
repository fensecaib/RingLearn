package com.ringlearn.app.ui.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringlearn.app.R
import com.ringlearn.app.ui.components.CircularProgressRing
import com.ringlearn.app.ui.LocalActiveRoute
import com.ringlearn.app.ui.components.EmptyState
import com.ringlearn.app.ui.components.FlameIcon
import com.ringlearn.app.ui.components.LoadingState
import com.ringlearn.app.ui.components.SakuTopBar
import com.ringlearn.app.ui.components.sakuCardBorder
import com.ringlearn.app.ui.components.sakuCardColors
import com.ringlearn.app.ui.navigation.HomeKey
import com.ringlearn.app.ui.rememberHapticManager
import com.ringlearn.app.ui.theme.FredokaBold
import com.ringlearn.app.util.HapticManager
import kotlin.math.roundToInt

/**
 * 首页：学习进度环形图、连续学习火焰、待复习角标、
 * 快捷操作、每日目标滑块与进度重置；顶栏齿轮进入设置页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToStudy: () -> Unit,
    onNavigateToWordBook: () -> Unit,
    onNavigateToQuiz: () -> Unit,
    onNavigateToLookup: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 性能采集：首页数据就绪后上报 fully drawn（TTFD），供 `am start -W`/媒体测量完整启动路径
    ReportDrawnWhen { uiState.stats.isReady }
    val haptic = rememberHapticManager()
    val snackbarHostState = remember { SnackbarHostState() }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SakuTopBar(
                containerColor = MaterialTheme.colorScheme.background,
                title = {
                    Text(
                        text = "RingLearn",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FredokaBold,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { haptic.click(); onNavigateToSettings() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "设置",
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
                uiState.isLoading -> LoadingState()

                uiState.error != null -> EmptyState(
                    iconRes = R.drawable.ic_refresh,
                    title = "加载失败",
                    subtitle = uiState.error ?: "请稍后重试",
                    actionLabel = "重试",
                    onAction = {}
                )

                else -> HomeContent(
                    state = uiState,
                    haptic = haptic,
                    onStartStudy = onNavigateToStudy,
                    onOpenWordBook = onNavigateToWordBook,
                    onOpenQuiz = onNavigateToQuiz,
                    onOpenLookup = onNavigateToLookup,
                    onSetDailyGoal = viewModel::setDailyGoal,
                    onResetProgress = viewModel::resetProgress,
                    onShowResetDialog = { showResetDialog = true }
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("重置学习进度") },
            text = { Text("确定要清空所有学习进度、复习记录和生词本吗？\n此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.heavy()
                        viewModel.resetProgress()
                        showResetDialog = false
                    }
                ) {
                    Text("重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    haptic: HapticManager,
    onStartStudy: () -> Unit,
    onOpenWordBook: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenLookup: () -> Unit,
    onSetDailyGoal: (Int) -> Unit,
    onResetProgress: () -> Unit,
    onShowResetDialog: () -> Unit
) {
    val settings = state.settings
    val stats = state.stats

    // LazyColumn：首屏只合成可见项，下方重置入口滚动到才合成，降低切换进首页的合成成本
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ProgressSection(
                learnedToday = stats.learnedToday,
                goal = settings.dailyGoal
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreakCard(
                    streakDays = stats.streakDays,
                    modifier = Modifier.weight(1f)
                )
                DueBadgeCard(
                    dueCount = stats.dueCount,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            QuickActionsRow(
                onStartStudy = { haptic.click(); onStartStudy() },
                onOpenWordBook = { haptic.click(); onOpenWordBook() },
                onOpenQuiz = { haptic.click(); onOpenQuiz() },
                onOpenLookup = { haptic.click(); onOpenLookup() }
            )
        }

        item {
            GoalCard(
                goal = settings.dailyGoal,
                onGoalChange = { onSetDailyGoal(it.roundToInt()) }
            )
        }

        item {
            OutlinedButton(
                onClick = onShowResetDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("重置所有学习进度")
            }
        }
    }
}

@Composable
private fun ProgressSection(learnedToday: Int, goal: Int) {
    val progress = if (goal > 0) (learnedToday.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = sakuCardColors(),
        border = sakuCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressRing(progress = progress, ringSize = 190.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "今日学习",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$learnedToday / $goal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (learnedToday >= goal) {
                    "🎉 今日目标已完成，太棒了！"
                } else {
                    "已学新词 $learnedToday 个 · 目标 $goal 个"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StreakCard(streakDays: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        border = sakuCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlameIcon(
                modifier = Modifier.size(40.dp),
                active = LocalActiveRoute.current.value == HomeKey
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "连续学习",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "$streakDays 天",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun DueBadgeCard(dueCount: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        border = sakuCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (dueCount > 0) {
                        Badge { Text(if (dueCount > 99) "99+" else "$dueCount") }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_notifications),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "待复习单词",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = if (dueCount > 0) "$dueCount 个到期" else "暂无到期",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onStartStudy: () -> Unit,
    onOpenWordBook: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenLookup: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickActionCard(
            icon = R.drawable.ic_play,
            label = "开始背词",
            modifier = Modifier.weight(1f),
            onClick = onStartStudy,
            accent = true
        )
        QuickActionCard(
            icon = R.drawable.ic_wordbook,
            label = "生词本",
            modifier = Modifier.weight(1f),
            onClick = onOpenWordBook
        )
        QuickActionCard(
            icon = R.drawable.ic_quiz,
            label = "随机测验",
            modifier = Modifier.weight(1f),
            onClick = onOpenQuiz
        )
        QuickActionCard(
            icon = R.drawable.ic_search,
            label = "查词",
            modifier = Modifier.weight(1f),
            onClick = onOpenLookup
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionCard(
    icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    accent: Boolean = false
) {
    val container = if (accent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val content = if (accent) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconTint = if (accent) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
        border = sakuCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content
            )
        }
    }
}

@Composable
private fun GoalCard(goal: Int, onGoalChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = sakuCardColors(),
        border = sakuCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "每日学习目标",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$goal 个",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = goal.toFloat(),
                onValueChange = onGoalChange,
                valueRange = 10f..100f,
                steps = 8
            )
            Text(
                text = "10 - 100 个 / 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
