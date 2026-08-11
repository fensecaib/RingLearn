package com.ringlearn.app.ui.lookup


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import com.ringlearn.app.R
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.ui.components.EmptyState
import com.ringlearn.app.ui.ime.LocalInAppImeController
import com.ringlearn.app.ui.ime.RingLearnImeField
import com.ringlearn.app.ui.ime.rememberRingLearnImeState
import com.ringlearn.app.ui.rememberHapticManager
import com.ringlearn.app.ui.rememberTtsManager
import com.ringlearn.app.util.HapticManager
import kotlinx.coroutines.flow.distinctUntilChanged

/** 查词页：内置键盘(罗马音)/系统输入法/手写 三种输入方式，实时查询词库。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupScreen(
    viewModel: LookupViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val inputMode by viewModel.inputMode.collectAsStateWithLifecycle()
    val useInAppKeyboard by viewModel.useInAppKeyboard.collectAsStateWithLifecycle()
    val imeComposing by viewModel.imeComposing.collectAsStateWithLifecycle()
    val strokes by viewModel.strokes.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val imeDictionaryCandidates by viewModel.imeDictionaryCandidates.collectAsStateWithLifecycle()
    val recognizerLoading by viewModel.recognizerLoading.collectAsStateWithLifecycle()
    val haptic = rememberHapticManager()
    val listState = rememberLazyListState()
    val tts = rememberTtsManager()

    // 输入框状态：与 ViewModel 双向同步（内置键盘直接编辑 state）
    val textFieldState = remember { TextFieldState() }
    // IME 组合期间不触发查询：内置键盘组合由引擎回调门控，系统输入法组合读取 state.composition
    LaunchedEffect(textFieldState, useInAppKeyboard) {
        snapshotFlow {
            val systemComposing = textFieldState.composition?.takeIf { !it.collapsed } != null
            Triple(textFieldState.text.toString(), systemComposing, imeComposing)
        }
            .distinctUntilChanged()
            .collect { (text, systemComposing, imeComposing) ->
                val composing = if (useInAppKeyboard) imeComposing else systemComposing
                viewModel.onFieldChanged(text, composing)
            }
    }
    // 查询变化时回到列表顶部（避免 LazyColumn 锚定旧项导致首卡被顶出视口）
    LaunchedEffect(results) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(query) {
        if (textFieldState.text.toString() != query) {
            textFieldState.edit { replace(0, length, query) }
        }
    }

    // 内置键盘默认收起（点击输入框才弹出，类真实 IME）；由根层 InAppKeyboardOverlay 覆盖渲染
    var keyboardVisible by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = keyboardVisible) {
        haptic.click()
        keyboardVisible = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = { CenterAlignedTopAppBar(title = { Text("查词") }) }
    ) { padding ->
        val ime = rememberRingLearnImeState(
            state = textFieldState,
            onSwitchToSystemIme = viewModel::onSwitchToSystemIme,
            onCompositionChange = viewModel::onCompositionChange,
            onCommit = {}
        )
        // 绑定根层内置键盘覆盖层：键盘可见时把 IME 状态/候选/收起回调交给覆盖层渲染
        val inAppImeController = LocalInAppImeController.current
        val imeActive = useInAppKeyboard && inputMode == LookupInputMode.KEYBOARD && keyboardVisible
        val contentOverflowDp = with(LocalDensity.current) {
            (inAppImeController.contentOverflowPx).coerceAtLeast(0).toDp()
        }
        SideEffect {
            if (imeActive) {
                inAppImeController.ime = ime
                inAppImeController.candidates = imeDictionaryCandidates
                inAppImeController.onCollapse = {
                    haptic.click()
                    keyboardVisible = false
                }
            } else {
                inAppImeController.ime = null
            }
        }
        DisposableEffect(Unit) {
            onDispose { inAppImeController.ime = null }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
        ) {
            // 输入方式切换：键盘 / 手写
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = inputMode == LookupInputMode.KEYBOARD,
                    onClick = { haptic.tick(); viewModel.onModeChange(LookupInputMode.KEYBOARD) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("键盘输入") }
                SegmentedButton(
                    selected = inputMode == LookupInputMode.HANDWRITING,
                    onClick = { haptic.tick(); viewModel.onModeChange(LookupInputMode.HANDWRITING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("手写汉字") }
            }

            when (inputMode) {
                LookupInputMode.KEYBOARD -> {
                    RingLearnImeField(
                        ime = ime,
                        useInAppKeyboard = useInAppKeyboard,
                        onSwitchToInAppKeyboard = viewModel::onSwitchToInAppKeyboard,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = "输入日文或中文释义",
                        keyboardVisible = keyboardVisible,
                        onShowKeyboard = {
                            haptic.click()
                            keyboardVisible = true
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (!keyboardVisible) {
                                IconButton(onClick = {
                                    haptic.click()
                                    keyboardVisible = true
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_keyboard),
                                        contentDescription = "显示键盘"
                                    )
                                }
                            } else if (query.isNotEmpty()) {
                                IconButton(onClick = {
                                    haptic.click()
                                    textFieldState.edit { replace(0, length, "") }
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = "清空"
                                    )
                                }
                            }
                        }
                    )
                }
                LookupInputMode.HANDWRITING -> {
                    HandwritingSection(
                        strokes = strokes,
                        candidates = candidates,
                        recognizerLoading = recognizerLoading,
                        haptic = haptic,
                        onStrokesChange = viewModel::onStrokesChange,
                        onClear = viewModel::clearHandwriting,
                        onCandidate = viewModel::appendCandidate
                    )
                }
            }

            // 结果区：占满中间剩余空间（键盘停靠在底部时不被挤压）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    query.isBlank() -> EmptyState(
                        iconRes = R.drawable.ic_search,
                        title = "输入关键字或手写汉字",
                        subtitle = "支持日文表记、假名读音、中文释义查询。\n键盘默认使用内置罗马音键盘，可一键切换系统输入法。"
                    )
                    results.isEmpty() -> EmptyState(
                        iconRes = R.drawable.ic_refresh,
                        title = "没有找到相关单词",
                        subtitle = "换个写法试试：\n今日 / きょう / 今天"
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, top = 8.dp, end = 16.dp,
                            bottom = 12.dp + if (imeActive) contentOverflowDp else 0.dp
                        )
                    ) {
                        items(results, key = { it.id }) { word ->

                            WordLookupCard(
                                word = word,
                                haptic = haptic,
                                onSpeak = { tts.speak(word.word) },
                                onToggleFavorite = { viewModel.onToggleFavorite(word) }
                            )
                        }
                    }
                }
            }

        }
    }
}

/** 手写区：画板 + 候选字 + 清空 */
@Composable
private fun HandwritingSection(
    strokes: List<List<Pair<Float, Float>>>,
    candidates: List<Char>,
    recognizerLoading: Boolean,
    haptic: HapticManager,
    onStrokesChange: (List<List<Pair<Float, Float>>>) -> Unit,
    onClear: () -> Unit,
    onCandidate: (Char) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        HandwritingPad(
            strokes = strokes,
            onStrokesChange = onStrokesChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "写一个汉字或假名，停笔自动识别",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                haptic.click()
                onClear()
            }) { Text("清空") }
        }
        when {
            recognizerLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "识别器准备中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            candidates.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates) { char ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            haptic.click()
                            onCandidate(char)
                        },
                        label = {
                            Text(
                                text = char.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
            strokes.isNotEmpty() -> Text(
                text = "没有识别到，试试写得更工整一些。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 手写画板：捕获笔画并绘制网格 + 笔画轨迹 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HandwritingPad(
    strokes: List<List<Pair<Float, Float>>>,
    onStrokesChange: (List<List<Pair<Float, Float>>>) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // 手势回调中使用最新 strokes，避免 pointerInput(Unit) 捕获陈旧闭包
    val latestStrokes by rememberUpdatedState(strokes)
    val inkColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val stroke = mutableListOf<Offset>()
                    awaitFirstDown(requireUnconsumed = false).also { stroke += it.position }
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) stroke += change.position
                    } while (event.changes.any { it.pressed })
                    currentStroke = stroke
                    if (stroke.size >= 2) {
                        onStrokesChange(latestStrokes + listOf(stroke.map { it.x to it.y }))
                    }
                    currentStroke = emptyList()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 网格
            val step = 28.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            val strokeStyle = Stroke(
                width = 4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            strokes.forEach { stroke ->
                if (stroke.size >= 2) {
                    val path = Path()
                    stroke.forEachIndexed { i, (px, py) ->
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, inkColor, style = strokeStyle)
                }
            }
            if (currentStroke.size >= 2) {
                val path = Path()
                currentStroke.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, inkColor, style = strokeStyle)
            }
        }
    }
}

/** 查询结果卡片：表记/假名/释义/例句 + 发音 + 收藏 */
@Composable
private fun WordLookupCard(
    word: WordEntity,
    haptic: HapticManager,
    onSpeak: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = word.kana,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "JLPT ${word.jlpt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = word.meaning,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (word.example.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "例：${word.example}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (word.exampleMeaning.isNotBlank()) {
                        Text(
                            text = word.exampleMeaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row {
                IconButton(
                    onClick = {
                        haptic.click()
                        onSpeak()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_volume),
                        contentDescription = "朗读",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.click()
                        onToggleFavorite()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bookmark),
                        contentDescription = if (word.isFavorite) "移出生词本" else "加入生词本",
                        tint = if (word.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


