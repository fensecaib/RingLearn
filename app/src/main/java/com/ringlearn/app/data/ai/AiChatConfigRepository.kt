package com.ringlearn.app.data.ai

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.aiConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "ringlearn_ai")

/**
 * AI 对话配置仓库（独立 DataStore 文件 ringlearn_ai）。
 * API Key 经 [SecretBox]（Android Keystore AES/GCM）加密后落盘。
 */
@Singleton
class AiChatConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("ai_base_url")
        val API_KEY_ENC = stringPreferencesKey("ai_api_key_enc")
        val MODEL = stringPreferencesKey("ai_model")
        val MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
    }

    val config: Flow<AiChatConfig> = context.aiConfigDataStore.data
        .catch { e ->
            Log.e("AiChatConfigRepository", "读取 AI 配置失败", e)
            emit(emptyPreferences())
        }
        .map { p ->
            AiChatConfig(
                baseUrl = p[Keys.BASE_URL] ?: DEFAULT_BASE_URL,
                apiKey = p[Keys.API_KEY_ENC]?.let { SecretBox.decrypt(it) } ?: "",
                model = p[Keys.MODEL] ?: DEFAULT_MODEL,
                maxTokens = (p[Keys.MAX_TOKENS] ?: DEFAULT_MAX_TOKENS).coerceIn(128, 8192),
                systemPrompt = p[Keys.SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
            )
        }

    suspend fun update(
        baseUrl: String = DEFAULT_BASE_URL,
        apiKey: String = "",
        model: String = DEFAULT_MODEL,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ) {
        context.aiConfigDataStore.edit { p ->
            p[Keys.BASE_URL] = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
            p[Keys.API_KEY_ENC] = if (apiKey.isBlank()) {
                p.remove(Keys.API_KEY_ENC)
            } else {
                SecretBox.encrypt(apiKey.trim())
            }
            p[Keys.MODEL] = model.trim().ifBlank { DEFAULT_MODEL }
            p[Keys.MAX_TOKENS] = maxTokens.coerceIn(128, 8192)
            p[Keys.SYSTEM_PROMPT] = systemPrompt.trim().ifBlank { DEFAULT_SYSTEM_PROMPT }
        }
    }
}
