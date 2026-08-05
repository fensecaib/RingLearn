package com.ringlearn.app.data.seed

import com.ringlearn.app.data.local.entity.WordEntity
import org.json.JSONArray

/**
 * 解析 assets/jlpt_n2_words.json 中的内置词库。
 *
 * 每个词条使用紧凑数组表示（节省体积）：
 *   [0] word  日文表记
 *   [1] kana  假名注音
 *   [2] meaning 中文释义
 *   [3] example 日文例句
 *   [4] exampleMeaning 例句中文翻译
 */
object SeedWordParser {

    fun parse(json: String, jlptLevel: String = "N2"): List<WordEntity> {
        val array = JSONArray(json)
        val words = ArrayList<WordEntity>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONArray(i)
            words += WordEntity(
                word = item.getString(0),
                kana = item.getString(1),
                meaning = item.getString(2),
                example = item.getString(3),
                exampleMeaning = item.optString(4, ""),
                jlpt = jlptLevel
            )
        }
        return words
    }
}
