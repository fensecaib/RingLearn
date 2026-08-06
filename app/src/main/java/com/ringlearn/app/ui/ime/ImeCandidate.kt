package com.ringlearn.app.ui.ime

/**
 * 内置键盘的转换候选条目。
 * @param text 选中后提交的文本（假名 / 片假名 / 汉字表记）
 * @param kana 注音提示（词典候选展示用，可为 null）
 */
data class ImeCandidate(
    val text: String,
    val kana: String? = null
)
