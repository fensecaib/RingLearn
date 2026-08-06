package com.ringlearn.app.domain.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RomajiEngine v2 测试：IME 组合状态机（committed + composed + buffer）。
 */
class RomajiEngineTest {

    @Test
    fun `basic syllables convert to composition kana`() {
        val e = RomajiEngine()
        e.input('k'); e.input('a')
        assertEquals("か", e.compositionKana)
        assertEquals("か", e.fullText)
        assertTrue(e.isComposing)
    }

    @Test
    fun `youon kyo converts correctly`() {
        val e = RomajiEngine()
        "kyou".forEach { e.input(it) }
        assertEquals("きょう", e.compositionKana)
    }

    @Test
    fun `sokuon double consonant`() {
        val e = RomajiEngine()
        "kka".forEach { e.input(it) }
        assertEquals("っか", e.compositionKana)
    }

    @Test
    fun `hatsuon nn and n plus consonant`() {
        val e = RomajiEngine()
        "minna".forEach { e.input(it) }
        assertEquals("みんな", e.compositionKana)
    }

    @Test
    fun `long vowel ou`() {
        val e = RomajiEngine()
        "gakkou".forEach { e.input(it) }
        assertEquals("がっこう", e.compositionKana)
    }

    @Test
    fun `apostrophe separates n from vowel`() {
        val e = RomajiEngine()
        "kan'i".forEach { e.input(it) }
        assertEquals("かんい", e.compositionKana)
    }

    @Test
    fun `katakana mode converts composition`() {
        val e = RomajiEngine()
        "kyou".forEach { e.input(it) }
        e.toggleMode()
        assertEquals(RomajiEngine.Mode.KATAKANA, e.mode)
        assertEquals("キョウ", e.compositionKana)
        e.toggleMode()
        assertEquals("きょう", e.compositionKana)
    }

    @Test
    fun `commit moves composition to committed`() {
        val e = RomajiEngine()
        "kyou".forEach { e.input(it) }
        val committed = e.commit()
        assertEquals("きょう", committed)
        assertEquals("きょう", e.fullText)
        assertFalse(e.isComposing)
        // 继续输入追加到新组合
        e.input('n'); e.input('i')
        assertEquals("きょうに", e.fullText)
    }

    @Test
    fun `commitCandidate replaces kana with kanji`() {
        val e = RomajiEngine()
        "kyou".forEach { e.input(it) }
        val committed = e.commitCandidate("今日")
        assertEquals("今日", committed)
        assertEquals("今日", e.fullText)
        assertFalse(e.isComposing)
    }

    @Test
    fun `space flushes pending romaji but keeps composing`() {
        val e = RomajiEngine()
        e.input('k'); e.input('y'); e.input('o')
        e.input('n')
        assertEquals("きょ", e.compositionKana)
        assertTrue(e.isComposing)
        e.space()
        assertEquals("きょん", e.compositionKana)
        assertTrue(e.isComposing)
    }

    @Test
    fun `backspace precedence buffer then kana then committed`() {
        val e = RomajiEngine()
        "kyou".forEach { e.input(it) }
        // 再输入一个未完成音节
        e.input('s')
        assertEquals("きょう", e.compositionKana)
        assertEquals("s", e.pendingRomaji)
        assertTrue(e.backspace()) // 先退缓冲
        assertEquals("", e.pendingRomaji)
        assertEquals("きょう", e.compositionKana)
        assertTrue(e.backspace()) // 再退组合假名
        assertEquals("きょ", e.compositionKana)
        e.commit()
        assertTrue(e.backspace()) // 再退已提交（きょ -> き）
        assertEquals("き", e.fullText)
        assertTrue(e.backspace()) // 移除最后一个已提交字符
        assertEquals("", e.fullText)
        assertFalse(e.backspace()) // 全部为空
    }

    @Test
    fun `inputKana appends directly to composition`() {
        val e = RomajiEngine()
        e.inputKana("きょう")
        assertEquals("きょう", e.compositionKana)
        e.inputKana("の")
        assertEquals("きょうの", e.compositionKana)
    }

    @Test
    fun `non letter commits composition and inserts literally`() {
        val e = RomajiEngine()
        "kyou".forEach { e.input(it) }
        e.input('1')
        assertEquals("きょう1", e.fullText)
        assertFalse(e.isComposing)
    }

    @Test
    fun `adoptText discards composition`() {
        val e = RomajiEngine()
        "kyou".forEach { e.input(it) }
        e.adoptText("今日")
        assertEquals("今日", e.fullText)
        assertFalse(e.isComposing)
    }

    @Test
    fun `katakana round trip`() {
        assertEquals("キョウ", RomajiEngine.toKatakana("きょう"))
        assertEquals("きょう", RomajiEngine.toHiragana("キョウ"))
        assertEquals("ヴ", RomajiEngine.toKatakana("ゔ"))
        assertEquals("ゔ", RomajiEngine.toHiragana("ヴ"))
    }

    @Test
    fun `invalid letter q becomes literal without crash`() {
        val e = RomajiEngine()
        e.input('q')
        assertEquals("q", e.compositionKana)
        e.input('q')
        assertEquals("qq", e.compositionKana)
    }

    @Test
    fun `invalid romaji mixed with valid converts partially`() {
        val e = RomajiEngine()
        "kyoq".forEach { e.input(it) }
        assertEquals("きょq", e.compositionKana)
    }

    @Test
    fun `sokuon with kana grid`() {
        val e = RomajiEngine()
        e.inputKana("がっこう")
        assertEquals("がっこう", e.compositionKana)
        e.commit()
        assertEquals("がっこう", e.fullText)
    }
}


