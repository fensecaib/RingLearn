package com.ringlearn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 全页面共用的紧凑顶栏：状态栏 inset + 48dp 内容高（M3 默认 TopAppBar 为 64dp）。
 * 标题居中、返回键居左、操作区居右；样式随全局 Saku 主题（页面各自传入底色）。
 */
@Composable
fun SakuTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = Color.Transparent,
    titleAlignStart: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon()
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
        }
        Box(
            modifier = Modifier
                .align(if (titleAlignStart) Alignment.CenterStart else Alignment.Center)
                .padding(start = if (titleAlignStart) 16.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            title()
        }
    }
}
