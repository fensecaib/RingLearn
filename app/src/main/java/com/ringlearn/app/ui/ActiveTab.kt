package com.ringlearn.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

/** 当前激活 Tab 是否为首页（用于首页火焰动画门控：非首页时静态渲染）。 */
val LocalActiveTabIsHome = staticCompositionLocalOf { true }

/** 当前激活的顶级 Tab 路由（供各页按激活态门控后台工作，如 Study 计时器）。 */
val LocalActiveRoute = staticCompositionLocalOf<NavKey?> { null }
