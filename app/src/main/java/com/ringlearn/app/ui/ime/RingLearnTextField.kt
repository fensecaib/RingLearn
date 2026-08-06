@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)

package com.ringlearn.app.ui.ime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ringlearn.app.R
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.domain.ime.RomajiEngine
import com.ringlearn.app.util.HapticManager
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max

/**
 * 统一的“应用内输入框”v2（类 IME 体验）：
 * - [useInAppKeyboard] = true：使用应用内置键盘（罗马音 / 五十音双布局），通过
 *   [PlatformTextInputInterceptor] 拦截并阻止系统输入法弹出；
 * - false：放行系统输入法。
 *
 * 内置键盘与系统输入法共享同一个 [TextFieldState]，切换输入法不丢内容。
 *
 * IME 组合（composition）：
 * - 引擎持有 committed + 组合区（composed 假名 + 待转换罗马音），字段实时镜像；
 * - 组合区通过公开的 [OutputTransformation] + [TextFieldBuffer.addStyle] 绘制下划线
 *   （Compose 未公开 setComposition，组合状态由引擎维护、由本组件向外部同步）；
 * - 组合期间不触发外部查询（由调用方按 [onCompositionChange] 门控）；
 * - 光标移出组合区 / 切换到系统输入法 / 确定 / 选中候选 → 自动提交组合。
 */
@Composable
fun RingLearnTextField(
    state: TextFieldState,
    useInAppKeyboard: Boolean,
    haptic: HapticManager,
    onSwitchToSystemIme: () -> Unit,
    onCompositionChange: (composing: Boolean, kana: String) -> Unit = { _, _ -> },
    imeDictionaryCandidates: List<WordEntity> = emptyList(),
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onSwitchToInAppKeyboard: (() -> Unit)? = null,
    onCommit: () -> Unit = {}
) {
    val engine = remember { RomajiEngine() }
    // 拦截器实例必须保持稳定：切到系统输入法时不安装即可放行
    val blockingInterceptor = remember {
        PlatformTextInputInterceptor { _, _ -> awaitCancellation() }
    }

    var keyboardLayout by remember { mutableStateOf(KeyboardLayout.QWERTY) }
    var kanaMode by remember { mutableStateOf(engine.mode) }
    var composing by remember { mutableStateOf(false) }
    var compositionKana by remember { mutableStateOf("") }
    // 组合区 [start, end)，供 OutputTransformation 绘制下划线
    val compositionRange = remember { mutableStateOf<IntRange?>(null) }

    /** 把引擎状态镜像到字段（组合区下划线 + 光标）并同步外部状态。 */
    fun syncFromEngine() {
        val full = engine.fullText
        val range = if (engine.isComposing) engine.compositionStart until engine.compositionEnd else null
        compositionRange.value = range
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
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != engine.fullText) {
                    engine.adoptText(text)
                    composing = false
                    compositionKana = ""
                    compositionRange.value = null
                    onCompositionChange(false, "")
                }
            }
    }

    // 光标离开组合区 → 自动提交组合（对齐真实 IME）
    LaunchedEffect(state) {
        snapshotFlow { state.selection to compositionRange.value }
            .collect { (sel, range) ->
                if (range != null && engine.isComposing) {
                    val selEnd = max(sel.start, sel.end)
                    if (selEnd < range.last) {
                        engine.commit()
                        syncFromEngine()
                    }
                }
            }
    }

    val handleKey: (KeyboardKey) -> Unit = { key ->
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
            }
        }
        syncFromEngine()
    }

    // 组合区下划线（公开 API：OutputTransformation + addStyle，lambda 接收者为 TextFieldBuffer）
    // 组合区样式：跟随主题主色的下划线（对齐 Google 日语输入法组合下划线）
    val compositionStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )

    val outputTransformation = OutputTransformation {
        val range = compositionRange.value
        if (range != null && range.last <= length) {
            addStyle(compositionStyle, range.first, range.last)
        }
    }

    // 候选列表：假名本身 + 片假名 + 词库候选
    val candidateList = remember(compositionKana, imeDictionaryCandidates) {
        if (compositionKana.isBlank()) emptyList()
        else buildList {
            add(ImeCandidate(text = compositionKana))
            val kata = RomajiEngine.toKatakana(compositionKana)
            if (kata != compositionKana) add(ImeCandidate(text = kata, kana = compositionKana))
            imeDictionaryCandidates.take(6).forEach { w ->
                add(ImeCandidate(text = w.word, kana = w.kana))
            }
        }.take(8)
    }

    Column(modifier = modifier) {
        // 输入框外壳
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.let {
                Box(Modifier.padding(end = 10.dp)) { it() }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                val textField: @Composable () -> Unit = {
                    BasicTextField(
                        state = state,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        outputTransformation = outputTransformation
                    )
                }
                if (useInAppKeyboard) {
                    InterceptPlatformTextInput(blockingInterceptor) { textField() }
                } else {
                    textField()
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

        if (useInAppKeyboard) {
            // 候选条固定高度（占位），避免键盘随候选出现/消失而跳动
            CandidateBar(
                candidates = if (composing) candidateList else emptyList(),
                haptic = haptic,
                onSelect = { candidate ->
                    engine.commitCandidate(candidate.text)
                    syncFromEngine()
                }
            )
            RomajiKeyboard(
                layout = keyboardLayout,
                kanaMode = kanaMode,
                haptic = haptic,
                onKey = handleKey
            )
        }
    }
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

