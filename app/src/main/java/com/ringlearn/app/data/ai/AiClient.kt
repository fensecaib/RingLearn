package com.ringlearn.app.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容 Chat Completions 客户端（OkHttp，手动 SSE 解析，无额外依赖）。
 *
 * - 端点：POST {baseUrl}/chat/completions（baseUrl 以 /v1 结尾则自然落在 /v1/chat/completions）。
 * - 流式：SSE 逐行读取，`data:` 行内 JSON 取 choices[].delta.content 增量累加，`data: [DONE]` 结束；
 *   跳过空行与注释行；协程取消时同步 cancel 底层 HTTP 调用。
 */
@Singleton
class AiClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** 流式对话：每个增量回调 onDelta（累积文本）。整体成功返回 [Result.success]；失败含原因。 */
    suspend fun streamChat(
        config: AiChatConfig,
        messages: List<AiChatMessage>,
        onDelta: (String) -> Unit
    ): Result<Unit> {
        val request = buildRequest(config, messages, stream = true)
        val call = client.newCall(request)
        return try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        return@withContext Result.failure(
                            AiException("HTTP ${response.code}: ${body.take(300)}")
                        )
                    }
                    val source = response.body?.source()
                        ?: return@withContext Result.failure(AiException("空响应体"))
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = source.readUtf8Line() ?: break
                        val data = line.trimStart().removePrefix("data:").trim()
                        when {
                            data.isEmpty() -> continue
                            data == "[DONE]" -> break
                            else -> parseDelta(json, data)?.let { delta ->
                                if (delta.isNotEmpty()) onDelta(delta)
                            }
                        }
                    }
                    Result.success(Unit)
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 非流式补全（用于连接测试）。 */
    suspend fun complete(config: AiChatConfig, messages: List<AiChatMessage>): Result<String> {
        val request = buildRequest(config, messages, stream = false)
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        throw AiException("HTTP ${response.code}: ${body.take(300)}")
                    }
                    val body = response.body?.string().orEmpty()
                    parseComplete(json, body) ?: throw AiException("响应解析失败")
                }
            }
        }
    }

    companion object {
        /** 拼接 Chat Completions 端点：trim 尾部斜杠后统一追加 /chat/completions。 */
        internal fun chatCompletionsUrl(baseUrl: String): String =
            baseUrl.trim().trimEnd('/') + "/chat/completions"

        /** 构造请求（可脱离网络单测）。 */
        internal fun buildRequest(
            config: AiChatConfig,
            messages: List<AiChatMessage>,
            stream: Boolean
        ): Request {
            val payload = buildJsonObject {
                put("model", config.model)
                put("stream", stream)
                put("max_tokens", config.maxTokens)
                put("temperature", 0.7)
                // DeepSeek V4 默认思考模式开启，可能把 max_tokens 全耗在 reasoning 上导致 content 为空；默认关闭
                put("thinking", buildJsonObject { put("type", if (config.thinkingEnabled) "enabled" else "disabled") })
                put(
                    "messages", buildJsonArray {
                        messages.forEach { m ->
                            add(
                                buildJsonObject {
                                    put("role", m.role)
                                    put("content", m.content)
                                }
                            )
                        }
                    }
                )
            }
            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            return Request.Builder()
                .url(chatCompletionsUrl(config.baseUrl))
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build()
        }

        /** 解析流式增量行中的文本（choices[].delta.content）；无内容返回 null。 */
        internal fun parseDelta(json: Json, jsonText: String): String? = runCatching {
            val delta = json.parseToJsonElement(jsonText).jsonObject
                .get("choices")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("delta")?.jsonObject
            delta?.get("content")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        /** 解析非流式响应文本（choices[0].message.content）。 */
        internal fun parseComplete(json: Json, jsonText: String): String? = runCatching {
            json.parseToJsonElement(jsonText).jsonObject
                .get("choices")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
}

