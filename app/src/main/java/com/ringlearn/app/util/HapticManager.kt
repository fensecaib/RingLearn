package com.ringlearn.app.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ringlearn.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 触觉反馈管理器：封装系统 VibrationManager / VibrationEffect（原生 API）。
 * 所有交互（点击、滑动、开关）统一从这里发出震动反馈，
 * 并受用户设置的“震动反馈开关”控制。
 */
@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository
) {
    /** 是否启用震动反馈（跟随设置实时更新） */
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    init {
        scope.launch {
            settingsRepository.settings.collect { _enabled.value = it.vibrationEnabled }
        }
    }

    private val vibrator: Vibrator? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /** 轻触反馈：开关切换、普通点击 */
    fun tick() {
        if (!_enabled.value) return
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    /** 标准点击反馈 */
    fun click() {
        if (!_enabled.value) return
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    /** 重反馈：卡片滑出、提交 */
    fun heavy() {
        if (!_enabled.value) return
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }

    /** 双脉冲：答错等需要提醒的场合 */
    fun doubleClick() {
        if (!_enabled.value) return
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }
}
