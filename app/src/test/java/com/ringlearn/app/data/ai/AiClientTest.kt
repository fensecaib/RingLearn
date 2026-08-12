package com.ringlearn.app.data.ai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- URL 拼接 ----

    @Test
    fun `chatCompletionsUrl - deepseek base`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            AiClient.chatCompletionsUrl("https://api.deepseek.com")
        )
    }

    @Test
    fun `chatCompletionsUrl - trailing slash trimmed`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            AiClient.chatCompletionsUrl("https://api.deepseek.com/")
        )
    }

    @Test
    fun `chatCompletionsUrl - v1 suffix kept`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiClient.chatCompletionsUrl("https://api.openai.com/v1")
        )
    }

    @Test
    fun `chatCompletionsUrl - whitespace trimmed`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            AiClient.chatCompletionsUrl("  https://api.deepseek.com  ")
        )
    }

    // ---- 请求构建 ----

    @Test
    fun `buildRequest - headers and body`() {
        val config = AiChatConfig(
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-test",
            model = "deepseek-v4-flash",
            maxTokens = 2000
        )
        val request = AiClient.buildRequest(
            config,
            listOf(AiChatMessage("system", "你是日语老师"), AiChatMessage("user", "你好")),
            stream = true
        )
        assertEquals("https://api.deepseek.com/chat/completions", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("Bearer sk-test", request.header("Authorization"))
        assertEquals("text/event-stream", request.header("Accept"))
        val body = request.body?.let {
            val buffer = okio.Buffer()
            it.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty()
        assertTrue(body.contains("\"model\":\"deepseek-v4-flash\""))
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("\"max_tokens\":2000"))
        // 默认关闭深度思考，避免 max_tokens 全耗在 reasoning 上导致空回复
        assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"content\":\"你好\""))
    }

    // ---- SSE 增量解析 ----

    @Test
    fun `parseDelta - content chunk`() {
        val chunk = """{"id":"x","choices":[{"delta":{"content":"你好"}}]}"""
        assertEquals("你好", AiClient.parseDelta(json, chunk))
    }

    @Test
    fun `parseDelta - role only chunk returns null`() {
        val chunk = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        assertNull(AiClient.parseDelta(json, chunk))
    }

    @Test
    fun `parseDelta - malformed json returns null`() {
        assertNull(AiClient.parseDelta(json, "not-json"))
    }

    @Test
    fun `parseDelta - empty content returns null`() {
        val chunk = """{"choices":[{"delta":{"content":""}}]}"""
        assertNull(AiClient.parseDelta(json, chunk))
    }

    // ---- 非流式响应解析 ----

    @Test
    fun `parseComplete - normal response`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"连接成功"}}]}"""
        assertEquals("连接成功", AiClient.parseComplete(json, body))
    }

    @Test
    fun `parseComplete - error response returns null`() {
        val body = """{"error":{"message":"invalid api key"}}"""
        assertNull(AiClient.parseComplete(json, body))
    }

    @Test
    fun `parseComplete - malformed returns null`() {
        assertNull(AiClient.parseComplete(json, "oops"))
    }

    @Test
    fun `buildRequest - thinking enabled`() {
        val config = AiChatConfig(
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-test",
            model = "deepseek-v4-flash",
            maxTokens = 2000,
            thinkingEnabled = true
        )
        val request = AiClient.buildRequest(
            config,
            listOf(AiChatMessage("user", "你好")),
            stream = true
        )
        val body = request.body?.let {
            val buffer = okio.Buffer()
            it.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty()
        assertTrue(body.contains("\"thinking\":{\"type\":\"enabled\"}"))
    }
}
