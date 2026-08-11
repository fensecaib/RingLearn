package com.ringlearn.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 对话消息实体。按 sessionId 隔离会话（当前单会话，重置后历史保留、按 sessionId 隔离）。
 * role: "user" / "assistant"；isError 标记失败/被取消的助手消息（用于错误样式与重试）。
 */
@Entity(tableName = "ai_chat_messages")
data class AiChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 会话 ID（当前固定为单会话 "default"） */
    val sessionId: String,
    /** 消息角色：user / assistant */
    val role: String,
    /** 消息内容（助手消息在流式期间逐步更新，完成时写入最终文本） */
    val content: String,
    /** 创建时间戳（epoch millis） */
    val createdAt: Long,
    /** 是否错误/被取消（true 时 UI 显示错误样式并提供重试） */
    val isError: Boolean = false
)
