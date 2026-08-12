package com.ringlearn.app.ui.ime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.ringlearn.app.data.local.entity.WordEntity

/**
 * 把页面 IME 状态/候选/收起回调绑定到根层覆盖层控制器：
 * 键盘可见（active）时渲染，离开页面（onDispose）自动解绑，避免键盘在其它页残留。
 */
@Composable
fun InAppImeBinding(
    controller: InAppImeController,
    ime: RingLearnImeState,
    candidates: List<WordEntity>,
    active: Boolean,
    onCollapse: () -> Unit
) {
    SideEffect {
        if (active) {
            controller.ime = ime
            controller.candidates = candidates
            controller.onCollapse = onCollapse
        } else {
            controller.ime = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { controller.ime = null }
    }
}

/**
 * 键盘覆盖层顶部超出页面内容底边的部分（dp）：lift = pageContentBottomPx - overlayTopPx（同一根坐标系实测），
 * 使输入行/列表底边恰好停在键盘覆盖层顶边之上。组合期读取两个状态即订阅其变化。
 */
@Composable
fun InAppImeController.contentOverflowDp(): Dp {
    val density = LocalDensity.current
    return with(density) {
        (pageContentBottomPx - overlayTopPx).coerceAtLeast(0).toDp()
    }
}
