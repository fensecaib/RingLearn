package com.ringlearn.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ringlearn.app.di.AppEntryPoints
import dagger.hilt.android.EntryPointAccessors
import com.ringlearn.app.util.HapticManager


/** 在 Composable 中获取 Hilt Singleton 的 HapticManager */
@Composable
fun rememberHapticManager(): HapticManager {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoints::class.java)
            .hapticManager()
    }
}
