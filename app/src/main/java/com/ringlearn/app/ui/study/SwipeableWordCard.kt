package com.ringlearn.app.ui.study

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ringlearn.app.R
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.ui.theme.BookmarkAmber
import com.ringlearn.app.ui.theme.BookmarkAmberDark
import com.ringlearn.app.ui.theme.KnowGreen
import com.ringlearn.app.ui.theme.KnowGreenDark
import com.ringlearn.app.ui.theme.UnknownRed
import com.ringlearn.app.ui.theme.UnknownRedDark
import com.ringlearn.app.util.HapticManager
import com.ringlearn.app.util.TtsManager
import kotlinx.coroutines.launch

/**
 * 可滑动 + 3D 翻转的单词卡片。
 *  - 点击卡片：rotationY 3D 翻转显示释义/例句（graphicsLayer + cameraDistance）
 *  - 右滑：认识（绿色指示） 左滑：不认识（红色指示） 上滑：生词本（琥珀色指示）
 *  - 滑动时按位移产生倾斜角，背后浮现半透明语义色指示
 */
@Composable
fun SwipeableWordCard(
    word: WordEntity,
    autoSpeakEnabled: Boolean,
    tts: TtsManager,
    haptic: HapticManager,
    onKnown: () -> Unit,
    onUnknown: () -> Unit,
    onToWordbook: () -> Unit,
    modifier: Modifier = Modifier
) {
    var flipped by remember(word.id) { mutableStateOf(false) }
    var offsetX by remember(word.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(word.id) { mutableFloatStateOf(0f) }
    var dismissing by remember(word.id) { mutableStateOf(false) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    val flipRotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "cardFlip"
    )

    // 切换单词时重置卡片状态，并按设置自动朗读
    LaunchedEffect(word.id) {
        flipped = false
        offsetX = 0f
        offsetY = 0f
        dismissing = false
        if (autoSpeakEnabled) tts.speak(word.word)
    }

    fun resetCard() {
        scope.launch {
            animate(initialValue = offsetX, targetValue = 0f, animationSpec = spring()) { value, _ -> offsetX = value }
            animate(initialValue = offsetY, targetValue = 0f, animationSpec = spring()) { value, _ -> offsetY = value }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .onSizeChanged { cardSize = it }
            .pointerInput(word.id) {
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        if (!dismissing) {
                            offsetX = (offsetX + amount.x).coerceIn(-cardSize.width.toFloat(), cardSize.width.toFloat())
                            offsetY = (offsetY + amount.y).coerceIn(-cardSize.height.toFloat(), cardSize.height.toFloat())
                        }
                    },
                    onDragEnd = {
                        if (dismissing) return@detectDragGestures
                        val dx = if (cardSize.width > 0) offsetX / cardSize.width else 0f
                        val dy = if (cardSize.height > 0) offsetY / cardSize.height else 0f
                        when {
                            dy < -UP_THRESHOLD -> {
                                dismissing = true
                                haptic.heavy()
                                scope.launch {
                                    animate(initialValue = offsetY, targetValue = -cardSize.height.toFloat(), animationSpec = tween(220)) { v, _ -> offsetY = v }
                                    onToWordbook()
                                }
                            }
                            dx > SWIPE_THRESHOLD -> {
                                dismissing = true
                                haptic.heavy()
                                scope.launch {
                                    animate(initialValue = offsetX, targetValue = cardSize.width.toFloat(), animationSpec = tween(220)) { v, _ -> offsetX = v }
                                    onKnown()
                                }
                            }
                            dx < -SWIPE_THRESHOLD -> {
                                dismissing = true
                                haptic.heavy()
                                scope.launch {
                                    animate(initialValue = offsetX, targetValue = -cardSize.width.toFloat(), animationSpec = tween(220)) { v, _ -> offsetX = v }
                                    onUnknown()
                                }
                            }
                            else -> resetCard()
                        }
                    },
                    onDragCancel = { resetCard() }
                )
            }
            .pointerInput(word.id) {
                detectTapGestures(
                    onTap = {
                        haptic.tick()
                        flipped = !flipped
                    }
                )
            }
    ) {
        val dragFraction = if (cardSize.width > 0) offsetX / cardSize.width else 0f
        val upFraction = if (cardSize.height > 0) -offsetY / cardSize.height else 0f
        val knownAlpha = (dragFraction.coerceIn(0f, 1f) * 0.9f).coerceIn(0f, 1f)
        val unknownAlpha = ((-dragFraction).coerceIn(0f, 1f) * 0.9f).coerceIn(0f, 1f)
        val upAlpha = (upFraction.coerceIn(0f, 1f) * 0.9f).coerceIn(0f, 1f)
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

        // ---- 背后语义色指示层 ----
        if (knownAlpha > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background((if (isDark) KnowGreenDark else KnowGreen).copy(alpha = knownAlpha))
            )
        }
        if (unknownAlpha > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background((if (isDark) UnknownRedDark else UnknownRed).copy(alpha = unknownAlpha))
            )
        }
        if (upAlpha > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background((if (isDark) BookmarkAmberDark else BookmarkAmber).copy(alpha = upAlpha))
            )
        }

        Text(
            text = "认识",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = knownAlpha }
                .padding(end = 28.dp)
        )
        Text(
            text = "不认识",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { alpha = unknownAlpha }
                .padding(start = 28.dp)
        )
        Text(
            text = "收进生词本",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = upAlpha }
                .padding(top = 24.dp)
        )

        // ---- 3D 翻转卡片本体 ----
        Card(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                    rotationZ = dragFraction * MAX_TILT_DEGREES
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                // 背面（默认 rotationY=180 预旋转；随 flipRotation 转回正面）
                WordCardFace(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            rotationY = 180f + flipRotation
                            cameraDistance = 12f * density
                            alpha = if (flipRotation in 0f..90f) 0f else 1f
                        }
                ) {
                    CardBackContent(word)
                }
                // 正面
                WordCardFace(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            rotationY = flipRotation
                            cameraDistance = 12f * density
                            alpha = if (flipRotation in 0f..90f) 1f else 0f
                        }
                ) {
                    CardFrontContent(
                        word = word,
                        onSpeak = { tts.speak(word.word) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WordCardFace(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier) {
        content()
    }
}

@Composable
private fun CardFrontContent(
    word: WordEntity,
    onSpeak: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JLPT ${word.jlpt}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            IconButton(onClick = onSpeak) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume),
                    contentDescription = "播放发音",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 日文单词 + 假名注音（视觉分割：假名小字在上，汉字大字在下）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = word.kana,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = word.word,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "点击卡片查看释义",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CardBackContent(word: WordEntity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "中文释义",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${word.word}（${word.kana}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = word.meaning,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text(
            text = "日文例句",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = word.example,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        if (word.exampleMeaning.isNotBlank()) {
            Text(
                text = word.exampleMeaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "点击返回正面",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val SWIPE_THRESHOLD = 0.30f
private const val UP_THRESHOLD = 0.12f
private const val MAX_TILT_DEGREES = 12f
