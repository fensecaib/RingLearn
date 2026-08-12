package com.ringlearn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.ringlearn.app.data.repository.WordRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class RingLearnApplication : Application() {

    @Inject lateinit var wordRepository: WordRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 首次启动时预置 1000 词内置词库
        applicationScope.launch { wordRepository.ensureSeeded() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "ringlearn_reminder"
    }
}
