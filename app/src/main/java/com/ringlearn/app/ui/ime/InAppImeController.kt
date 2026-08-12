package com.ringlearn.app.ui.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.ui.rememberHapticManager
import kotlin.math.roundToInt

/**
 * 内置键盘覆盖层控制器：由查词/生词本页在键盘激活时绑定，根层 [InAppKeyboardOverlay] 读取并渲染。
 */
class InAppImeController {
    /** 非空 = 覆盖层可见（指向当前活动页面的 IME 状态） */
    var ime: RingLearnImeState? by mutableStateOf(null)

    /** IME 词典候选（页面收集后同步进来，供候选栏使用） */
    var candidates: List<WordEntity> by mutableStateOf(emptyList())

    /** 收起回调（页面设置：收起键盘） */
    var onCollapse: (() -> Unit)? = null

    /** 键盘覆盖层实测高度（px，含导航栏 padding；键盘常驻组合，测量稳定） */
    var keyboardHeightPx by mutableIntStateOf(0)

    /** 页面内容底边在根坐标系中的 y（px）：由根层 nav host onGloballyPositioned 实测。 */
    var pageContentBottomPx by mutableIntStateOf(0)

    /** 键盘覆盖层顶边在根坐标系中的 y（px）：由覆盖层 Column onGloballyPositioned 实测（可见位置）。 */
    var overlayTopPx by mutableIntStateOf(0)
}

val LocalInAppImeController = staticCompositionLocalOf<InAppImeController> {
    error("LocalInAppImeController not provided")
}

/**
 * 根层内置键盘覆盖层：覆盖在底栏之上（底栏从不被移除）。
 *
 * 性能要点：
 * - 键盘**常驻组合**（首个非空 ime 后一直保留），开/关用 graphicsLayer 位移（GPU，不重新组成/重测量）。
 * - 外层 [Box] 常驻 → 根 Box 子项数稳定，键盘切换不触发整盒重测量。
 * - 隐藏时键盘下移到屏幕外（不挡触摸）；`navigationBarsPadding()` 让内容停在系统导航栏之上，完整 4 排。
 * - 实测高度 [InAppImeController.keyboardHeightPx] 供根层推导列表让位量（QWERTY/五十音/候选栏自适应）。
 */
@Composable
fun InAppKeyboardOverlay(
    controller: InAppImeController,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticManager()
    var lastIme by remember { mutableStateOf<RingLearnImeState?>(null) }
    // 组合期后更新快照（仅用于隐藏态保留渲染内容；可见态直接用 controller.ime）
    SideEffect { controller.ime?.let { lastIme = it } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 可见态直接用当前 ime（首开立即渲染）；隐藏态用最后快照（常驻组合、仅位移隐藏）
        val ime = controller.ime ?: lastIme
        if (ime != null) {
            val visible = controller.ime != null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .onSizeChanged { controller.keyboardHeightPx = it.height }
                    .onGloballyPositioned { coords -> controller.overlayTopPx = coords.positionInRoot().y.roundToInt() }
                    .graphicsLayer {
                        translationY = if (visible) 0f else controller.keyboardHeightPx.toFloat()
                    }
            ) {
                RingLearnImeCandidateBar(
                    ime = ime,
                    imeDictionaryCandidates = controller.candidates,
                    haptic = haptic
                )
                RomajiKeyboard(
                    layout = ime.keyboardLayout,
                    kanaMode = ime.kanaMode,
                    composing = ime.composing,
                    haptic = haptic,
                    onKey = ime::handleKey,
                    onCollapse = controller.onCollapse
                )
            }
        }
    }
}








