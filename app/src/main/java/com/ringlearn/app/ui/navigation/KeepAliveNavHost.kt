package com.ringlearn.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.ringlearn.app.ui.ime.LocalInAppImeController
import kotlinx.coroutines.delay

/** 错峰预热初始延迟（ms）：在首屏渲染 / TTFD 上报完成后执行 */
private const val PREWARM_INITIAL_DELAY_MS = 1200L

/** 错峰预热相邻两个 Tab 组合的间隔（ms） */
private const val PREWARM_STEP_MS = 350L

/**
 * 常驻 Tab 宿主：替代 NavDisplay，让所有已访问 Tab 保持组合。
 *
 * - 懒加载首访常驻：冷启动只组合首页；首次切到某 Tab 时才组合该屏，
 *   之后切换仅是 alpha 可见性切换，不再重新组合整树。
 * - 空闲错峰预热：首屏渲染完成后按间隔逐个预组合未访问 Tab（见下方 LaunchedEffect），
 *   任何 Tab 的首次切换也变为纯 alpha 切换，消除首访组合尖峰。
 * - 非激活屏幕：graphicsLayer alpha=0 不绘制，并用 pointerInput 拦截全部输入
 *   （任何事件均不传递给背后屏幕），同时 clearAndSetSemantics 移出无障碍树。
 */
@Composable
fun KeepAliveNavHost(
    modifier: Modifier = Modifier,
    entries: List<Pair<NavKey, NavEntry<NavKey>>>,
    topLevelRoute: NavKey,
    initialRoute: NavKey
) {
    // visitedRoutes 可保存：旋转/进程重建后已访问 Tab 保持常驻，不再重新组合
    val routeLookup = remember(entries) { entries.associate { it.first.toString() to it.first } }
    val visitedSaver = remember(routeLookup) {
        Saver<Set<NavKey>, ArrayList<String>>(
            save = { set -> ArrayList(set.map { it.toString() }) },
            restore = { names -> names.mapNotNull { routeLookup[it] }.toSet() }
        )
    }
    var visitedRoutes by rememberSaveable(stateSaver = visitedSaver) {
        mutableStateOf(setOf(initialRoute))
    }
    LaunchedEffect(topLevelRoute) {
        if (topLevelRoute !in visitedRoutes) {
            visitedRoutes = visitedRoutes + topLevelRoute
        }
    }
    // 空闲错峰预热：首屏渲染完成（TTFD 上报后）再按间隔逐个预组合未访问 Tab，
    // 消除首次切到某 Tab 的「整树首次组合」尖峰（优化前 release 99th≈150ms / debug≈450ms）。
    // 已知副作用（可接受）：预热会触发 Study/Quiz/Ai 等页 ViewModel init 的轻量 Room 查询
    // （autoSpeakEnabled 默认 false，不会触发 TTS）；内存提前达到全 Tab 常驻水平（约 117MB，基线内）。
    LaunchedEffect(Unit) {
        delay(PREWARM_INITIAL_DELAY_MS)
        for ((route, _) in entries) {
            if (route !in visitedRoutes) {
                visitedRoutes = visitedRoutes + route
                delay(PREWARM_STEP_MS)
            }
        }
    }

    // 外点收起（类真实 IME）：键盘可见时，落在页面内容区的任何按下都触发收起回调；
    // Initial  pass 且不 consume，滚动/点按穿透不受影响；键盘覆盖层在宿主之外且自行消费事件，点键盘不触发。
    val imeController = LocalInAppImeController.current
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // 仅在手势按下（Press）时收起：若对 UP 也响应，会把同一次点按刚打开的键盘立即收起。
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && imeController.ime != null) {
                        imeController.onCollapse?.invoke()
                    }
                }
            }
        }
    ) {
        entries.forEach { (route, entry) ->
            if (route in visitedRoutes) {
                key(route) {
                    val active = route == topLevelRoute
                    // 单一 Box 调用点：切换时仅改变 Modifier，避免分支交换导致内容子树被销毁/重建
                    val inputBlock = if (active) {
                        Modifier
                    } else {
                        Modifier
                            .clearAndSetSemantics {}
                            .pointerInput(route) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.forEach { it.consume() }
                                        awaitPointerEvent(PointerEventPass.Main)
                                            .changes.forEach { it.consume() }
                                        awaitPointerEvent(PointerEventPass.Final)
                                            .changes.forEach { it.consume() }
                                    }
                                }
                            }
                    }
                    // 内容 lambda 用 remember(entry) 稳定化：切换时 Box 的 Modifier 变化不会重新执行 entry.Content()，
                    // 屏幕子树真正保持组合（已用 logcat 实测证实切换时零重组合）
                    val content: @Composable BoxScope.() -> Unit = remember(entry) {
                        { entry.Content() }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (active) 1f else 0f)
                            .graphicsLayer { alpha = if (active) 1f else 0f }
                            .then(inputBlock),
                        content = content
                    )
                }
            }
        }
    }
}
