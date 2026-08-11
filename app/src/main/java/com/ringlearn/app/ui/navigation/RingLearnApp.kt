package com.ringlearn.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ringlearn.app.R
import com.ringlearn.app.ui.RootViewModel
import com.ringlearn.app.ui.home.HomeScreen
import com.ringlearn.app.ui.ime.InAppImeController
import com.ringlearn.app.ui.ime.InAppKeyboardOverlay
import com.ringlearn.app.ui.ime.LocalInAppImeController
import com.ringlearn.app.ui.lookup.LookupScreen
import com.ringlearn.app.ui.quiz.QuizScreen
import com.ringlearn.app.ui.study.StudyScreen
import com.ringlearn.app.ui.wordbook.WordBookScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey
@Serializable data object StudyKey : NavKey
@Serializable data object WordBookKey : NavKey
@Serializable data object QuizKey : NavKey
@Serializable data object LookupKey : NavKey

private data class BottomDestination(
    val key: NavKey,
    val label: String,
    val iconRes: Int
)

private val bottomDestinations = listOf(
    BottomDestination(HomeKey, "首页", R.drawable.ic_home),
    BottomDestination(StudyKey, "学习", R.drawable.ic_study),
    BottomDestination(LookupKey, "查词", R.drawable.ic_search),
    BottomDestination(WordBookKey, "生词本", R.drawable.ic_wordbook),
    BottomDestination(QuizKey, "测验", R.drawable.ic_quiz)
)

/**
 * 应用根：底部导航 + Navigation 3 (NavDisplay) + 内置键盘覆盖层。
 *
 * 键盘交互架构（类真实 IME）：
 * - 底栏 [NavigationBar] 始终组合（仅系统 IME 可见时隐藏，被系统键盘盖住），
 *   内置键盘弹出时**不再移除底栏**，而是由根层 [InAppKeyboardOverlay] 盖在底栏之上，
 *   杜绝「底栏移除→重现」的视觉抖动。
 * - 键盘常驻组合 + graphicsLayer 位移切换（开/关不重新组成/重测量），
 *   键盘由页面点击输入框才弹出（默认收起），不自动弹出。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RingLearnApp() {
    val navigationState = rememberNavigationState(
        startRoute = HomeKey,
        topLevelRoutes = setOf(HomeKey, StudyKey, WordBookKey, QuizKey, LookupKey)
    )
    val navigator = remember { Navigator(navigationState) }
    val inAppImeController = remember { InAppImeController() }
    val rootViewModel: RootViewModel = hiltViewModel()
    val useInAppKeyboard by rootViewModel.useInAppKeyboard.collectAsStateWithLifecycle()

    // 注意：字段聚焦时 WindowInsets.isImeVisible 可能误报 true（系统 IME insets 层怪癖），
    // 因此仅当处于系统输入法模式（!useInAppKeyboard）时才据此隐藏底栏。
    val systemImeVisible = WindowInsets.isImeVisible
    val hideDock = systemImeVisible && !useInAppKeyboard
    val density = LocalDensity.current

    // 底栏实测高度（含系统导航栏 inset）；键盘覆盖层高度用固定默认值（QWERTY ~240dp）
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val dockHeightDp = with(density) { dockHeightPx.toDp() }
    val keyboardHeightDp = 240.dp

    // 键盘覆盖高度超出底栏的部分：页面列表据此让出滚动区（内容不整体重排）
    inAppImeController.contentOverflowPx = with(density) {
        (keyboardHeightDp - dockHeightDp).coerceAtLeast(0.dp).roundToPx()
    }

    // 稳定 entryProvider 身份：避免根重组重建全部 NavEntry 导致页面整树重组合
    val entryProvider = remember {
        entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    onNavigateToStudy = { navigator.navigate(StudyKey) },
                    onNavigateToWordBook = { navigator.navigate(WordBookKey) },
                    onNavigateToQuiz = { navigator.navigate(QuizKey) },
                    onNavigateToLookup = { navigator.navigate(LookupKey) }
                )
            }
            entry<StudyKey> {
                StudyScreen(onExit = { navigator.goBack() })
            }
            entry<WordBookKey> {
                WordBookScreen()
            }
            entry<QuizKey> {
                QuizScreen(onExit = { navigator.goBack() })
            }
            entry<LookupKey> {
                LookupScreen()
            }
        }
    }

    CompositionLocalProvider(LocalInAppImeController provides inAppImeController) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    // 底栏常驻：仅在系统输入法模式下系统键盘可见时隐藏（被系统键盘盖住）；
                    // 内置键盘场景从不移除底栏（由覆盖层盖住）
                    if (!hideDock) {
                        NavigationBar(
                            modifier = Modifier.onSizeChanged { dockHeightPx = it.height }
                        ) {
                            bottomDestinations.forEach { destination ->
                                val selected = destination.key == navigationState.topLevelRoute
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigator.navigate(destination.key) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(destination.iconRes),
                                            contentDescription = destination.label
                                        )
                                    },
                                    label = { Text(destination.label) }
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                NavDisplay(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    entries = navigationState.toEntries(entryProvider),
                    onBack = { navigator.goBack() },
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) }
                )
            }
            // 内置键盘覆盖层：盖在底栏之上（z 更高），由 Box contentAlignment 底部对齐
            InAppKeyboardOverlay(
                controller = inAppImeController
            )
        }
    }
}
