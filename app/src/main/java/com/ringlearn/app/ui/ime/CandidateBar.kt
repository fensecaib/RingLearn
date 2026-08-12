package com.ringlearn.app.ui.ime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ringlearn.app.util.HapticManager

/**
 * 内置键盘的转换候选条：显示在输入框与键盘之间。
 * 首个条目为假名本身，其次为片假名，其后为词库候选（按 JLPT / 完全匹配排序）。
 */
@Composable
fun CandidateBar(
    candidates: List<ImeCandidate>,
    haptic: HapticManager,
    onSelect: (ImeCandidate) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp
) {
    // 仅在有候选（组合中）时渲染：空态完全收起，不再在键盘上方保留空白条
    AnimatedVisibility(
        visible = candidates.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "候选",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                // 候选可能含同文本（假名/片假名/词库表记），用索引作 key 避免崩溃
                itemsIndexed(candidates, key = { i, _ -> i }) { _, candidate ->
                    CandidateChip(candidate = candidate, haptic = haptic, onClick = { onSelect(candidate) })
                }
            }
        }
    }
}

@Composable
private fun CandidateChip(
    candidate: ImeCandidate,
    haptic: HapticManager,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                haptic.click()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = candidate.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (candidate.kana != null && candidate.kana != candidate.text) {
            Text(
                text = candidate.kana,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
