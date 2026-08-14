package com.ringlearn.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

/**
 * 使用 Compose Canvas 手绘的"连续学习火焰"图标（无需 Lottie / 第三方动画库）。
 * 火焰带有轻微缩放/摇摆的无限循环动画，底部附带渐变光晕。
 *
 * 火焰渐变色（#FDE68A/#F97316/#EF4444）为火焰语义专属调色，不属于 saku 表面令牌
 * （青/黄/墨蓝），因此保持内联常量而不令牌化；动画仅在 active=true（首页 Tab 激活）时运行。
 */
@Composable
fun FlameIcon(
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    if (active) {
        val transition = rememberInfiniteTransition(label = "flame")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flameScale"
        )
        val tilt by transition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flameTilt"
        )
        FlameCanvas(modifier = modifier, scale = scale, tilt = tilt)
    } else {
        // 非激活（首页不在前台）时渲染静态火焰，避免后台 60fps 空转
        FlameCanvas(modifier = modifier, scale = 1f, tilt = 0f)
    }
}

/**
 * 火焰形状以单位坐标系（0..1）一次性构建并全局复用：
 * 动画期间每帧仅做 DrawScope 缩放映射，消除原先每帧 2×Path + Brush 的分配。
 * 全部为填充绘制（无描边），非等比缩放安全。
 */
private val unitOuterFlame: Path = Path().apply {
    moveTo(0.50f, 0.06f)
    cubicTo(0.80f, 0.32f, 0.90f, 0.50f, 0.90f, 0.70f)
    cubicTo(0.90f, 0.94f, 0.72f, 1.02f, 0.50f, 1.02f)
    cubicTo(0.28f, 1.02f, 0.10f, 0.94f, 0.10f, 0.70f)
    cubicTo(0.10f, 0.50f, 0.20f, 0.32f, 0.50f, 0.06f)
    close()
}

private val unitInnerFlame: Path = Path().apply {
    moveTo(0.50f, 0.30f)
    cubicTo(0.66f, 0.44f, 0.72f, 0.56f, 0.72f, 0.68f)
    cubicTo(0.72f, 0.84f, 0.62f, 0.90f, 0.50f, 0.90f)
    cubicTo(0.38f, 0.90f, 0.28f, 0.84f, 0.28f, 0.68f)
    cubicTo(0.28f, 0.56f, 0.34f, 0.44f, 0.50f, 0.30f)
    close()
}

private val flameGradient: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFDE68A),
        Color(0xFFF97316),
        Color(0xFFEF4444)
    ),
    startY = 0f,
    endY = 1f
)

@Composable
private fun FlameCanvas(modifier: Modifier, scale: Float, tilt: Float) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        scale(scale, pivot = Offset(w / 2f, h)) {
            rotate(tilt, pivot = Offset(w / 2f, h * 0.9f)) {
                // 单位空间 → 实际画布的缩放映射；Path 与渐变 Brush 全部复用缓存
                scale(w, h, pivot = Offset.Zero) {
                    // 外焰
                    drawPath(unitOuterFlame, brush = flameGradient)
                    // 内焰
                    drawPath(unitInnerFlame, color = Color(0xFFFFF9E3))
                    // 底部光晕
                    drawOval(
                        color = Color(0x33F97316),
                        topLeft = Offset(0.22f, 0.92f),
                        size = Size(0.56f, 0.10f)
                    )
                }
            }
        }
    }
}
