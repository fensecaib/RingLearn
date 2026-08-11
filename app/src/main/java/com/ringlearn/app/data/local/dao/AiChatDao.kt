package com.ringlearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ringlearn.app.data.local.entity.AiChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeMessages(sessionId: String): Flow<List<AiChatEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getMessages(sessionId: String): List<AiChatEntity>

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY id DESC LIMIT 1")
    suspend fun getLastMessage(sessionId: String): AiChatEntity?

    @Insert
    suspend fun insert(message: AiChatEntity): Long

    @Update
    suspend fun update(message: AiChatEntity)

    @Query("DELETE FROM ai_chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM ai_chat_messages WHERE sessionId = :sessionId")
    fun observeCount(sessionId: String): Flow<Int>
}
