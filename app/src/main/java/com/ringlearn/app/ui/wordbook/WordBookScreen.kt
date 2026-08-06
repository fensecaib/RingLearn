package com.ringlearn.app.ui.wordbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringlearn.app.R
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.ui.components.EmptyState
import com.ringlearn.app.ui.ime.RingLearnTextField
import com.ringlearn.app.ui.rememberHapticManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 生词本：搜索（内置罗马音键盘/系统输入法）+ 列表 + 移除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBookScreen(
    viewModel: WordBookViewModel = hiltViewModel()
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val useInAppKeyboard by viewModel.useInAppKeyboard.collectAsStateWithLifecycle()
    val imeComposing by viewModel.imeComposing.collectAsStateWithLifecycle()
    val imeDictionaryCandidates by viewModel.imeDictionaryCandidates.collectAsStateWithLifecycle()
    val haptic = rememberHapticManager()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 输入框状态：与 ViewModel 双向同步
    val textFieldState = remember { TextFieldState() }
    // IME 组合期间不触发搜索：内置键盘组合由引擎回调门控，系统输入法组合读取 state.composition
    LaunchedEffect(textFieldState, useInAppKeyboard) {
        snapshotFlow {
            val systemComposing = textFieldState.composition?.takeIf { !it.collapsed } != null
            Triple(textFieldState.text.toString(), systemComposing, imeComposing)
        }
            .distinctUntilChanged()
            .collect { (text, systemComposing, imeComposing) ->
                val composing = if (useInAppKeyboard) imeComposing else systemComposing
                viewModel.onFieldChanged(text, composing)
            }
    }
    // 外部查询变化（清空 / 恢复）→ 同步字段；组合进行中不覆盖
    LaunchedEffect(query) {
        val composition = textFieldState.composition
        if ((composition == null || composition.collapsed) && textFieldState.text.toString() != query) {
            textFieldState.edit { replace(0, length, query) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(title = { Text("生词本") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            RingLearnTextField(
                state = textFieldState,
                useInAppKeyboard = useInAppKeyboard,
                haptic = haptic,
                onSwitchToSystemIme = viewModel::onSwitchToSystemIme,
                onSwitchToInAppKeyboard = viewModel::onSwitchToInAppKeyboard,
                onCompositionChange = viewModel::onCompositionChange,
                imeDictionaryCandidates = imeDictionaryCandidates,
                onCommit = {},
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "搜索单词 / 假名 / 释义",
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic.click()
                            viewModel.onQueryChange("")
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = "清空"
                            )
                        }
                    }
                }
            )

            if (favorites.isEmpty()) {
                val hasQuery = query.isNotBlank()
                EmptyState(
                    iconRes = R.drawable.ic_bookmark,
                    title = if (hasQuery) "没有匹配的单词" else "生词本还是空的",
                    subtitle = if (hasQuery) {
                        "换个关键词试试吧。"
                    } else {
                        "在学习卡片时上滑即可把单词收进生词本，\n方便随时集中复习。"
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(favorites, key = { it.id }) { word ->
                        FavoriteWordItem(
                            word = word,
                            onRemove = {
                                haptic.click()
                                viewModel.removeFromBook(word.id)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已从生词本移除：${word.word}")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteWordItem(
    word: WordEntity,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = word.kana,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = word.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "移出生词本",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
