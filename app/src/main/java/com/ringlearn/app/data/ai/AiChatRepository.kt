package com.ringlearn.app.data.ai

import com.ringlearn.app.data.local.dao.AiChatDao
import com.ringlearn.app.data.local.entity.AiChatEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AI 对话仓库：消息持久化（Room）+ 请求组装（system 提示词固定于首条，完整历史随请求携带，不做压缩）。
 * 流式期间助手消息行先占位、完成后写入最终文本；失败/停止写为 isError 行供 UI 展示重试。
 */
@Singleton
class AiChatRepository @Inject constructor(
    private val dao: AiChatDao,
    private val client: AiClient,
    private val configRepository: AiChatConfigRepository
) {
    private val mutex = Mutex()

    fun observeMessages(sessionId: String): Flow<List<AiChatEntity>> = dao.observeMessages(sessionId)

    /** 发送新消息：先落库用户消息，再流式生成助手回复。 */
    suspend fun sendMessage(
        sessionId: String,
        text: String,
        onDelta: (String) -> Unit
    ): Result<Unit> = mutex.withLock {
        val now = System.currentTimeMillis()
        dao.insert(
            AiChatEntity(
                sessionId = sessionId,
                role = "user",
                content = text,
                createdAt = now
            )
        )
        streamAssistant(sessionId, onDelta)
    }

    /** 重试：删除末尾失败的助手行，用现有历史重新流式生成（不重复插入用户消息）。 */
    suspend fun resendLast(
        sessionId: String,
        onDelta: (String) -> Unit
    ): Result<Unit> = mutex.withLock {
        dao.getLastMessage(sessionId)
            ?.takeIf { it.role == "assistant" && it.isError }
            ?.let { dao.deleteById(it.id) }
        streamAssistant(sessionId, onDelta)
    }

    /** 清空当前会话（历史行按 sessionId 隔离保留）。 */
    suspend fun clearSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    /** 连接测试：用最小请求验证 baseUrl / apiKey / model 可用。 */
    suspend fun testConnection(config: AiChatConfig): Result<String> =
        client.complete(
            config,
            listOf(AiChatMessage("user", "你好，请只回复：连接成功"))
        )

    private suspend fun streamAssistant(
        sessionId: String,
        onDelta: (String) -> Unit
    ): Result<Unit> {
        val config = configRepository.config.first()
        if (!config.isConfigured) {
            return Result.failure(AiException("请先在设置中配置 API Key"))
        }
        val history = dao.getMessages(sessionId)
        val requestMessages = buildList {
            add(AiChatMessage("system", config.systemPrompt))
            history.forEach { add(AiChatMessage(it.role, it.content)) }
        }
        val now = System.currentTimeMillis()
        val assistantId = dao.insert(
            AiChatEntity(sessionId = sessionId, role = "assistant", content = "", createdAt = now)
        )
        val sb = StringBuilder()
        // 节流 UI 更新：避免每个 token 一次全屏重组/Markdown 重解析（弱机流式期帧卡顿主因）
        val throttle = StreamThrottle()
        val result = try {
            client.streamChat(config, requestMessages) { delta ->
                sb.append(delta)
                if (throttle.shouldEmit(System.currentTimeMillis(), sb.length)) {
                    onDelta(sb.toString())
                }
            }
        } catch (e: CancellationException) {
            dao.update(
                AiChatEntity(
                    id = assistantId, sessionId = sessionId, role = "assistant",
                    content = sb.toString().ifEmpty { "已停止生成" },
                    createdAt = now, isError = true
                )
            )
            throw e
        }
        val finalText = sb.toString()
        if (result.isSuccess) {
            // 完成时强制 flush 最终累积文本（再写 DB），杜绝节流丢尾造成的「文本回跳」
            onDelta(finalText)
            dao.update(
                AiChatEntity(
                    id = assistantId, sessionId = sessionId, role = "assistant",
                    content = finalText, createdAt = now, isError = false
                )
            )
        } else {
            val message = result.exceptionOrNull()?.message ?: "请求失败"
            dao.update(
                AiChatEntity(
                    id = assistantId, sessionId = sessionId, role = "assistant",
                    content = finalText.ifEmpty { message },
                    createdAt = now, isError = true
                )
            )
        }
        return result
    }
}
