package com.ringlearn.app.data.ai

/** AI 对话配置（来自 DataStore，用户可自定义）。 */
data class AiChatConfig(
    /** OpenAI 兼容 baseUrl，如 https://api.deepseek.com 或 https://api.openai.com/v1 */
    val baseUrl: String = DEFAULT_BASE_URL,
    /** API Key（DataStore 中以 Keystore AES/GCM 加密存储；内存中为明文） */
    val apiKey: String = "",
    /** 模型名，如 deepseek-v4-flash */
    val model: String = DEFAULT_MODEL,
    /** 最大输出 token 数 */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    /** 系统提示词（固定于请求首条） */
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

/** OpenAI 兼容请求消息。 */
data class AiChatMessage(val role: String, val content: String)

/** AI 接口错误。 */
class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

const val DEFAULT_BASE_URL = "https://api.deepseek.com"
const val DEFAULT_MODEL = "deepseek-v4-flash"
const val DEFAULT_MAX_TOKENS = 2000

/** 默认日语学习助手系统提示词。 */
const val DEFAULT_SYSTEM_PROMPT =
    "你是一位资深日语老师。用户会用中文或日文提问，请用中文清晰、简洁地回答，" +
        "解释日语单词、语法、句子结构，必要时给出日文例句与中文翻译。"

/** 系统提示词预设（名称 → 内容）。 */
val SYSTEM_PROMPT_PRESETS: List<Pair<String, String>> = listOf(
    "日语学习助手" to DEFAULT_SYSTEM_PROMPT,
    "翻译模式" to "你是一位专业的中日互译助手。用户发日文时译为中文，发中文时译为日文，并简要说明翻译要点。",
    "语法讲解" to "你是一位日语语法专家。请拆解句子的语法结构，解释助词、动词变形与句型，并给出替换练习。",
    "自定义" to ""
)
