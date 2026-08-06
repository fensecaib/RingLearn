package com.ringlearn.app.domain.ime

/**
 * 罗马音 → 假名 IME 引擎（类日语输入法行为，纯 Kotlin、无 Android 依赖，可单元测试）。
 *
 * v2 设计（对齐 Google 日语输入法体验）：
 * - 引擎持有三段文本状态：已提交文本 [committed] + 组合中假名 [composed] + 待转换罗马音 [buffer]。
 * - 字段显示文本 = committed + composed + buffer；组合区（composed+buffer）由调用方
 *   通过 TextFieldState 的 composition 呈现下划线。
 * - 空格 = 转换（弹出词典候选）；回车 / 选中候选 = 提交组合；退格 = 先回退罗马音，
 *   再回退组合假名，最后回退已提交文本。
 * - 平/片假名切换会同步转换组合区（对齐真实 IME），已提交文本不转。
 * - 支持五十音键盘直输（inputKana）。
 */
class RomajiEngine(
    var mode: Mode = Mode.HIRAGANA
) {
    enum class Mode { HIRAGANA, KATAKANA }

    private val committed = StringBuilder()
    private val composed = StringBuilder()
    private val buffer = StringBuilder()

    /** 字段应显示的完整文本（已提交 + 组合区） */
    val fullText: String get() = committed.toString() + composed.toString() + buffer.toString()

    /** 组合区起点（= 已提交文本长度） */
    val compositionStart: Int get() = committed.length

    /** 组合区终点 */
    val compositionEnd: Int get() = committed.length + composed.length + buffer.length

    /** 是否存在未提交的组合（含仅罗马音缓冲） */
    val isComposing: Boolean get() = composed.isNotEmpty() || buffer.isNotEmpty()

    /** 组合中纯假名部分（用于词典候选查询，不含待转换罗马音） */
    val compositionKana: String get() = composed.toString()

    /** 待转换的罗马音缓冲 */
    val pendingRomaji: String get() = buffer.toString()

    /**
     * 直接收养外部文本（如：切换回内置键盘、外部清空、手写追加后的同步）。
     * 会丢弃当前组合，以 [text] 作为已提交文本。
     */
    fun adoptText(text: String) {
        committed.setLength(0); committed.append(text)
        composed.setLength(0)
        buffer.setLength(0)
    }

    /** 清空全部状态 */
    fun clearAll() = adoptText("")

    /**
     * 处理单个按键。
     * - 字母 / 撇号：进入罗马音转换流程（产出物进入组合区）；
     * - 其余字符：先提交当前组合，再原样插入已提交文本（对齐真实 IME）。
     */
    fun input(ch: Char) {
        val c = ch.lowercaseChar()
        if (c in 'a'..'z' || c == '\'') {
            processRomaji(c)
        } else {
            if (isComposing) commit()
            committed.append(ch)
        }
    }

    /** 五十音键盘直输：把 [kana] 追加到组合区（先落定残留罗马音） */
    fun inputKana(kana: String) {
        if (kana.isEmpty()) return
        flushBufferToComposed()
        composed.append(kana)
    }

    /** 空格（转换）：把残留罗马音落定为组合假名，用于候选查询；不提交组合 */
    fun space() {
        flushBufferToComposed()
    }

    /** 提交组合：残留罗马音一并落定（n→ん，其余按原样），并入已提交文本 */
    fun commit(): String {
        flushBufferToComposed()
        committed.append(composed)
        composed.setLength(0)
        return committed.toString()
    }

    /** 选中候选：以 [text] 替换组合区并提交，返回新的已提交文本 */
    fun commitCandidate(text: String): String {
        flushBufferToComposed()
        committed.append(text)
        composed.setLength(0)
        return committed.toString()
    }

    /**
     * 退格：优先回退罗马音缓冲 → 组合假名 → 已提交文本。
     * @return true 表示本次退格已消费（无需调用方额外删除字段字符）
     */
    fun backspace(): Boolean = when {
        buffer.isNotEmpty() -> { buffer.deleteCharAt(buffer.length - 1); true }
        composed.isNotEmpty() -> { composed.deleteCharAt(composed.length - 1); true }
        committed.isNotEmpty() -> { committed.deleteCharAt(committed.length - 1); true }
        else -> false
    }

    /** 切换平/片假名，并同步转换组合区（缓冲罗马音不受影响） */
    fun toggleMode() {
        mode = if (mode == Mode.HIRAGANA) Mode.KATAKANA else Mode.HIRAGANA
        if (composed.isNotEmpty()) {
            val kana = composed.toString()
            composed.setLength(0)
            composed.append(if (mode == Mode.KATAKANA) toKatakana(kana) else toHiragana(kana))
        }
    }

    private fun processRomaji(c: Char) {
        if (c == '\'') {
            if (buffer.toString() == "n") {
                buffer.setLength(0)
                composed.append(toKana("ん"))
            } else {
                flushBufferToComposed()
            }
            return
        }
        buffer.append(c)
        convert()
    }

    /** 把残留罗马音落定为组合假名；"n" 转 ん，其余按原样（如孤立辅音 k） */
    private fun flushBufferToComposed() {
        if (buffer.isEmpty()) return
        val s = buffer.toString()
        buffer.setLength(0)
        composed.append(if (s == "n") toKana("ん") else s)
    }

    /** 核心转换：尝试把缓冲内容转换为假名，结果追加进组合区 */
    private fun convert() {
        if (buffer.isEmpty()) return
        val s = buffer.toString()

        // 1. 促音：双写辅音 -> っ（保留一个辅音等待后续元音），如 kka -> っか
        if (s.length >= 2 && s[0] == s[1] && s[0] in "kstcphfgzjdvb") {
            buffer.deleteCharAt(0)
            composed.append(toKana("っ"))
            return
        }

        // 2. 拨音 nn -> ん（保留一个 n，使得 nna -> んな、minna -> みんな）
        if (s == "nn") {
            buffer.setLength(0)
            buffer.append('n')
            composed.append(toKana("ん"))
            return
        }

        // 3. n + 非元音/非 y 的辅音 -> ん + 继续处理剩余（如 nt -> ん + t 待转换）
        if (s.length > 1 && s[0] == 'n' && !isPrefix(s)) {
            buffer.deleteCharAt(0)
            composed.append(toKana("ん"))
            convert()
            return
        }

        // 4. 整体匹配：整拍假名
        val kana = TABLE[s]
        if (kana != null) {
            buffer.setLength(0)
            composed.append(toKana(kana))
            return
        }

        // 5. tch 消音：tcha/tchi 等中先导的 t 并入后续 ch
        if (s.startsWith("tch") && !isPrefix(s)) {
            buffer.deleteCharAt(0)
            convert()
            return
        }

        // 6. 若缓冲区开头不再可能是任何罗马音的前缀 -> 落为罗马音字面量（留在组合区）
        if (!isPrefix(s)) {
            val c = s[0]
            buffer.deleteCharAt(0)
            composed.append(c)
            convert()
            return
        }

        // 7. 仍可能是有效前缀，继续等待更多按键
    }

    private fun toKana(hiragana: String): String =
        if (mode == Mode.HIRAGANA) hiragana else toKatakana(hiragana)

    companion object {
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

        /** 片假名 → 平假名（toKatakana 的逆变换） */
        fun toHiragana(katakana: String): String = buildString {
            katakana.forEach { c ->
                val code = c.code
                append(
                    if (code in 0x30A1..0x30F6) (code - 0x60).toChar()
                    else if (c == 'ヴ') 'ゔ'
                    else c
                )
            }
        }
    }
}
