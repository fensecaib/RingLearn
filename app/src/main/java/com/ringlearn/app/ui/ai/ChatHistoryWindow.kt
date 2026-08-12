package com.ringlearn.app.ui.ai

import com.ringlearn.app.data.local.entity.AiChatEntity

/**
 * 对话历史分页窗口（纯逻辑，可单测）：维护「当前显示的最近一页 + 是否还有更早消息」。
 *
 * - [sync]：全量列表变化（新消息追加 / 重置清空）时回到最近一页，保证首屏组合量有界；
 * - [appendOlder]：向前加载一页并前置（不丢弃已有内容），由调用方负责并发防护。
 */
internal class ChatHistoryWindow(private val pageSize: Int = PAGE_SIZE) {
    var items: List<AiChatEntity> = emptyList()
        private set

    /** 全量列表中最老一条的 id（用于判断是否还有更早） */
    var oldestKnownId: Long = 0L
        private set

    val hasMoreOlder: Boolean
        get() = items.firstOrNull()?.let { it.id > oldestKnownId } ?: false

    /** 全量变化时同步：回到最近一页；空列表则清空。 */
    fun sync(full: List<AiChatEntity>) {
        if (full.isEmpty()) {
            items = emptyList()
            oldestKnownId = 0L
            return
        }
        oldestKnownId = full.first().id
        val latestId = full.last().id
        // 窗口为空 / 末尾有新消息 / 末尾被回退（重置后重建）→ 重置为最近一页
        if (items.isEmpty() || items.last().id != latestId || items.last().id > latestId) {
            items = full.takeLast(pageSize)
        }
    }

    /** 向前加载一页并前置；返回是否加载了内容。 */
    fun appendOlder(full: List<AiChatEntity>): Boolean {
        val first = items.firstOrNull() ?: return false
        val older = full.filter { it.id < first.id }.takeLast(pageSize)
        if (older.isEmpty()) return false
        items = older + items
        return true
    }

    companion object {
        const val PAGE_SIZE = 40
    }
}
