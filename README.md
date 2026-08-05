# RingLearn 背单词

RingLearn 是一款使用 **Kotlin + Jetpack Compose** 从零构建的日语（JLPT N2）背单词 Android 应用。
内置 **1000 条日语词条**（日文表记 / 假名注音 / 中文释义 / 日文例句 / 例句翻译），
基于 **SM-2 间隔重复算法**自动安排复习，支持 3D 翻转卡片、滑动手势、原生 TTS 发音与触觉反馈。

> 核心限制遵循：动画、3D 翻转、TTS 全部使用 Android 系统原生能力（Compose graphicsLayer / android.speech.tts / VibrationEffect），不引入臃肿的第三方动画与语音库。

---

## 1. 项目概述

| 模块 | 说明 |
| --- | --- |
| 首页 | Compose Canvas 环形学习进度、火焰连击天数、待复习角标、快捷操作、每日目标滑块、系统设置（提醒 / 音效 / 震动 / 自动发音 / 主题）、进度重置（AlertDialog 二次确认） |
| 学习页 | 3D 翻转单词卡（graphicsLayer.rotationY）、左右上滑手势（detectDragGestures）、背后语义色指示条、原生 TTS 日语发音、本轮统计弹窗（正确率 / 用时） |
| 生词本 | 搜索、移除、空状态 |
| 随机测验 | 四选一选择题，看日文选中文释义，自动判分 |
| 算法 | SM-2 (SuperMemo 2) 间隔重复，复习时间写入 Room |
| 存储 | Room (Coroutines + Flow)，设置使用 DataStore Preferences |
| 架构 | MVVM + StateFlow + Hilt 依赖注入 |

## 2. 开发环境要求

| 项目 | 版本 |
| --- | --- |
| Android Studio | Ladybug (2024.2.1) 或更高版本 |
| JDK | 17 或 21（推荐 21；本机 CLI 构建使用 `C:\environment\jdk21`，AS Ladybug 默认 JBR 21） |
| Gradle | 8.12（由 wrapper 自动下载） |
| Android Gradle Plugin (AGP) | 8.9.1 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01（Compose UI 1.7.6 / Material3 1.3.1） |
| KSP | 2.0.21-1.0.27 |
| Room | 2.6.1 |
| Hilt | 2.52 |
| Navigation Compose | 2.8.5 |
| DataStore | 1.1.1 |
| compileSdk / targetSdk | 36（Android 16） |
| minSdk | 31（Android 12+） |
| ABI | 仅 arm64-v8a（`ndk.abiFilters`，精简包体积） |
| 性能 | TTS 全局单例懒加载（切换不重建）、首页 LazyColumn 懒合成、150ms 快速导航过渡、卡片去阴影（色调分层） |

## 3. 如何编译运行

### 3.1 打开项目

1. 用 Android Studio 打开项目根目录（含 `settings.gradle.kts` 的目录）。
2. 等待 Gradle Sync 完成（首次会自动下载依赖，需要网络）。
3. 若提示缺少 Android SDK，在 `local.properties` 中配置：

   ```
   sdk.dir=C:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
   ```

### 3.2 命令行构建

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

> 说明：APK 仅打包 `arm64-v8a`（通过 `ndk.abiFilters` 配置），如需要在 x86 模拟器上运行，请移除该配置。

### 3.3 安装运行

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.ringlearn.app/.MainActivity
```

也可以在 Android Studio 中直接点击 ▶ Run 选择模拟器/真机运行。

### 3.4 首次启动

- 首次启动时应用会自动把 `app/src/main/assets/jlpt_n2_words.json`（1000 条词条）写入 Room 数据库（幂等，只执行一次）。
- 首页顶部环形进度会随“已学新词 / 每日目标”动态更新。

## 4. 项目结构

```
app/src/main/java/com/ringlearn/app/
├── MainActivity.kt                     // 入口 Activity，应用主题
├── RingLearnApplication.kt             // Application：通知渠道 + 首次种子数据
├── di/
│   ├── AppModule.kt                    // Hilt：Room Database / DAO 提供
│   └── AppEntryPoints.kt               // Compose 层获取 Singleton 的入口点
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt              // Room 数据库（words / review_logs）
│   │   ├── entity/WordEntity.kt        // 词条 + SM-2 调度字段
│   │   ├── entity/ReviewLogEntity.kt   // 复习日志
│   │   └── dao/WordDao.kt / ReviewLogDao.kt
│   ├── repository/
│   │   ├── WordRepository.kt           // 词库/进度/复习记录统一仓库
│   │   └── SettingsRepository.kt       // DataStore 设置
│   └── seed/
│       └── SeedWordParser.kt           // 解析内置 JSON 词库
├── domain/
│   ├── algorithm/Sm2Scheduler.kt       // ★ SM-2 算法
│   └── model/                          // ThemeMode / AppSettings
├── ui/
│   ├── RootViewModel.kt                // 主题模式 StateFlow
│   ├── theme/                          // Material3 配色（浅色/深色）
│   ├── navigation/RingLearnApp.kt      // 底部导航 + NavHost
│   ├── components/                     // 环形进度 / 火焰 / 空状态 / 加载状态
│   ├── home/                           // 首页
│   ├── study/                          // 学习页（翻转卡 + 手势）
│   ├── wordbook/                       // 生词本
│   └── quiz/                           // 随机测验
└── util/
    ├── TtsManager.kt                   // ★ 原生 TextToSpeech 封装
    ├── HapticManager.kt                // ★ VibrationEffect 触觉反馈
    └── reminder/                       // AlarmManager 学习提醒
```

## 5. 核心逻辑说明

### 5.1 SM-2 (SuperMemo 2) 算法

文件：`domain/algorithm/Sm2Scheduler.kt`

每个单词在 Room 中保存三个调度状态：`repetitions`（连续答对次数）、`easeFactor`（易度因子，初始 2.5）、`intervalDays`（复习间隔，天）。

每次滑动卡片时调用 `Sm2Scheduler.review(word, quality)`：

```kotlin
// quality 0..5，App 内映射：右滑“认识”=5，左滑/上滑“不认识”=2
if (q >= 3) {
    repetitions += 1
    interval = when (repetitions) {
        1 -> 1          // 第一次答对：1 天后复习
        2 -> 6          // 第二次答对：6 天后复习
        else -> round(interval * easeFactor)  // 之后间隔按 EF 指数增长
    }
} else {
    repetitions = 0
    interval = 1        // 答错：重置，次日再复习
}
easeFactor = easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
easeFactor = max(easeFactor, 1.3)
dueAt = now + interval * 24h
```

- 更新后的状态立即写回 `words` 表，并插入一条 `review_logs` 记录（用于连续天数统计）。
- `isLearnedToday` 保证当天学过的单词不会在同一轮重复出现；到期复习词优先于新词进入学习队列。

### 5.2 TTS 日语发音

文件：`util/TtsManager.kt`

- 使用 Android 原生 `android.speech.tts.TextToSpeech`，不引入任何第三方语音库。
- 初始化回调中 `setLanguage(Locale.JAPANESE)` 锁定日语，`setSpeechRate(0.8f)` 放慢语速便于跟读。
- 学习页卡片右下角喇叭按钮点击即朗读；开启“自动播放发音”后翻到新卡片自动朗读。
- TTS 初始化可能异步完成，管理器会把初始化前的请求缓存为 `pendingText`，就绪后自动补播。

### 5.3 3D 翻转卡片

文件：`ui/study/SwipeableWordCard.kt`

- 翻转：`graphicsLayer { rotationY = animateFloatAsState(...) ; cameraDistance = 12f * density }`。
  正面 rotationY 0→180，背面预旋转 180 + 翻转角，两层面板按角度切换透明度，实现真实 3D 翻转。
- 滑动：`detectDragGestures` 跟踪位移；卡片 `translationX/Y` 跟随手指，
  `rotationZ = 位移比例 × 12°` 产生倾斜；右滑/左滑/上滑超过阈值后飞出示意语义色指示并回调 ViewModel。
- 触觉反馈：滑出时 `VibrationEffect.EFFECT_HEAVY_CLICK`，点击翻面 `EFFECT_TICK`。

### 5.4 数据流（MVVM）

- Repository 暴露 `Flow`；ViewModel 用 `combine` + `stateIn` 合并为 `StateFlow<UiState>`。
- 首页看板（今日已学、待复习、掌握数、连续天数）全部来自 Room Flow，数据变化自动刷新。
- 设置（主题 / 目标 / 提醒 / 开关）写入 DataStore，主题模式由 `RootViewModel` 全局收集并驱动 `RingLearnTheme`。

## 6. 配色方案

主色天蓝 `#0EA5E9`、辅助青绿 `#14B8A6`，基于 Material 3 `lightColorScheme / darkColorScheme` 生成
Primary / Secondary / Surface / Container 等全套色阶，深色模式使用提亮变体保证对比度。
详见 `ui/theme/Color.kt` 与 `ui/theme/Theme.kt`。

## 7. 常见问题

- **首次运行首页一直显示加载中**：确认 `assets/jlpt_n2_words.json` 存在且未损坏（词库为空时 `isReady=false`）。
- **TTS 无声音**：请确认系统已安装日语语音包（设置 → 系统 → 无障碍 → 文字转语音）。
- **学习提醒不弹通知**：Android 13+ 需要在系统设置中允许应用发送通知；提醒使用不精确的 `AlarmManager.set()`，可能延迟数分钟。
