package com.ringlearn.app.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringlearn.app.R
import com.ringlearn.app.data.ai.AiChatConfig
import com.ringlearn.app.data.ai.DEFAULT_MAX_TOKENS
import com.ringlearn.app.data.ai.SYSTEM_PROMPT_PRESETS
import com.ringlearn.app.data.local.entity.AiChatEntity
import com.ringlearn.app.ui.components.EmptyState
import com.ringlearn.app.ui.ime.InAppImeBinding
import com.ringlearn.app.ui.ime.LocalInAppImeController
import com.ringlearn.app.ui.ime.RingLearnImeField
import com.ringlearn.app.ui.ime.contentOverflowDp
import com.ringlearn.app.ui.ime.rememberRingLearnImeState
import com.ringlearn.app.ui.rememberHapticManager

/** AI 对话页：聊天气泡 + 内置键盘/系统输入法 + 上下文统计徽章 + 设置/重置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val contextStats by viewModel.contextStats.collectAsStateWithLifecycle()
    val useInAppKeyboard by viewModel.useInAppKeyboard.collectAsStateWithLifecycle()
    val haptic = rememberHapticManager()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var showContextInfo by rememberSaveable { mutableStateOf(false) }

    val textFieldState = remember { TextFieldState() }
    var keyboardVisible by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = keyboardVisible) {
        haptic.click()
        keyboardVisible = false
    }

    // 新消息出现时滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 发送：读取输入框文本并清空
    val sendCurrent: () -> Unit = {
        val text = textFieldState.text.toString().trim()
        if (text.isNotEmpty() && !sending) {
            haptic.click()
            viewModel.send(text)
            textFieldState.edit { replace(0, length, "") }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AI 对话") },
                actions = {
                    ContextBadge(
                        stats = contextStats,
                        onClick = {
                            haptic.tick()
                            showContextInfo = !showContextInfo
                        }
                    )
                    IconButton(onClick = {
                        haptic.click()
                        showSettings = true
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "AI 设置"
                        )
                    }
                    IconButton(onClick = {
                        haptic.click()
                        showResetConfirm = true
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "重置会话"
                        )
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
            onCollapse = {
                haptic.click()
                keyboardVisible = false
            }
        )
        val contentOverflowDp = inAppImeController.contentOverflowDp()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (showContextInfo) {
                ContextInfoBanner(stats = contextStats, onClose = { showContextInfo = false })
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && !sending && streamingText == null) {
                    EmptyState(
                        iconRes = R.drawable.ic_ai,
                        title = "开始 AI 对话",
                        subtitle = "发送日语或中文句子，AI 帮你翻译、讲解语法、解析单词。\n配置与 API Key 在右上角设置中完成。",
                        actionLabel = "打开设置",
                        onAction = { showSettings = true }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp, top = 8.dp, end = 12.dp,
                            bottom = 12.dp + if (imeActive) contentOverflowDp else 0.dp
                        )
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatBubble(
                                message = msg,
                                liveText = if (msg.id == messages.lastOrNull()?.id) streamingText else null,
                                onRetry = viewModel::retry
                            )
                        }
                    }
                }
            }
            // 输入区：复用内置罗马音键盘 / 系统输入法
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RingLearnImeField(
                    ime = ime,
                    useInAppKeyboard = useInAppKeyboard,
                    modifier = Modifier.weight(1f),
                    placeholder = "输入句子，问问 AI…",
                    keyboardVisible = keyboardVisible,
                    onSwitchToInAppKeyboard = viewModel::switchToInAppKeyboard,
                    onShowKeyboard = {
                        haptic.click()
                        keyboardVisible = true
                    }
                )
                Spacer(Modifier.width(8.dp))
                if (sending) {
                    IconButton(onClick = {
                        haptic.click()
                        viewModel.stop()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "停止生成",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = sendCurrent,
                        enabled = textFieldState.text.isNotEmpty()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_send),
                            contentDescription = "发送",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        AiSettingsDialog(
            config = config,
            onTest = viewModel::testConnection,
            onDismiss = { showSettings = false },
            onSave = { baseUrl, apiKey, model, maxTokens, systemPrompt ->
                haptic.click()
                viewModel.updateConfig(baseUrl, apiKey, model, maxTokens, systemPrompt)
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

/** 上下文统计状态徽章：N 轮 · M chars，点击展开说明。 */
@Composable
private fun ContextBadge(stats: ContextStats, onClick: () -> Unit) {
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

/** 上下文说明横幅：完整上下文已发送、system 提示词固定于首条（不压缩）。 */
@Composable
private fun ContextInfoBanner(stats: ContextStats, onClose: () -> Unit) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 1f)
                .clip(RoundedCornerShape(16.dp))
                .background(container)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val text = liveText ?: message.content
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
                else -> MarkdownText(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
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
    onSave: (baseUrl: String, apiKey: String, model: String, maxTokens: Int, systemPrompt: String) -> Unit
) {
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var model by remember { mutableStateOf(config.model) }
    var maxTokens by remember { mutableStateOf(config.maxTokens.toString()) }
    var systemPrompt by remember { mutableStateOf(config.systemPrompt) }
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
                    onSave(baseUrl.trim(), apiKey.trim(), model.trim(), maxTokensInt, systemPrompt.trim())
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

