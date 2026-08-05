package com.ringlearn.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日语语音合成管理器（应用级单例，懒初始化）。
 *
 * 性能说明：TextToSpeech 与系统 TTS 服务的绑定/解绑是昂贵操作（Binder + 服务连接），
 * 如果像早期版本一样在每个页面进入/退出时创建/销毁，会导致底栏切换出现 200ms+ 卡顿。
 * 因此这里改为：单例只创建一次，首次 speak 时才真正初始化，之后全程复用、永不销毁。
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val lock = Any()
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var pendingText: String? = null

    /** 首次调用时初始化（主线程创建对象，绑定为异步回调，不会阻塞切换动画） */
    private fun ensureInit() {
        if (tts != null) return
        synchronized(lock) {
            if (tts != null) return
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.JAPANESE)
                    ready = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    if (ready) {
                        tts?.setSpeechRate(0.8f)
                        tts?.setPitch(1.0f)
                        pendingText?.let { speak(it) }
                        pendingText = null
                    }
                }
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        ensureInit()
        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ringlearn_" + System.currentTimeMillis())
        } else {
            pendingText = text
        }
    }
}
