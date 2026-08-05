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
 * 使用 Compose Canvas 手绘的“连续学习火焰”图标（无需 Lottie / 第三方动画库）。
 * 火焰带有轻微缩放/摇摆的无限循环动画，底部附带渐变光晕。
 */
@Composable
fun FlameIcon(modifier: Modifier = Modifier) {
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

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        scale(scale, pivot = Offset(w / 2f, h)) {
            rotate(tilt, pivot = Offset(w / 2f, h * 0.9f)) {
                // 外焰
                val outer = Path().apply {
                    moveTo(w * 0.50f, h * 0.06f)
                    cubicTo(w * 0.80f, h * 0.32f, w * 0.90f, h * 0.50f, w * 0.90f, h * 0.70f)
                    cubicTo(w * 0.90f, h * 0.94f, w * 0.72f, h * 1.02f, w * 0.50f, h * 1.02f)
                    cubicTo(w * 0.28f, h * 1.02f, w * 0.10f, h * 0.94f, w * 0.10f, h * 0.70f)
                    cubicTo(w * 0.10f, h * 0.50f, w * 0.20f, h * 0.32f, w * 0.50f, h * 0.06f)
                    close()
                }
                drawPath(
                    outer,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFDE68A),
                            Color(0xFFF97316),
                            Color(0xFFEF4444)
                        ),
                        startY = 0f,
                        endY = h
                    )
                )

                // 内焰
                val inner = Path().apply {
                    moveTo(w * 0.50f, h * 0.30f)
                    cubicTo(w * 0.66f, h * 0.44f, w * 0.72f, h * 0.56f, w * 0.72f, h * 0.68f)
                    cubicTo(w * 0.72f, h * 0.84f, w * 0.62f, h * 0.90f, w * 0.50f, h * 0.90f)
                    cubicTo(w * 0.38f, h * 0.90f, w * 0.28f, h * 0.84f, w * 0.28f, h * 0.68f)
                    cubicTo(w * 0.28f, h * 0.56f, w * 0.34f, h * 0.44f, w * 0.50f, h * 0.30f)
                    close()
                }
                drawPath(inner, color = Color(0xFFFFF9E3))

                // 底部光晕
                drawOval(
                    color = Color(0x33F97316),
                    topLeft = Offset(w * 0.22f, h * 0.92f),
                    size = Size(w * 0.56f, h * 0.10f)
                )
            }
        }
    }
}
