package com.ringlearn.app.ui.ai

import androidx.compose.runtime.staticCompositionLocalOf

/** 对话气泡字号缩放（默认 1.0；由 AI 页根据设置提供，气泡/Markdown 读取）。 */
val LocalChatFontScale = staticCompositionLocalOf { 1f }
