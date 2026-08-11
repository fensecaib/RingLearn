package com.ringlearn.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** 当前激活 Tab 是否为首页（用于首页火焰动画门控：非首页时静态渲染）。 */
val LocalActiveTabIsHome = staticCompositionLocalOf { true }
