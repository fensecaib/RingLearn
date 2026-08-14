package com.ringlearn.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.ringlearn.app.ui.theme.SakuInk
import com.ringlearn.app.ui.theme.SakuYellow
import com.ringlearn.app.ui.theme.SakuYellowDeep

/** Saku 卡片统一底色：白卡片（深色模式为最深的墨蓝卡）。 */
@Composable
fun sakuCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
)

/** Saku 卡片统一描边：outlineVariant 已在全局主题中定义为 12% 墨色。 */
@Composable
fun sakuCardBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** Saku 主操作按钮：暖黄底 + 墨色文字；按压态加深为 SakuYellowDeep（参考站 .btn-mea-download:hover/active）。 */
@Composable
fun sakuCtaButtonColors(pressed: Boolean = false): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = if (pressed) SakuYellowDeep else SakuYellow,
    contentColor = SakuInk
)
