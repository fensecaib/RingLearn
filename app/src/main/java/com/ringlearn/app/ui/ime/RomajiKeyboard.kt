package com.ringlearn.app.ui.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ringlearn.app.R
import com.ringlearn.app.domain.ime.RomajiEngine
import com.ringlearn.app.util.HapticManager

/** 应用内置键盘的按键事件 */
sealed interface KeyboardKey {
    data class Letter(val char: Char) : KeyboardKey
    data object Backspace : KeyboardKey
    data object Space : KeyboardKey
    data object ToggleKanaMode : KeyboardKey
    data object SwitchToSystemIme : KeyboardKey
    data object Commit : KeyboardKey
}

/**
 * 应用内置 QWERTY 罗马音键盘。
 * - 支持平假名/片假名模式切换（かな/カナ）
 * - 底部提供“切换到系统输入法”入口
 * - 所有按键带触觉反馈
 */
@Composable
fun RomajiKeyboard(
    engine: RomajiEngine,
    haptic: HapticManager,
    onKey: (KeyboardKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        KeyboardRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), haptic, onKey)
        Spacer(Modifier.height(6.dp))
        KeyboardRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), haptic, onKey)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("z", "x", "c", "v", "b", "n", "m").forEach { label ->
                KeyboardKeyButton(
                    label = label,
                    haptic = haptic,
                    modifier = Modifier.weight(1f),
                    onClick = { onKey(KeyboardKey.Letter(label[0])) }
                )
            }
            KeyboardKeyButton(
                label = "⌫",
                haptic = haptic,
                modifier = Modifier.weight(1.35f),
                onClick = { onKey(KeyboardKey.Backspace) }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyboardKeyButton(
                label = if (engine.mode == RomajiEngine.Mode.HIRAGANA) "かな" else "カナ",
                haptic = haptic,
                modifier = Modifier.weight(0.9f),
                onClick = { onKey(KeyboardKey.ToggleKanaMode) }
            )
            KeyboardKeyButton(
                label = "空格",
                haptic = haptic,
                modifier = Modifier.weight(1.6f),
                onClick = { onKey(KeyboardKey.Space) }
            )
            KeyboardKeyButton(
                label = "⌨",
                haptic = haptic,
                modifier = Modifier.weight(0.8f),
                onClick = { onKey(KeyboardKey.SwitchToSystemIme) }
            )
            KeyboardKeyButton(
                label = "确定",
                haptic = haptic,
                modifier = Modifier.weight(1f),
                onClick = { onKey(KeyboardKey.Commit) }
            )
        }
    }
}

@Composable
private fun KeyboardRow(
    labels: List<String>,
    haptic: HapticManager,
    onKey: (KeyboardKey) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEach { label ->
            KeyboardKeyButton(
                label = label,
                haptic = haptic,
                modifier = Modifier.weight(1f),
                onClick = { onKey(KeyboardKey.Letter(label[0])) }
            )
        }
    }
}

@Composable
private fun KeyboardKeyButton(
    label: String,
    haptic: HapticManager,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                haptic.tick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 键盘上方的预输入/模式指示条（含切换到系统输入法的入口） */
@Composable
fun KeyboardStatusBar(
    pendingRomaji: String,
    kanaMode: RomajiEngine.Mode,
    onSwitchToSystemIme: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (pendingRomaji.isNotEmpty()) pendingRomaji else "罗马音输入中…",
            style = MaterialTheme.typography.labelMedium,
            color = if (pendingRomaji.isNotEmpty()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (kanaMode == RomajiEngine.Mode.HIRAGANA) "かな" else "カナ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onSwitchToSystemIme) {
            Icon(
                painter = painterResource(R.drawable.ic_keyboard),
                contentDescription = "切换为系统输入法",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
