package com.ringlearn.app.domain.ime

import com.ringlearn.app.domain.ime.RomajiEngine.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiEngineTest {

    private fun type(engine: RomajiEngine, text: String): String = buildString {
        text.forEach { append(engine.input(it)) }
    }

    /** 每次转换使用全新引擎，避免 pending 缓冲跨用例残留 */
    private fun convert(text: String, mode: Mode = Mode.HIRAGANA): String =
        type(RomajiEngine(mode), text)

    @Test
    fun `basic vowels`() {
        assertEquals("あいうえお", convert("aiueo"))
    }

    @Test
    fun `k row`() {
        assertEquals("かきくけこ", convert("kakikukeko"))
    }

    @Test
    fun `shi and s row`() {
        assertEquals("し", convert("shi"))
        assertEquals("さすせそ", convert("sasuseso"))
    }

    @Test
    fun `chi and tsu`() {
        assertEquals("ち", convert("chi"))
        assertEquals("つ", convert("tsu"))
        assertEquals("たてと", convert("tateto"))
    }

    @Test
    fun `dakuten`() {
        assertEquals("がぎぐげご", convert("gagigugego"))
        assertEquals("ざじずぜぞ", convert("zajizuzezo"))
        assertEquals("だ", convert("da"))
        assertEquals("ばびぶべぼ", convert("babibubebo"))
    }

    @Test
    fun `handakuten`() {
        assertEquals("ぱぴぷぺぽ", convert("papipupepo"))
    }

    @Test
    fun `youon`() {
        assertEquals("きゃきゅきょ", convert("kyakyukyo"))
        assertEquals("しゃしゅしょ", convert("shashusho"))
        assertEquals("ちゃちゅちょ", convert("chachucho"))
        assertEquals("にゃにゅにょ", convert("nyanyunyo"))
        assertEquals("ぎゃぎゅぎょ", convert("gyagyugyo"))
        assertEquals("じゃじゅじょ", convert("jajujo"))
        assertEquals("ぴゃぴゅぴょ", convert("pyapyupyo"))
    }

    @Test
    fun `sokuon double consonant`() {
        assertEquals("っ", convert("kk"))
        assertEquals("っか", convert("kka"))
        assertEquals("がっこう", convert("gakkou"))
        assertEquals("ざっし", convert("zasshi"))
        assertEquals("きって", convert("kitte"))
        assertEquals("いっぱい", convert("ippai"))
    }

    @Test
    fun `long vowel o plus u`() {
        assertEquals("こう", convert("kou"))
        assertEquals("おう", convert("ou"))
        assertEquals("せんせい", convert("sensei"))
    }

    @Test
    fun `n handling`() {
        val engine = RomajiEngine()
        // n 单独输入保持 pending，flush 后为 ん
        type(engine, "hon")
        assertEquals("ほん", "ほ" + engine.flush())
        assertEquals("みんな", convert("minna"))
        assertEquals("ん", convert("nn"))
        assertEquals("んな", convert("nna"))
        assertEquals("かんい", convert("kan'i"))
        // n + 辅音 -> ん + 后续音节
        assertEquals("んか", convert("nka"))
        val e2 = RomajiEngine()
        assertEquals("こんばん", type(e2, "konban") + e2.flush())
        assertEquals("きんようび", convert("kin'youbi"))
    }

    @Test
    fun `katakana mode`() {
        assertEquals("カキクケコ", convert("kakikukeko", Mode.KATAKANA))
        assertEquals("ッ", convert("kk", Mode.KATAKANA))
        assertEquals("キャ", convert("kya", Mode.KATAKANA))
        assertEquals("シャ", convert("sha", Mode.KATAKANA))
        val e = RomajiEngine(Mode.KATAKANA); type(e, "n"); assertEquals("ン", e.flush())
    }

    @Test
    fun `small kana`() {
        assertEquals("ぁ", convert("la"))
        assertEquals("ゃ", convert("xya"))
        assertEquals("っ", convert("ltu"))
        assertEquals("ぃ", convert("xi"))
    }

    @Test
    fun `non romaji characters pass through`() {
        assertEquals("あ1", convert("a1"))
        assertEquals("漢字", convert("漢字"))
    }

    @Test
    fun `flush commits pending as romaji except n`() {
        val engine = RomajiEngine()
        type(engine, "sh")
        assertEquals("sh", engine.flush())
        assertFalse(engine.hasPending)
    }

    @Test
    fun `backspace pops pending first`() {
        val engine = RomajiEngine()
        type(engine, "k")
        assertTrue(engine.hasPending)
        assertTrue(engine.backspace())
        assertFalse(engine.hasPending)
    }

    @Test
    fun `toggle mode`() {
        val engine = RomajiEngine()
        engine.toggleMode()
        assertEquals("カ", type(engine, "ka"))
        engine.toggleMode()
        assertEquals("か", type(engine, "ka"))
    }

    @Test
    fun `real words roundtrip`() {
        assertEquals("にほんご", convert("nihongo"))
        assertEquals("きょう", convert("kyou"))
        assertEquals("しゅくだい", convert("shukudai"))
        assertEquals("べんきょう", convert("benkyou"))
        assertEquals("おおきい", convert("ookii"))
    }
}
