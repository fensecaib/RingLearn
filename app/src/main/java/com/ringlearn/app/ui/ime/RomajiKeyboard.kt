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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ringlearn.app.domain.ime.RomajiEngine
import com.ringlearn.app.util.HapticManager

/** 内置键盘布局 */
enum class KeyboardLayout { QWERTY, KANA }

/** 五十音键盘的修饰模式（清音 / 浊音 / 半浊音 / 小假名） */
private enum class KanaModifier(val label: String) {
    SEI("清音"), DAKU("濁音"), HANDAKU("半濁"), SHO("小");

    companion object {
        /** 浊音：か/さ/た/は 行 → が/ざ/だ/ば 行 */
        private val DAKU_MAP = mapOf(
            "か" to "が", "き" to "ぎ", "く" to "ぐ", "け" to "げ", "こ" to "ご",
            "さ" to "ざ", "し" to "じ", "す" to "ず", "せ" to "ぜ", "そ" to "ぞ",
            "た" to "だ", "ち" to "ぢ", "つ" to "づ", "て" to "で", "と" to "ど",
            "は" to "ば", "ひ" to "び", "ふ" to "ぶ", "へ" to "べ", "ほ" to "ぼ"
        )
        /** 半浊音：は 行 → ぱ 行 */
        private val HANDAKU_MAP = mapOf(
            "は" to "ぱ", "ひ" to "ぴ", "ふ" to "ぷ", "へ" to "ぺ", "ほ" to "ぽ"
        )
        /** 小假名：あ/や/つ/わ → ぁ/ゃ/っ/ゎ 等 */
        private val SHO_MAP = mapOf(
            "あ" to "ぁ", "い" to "ぃ", "う" to "ぅ", "え" to "ぇ", "お" to "ぉ",
            "や" to "ゃ", "ゆ" to "ゅ", "よ" to "ょ", "つ" to "っ", "わ" to "ゎ"
        )

        fun transform(base: String, mod: KanaModifier): String = when (mod) {
            SEI -> base
            DAKU -> DAKU_MAP[base] ?: base
            HANDAKU -> HANDAKU_MAP[base] ?: base
            SHO -> SHO_MAP[base] ?: base
        }
    }
}

/** 内置键盘的按键事件 */
sealed interface KeyboardKey {
    data class Letter(val char: Char) : KeyboardKey
    data class Kana(val kana: String) : KeyboardKey
    data object Backspace : KeyboardKey
    data object Convert : KeyboardKey
    data object ToggleKanaMode : KeyboardKey
    data object ToggleLayout : KeyboardKey
    data object SwitchToSystemIme : KeyboardKey
    data object Commit : KeyboardKey
}

/**
 * 应用内置键盘 v2：
 * - QWERTY 罗马音布局与五十音布局可一键切换（Google 日语输入法同款双布局）。
 * - 五十音布局支持 清音/浊音/半浊音/小假名 修饰切换。
 * - 底部统一功能键栏：かな⇄カナ、変換、布局切换、系统输入法、退格、确定。
 * - 所有按键带触觉反馈。
 */
@Composable
fun RomajiKeyboard(
    layout: KeyboardLayout,
    kanaMode: RomajiEngine.Mode,
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
        when (layout) {
            KeyboardLayout.QWERTY -> QwertyRows(haptic, onKey)
            KeyboardLayout.KANA -> KanaGrid(kanaMode, haptic, onKey)
        }
        Spacer(Modifier.height(6.dp))
        FunctionBar(kanaMode = kanaMode, layout = layout, haptic = haptic, onKey = onKey)
    }
}

@Composable
private fun QwertyRows(
    haptic: HapticManager,
    onKey: (KeyboardKey) -> Unit
) {
    KeyboardRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), haptic, onKey)
    Spacer(Modifier.height(6.dp))
    KeyboardRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), haptic, onKey)
    Spacer(Modifier.height(6.dp))
    KeyboardRow(listOf("z", "x", "c", "v", "b", "n", "m"), haptic, onKey)
}

/** 五十音键盘：修饰模式行 + 5×10 假名网格 */
@Composable
private fun KanaGrid(
    kanaMode: RomajiEngine.Mode,
    haptic: HapticManager,
    onKey: (KeyboardKey) -> Unit
) {
    var modifier by remember { mutableStateOf(KanaModifier.SEI) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KanaModifier.entries.forEach { m ->
            val selected = m == modifier
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable {
                        haptic.tick()
                        modifier = m
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = m.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    KANA_GRID.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row.forEach { base ->
                val label = KanaModifier.transform(base, modifier)
                    .let { if (kanaMode == RomajiEngine.Mode.KATAKANA) RomajiEngine.toKatakana(it) else it }
                KeyboardKeyButton(
                    label = label,
                    haptic = haptic,
                    modifier = Modifier.weight(1f),
                    fontSize = 20,
                    onClick = { onKey(KeyboardKey.Kana(label)) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** 底部统一功能键栏 */
@Composable
private fun FunctionBar(
    kanaMode: RomajiEngine.Mode,
    layout: KeyboardLayout,
    haptic: HapticManager,
    onKey: (KeyboardKey) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KeyboardKeyButton(
            label = if (kanaMode == RomajiEngine.Mode.HIRAGANA) "かな" else "カナ",
            haptic = haptic,
            modifier = Modifier.weight(0.85f),
            onClick = { onKey(KeyboardKey.ToggleKanaMode) }
        )
        KeyboardKeyButton(
            label = "変換",
            haptic = haptic,
            modifier = Modifier.weight(1.5f),
            onClick = { onKey(KeyboardKey.Convert) }
        )
        KeyboardKeyButton(
            label = if (layout == KeyboardLayout.QWERTY) "五十音" else "QWERTY",
            haptic = haptic,
            modifier = Modifier.weight(1f),
            onClick = { onKey(KeyboardKey.ToggleLayout) }
        )
        KeyboardKeyButton(
            label = "⌨",
            haptic = haptic,
            modifier = Modifier.weight(0.8f),
            onClick = { onKey(KeyboardKey.SwitchToSystemIme) }
        )
        KeyboardKeyButton(
            label = "⌫",
            haptic = haptic,
            modifier = Modifier.weight(0.8f),
            onClick = { onKey(KeyboardKey.Backspace) }
        )
        KeyboardKeyButton(
            label = "確定",
            haptic = haptic,
            modifier = Modifier.weight(1f),
            onClick = { onKey(KeyboardKey.Commit) }
        )
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
    fontSize: Int = 18,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(44.dp)
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
            style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize.sp),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 五十音网格（平假名基准；修饰与小/片假名在上层转换） */
private val KANA_GRID: List<List<String>> = listOf(
    listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ"),
    listOf("い", "き", "し", "ち", "に", "ひ", "み", "ゆ", "り", "を"),
    listOf("う", "く", "す", "つ", "ぬ", "ふ", "む", "よ", "る", "ん"),
    listOf("え", "け", "せ", "て", "ね", "へ", "め", "ゃ", "れ", "ー"),
    listOf("お", "こ", "そ", "と", "の", "ほ", "も", "ゅ", "ろ", "っ")
)
