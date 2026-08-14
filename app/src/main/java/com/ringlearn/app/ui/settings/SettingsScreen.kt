package com.ringlearn.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ringlearn.app.R
import com.ringlearn.app.domain.model.AppSettings
import com.ringlearn.app.domain.model.ThemeMode
import com.ringlearn.app.ui.components.SakuTopBar
import com.ringlearn.app.ui.components.sakuCardBorder
import com.ringlearn.app.ui.components.sakuCardColors
import com.ringlearn.app.ui.rememberHapticManager
import com.ringlearn.app.util.HapticManager

/** 设置页：学习提醒 / 主题 / 音效 / 震动 / 自动发音（原首页「系统设置」卡片迁入）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onExit: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberHapticManager()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Android 13+ 开启学习提醒需要运行时通知权限
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setReminderEnabled(true)
        } else {
            viewModel.postMessage("未获得通知权限，学习提醒将无法弹出通知")
        }
    }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SakuTopBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsCard(
                settings = settings,
                haptic = haptic,
                onSetThemeMode = viewModel::setThemeMode,
                onSetSound = viewModel::setSoundEnabled,
                onSetVibration = viewModel::setVibrationEnabled,
                onSetAutoSpeak = viewModel::setAutoSpeakEnabled,
                onSetReminderEnabled = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setReminderEnabled(enabled)
                    }
                },
                onShowTimePicker = { showTimePicker = true }
            )
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = settings.reminderHour,
            initialMinute = settings.reminderMinute,
            onConfirm = { hour, minute ->
                haptic.tick()
                viewModel.setReminderTime(hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(
    settings: AppSettings,
    haptic: HapticManager,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetSound: (Boolean) -> Unit,
    onSetVibration: (Boolean) -> Unit,
    onSetAutoSpeak: (Boolean) -> Unit,
    onSetReminderEnabled: (Boolean) -> Unit,
    onShowTimePicker: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = sakuCardColors(),
        border = sakuCardBorder()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "系统设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()

            SettingRow(
                icon = R.drawable.ic_notifications,
                title = "学习提醒",
                subtitle = if (settings.reminderEnabled) "每天 ${settings.reminderTimeText}" else "关闭",
                trailing = {
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = {
                            haptic.tick()
                            onSetReminderEnabled(it)
                        }
                    )
                },
                onClick = onShowTimePicker
            )

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_brightness_auto),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "主题",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                SingleChoiceSegmentedButtonRow {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = {
                                haptic.tick()
                                onSetThemeMode(mode)
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size
                            ),
                            label = { Text(mode.label()) }
                        )
                    }
                }
            }

            HorizontalDivider()

            SettingRow(
                icon = R.drawable.ic_volume,
                title = "音效",
                subtitle = "按钮与操作音效",
                trailing = {
                    Switch(
                        checked = settings.soundEnabled,
                        onCheckedChange = {
                            haptic.tick()
                            onSetSound(it)
                        }
                    )
                }
            )

            HorizontalDivider()

            SettingRow(
                icon = R.drawable.ic_settings,
                title = "震动反馈",
                subtitle = "交互触觉反馈（VibrationEffect）",
                trailing = {
                    Switch(
                        checked = settings.vibrationEnabled,
                        onCheckedChange = {
                            haptic.tick()
                            onSetVibration(it)
                        }
                    )
                }
            )

            HorizontalDivider()

            SettingRow(
                icon = R.drawable.ic_volume,
                title = "自动播放发音",
                subtitle = "翻到新卡片时自动 TTS 朗读",
                trailing = {
                    Switch(
                        checked = settings.autoSpeakEnabled,
                        onCheckedChange = {
                            haptic.tick()
                            onSetAutoSpeak(it)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: Int,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val base = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
    Row(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择学习提醒时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}
