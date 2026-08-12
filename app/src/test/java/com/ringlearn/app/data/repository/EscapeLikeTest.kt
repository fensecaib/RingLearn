package com.ringlearn.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class EscapeLikeTest {

    @Test
    fun `percent is escaped`() {
        assertEquals("a\\%b", escapeLike("a%b"))
    }

    @Test
    fun `underscore is escaped`() {
        assertEquals("a\\_b", escapeLike("a_b"))
    }

    @Test
    fun `backslash is doubled`() {
        assertEquals("a\\\\b", escapeLike("a\\b"))
    }

    @Test
    fun `mixed wildcards are all escaped`() {
        assertEquals("\\%\\_\\\\", escapeLike("%_\\"))
    }

    @Test
    fun `plain text is unchanged`() {
        assertEquals("hello 日本語", escapeLike("hello 日本語"))
    }

    @Test
    fun `empty query stays empty`() {
        assertEquals("", escapeLike(""))
    }
}
