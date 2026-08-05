package com.ringlearn.app.di

import com.ringlearn.app.util.HapticManager
import com.ringlearn.app.util.TtsManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 供 Compose 层获取 Singleton 服务的入口点 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoints {
    fun hapticManager(): HapticManager

    fun ttsManager(): TtsManager
}
