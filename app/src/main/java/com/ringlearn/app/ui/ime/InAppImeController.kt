package com.ringlearn.app.ui.ime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.ui.rememberHapticManager

class InAppImeController {
    var ime: RingLearnImeState? by mutableStateOf(null)
    var candidates: List<WordEntity> by mutableStateOf(emptyList())
    var onCollapse: (() -> Unit)? = null

    /** 键盘覆盖高度超出底栏的部分（px），供页面列表滚动让出键盘区 */
    var contentOverflowPx: Int = 0
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
 */
@Composable
fun InAppKeyboardOverlay(
    controller: InAppImeController,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticManager()
    var keyboardHeight by remember { mutableIntStateOf(0) }
    var lastIme by remember { mutableStateOf<RingLearnImeState?>(null) }
    controller.ime?.let { lastIme = it }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val ime = lastIme
        if (ime != null) {
            val visible = controller.ime != null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .onSizeChanged { keyboardHeight = it.height }
                    .graphicsLayer {
                        translationY = if (visible) 0f else keyboardHeight.toFloat()
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
