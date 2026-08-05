package com.ringlearn.app.domain.model

/** 应用设置快照（来自 DataStore Preferences） */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 每日学习目标（10..100） */
    val dailyGoal: Int = 20,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    /** 音效开关 */
    val soundEnabled: Boolean = true,
    /** 震动反馈开关 */
    val vibrationEnabled: Boolean = true,
    /** 自动播放发音开关 */
    val autoSpeakEnabled: Boolean = false
) {
    val reminderTimeText: String
        get() = String.format("%02d:%02d", reminderHour, reminderMinute)
}
