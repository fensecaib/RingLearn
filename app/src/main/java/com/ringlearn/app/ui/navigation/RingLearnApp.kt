package com.ringlearn.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/** 应用根：底部导航 + Navigation 3 (NavDisplay)。 */
@Composable
fun RingLearnApp() {
    val navigationState = rememberNavigationState(
        startRoute = HomeKey,
        topLevelRoutes = setOf(HomeKey, StudyKey, WordBookKey, QuizKey, LookupKey)
    )
    val navigator = remember { Navigator(navigationState) }

    val entryProvider = entryProvider {
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
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
    ) { padding ->
        NavDisplay(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() }
        )
    }
}
