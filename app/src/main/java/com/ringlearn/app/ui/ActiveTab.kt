package com.ringlearn.app.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

/**
 * 当前激活的顶级 Tab 路由（稳定 State 持有，供各页按激活态门控后台工作，如 Study 计时器、
 * 首页火焰动画）。根层只提供一次 State 对象；消费方在叶子作用域读 `.value`，
 * Tab 切换只重组真正读取该值的叶子，不重组整棵根树。
 */
val LocalActiveRoute = staticCompositionLocalOf<State<NavKey>> {
    error("LocalActiveRoute not provided")
}
