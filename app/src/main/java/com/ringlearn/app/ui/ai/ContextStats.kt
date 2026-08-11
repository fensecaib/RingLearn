package com.ringlearn.app.ui.ai

import java.util.Locale

/** 上下文统计：对话轮数（用户消息数）与总字符数。 */
data class ContextStats(val rounds: Int = 0, val chars: Int = 0)

/** 格式化为「N 轮 · X chars」，如 12 轮 · 3.2k chars。 */
fun formatContextStats(stats: ContextStats): String {
    val r = "${stats.rounds} 轮"
    val c = when {
        stats.chars >= 1_000_000 -> String.format(Locale.US, "%.1fM", stats.chars / 1_000_000.0)
        stats.chars >= 1_000 -> String.format(Locale.US, "%.1fk", stats.chars / 1000.0)
        else -> "${stats.chars}"
    }
    return "$r · $c chars"
}
