package com.ringlearn.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringlearn.app.ui.RootViewModel
import com.ringlearn.app.ui.navigation.RingLearnApp
import com.ringlearn.app.ui.theme.RingLearnTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 内置键盘模式不依赖系统 IME insets；adjustResize 会放大 insets 误报（幻影留白根因，
        // SO 76014880 / IssueTracker 388616191）。Manifest 已声明 adjustNothing，这里再显式固定一次
        // 作为防御（已验证 androidx.activity 1.13.0 的 EdgeToEdge.enable 不触碰 softInputMode，
        // 但保留显式调用以防未来版本改变默认行为）。
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            val rootViewModel: RootViewModel = hiltViewModel()
            val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()
            RingLearnTheme(themeMode = themeMode) {
                RingLearnApp()
            }
        }
    }
}

