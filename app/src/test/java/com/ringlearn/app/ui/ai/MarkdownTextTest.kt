package com.ringlearn.app.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun `parse heading and paragraph`() {
        val blocks = parseMarkdown("# 标题\n正文内容")
        assertEquals(2, blocks.size)
    }

    @Test
    fun `parse bullets`() {
        val blocks = parseMarkdown("- 甲\n- 乙")
        assertEquals(2, blocks.size)
    }

    @Test
    fun `parse numbered list`() {
        val blocks = parseMarkdown("1. 第一\n2. 第二")
        assertEquals(2, blocks.size)
        assertEquals("第一", (blocks[0] as MdBlock.Numbered).text)
    }

    @Test
    fun `parse code fence`() {
        val blocks = parseMarkdown("```kotlin\nval x = 1\n```")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun `strip html`() {
        val blocks = parseMarkdown("a<b>tag</b>c")
        assertEquals(1, blocks.size)
        val para = blocks[0] as MdBlock.Paragraph
        assertEquals("atagc", para.text)
    }
}

