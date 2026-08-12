package com.ringlearn.app.ui.ai

import com.ringlearn.app.data.local.entity.AiChatEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryWindowTest {

    private fun msg(id: Long) = AiChatEntity(
        id = id,
        sessionId = "default",
        role = "user",
        content = "m$id",
        createdAt = id
    )

    private fun ids(list: List<AiChatEntity>) = list.map { it.id }

    @Test
    fun `sync empty clears window`() {
        val w = ChatHistoryWindow(pageSize = 40)
        w.sync((1L..50L).map(::msg))
        assertTrue(w.items.isNotEmpty())
        w.sync(emptyList())
        assertTrue(w.items.isEmpty())
        assertFalse(w.hasMoreOlder)
    }

    @Test
    fun `sync less than page keeps all`() {
        val w = ChatHistoryWindow(pageSize = 40)
        w.sync((1L..10L).map(::msg))
        assertEquals((1L..10L).toList(), ids(w.items))
        assertFalse(w.hasMoreOlder)
    }

    @Test
    fun `sync exactly page keeps all`() {
        val w = ChatHistoryWindow(pageSize = 40)
        w.sync((1L..40L).map(::msg))
        assertEquals((1L..40L).toList(), ids(w.items))
        assertFalse(w.hasMoreOlder)
    }

    @Test
    fun `sync over page keeps latest page`() {
        val w = ChatHistoryWindow(pageSize = 40)
        w.sync((1L..100L).map(::msg))
        assertEquals((61L..100L).toList(), ids(w.items))
        assertTrue(w.hasMoreOlder)
    }

    @Test
    fun `append older prepends without dropping newest`() {
        val w = ChatHistoryWindow(pageSize = 40)
        val full = (1L..120L).map(::msg)
        w.sync(full)
        assertTrue(w.appendOlder(full))
        assertEquals((41L..120L).toList(), ids(w.items))
        assertTrue(w.appendOlder(full))
        assertEquals((1L..120L).toList(), ids(w.items))
        assertFalse(w.appendOlder(full))
    }

    @Test
    fun `new message appended after loading older resyncs to latest page`() {
        val w = ChatHistoryWindow(pageSize = 40)
        var full = (1L..120L).map(::msg)
        w.sync(full)
        w.appendOlder(full)
        full = full + msg(121L)
        w.sync(full)
        assertEquals((82L..121L).toList(), ids(w.items))
    }

    @Test
    fun `reset then rebuild window`() {
        val w = ChatHistoryWindow(pageSize = 40)
        w.sync((1L..50L).map(::msg))
        w.sync(emptyList())
        w.sync((200L..230L).map(::msg))
        assertEquals((200L..230L).toList(), ids(w.items))
        assertFalse(w.hasMoreOlder)
    }

    @Test
    fun `lastMessageIndex without sentinel`() {
        assertEquals(39, lastMessageIndex(hasMoreOlder = false, messageCount = 40))
    }

    @Test
    fun `lastMessageIndex with sentinel`() {
        assertEquals(40, lastMessageIndex(hasMoreOlder = true, messageCount = 40))
    }

    @Test
    fun `lastMessageIndex empty list`() {
        assertEquals(0, lastMessageIndex(hasMoreOlder = false, messageCount = 0))
        assertEquals(0, lastMessageIndex(hasMoreOlder = true, messageCount = 0))
    }

    @Test
    fun `sync refreshes content for same ids`() {
        val w = ChatHistoryWindow(pageSize = 40)
        w.sync((1L..50L).map { msg(it) })
        // 仅 id=50 内容更新（模拟助手回复完成写库，id 不变）
        val updated = (1L..50L).map { if (it == 50L) msg(50L).copy(content = "updated-reply") else msg(it) }
        w.sync(updated)
        assertEquals(50L, w.items.last().id)
        assertEquals("updated-reply", w.items.last().content)
    }
}
