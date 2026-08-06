@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalTextApi::class
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import com.ringlearn.app.R
import com.ringlearn.app.domain.ime.RomajiEngine
import com.ringlearn.app.util.HapticManager
import kotlinx.coroutines.awaitCancellation
import kotlin.math.max
import kotlin.math.min

/**
 * 统一的“应用内输入框”：
 * - [useInAppKeyboard] = true：使用应用内置键盘（罗马音→假名），通过
 *   [PlatformTextInputInterceptor] 拦截并阻止系统输入法弹出；
 * - false：放行，使用系统输入法。
 *
 * 内置键盘与系统输入法共享同一个 [TextFieldState]，切换输入法不丢内容。
 */
@Composable
fun RingLearnTextField(
    state: TextFieldState,
    useInAppKeyboard: Boolean,
    haptic: HapticManager,
    onSwitchToSystemIme: () -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onSwitchToInAppKeyboard: (() -> Unit)? = null
) {
    val engine = remember { RomajiEngine() }
    // 拦截器实例必须保持稳定：切到系统输入法时不安装即可放行
    val blockingInterceptor = remember {
        PlatformTextInputInterceptor { _, _ -> awaitCancellation() }
    }

    var pendingRomaji by remember { mutableStateOf("") }
    var kanaMode by remember { mutableStateOf(RomajiEngine.Mode.HIRAGANA) }

    val handleKey: (KeyboardKey) -> Unit = { key ->
        when (key) {
            is KeyboardKey.Letter -> appendOutput(state, engine.input(key.char))
            KeyboardKey.Backspace -> backspace(state, engine)
            KeyboardKey.Space -> {
                val flushed = engine.flush()
                if (flushed.isNotEmpty()) appendText(state, flushed)
                appendText(state, " ")
            }
            KeyboardKey.ToggleKanaMode -> engine.toggleMode()
            KeyboardKey.SwitchToSystemIme -> onSwitchToSystemIme()
            KeyboardKey.Commit -> {
                val flushed = engine.flush()
                if (flushed.isNotEmpty()) appendText(state, flushed)
                onCommit()
            }
        }
        pendingRomaji = engine.pendingRomaji
        kanaMode = engine.mode
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
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
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
            KeyboardStatusBar(
                pendingRomaji = pendingRomaji,
                kanaMode = kanaMode,
                onSwitchToSystemIme = onSwitchToSystemIme
            )
            RomajiKeyboard(
                engine = engine,
                haptic = haptic,
                onKey = handleKey
            )
        }
    }
}

private fun appendOutput(state: TextFieldState, output: String) {
    if (output.isNotEmpty()) appendText(state, output)
}

private fun appendText(state: TextFieldState, text: String) {
    if (text.isEmpty()) return
    state.edit {
        val sel = selection
        val start = min(sel.start, sel.end)
        val end = max(sel.start, sel.end)
        replace(start, end, text)
        moveCursorTo(start + text.length)
    }
}

private fun backspace(state: TextFieldState, engine: RomajiEngine) {
    // 优先回退罗马音缓冲区
    if (engine.backspace()) return
    state.edit {
        val sel = selection
        if (sel.start != sel.end) {
            replace(sel.start, sel.end, "")
            moveCursorTo(sel.start)
        } else if (sel.start > 0) {
            replace(sel.start - 1, sel.start, "")
            moveCursorTo(sel.start - 1)
        }
    }
}

/** 安全地把光标移动到 [pos]（处理空文本与边界）。 */
private fun androidx.compose.foundation.text.input.TextFieldBuffer.moveCursorTo(pos: Int) {
    if (length == 0) return
    when {
        pos <= 0 -> placeCursorBeforeCharAt(0)
        pos >= length -> placeCursorAfterCharAt(length - 1)
        else -> placeCursorBeforeCharAt(pos)
    }
}
