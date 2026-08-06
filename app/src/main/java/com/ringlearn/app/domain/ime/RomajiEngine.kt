package com.ringlearn.app.domain.ime

/**
 * 罗马音 → 假名转换引擎（类 IME 行为，纯 Kotlin、无 Android 依赖，可单元测试）。
 *
 * 设计要点：
 * - 维护一个 pending 缓冲区：输入的罗马音先进入缓冲区，能完整成拍时立即提交为假名。
 * - 支持清音 / 浊音 / 半浊音 / 拗音 / 促音（双写辅音）/ 长音（o+u）/ 拨音（n）/
 *   小假名（l/x 前缀）/ 片假名模式。
 * - 引擎只负责“输入按键 → 应追加到已提交文本的内容”，文本状态由调用方持有。
 */
class RomajiEngine(
    var mode: Mode = Mode.HIRAGANA
) {
    enum class Mode { HIRAGANA, KATAKANA }

    /** 待转换的罗马音缓冲区 */
    private val buffer = StringBuilder()

    val pendingRomaji: String get() = buffer.toString()
    val hasPending: Boolean get() = buffer.isNotEmpty()

    fun toggleMode() {
        mode = if (mode == Mode.HIRAGANA) Mode.KATAKANA else Mode.HIRAGANA
    }

    /**
     * 处理单个按键，返回需要追加到“已提交文本”的内容（可能为空串）。
     * @param ch 按键字符；字母会进入罗马音转换流程，其余字符（含假名、数字、符号）直接原样追加。
     */
    fun input(ch: Char): String {
        val c = ch.lowercaseChar()
        if (c !in 'a'..'z' && c != '\'') {
            val flushed = flush()
            return flushed + ch
        }
        if (c == '\'') {
            // 撇号用于终止拨音 n（如 kan'i -> かんい）
            if (buffer.toString() == "n") {
                buffer.setLength(0)
                return toKana("ん")
            }
            return flush()
        }
        buffer.append(c)
        return convert()
    }

    /** 强制提交缓冲区（如按下空格/回车）；"n" 会转为 ん，其余原样输出罗马音。 */
    fun flush(): String {
        val s = buffer.toString()
        buffer.setLength(0)
        return if (s == "n") toKana("ん") else s
    }

    /** 回退一个字符：优先从缓冲区回退；缓冲区为空则返回 false（由调用方删除已提交文本）。 */
    fun backspace(): Boolean {
        if (buffer.isNotEmpty()) {
            buffer.deleteCharAt(buffer.length - 1)
            return true
        }
        return false
    }

    /** 核心转换：尝试把缓冲区内容转换为假名。 */
    private fun convert(): String {
        val s = buffer.toString()

        // 1. 促音：双写辅音 -> っ（保留一个辅音等待后续元音），如 kka -> っか
        if (s.length >= 2 && s[0] == s[1] && s[0] in "kstcphfgzjdvb") {
            buffer.deleteCharAt(0)
            return toKana("っ")
        }

        // 2. 拨音 nn -> ん（保留一个 n，使得 nna -> んな、minna -> みんな）
        if (s == "nn") {
            buffer.setLength(0)
            buffer.append('n')
            return toKana("ん")
        }

        // 3. n + 非元音/非 y 的辅音 -> ん + 继续处理剩余（如 nt -> ん + t 待转换）
        if (s.length > 1 && s[0] == 'n' && !isPrefix(s)) {
            buffer.deleteCharAt(0)
            return toKana("ん") + convert()
        }

        // 4. 整体匹配：整拍假名
        val kana = TABLE[s]
        if (kana != null) {
            buffer.setLength(0)
            return toKana(kana)
        }

        // 5. tch 消音：tcha/tchi 等中先导的 t 并入后续 ch
        if (s.startsWith("tch") && !isPrefix(s)) {
            buffer.deleteCharAt(0)
            return convert()
        }

        // 6. 若缓冲区开头不再可能是任何罗马音的前缀 -> 提交第一个字符为罗马音
        if (!isPrefix(s)) {
            val c = s[0]
            buffer.deleteCharAt(0)
            return c.toString()
        }

        // 7. 仍可能是有效前缀，继续等待更多按键
        return ""
    }

    private fun toKana(hiragana: String): String =
        if (mode == Mode.HIRAGANA) hiragana else toKatakana(hiragana)

    private companion object {
        /** 平假名音节表（含拗音、特殊外来语音、小假名） */
        val TABLE: Map<String, String> = buildMap {
            put("a", "あ"); put("i", "い"); put("u", "う"); put("e", "え"); put("o", "お")
            put("ka", "か"); put("ki", "き"); put("ku", "く"); put("ke", "け"); put("ko", "こ")
            put("sa", "さ"); put("shi", "し"); put("su", "す"); put("se", "せ"); put("so", "そ")
            put("ta", "た"); put("chi", "ち"); put("tsu", "つ"); put("te", "て"); put("to", "と")
            put("na", "な"); put("ni", "に"); put("nu", "ぬ"); put("ne", "ね"); put("no", "の")
            put("ha", "は"); put("hi", "ひ"); put("fu", "ふ"); put("he", "へ"); put("ho", "ほ")
            put("ma", "ま"); put("mi", "み"); put("mu", "む"); put("me", "め"); put("mo", "も")
            put("ya", "や"); put("yu", "ゆ"); put("yo", "よ")
            put("ra", "ら"); put("ri", "り"); put("ru", "る"); put("re", "れ"); put("ro", "ろ")
            put("wa", "わ"); put("wo", "を")
            // 浊音
            put("ga", "が"); put("gi", "ぎ"); put("gu", "ぐ"); put("ge", "げ"); put("go", "ご")
            put("za", "ざ"); put("ji", "じ"); put("zu", "ず"); put("ze", "ぜ"); put("zo", "ぞ")
            put("da", "だ"); put("de", "で"); put("do", "ど")
            // 半浊音
            put("ba", "ば"); put("bi", "び"); put("bu", "ぶ"); put("be", "べ"); put("bo", "ぼ")
            put("pa", "ぱ"); put("pi", "ぴ"); put("pu", "ぷ"); put("pe", "ぺ"); put("po", "ぽ")
            // 拗音
            put("kya", "きゃ"); put("kyu", "きゅ"); put("kyo", "きょ")
            put("sha", "しゃ"); put("shu", "しゅ"); put("sho", "しょ")
            put("cha", "ちゃ"); put("chu", "ちゅ"); put("cho", "ちょ")
            put("nya", "にゃ"); put("nyu", "にゅ"); put("nyo", "にょ")
            put("hya", "ひゃ"); put("hyu", "ひゅ"); put("hyo", "ひょ")
            put("mya", "みゃ"); put("myu", "みゅ"); put("myo", "みょ")
            put("rya", "りゃ"); put("ryu", "りゅ"); put("ryo", "りょ")
            put("gya", "ぎゃ"); put("gyu", "ぎゅ"); put("gyo", "ぎょ")
            put("ja", "じゃ"); put("ju", "じゅ"); put("jo", "じょ")
            put("bya", "びゃ"); put("byu", "びゅ"); put("byo", "びょ")
            put("pya", "ぴゃ"); put("pyu", "ぴゅ"); put("pyo", "ぴょ")
            // 外来语音
            put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ")
            put("va", "ゔぁ"); put("vi", "ゔぃ"); put("vu", "ゔ"); put("ve", "ゔぇ"); put("vo", "ゔぉ")
            put("wi", "うぃ"); put("we", "うぇ")
            // 小假名（l / x 前缀）
            put("la", "ぁ"); put("li", "ぃ"); put("lu", "ぅ"); put("le", "ぇ"); put("lo", "ぉ")
            put("lya", "ゃ"); put("lyu", "ゅ"); put("lyo", "ょ")
            put("xa", "ぁ"); put("xi", "ぃ"); put("xu", "ぅ"); put("xe", "ぇ"); put("xo", "ぉ")
            put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ")
            put("ltu", "っ"); put("xtu", "っ")
        }

        /** 所有表项的前缀集合（含表项自身），用于判断缓冲区是否仍可能成为完整音节 */
        val PREFIXES: Set<String> = buildSet {
            TABLE.keys.forEach { key ->
                for (i in 1..key.length) add(key.substring(0, i))
            }
        }

        fun isPrefix(s: String): Boolean = s in PREFIXES

        /** 平假名 → 片假名（利用 Unicode 偏移，覆盖小假名/促音/浊音等） */
        fun toKatakana(hiragana: String): String = buildString {
            hiragana.forEach { c ->
                val code = c.code
                append(
                    if (code in 0x3041..0x3096) (code + 0x60).toChar()
                    else if (code == 0x3094) 'ヴ' // ゔ -> ヴ
                    else c
                )
            }
        }
    }
}
