package com.ringlearn.app.ui.navigation

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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ringlearn.app.R
import com.ringlearn.app.ui.LocalActiveRoute
import com.ringlearn.app.ui.LocalActiveTabIsHome
import com.ringlearn.app.ui.RootViewModel
import com.ringlearn.app.ui.home.HomeScreen
import com.ringlearn.app.ui.ime.InAppImeController
import com.ringlearn.app.ui.ime.InAppKeyboardOverlay
import com.ringlearn.app.ui.ime.LocalInAppImeController
import com.ringlearn.app.ui.ai.AiChatScreen
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
@Serializable data object AiKey : NavKey

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
    BottomDestination(QuizKey, "测验", R.drawable.ic_quiz),
    BottomDestination(AiKey, "AI", R.drawable.ic_ai)
)

/**
 * 应用根：底部导航 + 常驻 Tab 宿主 (KeepAliveNavHost) + 内置键盘覆盖层。
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
        topLevelRoutes = setOf(HomeKey, StudyKey, WordBookKey, QuizKey, LookupKey, AiKey)
    )
    val navigator = remember { Navigator(navigationState) }
    val inAppImeController = remember { InAppImeController() }
    val rootViewModel: RootViewModel = hiltViewModel()
    val useInAppKeyboard by rootViewModel.useInAppKeyboard.collectAsStateWithLifecycle()

    // 注意：字段聚焦时 WindowInsets.isImeVisible 可能误报 true（系统 IME insets 层怪癖），
    // 因此仅当处于系统输入法模式（!useInAppKeyboard）时才据此隐藏底栏。
    val systemImeVisible = WindowInsets.isImeVisible
    val hideDock = systemImeVisible && !useInAppKeyboard

    // 底栏实测高度（含系统导航栏 inset）
    var dockHeightPx by remember { mutableIntStateOf(0) }

    // 键盘覆盖高度超出底栏的部分 = 键盘实测高度 - 底栏高度（导航栏 padding 两侧相消），
    // 供页面列表滚动让出键盘区；QWERTY/五十音/候选栏出现时随实测高度自适应
    SideEffect {
        inAppImeController.contentOverflowPx =
            (inAppImeController.keyboardHeightPx - dockHeightPx).coerceAtLeast(0)
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
            entry<AiKey> {
                AiChatScreen()
            }
        }
    }

    // 返回键：非首页 Tab 优先回首页（NavDisplay.onBack 的替代实现）
    BackHandler(enabled = navigationState.topLevelRoute != HomeKey) {
        navigator.goBack()
    }

    CompositionLocalProvider(
        LocalInAppImeController provides inAppImeController,
        LocalActiveTabIsHome provides (navigationState.topLevelRoute == HomeKey),
        LocalActiveRoute provides navigationState.topLevelRoute
    ) {
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
                // 常驻 Tab 宿主：首访后保持组合，切换仅变 alpha，消灭重复切换的整树重组合开销
                KeepAliveNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    entries = navigationState.toAllEntries(entryProvider),
                    topLevelRoute = navigationState.topLevelRoute,
                    initialRoute = HomeKey
                )
            }
            // 内置键盘覆盖层：盖在底栏之上（z 更高），由 Box contentAlignment 底部对齐
            InAppKeyboardOverlay(
                controller = inAppImeController
            )
        }
    }
}


