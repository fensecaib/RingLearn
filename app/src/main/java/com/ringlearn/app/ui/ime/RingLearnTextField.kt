@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)

package com.ringlearn.app.ui.ime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ringlearn.app.R
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.domain.ime.RomajiEngine
import com.ringlearn.app.ui.theme.SakuFieldBg
import com.ringlearn.app.util.HapticManager
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max

/**
 * 应用内 IME 状态：罗马音引擎 + 布局/组合 UI 状态 + 按键处理。
 * 由 [rememberRingLearnImeState] 创建并接管外部文本收养与光标离开组合区自动提交。
 *
 * 设计要点：字段（[RingLearnImeField]）与键盘（[RomajiKeyboard]）解耦，
 * 屏幕可把候选条与键盘停靠在底部（类真实 IME），字段置于顶部，中间留给结果区。
 */
@Stable
class RingLearnImeState internal constructor(
    internal val state: TextFieldState,
    internal val engine: RomajiEngine,
    internal val blockingInterceptor: PlatformTextInputInterceptor,
    private val onSwitchToSystemIme: () -> Unit,
    private val onCompositionChange: (composing: Boolean, kana: String) -> Unit,
    private val onCommit: () -> Unit
) {
    var keyboardLayout by mutableStateOf(KeyboardLayout.QWERTY)
        internal set
    var kanaMode by mutableStateOf(engine.mode)
        internal set
    var composing by mutableStateOf(false)
        internal set
    var compositionKana by mutableStateOf("")
        internal set

    /** 组合区 [start, end)，供 OutputTransformation 绘制下划线 */
    internal var compositionRange by mutableStateOf<IntRange?>(null)

    /** 把引擎状态镜像到字段（组合区下划线 + 光标）并同步外部状态。 */
    internal fun syncFromEngine() {
        val full = engine.fullText
        compositionRange = if (engine.isComposing) engine.compositionStart until engine.compositionEnd else null
        composing = engine.isComposing
        compositionKana = engine.compositionKana
        kanaMode = engine.mode
        onCompositionChange(engine.isComposing, engine.compositionKana)
        state.edit {
            if (toString() != full) replace(0, length, full)
            moveCursorTo(length)
        }
    }

    /** 外部文本变化（清空、手写追加、系统输入法输入）→ 引擎收养，丢弃组合避免漂移。 */
    internal fun adoptExternalText(text: String) {
        engine.adoptText(text)
        composing = false
        compositionKana = ""
        compositionRange = null
        onCompositionChange(false, "")
    }

    /** 按键处理：字母 / 假名 / 退格 / 转换 / 平片切换 / 布局 / 系统输入法 / 确定。 */
    fun handleKey(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Letter -> engine.input(key.char)
            is KeyboardKey.Kana -> engine.inputKana(key.kana)
            KeyboardKey.Backspace -> engine.backspace()
            KeyboardKey.Convert -> if (engine.isComposing) engine.space() else engine.input(' ')
            KeyboardKey.ToggleKanaMode -> engine.toggleMode()
            KeyboardKey.ToggleLayout ->
                keyboardLayout = if (keyboardLayout == KeyboardLayout.QWERTY) KeyboardLayout.KANA else KeyboardLayout.QWERTY
            KeyboardKey.SwitchToSystemIme -> {
                if (engine.isComposing) engine.commit()
                syncFromEngine()
                onSwitchToSystemIme()
            }
            KeyboardKey.Commit -> {
                if (engine.isComposing) engine.commit()
                syncFromEngine()
                onCommit()
                // onCommit 可能清空了字段（如 AI 发送）：同步收养外部文本，避免尾部 syncFromEngine
                // 把尚未重置的引擎旧文本写回输入框（异步 snapshotFlow 收养来不及生效导致回写）。
                if (state.text.toString() != engine.fullText) {
                    adoptExternalText(state.text.toString())
                }
            }
        }
        syncFromEngine()
    }

    /** 选中候选：以 [text] 替换组合区并提交。 */
    fun commitCandidate(text: String) {
        engine.commitCandidate(text)
        syncFromEngine()
    }
}

/** 创建并接管 [RingLearnImeState] 的生命周期（外部文本收养 + 光标离开组合区自动提交）。 */
@Composable
fun rememberRingLearnImeState(
    state: TextFieldState,
    onSwitchToSystemIme: () -> Unit,
    onCompositionChange: (composing: Boolean, kana: String) -> Unit = { _, _ -> },
    onCommit: () -> Unit = {}
): RingLearnImeState {
    val currentSwitchToSystemIme by rememberUpdatedState(onSwitchToSystemIme)
    val currentCompositionChange by rememberUpdatedState(onCompositionChange)
    val currentCommit by rememberUpdatedState(onCommit)
    val ime = remember {
        RingLearnImeState(
            state = state,
            engine = RomajiEngine(),
            // 内置键盘模式：拦截系统文本输入会话（不连接 IME → 系统键盘不弹出，由应用内键盘独占输入）。
            // 取舍说明（2026-08 审查）：曾尝试「放行长按粘贴」——但 PlatformTextInputInterceptor 仅有
            // interceptStartInputMethod 一个入口，收到剪贴板粘贴的唯一方式是 session.startInputMethod()
            // 连接 IME，而连接必然弹出系统键盘（双重键盘 + 输入漂移）。API 无法区分「粘贴提交」与
            // 「按键提交」，故保持拦截；需要粘贴时用户可一键切到系统输入法模式（⌨ 键）。
            blockingInterceptor = PlatformTextInputInterceptor { _, _ -> awaitCancellation() },
            onSwitchToSystemIme = { currentSwitchToSystemIme() },
            onCompositionChange = { composing, kana -> currentCompositionChange(composing, kana) },
            onCommit = { currentCommit() }
        )
    }
    // 外部文本变化（清空、手写追加、系统输入法输入）→ 引擎收养
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != ime.engine.fullText) ime.adoptExternalText(text)
            }
    }
    // 光标移出组合区 → 自动提交组合（对齐真实 IME）
    LaunchedEffect(state) {
        snapshotFlow { state.selection to ime.compositionRange }
            .collect { (sel, range) ->
                if (range != null && ime.engine.isComposing) {
                    val selEnd = max(sel.start, sel.end)
                    if (selEnd < range.last) {
                        ime.engine.commit()
                        ime.syncFromEngine()
                    }
                }
            }
    }
    return ime
}

/** 输入框外壳（不含键盘）：字段 + 前后图标 + 系统输入法切换图标。 */
@Composable
fun RingLearnImeField(
    ime: RingLearnImeState,
    useInAppKeyboard: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onSwitchToInAppKeyboard: (() -> Unit)? = null,
    keyboardVisible: Boolean = true,
    onShowKeyboard: (() -> Unit)? = null,
    maxLines: Int = 1,
    minHeight: Dp = 52.dp,
    // 浅色用参考站输入框底 #FAFCFF，深色复用 surfaceContainerHigh（#2C4A5A）；与规范一致。
    containerColor: Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        SakuFieldBg
    },
    containerShape: Shape = RoundedCornerShape(16.dp),
    // 默认 12% 墨色描边（outlineVariant 已映射为 SakuCardBorder），焦点青描边由调用方显式传入。
    containerBorder: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    glowColor: Color = Color.Transparent,
    glowElevation: Dp = 0.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    // 组合区下划线（公开 API：OutputTransformation + addStyle，lambda 接收者为 TextFieldBuffer）
    val compositionStyle = SpanStyle(
        color = accentColor,
        textDecoration = TextDecoration.Underline
    )
    val outputTransformation = OutputTransformation {
        val range = ime.compositionRange
        if (range != null && range.last <= length) {
            addStyle(compositionStyle, range.first, range.last)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (maxLines > 1) Modifier.heightIn(min = minHeight) else Modifier.height(minHeight))
            .then(
                if (glowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = glowElevation,
                        shape = containerShape,
                        ambientColor = glowColor,
                        spotColor = glowColor
                    )
                } else {
                    Modifier
                }
            )
            .background(
                color = containerColor,
                shape = containerShape
            )
            .then(
                if (containerBorder != null) {
                    Modifier.border(containerBorder, containerShape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon?.let {
            Box(Modifier.padding(end = 10.dp)) { it() }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (ime.state.text.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    color = placeholderColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            val textField: @Composable () -> Unit = {
                BasicTextField(
                    state = ime.state,
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = if (maxLines > 1) {
                        TextFieldLineLimits.MultiLine(1, maxLines)
                    } else {
                        TextFieldLineLimits.SingleLine
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = textColor
                    ),
                    cursorBrush = SolidColor(accentColor),
                    outputTransformation = outputTransformation
                )
            }
            if (useInAppKeyboard) {
                InterceptPlatformTextInput(ime.blockingInterceptor) { textField() }
            } else {
                textField()
            }
            // 键盘收起态：点击字段本身重新展开内置键盘
            if (useInAppKeyboard && !keyboardVisible && onShowKeyboard != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onShowKeyboard)
                )
            }
        }
        trailingIcon?.let {
            Box(Modifier.padding(start = 10.dp)) { it() }
        }
        if (!useInAppKeyboard && onSwitchToInAppKeyboard != null) {
            IconButton(onClick = onSwitchToInAppKeyboard) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard),
                    contentDescription = "切回内置键盘",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 转换候选条：假名本身 + 片假名 + 词库候选；与键盘一起停靠在屏幕底部。 */
@Composable
fun RingLearnImeCandidateBar(
    ime: RingLearnImeState,
    imeDictionaryCandidates: List<WordEntity>,
    haptic: HapticManager,
    modifier: Modifier = Modifier
) {
    val candidateList = remember(ime.compositionKana, imeDictionaryCandidates) {
        val kana = ime.compositionKana
        if (kana.isBlank()) emptyList()
        else buildList {
            add(ImeCandidate(text = kana))
            val kata = RomajiEngine.toKatakana(kana)
            if (kata != kana) add(ImeCandidate(text = kata, kana = kana))
            imeDictionaryCandidates.take(6).forEach { w ->
                add(ImeCandidate(text = w.word, kana = w.kana))
            }
        }.take(8)
    }
    CandidateBar(
        candidates = if (ime.composing) candidateList else emptyList(),
        haptic = haptic,
        onSelect = { ime.commitCandidate(it.text) },
        modifier = modifier
    )
}

/** 安全地把光标移动到 [pos]（处理空文本与边界）。 */
private fun TextFieldBuffer.moveCursorTo(pos: Int) {
    if (length == 0) return
    when {
        pos <= 0 -> placeCursorBeforeCharAt(0)
        pos >= length -> placeCursorAfterCharAt(length - 1)
        else -> placeCursorBeforeCharAt(pos)
    }
}
