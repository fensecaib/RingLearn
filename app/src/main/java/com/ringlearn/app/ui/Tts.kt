package com.ringlearn.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ringlearn.app.di.AppEntryPoints
import com.ringlearn.app.util.TtsManager
import dagger.hilt.android.EntryPointAccessors

/** 获取应用级 TTS 单例（懒初始化，进出页面不重建、不销毁） */
@Composable
fun rememberTtsManager(): TtsManager {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoints::class.java)
            .ttsManager()
    }
}
