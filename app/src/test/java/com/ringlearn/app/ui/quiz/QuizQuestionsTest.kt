package com.ringlearn.app.ui.quiz

import com.ringlearn.app.data.local.entity.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizQuestionsTest {

    private fun word(id: Long, meaning: String) = WordEntity(
        id = id,
        word = "word$id",
        kana = "kana$id",
        meaning = meaning,
        example = "",
        exampleMeaning = ""
    )

    @Test
    fun `normal pool yields four options with correct answer`() {
        val targets = listOf(word(1, "A"), word(2, "B"), word(3, "C"))
        val pool = targets + (10L..30L).map { word(it, "M$it") }
        val questions = buildQuizQuestions(targets, pool)
        assertEquals(3, questions.size)
        questions.forEach { q ->
            assertEquals(4, q.options.size)
            assertEquals(q.word.meaning, q.options[q.correctIndex])
            assertEquals(1, q.options.count { it == q.word.meaning })
        }
    }

    @Test
    fun `insufficient non-target meanings are supplemented by other target meanings`() {
        // 干扰项池只有 3 个目标词自身（无额外释义）：每题仍能得到其他目标释义作干扰项
        val targets = listOf(word(1, "A"), word(2, "B"), word(3, "C"))
        val questions = buildQuizQuestions(targets, targets)
        assertEquals(3, questions.size)
        questions.forEach { q ->
            assertTrue(q.options.size >= 2)
            assertEquals(q.word.meaning, q.options[q.correctIndex])
            assertTrue(q.options.all { it in setOf("A", "B", "C") })
        }
    }

    @Test
    fun `all meanings identical degrades gracefully to single option`() {
        val targets = listOf(word(1, "A"), word(2, "A"), word(3, "A"))
        val questions = buildQuizQuestions(targets, targets)
        questions.forEach { q ->
            assertEquals(listOf("A"), q.options)
            assertEquals(0, q.correctIndex)
        }
    }

    @Test
    fun `empty targets return empty questions`() {
        assertTrue(buildQuizQuestions(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `wrong options never equal correct meaning`() {
        val targets = listOf(word(1, "目标"))
        val pool = targets + (10L..40L).map { word(it, "干扰$it") }
        val q = buildQuizQuestions(targets, pool).single()
        q.options.forEachIndexed { i, option ->
            if (i != q.correctIndex) assertNotEquals("目标", option)
        }
    }
}
