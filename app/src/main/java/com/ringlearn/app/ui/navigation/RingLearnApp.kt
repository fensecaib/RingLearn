package com.ringlearn.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ringlearn.app.R
import com.ringlearn.app.ui.home.HomeScreen
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
 * 应用根：底部导航 + Navigation 3 (NavDisplay)。
 *
 * 输入面可见性策略：系统 IME（[WindowInsets.isImeVisible]）或内置键盘
 * （由查词/生词本页通过 [LookupScreen]/[WordBookScreen] 的 onInAppImeVisibilityChange 上报）
 * 任一可见时**完全不组合** NavigationBar，让键盘真正停靠在屏幕底部（类真实 IME），
 * 避免键盘上方残留 dock 栏空白；键盘收起或离开页面后 dock 自动恢复。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RingLearnApp() {
    val navigationState = rememberNavigationState(
        startRoute = HomeKey,
        topLevelRoutes = setOf(HomeKey, StudyKey, WordBookKey, QuizKey, LookupKey)
    )
    val navigator = remember { Navigator(navigationState) }

    val systemImeVisible = WindowInsets.isImeVisible
    var inAppImeVisible by remember { mutableStateOf(false) }
    // 稳定回调身份：避免每次根重组创建新 lambda
    val reportInAppIme: (Boolean) -> Unit = remember {
        { visible -> inAppImeVisible = visible }
    }
    val bottomBarVisible = !systemImeVisible && !inAppImeVisible

    // 稳定 entryProvider 身份：Navigation 3 的 rememberDecoratedNavEntries 以它为 remember key，
    // 若不稳定，键盘开关/Tab 切换等每次根重组都会重建全部 NavEntry，导致已组合页面整树重组合。
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
                WordBookScreen(onInAppImeVisibilityChange = reportInAppIme)
            }
            entry<QuizKey> {
                QuizScreen(onExit = { navigator.goBack() })
            }
            entry<LookupKey> {
                LookupScreen(onInAppImeVisibilityChange = reportInAppIme)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (bottomBarVisible) {
                NavigationBar {
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
}
