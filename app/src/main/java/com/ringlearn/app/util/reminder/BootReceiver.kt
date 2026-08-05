package com.ringlearn.app.util.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ringlearn.app.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** 开机后恢复学习提醒。 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = runBlocking { settingsRepository.settings.first() }
        if (settings.reminderEnabled) {
            reminderScheduler.schedule(settings.reminderHour, settings.reminderMinute)
        }
    }
}
