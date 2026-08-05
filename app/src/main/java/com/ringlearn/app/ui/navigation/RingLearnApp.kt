package com.ringlearn.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ringlearn.app.R
import com.ringlearn.app.ui.home.HomeScreen
import com.ringlearn.app.ui.quiz.QuizScreen
import com.ringlearn.app.ui.study.StudyScreen
import com.ringlearn.app.ui.wordbook.WordBookScreen

private data class BottomDestination(
    val route: String,
    val label: String,
    val iconRes: Int
)

private val bottomDestinations = listOf(
    BottomDestination("home", "首页", R.drawable.ic_home),
    BottomDestination("study", "学习", R.drawable.ic_study),
    BottomDestination("wordbook", "生词本", R.drawable.ic_wordbook),
    BottomDestination("quiz", "测验", R.drawable.ic_quiz)
)

/** 应用根：底部导航 + NavHost */
@Composable
fun RingLearnApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                bottomDestinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            // 快速淡入淡出，避免默认长过渡期间新旧页面叠加渲染造成卡顿
            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(150)) },
            popExitTransition = { fadeOut(animationSpec = tween(150)) }
        ) {
            composable("home") {
                HomeScreen(
                    onNavigateToStudy = {
                        navController.navigate("study") { launchSingleTop = true }
                    },
                    onNavigateToWordBook = {
                        navController.navigate("wordbook") { launchSingleTop = true }
                    },
                    onNavigateToQuiz = {
                        navController.navigate("quiz") { launchSingleTop = true }
                    }
                )
            }
            composable("study") {
                StudyScreen(onExit = { navController.popBackStack() })
            }
            composable("wordbook") {
                WordBookScreen()
            }
            composable("quiz") {
                QuizScreen(onExit = { navController.popBackStack() })
            }
        }
    }
}
