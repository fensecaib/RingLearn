package com.ringlearn.app.ui.ime

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 内容区外点收起内置键盘（类真实 IME）：
 * - 仅响应手势按下（Press），避免同一手势的 UP 把刚打开的键盘立即收起；
 * - [PointerEventPass.Initial] 阶段且不消费事件，滚动/点按照常穿透；
 * - `enabled=false` 时不进入事件循环，零开销。
 *
 * 挂在「消息区 / 结果区」等可滚动内容容器上，而非页面根节点：
 * 点输入框、发送按钮、顶栏不会误收起，点内容区任意位置仍会收起。
 */
fun Modifier.dismissInAppImeOnTap(enabled: Boolean, onDismiss: () -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) onDismiss()
            }
        }
    }
