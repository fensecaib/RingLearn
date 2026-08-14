package com.ringlearn.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ringlearn.app.R
import com.ringlearn.app.ui.LocalActiveRoute
import com.ringlearn.app.ui.RootViewModel
import com.ringlearn.app.ui.home.HomeScreen
import com.ringlearn.app.ui.ime.InAppImeController
import com.ringlearn.app.ui.ime.InAppKeyboardOverlay
import com.ringlearn.app.ui.ime.LocalInAppImeController
import com.ringlearn.app.ui.ai.AiChatScreen
import com.ringlearn.app.ui.lookup.LookupScreen
import com.ringlearn.app.ui.quiz.QuizScreen
import com.ringlearn.app.ui.settings.SettingsScreen
import com.ringlearn.app.ui.study.StudyScreen
import com.ringlearn.app.ui.tools.ToolsScreen
import com.ringlearn.app.ui.wordbook.WordBookScreen
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey
@Serializable data object StudyKey : NavKey
@Serializable data object WordBookKey : NavKey
@Serializable data object QuizKey : NavKey
@Serializable data object LookupKey : NavKey
@Serializable data object AiKey : NavKey
@Serializable data object ToolsKey : NavKey
@Serializable data object SettingsKey : NavKey

private data class BottomDestination(
    val key: NavKey,
    val label: String,
    val iconRes: Int
)

private val bottomDestinations = listOf(
    BottomDestination(HomeKey, "首页", R.drawable.ic_home),
    BottomDestination(ToolsKey, "功能", R.drawable.ic_apps),
    BottomDestination(AiKey, "AI", R.drawable.ic_ai)
)

/** 四个工具页：不展示在底栏，经「功能」聚合页进入 */
private val toolRoutes = setOf(StudyKey, LookupKey, WordBookKey, QuizKey)

/** 所有「叶子」二级页（工具页 + 设置页），返回时回来源 Tab */
private val leafRoutes = toolRoutes + SettingsKey

/**
 * 应用根：底部导航 + 常驻 Tab 宿主 (KeepAliveNavHost) + 内置键盘覆盖层。
 *
 * 键盘交互架构（类真实 IME）：
 * - 底栏 [NavigationBar] 始终组合（仅系统 IME 可见时隐藏，被系统键盘盖住），
 *   内置键盘弹出时**不再移除底栏**，而是由根层 [InAppKeyboardOverlay] 盖在底栏之上，
 *   杜绝「底栏移除→重现」的视觉抖动。
 * - 键盘常驻组合 + offset 布局位移切换（背景+内容+命中区域整体移动，lambda 延迟读取不重组），
 *   键盘由页面点击输入框才弹出（默认收起），不自动弹出。
 * - 输入行/列表抬升量 = 页面内容底（nav host 实测）− 键盘覆盖层顶（Column 实测），
 *   两者同用 positionInRoot 坐标系，构造上精确，消除 inset/padding 推导误差。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RingLearnApp() {
    val navigationState = rememberNavigationState(
        startRoute = HomeKey,
        topLevelRoutes = setOf(
            HomeKey, ToolsKey, StudyKey, WordBookKey, QuizKey, LookupKey, AiKey, SettingsKey
        )
    )
    val navigator = remember { Navigator(navigationState, leafRoutes, ToolsKey) }
    val inAppImeController = remember { InAppImeController() }
    val rootViewModel: RootViewModel = hiltViewModel()
    val useInAppKeyboard by rootViewModel.useInAppKeyboard.collectAsStateWithLifecycle()

    // 注意：字段聚焦时 WindowInsets.isImeVisible 可能误报 true（系统 IME insets 层怪癖），
    // 因此仅当处于系统输入法模式（!useInAppKeyboard）时才据此隐藏底栏。
    // 条件读取同时避免内置键盘模式订阅 IME insets：本机聚焦时会误报非零，否则引发整根重组。
    val systemImeVisible = if (useInAppKeyboard) false else WindowInsets.isImeVisible
    val hideDock = systemImeVisible && !useInAppKeyboard

    // 稳定 entryProvider 身份：避免根重组重建全部 NavEntry 导致页面整树重组合
    val entryProvider = remember {
        entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    onNavigateToSettings = { navigator.navigate(SettingsKey) },
                    onNavigateToStudy = { navigator.navigate(StudyKey) },
                    onNavigateToWordBook = { navigator.navigate(WordBookKey) },
                    onNavigateToQuiz = { navigator.navigate(QuizKey) },
                    onNavigateToLookup = { navigator.navigate(LookupKey) }
                )
            }
            entry<ToolsKey> {
                ToolsScreen(
                    onNavigateToStudy = { navigator.navigate(StudyKey) },
                    onNavigateToLookup = { navigator.navigate(LookupKey) },
                    onNavigateToWordBook = { navigator.navigate(WordBookKey) },
                    onNavigateToQuiz = { navigator.navigate(QuizKey) }
                )
            }
            entry<StudyKey> {
                StudyScreen(onExit = { navigator.goBack() })
            }
            entry<WordBookKey> {
                WordBookScreen(onExit = { navigator.goBack() })
            }
            entry<QuizKey> {
                QuizScreen(onExit = { navigator.goBack() })
            }
            entry<LookupKey> {
                LookupScreen(onExit = { navigator.goBack() })
            }
            entry<AiKey> {
                AiChatScreen()
            }
            entry<SettingsKey> {
                SettingsScreen(onExit = { navigator.goBack() })
            }
        }
    }

    CompositionLocalProvider(
        LocalInAppImeController provides inAppImeController,
        // 提供稳定的 State 对象而非当前路由值：根体不再读 topLevelRoute，
        // Tab 切换只重组「真正读取 .value 的叶子」，根 Scaffold/覆盖层保持不重组。
        LocalActiveRoute provides navigationState.topLevelRouteState
    ) {
        // 返回键：非首页 Tab 优先回首页（NavDisplay.onBack 的替代实现）
        AppBackHandler(navigationState)
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
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ) {
                            bottomDestinations.forEach { destination ->
                                key(destination.key) {
                                    // 每个 item 独立作用域读路由：切 Tab 只重组受影响的 item
                                    val activeRoute = LocalActiveRoute.current.value
                                    val selected = when (destination.key) {
                                        HomeKey -> activeRoute == HomeKey || activeRoute == SettingsKey
                                        ToolsKey -> activeRoute == ToolsKey || activeRoute in toolRoutes
                                        else -> activeRoute == destination.key
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigator.navigate(destination.key) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
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
                }
            ) { padding ->
                // 页面内容底边（根坐标系 y）：实测 nav host 底部，供三页计算输入行/列表抬升量
                // lift = pageContentBottomPx - overlayTopPx（与覆盖层 Column 同一坐标系）
                KeepAliveNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .onGloballyPositioned { coords ->
                            inAppImeController.pageContentBottomPx =
                                (coords.positionInRoot().y + coords.size.height).roundToInt()
                        },
                    entries = navigationState.toAllEntries(entryProvider),
                    topLevelRoute = navigationState.topLevelRouteState,
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

/**
 * 返回键处理：独立组合作用域自读激活路由，避免根体订阅 topLevelRoute。
 */
@Composable
private fun AppBackHandler(navigationState: NavigationState) {
    val navigator = remember(navigationState) { Navigator(navigationState, leafRoutes, ToolsKey) }
    val isHome = LocalActiveRoute.current.value == HomeKey
    BackHandler(enabled = !isHome) {
        navigator.goBack()
    }
}
