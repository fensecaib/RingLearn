package com.ringlearn.app.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringlearn.app.R
import com.ringlearn.app.data.ai.AiChatConfig
import com.ringlearn.app.data.ai.DEFAULT_MAX_TOKENS
import com.ringlearn.app.data.ai.SYSTEM_PROMPT_PRESETS
import com.ringlearn.app.data.local.entity.AiChatEntity
import com.ringlearn.app.ui.ime.InAppImeBinding
import com.ringlearn.app.ui.ime.LocalInAppImeController
import com.ringlearn.app.ui.ime.RingLearnImeField
import com.ringlearn.app.ui.ime.RingLearnImeState
import com.ringlearn.app.ui.ime.contentOverflowDp
import com.ringlearn.app.ui.ime.dismissInAppImeOnTap
import com.ringlearn.app.ui.ime.rememberRingLearnImeState
import com.ringlearn.app.ui.rememberHapticManager
import com.ringlearn.app.util.HapticManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 消息时间分组标签格式（java.time 线程安全，仅组合期主线程调用） */
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/**
 * 计算消息时间分组标签：与上一条相隔 ≥5 分钟或跨天时显示；首条必显（带日期）。
 * 纯函数、不读任何状态，不影响重组作用域。
 */
private fun timeHeaderLabel(createdAt: Long, prevCreatedAt: Long?): String? {
    if (createdAt <= 0L) return null
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(createdAt).atZone(zone)
    if (prevCreatedAt == null) return current.format(DATE_TIME_FORMAT)
    val prev = Instant.ofEpochMilli(prevCreatedAt).atZone(zone)
    val gapMinutes = Duration.between(prev, current).toMinutes()
    return when {
        current.toLocalDate() != prev.toLocalDate() -> current.format(DATE_TIME_FORMAT)
        gapMinutes >= 5 -> current.format(TIME_FORMAT)
        else -> null
    }
}

/** AI 对话页：聊天气泡 + 内置键盘/系统输入法 + 上下文统计徽章 + 设置/重置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val useInAppKeyboard by viewModel.useInAppKeyboard.collectAsStateWithLifecycle()
    val haptic = rememberHapticManager()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var showContextInfo by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasMoreOlder by viewModel.hasMoreOlder.collectAsStateWithLifecycle()

    val textFieldState = remember { TextFieldState() }
    var keyboardVisible by rememberSaveable { mutableStateOf(false) }
    val collapseKeyboard = {
        haptic.click()
        keyboardVisible = false
    }
    BackHandler(enabled = keyboardVisible, onBack = collapseKeyboard)

    // 滚动到底：首次加载/新消息追加（末条 id 变化）时，靠近底部则钉到最后一条（考虑「加载更早」哨兵 index 0）
    var lastBottomId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (lastBottomId == null || last.id != lastBottomId) {
            val isInitial = lastBottomId == null
            val info = listState.layoutInfo
            val lastIdx = lastMessageIndex(hasMoreOlder, messages.size)
            // 首次加载始终回到底部；新消息仅在用户原本靠近底部时跟随
            val nearBottom = isInitial || info.totalItemsCount == 0 ||
                ((info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= lastIdx - 2)
            if (nearBottom) listState.scrollToItem(lastIdx)
        }
        lastBottomId = last.id
    }
    // 流式钉底：跟随流式增长滚动到最后一条（仅订阅 streamingText，不引起整屏重组；用户上滑读历史时不打断）
    LaunchedEffect(Unit) {
        viewModel.streamingText.collect {
            val size = viewModel.messages.value.size
            if (size > 0) {
                val lastIdx = lastMessageIndex(viewModel.hasMoreOlder.value, size)
                val info = listState.layoutInfo
                val nearBottom = info.totalItemsCount == 0 ||
                    ((info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= lastIdx - 2)
                if (nearBottom) listState.scrollToItem(lastIdx)
            }
        }
    }
    // 上滑到顶部自动加载更早历史（无限滚动，每页触发一次）
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx -> if (idx <= 1 && hasMoreOlder) viewModel.loadOlder() }
    }

    // 发送：读取输入框文本并清空
    val sendCurrent: () -> Unit = {
        val text = textFieldState.text.toString().trim()
        if (text.isNotEmpty() && !viewModel.sending.value) {
            haptic.click()
            viewModel.send(text)
            textFieldState.edit { replace(0, length, "") }
        }
    }

    CompositionLocalProvider(LocalChatFontScale provides config.chatFontScale) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AI 对话") },
                actions = {
                    ContextBadge(
                        viewModel = viewModel,
                        onClick = {
                            haptic.tick()
                            showContextInfo = !showContextInfo
                        }
                    )
                    Box {
                        IconButton(onClick = {
                            haptic.click()
                            menuExpanded = true
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = "更多"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("AI 设置") },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_settings), contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    haptic.click()
                                    showSettings = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("重置会话") },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    haptic.click()
                                    showResetConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val ime = rememberRingLearnImeState(
            state = textFieldState,
            onSwitchToSystemIme = viewModel::switchToSystemIme,
            onCompositionChange = { _, _ -> },
            onCommit = sendCurrent
        )
        val inAppImeController = LocalInAppImeController.current
        val imeActive = useInAppKeyboard && keyboardVisible
        InAppImeBinding(
            controller = inAppImeController,
            ime = ime,
            candidates = emptyList(),
            active = imeActive,
            onCollapse = collapseKeyboard
        )
        val contentOverflowDp = inAppImeController.contentOverflowDp()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // imePadding 仅系统输入法模式启用：内置键盘模式系统 IME 不连接（blockingInterceptor 拦截），
                // 抬升由 contentOverflowDp 实测负责；部分设备聚焦时 IME insets 误报非零，
                // imePadding 会产生幻影底部 padding，导致收起键盘后键盘区域留白。
                .then(if (useInAppKeyboard) Modifier else Modifier.imePadding())
        ) {
            if (showContextInfo) {
                ContextInfoBanner(viewModel = viewModel, onClose = { showContextInfo = false })
            }
            AiMessagesArea(
                viewModel = viewModel,
                messages = messages,
                hasMoreOlder = hasMoreOlder,
                listState = listState,
                haptic = haptic,
                scope = scope,
                keyboardVisible = keyboardVisible,
                onDismissKeyboard = collapseKeyboard,
                onShowSettings = { showSettings = true }
            )
            // 输入区：内部订阅 sending / 文本非空，发送/停止切换与键入只重组输入行
            AiInputBar(
                viewModel = viewModel,
                ime = ime,
                textFieldState = textFieldState,
                haptic = haptic,
                useInAppKeyboard = useInAppKeyboard,
                keyboardVisible = keyboardVisible,
                onShowKeyboard = {
                    haptic.click()
                    keyboardVisible = true
                },
                onSend = sendCurrent,
                bottomPadding = if (imeActive) contentOverflowDp + 6.dp else 8.dp
            )
        }
    }
    }

    if (showSettings) {
        AiSettingsDialog(
            config = config,
            onTest = viewModel::testConnection,
            onDismiss = { showSettings = false },
            onSave = { baseUrl, apiKey, model, maxTokens, systemPrompt, thinkingEnabled, chatFontScale ->
                haptic.click()
                viewModel.updateConfig(baseUrl, apiKey, model, maxTokens, systemPrompt, thinkingEnabled, chatFontScale)
                showSettings = false
            }
        )
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置会话？") },
            text = { Text("将清空当前对话记录。历史数据会保留在本机（按会话隔离），此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.click()
                    viewModel.resetSession()
                    showResetConfirm = false
                }) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }
}

/** 消息区：内部订阅 sending（空态判定），消息/流式增量不触发整屏重组。 */
@Composable
private fun ColumnScope.AiMessagesArea(
    viewModel: AiChatViewModel,
    messages: List<AiChatEntity>,
    hasMoreOlder: Boolean,
    listState: LazyListState,
    haptic: HapticManager,
    scope: CoroutineScope,
    keyboardVisible: Boolean,
    onDismissKeyboard: () -> Unit,
    onShowSettings: () -> Unit
) {
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .dismissInAppImeOnTap(enabled = keyboardVisible, onDismiss = onDismissKeyboard)
    ) {
        if (messages.isEmpty() && !sending) {
            AiWelcomeEmpty(onOpenSettings = onShowSettings)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 短会话贴底（聊天范式）：内容不足视口时整体贴底，键盘收起后原键盘区域不留白；
                // 内容≥视口时无剩余空间，排列不生效，滚动/吸底逻辑不变。
                verticalArrangement = Arrangement.Bottom,
                contentPadding = PaddingValues(
                    start = 12.dp, top = 8.dp, end = 12.dp,
                    bottom = 12.dp
                )
            ) {
                if (hasMoreOlder) {
                    item(key = "load_older") { LoadMoreItem() }
                }
                val lastId = messages.lastOrNull()?.id
                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        timeHeaderLabel(msg.createdAt, messages.getOrNull(index - 1)?.createdAt)
                            ?.let { label ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        if (msg.id == lastId) {
                            // 最后一条（助手流式）在此订阅 streamingText：增量只重组本气泡，避免整屏重组
                            StreamingBubble(
                                viewModel = viewModel,
                                message = msg,
                                onRetry = viewModel::retry
                            )
                        } else {
                            ChatBubble(
                                message = msg,
                                liveText = null,
                                onRetry = viewModel::retry
                            )
                        }
                    }
                }
            }
        }
        // 浮动「回到底部」圆钮：不在最后一条时显示（↑ 已移除）
        val atBottom by remember { derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount == 0 ||
                ((info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= lastMessageIndex(hasMoreOlder, messages.size))
        } }
        if (!atBottom) {
            Surface(
                onClick = {
                    haptic.click()
                    scope.launch { listState.scrollToItem(lastMessageIndex(hasMoreOlder, messages.size)) }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(40.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "↓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 空会话欢迎区：圆形图标 + 标题/副标题 + 静态示例提示卡（无交互）。 */
@Composable
private fun AiWelcomeEmpty(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "开始 AI 对话",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "发送日语或中文句子，AI 帮你翻译、讲解语法、解析单词。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = "例如：",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "こんにちは",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "「これは何ですか」是什么意思？",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenSettings) {
            Text("打开设置")
        }
    }
}

/** 输入行：内部订阅 sending 与文本非空，发送/停止切换、每次键入只重组本行。 */
@Composable
private fun AiInputBar(
    viewModel: AiChatViewModel,
    ime: RingLearnImeState,
    textFieldState: TextFieldState,
    haptic: HapticManager,
    useInAppKeyboard: Boolean,
    keyboardVisible: Boolean,
    onShowKeyboard: () -> Unit,
    onSend: () -> Unit,
    bottomPadding: Dp
) {
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 内置键盘弹出时抬升到键盘（含候选栏）正上方，避免遮挡输入框
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RingLearnImeField(
            ime = ime,
            useInAppKeyboard = useInAppKeyboard,
            modifier = Modifier.weight(1f),
            placeholder = "输入句子，问问 AI…",
            keyboardVisible = keyboardVisible,
            onSwitchToInAppKeyboard = viewModel::switchToInAppKeyboard,
            onShowKeyboard = onShowKeyboard
        )
        Spacer(Modifier.width(8.dp))
        if (sending) {
            IconButton(
                onClick = {
                    haptic.click()
                    viewModel.stop()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "停止生成",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = textFieldState.text.isNotEmpty(),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = "发送",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 列表顶部「加载更早消息」占位（hasMoreOlder 时显示，上滑到顶部自动触发加载）。 */
@Composable
private fun LoadMoreItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "加载更早消息…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
/** 上下文统计状态徽章：N 轮 · M chars，点击展开说明。内部订阅 contextStats（仅徽章随流式增量重组）。 */
@Composable
private fun ContextBadge(
    viewModel: AiChatViewModel,
    onClick: () -> Unit
) {
    val stats by viewModel.contextStats.collectAsStateWithLifecycle()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_context),
                contentDescription = "上下文统计",
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatContextStats(stats),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** 上下文说明横幅：完整上下文已发送、system 提示词固定于首条（不压缩）。内部订阅 contextStats。 */
@Composable
private fun ContextInfoBanner(
    viewModel: AiChatViewModel,
    onClose: () -> Unit
) {
    val stats by viewModel.contextStats.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_context),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "上下文统计：${formatContextStats(stats)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "完整上下文已发送：system 提示词固定于首条，历史消息全部随请求携带（不压缩）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "关闭",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 最后一条气泡：内部订阅流式文本，流式增量只重组本气泡（避免整屏重组）。 */
@Composable
private fun StreamingBubble(
    viewModel: AiChatViewModel,
    message: AiChatEntity,
    onRetry: () -> Unit
) {
    val liveText by viewModel.streamingText.collectAsStateWithLifecycle()
    ChatBubble(
        message = message,
        liveText = liveText,
        onRetry = onRetry
    )
}
/** 聊天气泡：用户右对齐纯文本；助手左对齐 Markdown；错误行红色容器 + 重试。 */
@Composable
private fun ChatBubble(
    message: AiChatEntity,
    liveText: String?,
    onRetry: () -> Unit
) {
    val isUser = message.role == "user"
    val isError = message.isError && liveText == null
    val container = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // 助手/错误消息的角色圆形标
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 8.dp)
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.78f else 0.88f)
                .clip(bubbleShape)
                .background(container)
                .then(
                    if (!isUser && !isError) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, bubbleShape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val fullText = liveText ?: message.content
            // 超长已完成的助手消息默认截断展示，控制单气泡首绘成本（用户可「展开全文」）
            val isStreaming = liveText != null
            var expanded by remember(message.id) { mutableStateOf(false) }
            val MAX_DISPLAY_CHARS = 1000
            val truncated = !isStreaming && !expanded && fullText.length > MAX_DISPLAY_CHARS
            val text = if (truncated) fullText.take(MAX_DISPLAY_CHARS) + "…" else fullText
            when {
                liveText != null && liveText.isEmpty() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在思考…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                isUser -> Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * LocalChatFontScale.current
                    ),
                    color = contentColor
                )
                else -> if (liveText != null) {
                    // 流式中：轻量行内渲染（避免长文本块级 Markdown 布局的主线程开销）
                    InlineMarkdownText(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                } else {
                    MarkdownText(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
            if (truncated) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.padding(top = 2.dp)) {
                    Text("展开全文")
                }
            }
            if (isError) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) {
                    Text("重试")
                }
            }
        }
    }
}

/** AI 设置对话框：baseUrl / apiKey / model / maxTokens / 系统提示词预设 + 测试连接。 */
@Composable
private fun AiSettingsDialog(
    config: AiChatConfig,
    onTest: (AiChatConfig, (Result<String>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (baseUrl: String, apiKey: String, model: String, maxTokens: Int, systemPrompt: String, thinkingEnabled: Boolean, chatFontScale: Float) -> Unit
) {
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var model by remember { mutableStateOf(config.model) }
    var maxTokens by remember { mutableStateOf(config.maxTokens.toString()) }
    var systemPrompt by remember { mutableStateOf(config.systemPrompt) }
    var thinkingEnabled by remember { mutableStateOf(config.thinkingEnabled) }
    var chatFontScale by remember { mutableStateOf(config.chatFontScale) }
    var presetName by remember {
        mutableStateOf(SYSTEM_PROMPT_PRESETS.firstOrNull { it.second == config.systemPrompt }?.first ?: "自定义")
    }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val maxTokensInt = maxTokens.toIntOrNull()?.coerceIn(128, 8192) ?: DEFAULT_MAX_TOKENS
    val candidate = AiChatConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        maxTokens = maxTokensInt,
        systemPrompt = systemPrompt
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 设置") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp)
            ) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter(Char::isDigit).take(5) },
                    label = { Text("最大输出 Tokens") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "字号",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { chatFontScale = (chatFontScale - 0.05f).coerceAtLeast(0.85f) },
                        enabled = chatFontScale > 0.85f
                    ) { Text("A-") }
                    Text(
                        text = "${(chatFontScale * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { chatFontScale = (chatFontScale + 0.05f).coerceAtMost(1.3f) },
                        enabled = chatFontScale < 1.3f
                    ) { Text("A+") }
                    OutlinedButton(
                        onClick = { chatFontScale = 1f }
                    ) { Text("重置") }
                }
                Spacer(Modifier.height(12.dp))
                // 深度思考开关：默认关闭（V4 思考模式可能把 max_tokens 全耗在 reasoning 上导致空回复）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "深度思考",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "开启更细致但更慢，可能因思考过长返回空内容；默认关闭更稳更快",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = thinkingEnabled,
                        onCheckedChange = { thinkingEnabled = it }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "系统提示词预设",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    SYSTEM_PROMPT_PRESETS.forEach { (name, content) ->
                        FilterChip(
                            selected = presetName == name,
                            onClick = {
                                presetName = name
                                if (name != "自定义") systemPrompt = content
                            },
                            label = { Text(name) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(if (presetName == "自定义") "自定义提示词" else "提示词内容") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            testing = true
                            testResult = null
                            onTest(candidate) { result ->
                                testing = false
                                testResult = result.fold(
                                    onSuccess = { "连接成功：${it.take(60)}" },
                                    onFailure = { "连接失败：${it.message?.take(80) ?: "未知错误"}" }
                                )
                            }
                        },
                        enabled = !testing
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (testing) "测试中…" else "测试连接")
                    }
                }
                testResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("连接成功")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(baseUrl.trim(), apiKey.trim(), model.trim(), maxTokensInt, systemPrompt.trim(), thinkingEnabled, chatFontScale)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
