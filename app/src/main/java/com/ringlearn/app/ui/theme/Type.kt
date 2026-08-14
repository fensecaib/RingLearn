package com.ringlearn.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ringlearn.app.R

/**
 * Zen Maru Gothic 圆体（OFL 开源授权，见 licenses/ZenMaruGothic-OFL.txt）。
 * 字形为常用漢字子集（tools/subset_fonts.py 维护），简体专用字形回退系统字体。
 */
val ZenMaruGothic = FontFamily(
    Font(R.font.zen_maru_gothic_regular, FontWeight.Normal),
    Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
    Font(R.font.zen_maru_gothic_bold, FontWeight.Bold),
    Font(R.font.zen_maru_gothic_black, FontWeight.Black)
)

/** Fredoka 可变字体（OFL 授权，见 licenses/Fredoka-OFL.txt）：纯拉丁显示文本用。 */
@OptIn(ExperimentalTextApi::class)
val FredokaBold = FontFamily(
    Font(
        resId = R.font.fredoka_var,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

/** 全局字体排版：Zen Maru Gothic 圆体 + Material 3 行高/字距微调。 */
val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
