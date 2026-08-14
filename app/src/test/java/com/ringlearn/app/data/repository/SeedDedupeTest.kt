package com.ringlearn.app.data.repository

import com.ringlearn.app.data.local.entity.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** 增量种子去重：仅保留 (word, kana) 不在词库中的词条，且过滤词表内部重复。 */
class SeedDedupeTest {

    private fun word(word: String, kana: String, id: Long = 0L) = WordEntity(
        id = id,
        word = word,
        kana = kana,
        meaning = "测试",
        example = "例文",
        exampleMeaning = "例句译文",
        jlpt = "N1"
    )

    @Test
    fun `保留新词并过滤与词库重复的词条`() {
        val existing = setOf("既存" to "きぞん")
        val words = listOf(word("既存", "きぞん"), word("新語", "しんご"), word("別語", "べつご"))

        val result = filterNewWords(existing, words)

        assertEquals(listOf("新語", "別語"), result.map { it.word })
    }

    @Test
    fun `词表内部重复只保留第一条`() {
        val result = filterNewWords(emptySet(), listOf(word("重複", "ちょうふく"), word("重複", "ちょうふく")))

        assertEquals(1, result.size)
    }

    @Test
    fun `空词表返回空列表`() {
        assertEquals(emptyList<WordEntity>(), filterNewWords(emptySet(), emptyList()))
    }
}
