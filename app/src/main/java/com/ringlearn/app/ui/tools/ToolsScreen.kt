package com.ringlearn.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ringlearn.app.R
import com.ringlearn.app.ui.components.SakuTopBar
import com.ringlearn.app.ui.components.sakuCardBorder
import com.ringlearn.app.ui.components.sakuCardColors
import com.ringlearn.app.ui.rememberHapticManager

/**
 * 功能聚合页：学习 / 查词 / 生词本 / 测验 的二级入口。
 * 纯静态导航页（无状态、无逐帧动画），白卡墨描边 + 克制动效。
 */
@Composable
fun ToolsScreen(
    onNavigateToStudy: () -> Unit,
    onNavigateToLookup: () -> Unit,
    onNavigateToWordBook: () -> Unit,
    onNavigateToQuiz: () -> Unit
) {
    val haptic = rememberHapticManager()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SakuTopBar(title = { Text("功能") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard(
                    iconRes = R.drawable.ic_study,
                    title = "学习",
                    subtitle = "间隔重复背单词",
                    onClick = { haptic.click(); onNavigateToStudy() },
                    modifier = Modifier.weight(1f)
                )
                ToolCard(
                    iconRes = R.drawable.ic_search,
                    title = "查词",
                    subtitle = "罗马音 / 手写",
                    onClick = { haptic.click(); onNavigateToLookup() },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard(
                    iconRes = R.drawable.ic_wordbook,
                    title = "生词本",
                    subtitle = "收藏与搜索",
                    onClick = { haptic.click(); onNavigateToWordBook() },
                    modifier = Modifier.weight(1f)
                )
                ToolCard(
                    iconRes = R.drawable.ic_quiz,
                    title = "随机测验",
                    subtitle = "四选一自测",
                    onClick = { haptic.click(); onNavigateToQuiz() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ToolCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = sakuCardColors(),
        border = sakuCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
