package com.ringlearn.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * 创建导航状态：每个顶级 Tab 拥有独立的 back stack，切换 Tab 时状态被保留。
 */
@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>
): NavigationState {
    val topLevelRoute = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) {
        mutableStateOf(startRoute)
    }
    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }
    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks
        )
    }
}

/** 导航状态持有者（Navigation 3 官方范式）。 */
class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var topLevelRoute: NavKey by topLevelRoute

    /** 当前实际参与组合的栈：首页栈 + 当前 Tab 栈（NavDisplay 仅组合当前场景）。 */
    val stacksInUse: List<NavKey>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }
}

/** 处理导航事件（前进 / 返回），只修改 [NavigationState]。 */
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            // 顶级 Tab：直接切换
            state.topLevelRoute = route
        } else {
            // 子路由：压入当前栈
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.last()
        if (currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}

/** 将导航状态转换为 NavDisplay 需要的条目列表（应用装饰器：状态保存 + ViewModel）。 */
@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): SnapshotStateList<NavEntry<NavKey>> {
    return toAllEntries(entryProvider).map { it.second }.toMutableStateList()
}

/**
 * 全部顶级 Tab 的装饰条目（每个 Tab 一个 back stack）。
 * 供常驻 Tab 宿主使用：所有条目保持组合、用 alpha 切换可见性，
 * 彻底避免 NavDisplay「离开即销毁 → 每次切换整树重组合」的重复开销。
 * [entryProvider] 必须在调用方 remember 稳定，避免重建全部 NavEntry。
 */
@Composable
fun NavigationState.toAllEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<Pair<NavKey, NavEntry<NavKey>>> {
    val decoratedEntries = backStacks.mapValues { (_, stack) ->
        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator<NavKey>()
        )
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )
    }
    // 保持确定顺序：backStacks 按顶级路由插入序（Home/Study/WordBook/Quiz/Lookup）
    // NavEntry.key 为 private，故由对应的 back stack 键明确路由关系
    return decoratedEntries.mapNotNull { (route, stackEntries) ->
        stackEntries.firstOrNull()?.let { route to it }
    }
}
