package com.ringlearn.app.ui.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ringlearn.app.ui.theme.SakuBg
import com.ringlearn.app.ui.theme.SakuCardBorder
import com.ringlearn.app.ui.theme.SakuCyan
import com.ringlearn.app.ui.theme.SakuCyanDeep
import com.ringlearn.app.ui.theme.SakuCyanGlow
import com.ringlearn.app.ui.theme.SakuCyanSoft
import com.ringlearn.app.ui.theme.SakuInk
import com.ringlearn.app.ui.theme.SakuInkSecondary
import com.ringlearn.app.ui.theme.SakuPink
import com.ringlearn.app.ui.theme.SakuYellow
import com.ringlearn.app.ui.theme.SakuYellowGlow

/**
 * AI 对话页专用设计系统（saku 风格，规范见 .codex/skills/saku-design-spec）：
 * 平涂浅蓝白底 #EEF8FC、品牌青 #7DD3E8、暖黄 #F9D85A / 粉 #FFB7C5 点缀、
 * 深墨蓝文字 #2C4A5A、白卡片墨色细描边 + 中性轻阴影，
 * 青色/黄色柔光仅用于交互元素。
 * 字体与全局配色已提升至 ui/theme（全部页面共用）；此处仅保留 AI 页装饰性令牌。
 */

/** AI 页配色令牌：浅色 / 深色两套，随全局主题模式自动切换。 */
internal data class SakuPalette(
    val bg: Color,
    val topBarBg: Color,
    val ink: Color,
    val inkSecondary: Color,
    val cyan: Color,
    val cyanDeep: Color,
    val cyanSoft: Color,
    val yellow: Color,
    val pink: Color,
    val glow: Color,
    val yellowGlow: Color,
    val cardBorder: Color,
    val cardShadow: Color,
    val blobCyan: Color,
    val blobYellow: Color,
    val blobPink: Color,
    val userBubble: Color,
    val userText: Color,
    val assistantBubble: Color,
    val avatarBg: Color,
    val avatarText: Color,
    val fieldBg: Color,
    val fieldBorder: Color,
    val sendBg: Color,
    val sendIcon: Color,
    val stopBg: Color,
    val stopIcon: Color,
    val ctaBg: Color,
    val ctaFg: Color,
    val timeChipBg: Color,
    val badgeBg: Color,
    val badgeFg: Color,
    val bannerBg: Color,
    val heroCircleBg: Color,
    val heroIcon: Color,
    val exampleCardBg: Color,
    val exampleChipBg: Color,
    val fabBg: Color,
    val codeBg: Color,
    val codeFg: Color
)

private val LightSakuPalette = SakuPalette(
    bg = SakuBg,
    topBarBg = SakuBg,
    ink = SakuInk,
    inkSecondary = SakuInkSecondary,
    cyan = SakuCyan,
    cyanDeep = SakuCyanDeep,
    cyanSoft = SakuCyanSoft,
    yellow = SakuYellow,
    pink = SakuPink,
    glow = SakuCyanGlow,
    yellowGlow = SakuYellowGlow,
    cardBorder = SakuCardBorder,
    cardShadow = Color(0x1A000000),
    blobCyan = Color(0x1A7DD3E8),
    blobYellow = Color(0x24F9D85A),
    blobPink = Color(0x14FFB7C5),
    userBubble = SakuInk,
    userText = Color.White,
    assistantBubble = Color.White,
    avatarBg = SakuCyan,
    avatarText = Color.White,
    fieldBg = Color.White,
    fieldBorder = SakuCyan.copy(alpha = 0.4f),
    sendBg = SakuCyanDeep,
    sendIcon = Color.White,
    stopBg = SakuYellow,
    stopIcon = SakuInk,
    ctaBg = SakuYellow,
    ctaFg = SakuInk,
    timeChipBg = Color(0x1F2C4A5A),
    badgeBg = SakuCyanSoft,
    badgeFg = SakuInk,
    bannerBg = SakuCyanSoft,
    heroCircleBg = SakuCyanSoft,
    heroIcon = SakuCyanDeep,
    exampleCardBg = Color.White,
    exampleChipBg = SakuCyanSoft,
    fabBg = Color.White,
    codeBg = SakuCyanSoft,
    codeFg = SakuCyanDeep
)

private val DarkSakuPalette = SakuPalette(
    bg = Color(0xFF112833),
    topBarBg = Color(0xFF112833),
    ink = Color(0xFFEAF6FC),
    inkSecondary = Color(0xB3EAF6FC),
    cyan = SakuCyan,
    cyanDeep = SakuCyanDeep,
    cyanSoft = Color(0xFF2C4A5A),
    yellow = SakuYellow,
    pink = SakuPink,
    glow = SakuCyanGlow,
    yellowGlow = SakuYellowGlow,
    cardBorder = Color(0x1FEAF6FC),
    cardShadow = Color(0x33000000),
    blobCyan = Color(0x1F7DD3E8),
    blobYellow = Color(0x1FF9D85A),
    blobPink = Color(0x1AFFB7C5),
    userBubble = SakuCyan,
    userText = Color(0xFF12303C),
    assistantBubble = Color(0xFF2C4A5A),
    avatarBg = SakuCyanDeep,
    avatarText = Color.White,
    fieldBg = Color(0xFF2C4A5A),
    fieldBorder = SakuCyan.copy(alpha = 0.3f),
    sendBg = SakuCyanDeep,
    sendIcon = Color.White,
    stopBg = SakuYellow,
    stopIcon = SakuInk,
    ctaBg = SakuYellow,
    ctaFg = SakuInk,
    timeChipBg = Color(0x1FEAF6FC),
    badgeBg = Color(0xFF2C4A5A),
    badgeFg = Color(0xFFEAF6FC),
    bannerBg = Color(0xFF2C4A5A),
    heroCircleBg = Color(0xFF2C4A5A),
    heroIcon = SakuCyan,
    exampleCardBg = Color(0xFF2C4A5A),
    exampleChipBg = Color(0xFF16323F),
    fabBg = Color(0xFF2C4A5A),
    codeBg = Color(0xFF16323F),
    codeFg = SakuCyan
)

/** 按当前生效的主题（RingLearnTheme 已解析 ThemeMode）选择 AI 页配色。 */
@Composable
internal fun sakuChatPalette(): SakuPalette =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkSakuPalette else LightSakuPalette
