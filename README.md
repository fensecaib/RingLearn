# RingLearn 背单词

RingLearn 是一款使用 **Kotlin + Jetpack Compose** 从零构建的日语（JLPT N2）背单词 Android 应用。
内置 **1000 条日语词条**（日文表记 / 假名注音 / 中文释义 / 日文例句 / 例句翻译），
基于 **SM-2 间隔重复算法**自动安排复习，支持 3D 翻转卡片、滑动手势、原生 TTS 发音与触觉反馈。
内置**应用级罗马音输入法**（默认输入方式，可一键切换系统输入法），并提供
**查词页**（键盘输入 / 手写汉字识别，纯离线）。

> 核心限制遵循：动画、3D 翻转、TTS、手写识别全部使用 Android 系统原生能力或自研轻量实现
> （Compose graphicsLayer / android.speech.tts / VibrationEffect / 自研字形模板匹配），不引入臃肿的第三方动画、语音与识别库。

---

## 1. 项目概述

| 模块 | 说明 |
| --- | --- |
| 首页 | Compose Canvas 环形学习进度、火焰连击天数、待复习角标、快捷操作（开始背词 / 生词本 / 随机测验 / 查词）、每日目标滑块、系统设置（提醒 / 音效 / 震动 / 自动发音 / 主题）、进度重置（AlertDialog 二次确认） |
| 学习页 | 3D 翻转单词卡（graphicsLayer.rotationY）、左右上滑手势（detectDragGestures）、背后语义色指示条、原生 TTS 日语发音、本轮统计弹窗（正确率 / 用时） |
| 查词页 | **三种输入方式**：应用内置罗马音键盘（默认）/ 系统输入法 / 手写汉字；实时 Room 查询（表记 / 假名 / 中文释义，精确匹配优先）；结果卡片（TTS 朗读 / 加入生词本） |
| 内置输入法 | 应用内置 QWERTY 键盘 + **罗马音→假名引擎**（RomajiEngine），默认用于全部输入框（查词、生词本搜索）；键盘上“⌨”一键切换系统输入法，设置持久化 |
| 手写识别 | 纯离线自研：系统字体字形模板 + 双向 Chamfer 距离匹配，Top-8 候选点选；零第三方依赖 |
| 生词本 | 搜索（内置键盘 / 系统输入法）、移除、空状态 |
| 随机测验 | 四选一选择题，看日文选中文释义，自动判分 |
| 算法 | SM-2 (SuperMemo 2) 间隔重复，复习时间写入 Room |
| 存储 | Room (Coroutines + Flow)，设置使用 DataStore Preferences |
| 架构 | MVVM + StateFlow + Hilt 依赖注入，Navigation 3（多 back stack 底部导航） |

## 2. 开发环境要求

| 项目 | 版本 |
| --- | --- |
| Android Studio | Ladybug (2024.2.1) 或更高版本（建议使用自带 JBR 21） |
| JDK | **21（必须为 HotSpot 系，如 Android Studio 自带 JBR；IBM Semeru/OpenJ9 会导致 Gradle 守护进程崩溃）** |
| Gradle | 9.5.1（由 wrapper 自动下载） |
| Android Gradle Plugin (AGP) | 9.3.1（内置 Kotlin 支持） |
| Kotlin | 2.4.10（Compose 编译器插件版本，AGP 内置 Kotlin 据此解析） |
| Compose BOM | 2026.06.01（Compose UI 1.11.4 / Material3 1.4.0） |
| KSP | 2.3.11（支持 AGP 9 内置 Kotlin） |
| Room | 2.8.4 |
| Hilt | 2.60.1（ViewModel 集成使用 `androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0`） |
| Navigation 3 | 1.1.5（`androidx.navigation3:runtime + ui`，配合 `lifecycle-viewmodel-navigation3`） |
| DataStore | 1.2.1 |
| kotlinx-serialization | 1.11.0（Navigation 3 状态持久化） |
| compileSdk | 37（Android 17；新版 androidx 库要求） |
| targetSdk | 36（Android 16） |
| minSdk | 31（Android 12+） |
| ABI | 仅 arm64-v8a（`ndk.abiFilters`，精简包体积） |
| 性能 | TTS 全局单例懒加载（切换不重建）、首页 LazyColumn 懒合成、150ms 快速导航过渡、卡片去阴影（色调分层）、内置键盘固定输入框高度避免布局跳动 |

> 仓库已配置阿里云 Maven 镜像（`settings.gradle.kts`），国内网络下加速 Maven Central / Gradle 插件下载；
> `gradle.properties` 强制 Java 优先 IPv4，规避 Maven Central 在部分网络下 IPv6 TLS 握手失败的问题。

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
# Windows（推荐使用 HotSpot JDK 21，如 Android Studio 的 JBR）
set JAVA_HOME=D:\Apps\Android Studio\jbr
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
- 手写识别模板在首次进入查词页时懒构建（词库字符集 + 字形模板，约 1 秒，纯离线）。

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
│   │   ├── WordRepository.kt           // 词库/进度/复习记录统一仓库（含查词 LIKE 查询）
│   │   └── SettingsRepository.kt       // DataStore 设置（含 useInAppKeyboard）
│   └── seed/
│       └── SeedWordParser.kt           // 解析内置 JSON 词库
├── domain/
│   ├── algorithm/Sm2Scheduler.kt       // ★ SM-2 算法
│   ├── ime/RomajiEngine.kt             // ★ 罗马音 → 假名引擎（纯 Kotlin，可单测）
│   └── model/                          // ThemeMode / AppSettings
├── ui/
│   ├── RootViewModel.kt                // 主题模式 StateFlow
│   ├── theme/                          // Material3 配色（浅色/深色）
│   ├── navigation/
│   │   ├── RingLearnApp.kt             // 底部导航 + Navigation 3 (NavDisplay)
│   │   └── NavigationState.kt          // 多 back stack 导航状态（官方范式）
│   ├── components/                     // 环形进度 / 火焰 / 空状态 / 加载状态
│   ├── home/                           // 首页
│   ├── study/                          // 学习页（翻转卡 + 手势）
│   ├── lookup/                         // ★ 查词页（键盘/手写 + 实时查询）
│   ├── ime/                            // ★ 内置键盘 + RingLearnTextField（输入法拦截/切换）
│   ├── wordbook/                       // 生词本
│   └── quiz/                           // 随机测验
└── util/
    ├── TtsManager.kt                   // ★ 原生 TextToSpeech 封装
    ├── HapticManager.kt                // ★ VibrationEffect 触觉反馈
    ├── handwriting/HandwritingRecognizer.kt  // ★ 纯离线手写识别（字形模板 + Chamfer）
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
- 学习页卡片右下角喇叭按钮点击即朗读；查词页结果卡片同样提供朗读按钮；开启“自动播放发音”后翻到新卡片自动朗读。
- TTS 初始化可能异步完成，管理器会把初始化前的请求缓存为 `pendingText`，就绪后自动补播。
- 注意：真机若无 Google Play Store / 系统日语语音包，TTS 可能无声音（设备限制，非应用问题）。

### 5.3 3D 翻转卡片

文件：`ui/study/SwipeableWordCard.kt`

- 翻转：`graphicsLayer { rotationY = animateFloatAsState(...) ; cameraDistance = 12f * density }`。
  正面 rotationY 0→180，背面预旋转 180 + 翻转角，两层面板按角度切换透明度，实现真实 3D 翻转。
- 滑动：`detectDragGestures` 跟踪位移；卡片 `translationX/Y` 跟随手指，
  `rotationZ = 位移比例 × 12°` 产生倾斜；右滑/左滑/上滑超过阈值后飞出示意语义色指示并回调 ViewModel。
- 触觉反馈：滑出时 `VibrationEffect.EFFECT_HEAVY_CLICK`，点击翻面 `EFFECT_TICK`。

### 5.4 罗马音引擎（RomajiEngine）

文件：`domain/ime/RomajiEngine.kt`（纯 Kotlin，`app/src/test/.../RomajiEngineTest.kt` 覆盖 17 个用例）

- 类 IME 行为：维护 pending 缓冲区，能完整成拍时立即提交假名。
- 支持：清音 / 浊音 / 半浊音 / 拗音（kya→きゃ）、促音双写（kka→っか）、
  长音（ou→おう）、拨音（n、nn、n'、n+辅音→ん）、小假名（la/ltu/xya 前缀）、
  片假名模式（かな↔カナ 一键切换）、非罗马字符原样透传。
- 示例：`gakkou → がっこう`、`minna → みんな`、`benkyou → べんきょう`、`kin'youbi → きんようび`。

### 5.5 内置键盘与输入法切换

文件：`ui/ime/RingLearnTextField.kt`、`ui/ime/RomajiKeyboard.kt`

- 使用新版 `BasicTextField(state: TextFieldState)`；内置键盘直接编辑 `TextFieldState`，
  与系统输入法共享同一状态，切换不丢内容。
- 内置键盘模式下，通过 `PlatformTextInputInterceptor`（`InterceptPlatformTextInput`）拦截
  `startInputMethod` 并挂起，**阻止系统输入法弹出**；切换系统输入法时移除拦截器即放行。
- 键盘底部“⌨”按钮持久化 `useInAppKeyboard=false`（DataStore），此后所有输入框默认使用系统输入法；
  输入框右侧图标可一键切回内置键盘。
- 键盘按键带触觉反馈；输入框固定高度，避免“清空”按钮出现导致键盘跳动。
- 查词页 / 生词本搜索框均使用 `RingLearnTextField`，满足“所有应用内输入默认内置键盘”的要求。

### 5.6 查词页与实时查询

文件：`ui/lookup/LookupScreen.kt`、`ui/lookup/LookupViewModel.kt`

- 三种输入模式（SegmentedButton 切换）：内置罗马音键盘 / 系统输入法 / 手写汉字。
- 查询：输入去抖 300ms → `WordDao.observeLookup`（`word/kana/meaning LIKE`，`%`/`_` 已转义 +
  `ESCAPE '\'`），排序为“精确匹配 > 前缀 > 包含”，实时刷新。
- 结果卡片：日文表记、假名、JLPT 等级、中文释义、日文例句 + 例句翻译、TTS 朗读、加入/移出生词本。
- 状态处理：空查询提示、无结果空状态、识别器加载中状态；查询变化后列表自动回到顶部。

### 5.7 纯离线手写识别

文件：`util/handwriting/HandwritingRecognizer.kt`

- **零第三方依赖**，完全离线：启动时把词库中出现过的全部唯一字符（约 830 个）用系统字体
  （Noto Sans CJK 回退链）渲染为 48×48 二值字形模板，并预计算 Chamfer 距离变换。
- 手写笔画按同样方式光栅化（拉伸填满方形单元，消除宽高比差异）。
- 匹配：**双向 Chamfer 距离**（手写→模板 + 模板→手写，±1px 平移对齐取最优），
  避免“密集模板得分虚低”导致候选漂移；返回 Top-8 候选。
- 识别在 `Dispatchers.Default` 执行（单次 <15ms），停笔 250ms 自动识别；候选以 Chips 展示，点选后追加到查询框。
- 特点：内存约 3MB、无网络、无 Google 服务依赖；对工整书写的日文汉字/假名有可用精度，配合候选点选获得可靠体验。

### 5.8 数据流（MVVM）

- Repository 暴露 `Flow`；ViewModel 用 `combine` + `stateIn` 合并为 `StateFlow<UiState>`。
- 首页看板（今日已学、待复习、掌握数、连续天数）全部来自 Room Flow，数据变化自动刷新。
- 设置（主题 / 目标 / 提醒 / 开关 / 输入法）写入 DataStore，主题模式由 `RootViewModel` 全局收集并驱动 `RingLearnTheme`。
- 导航采用 **Navigation 3**：每个顶级 Tab 独立 back stack（`NavigationState` + `Navigator`），
  切换 Tab 保留状态；ViewModels 通过 `hilt-lifecycle-viewmodel-compose` + `ViewModelStoreNavEntryDecorator` 作用域化到 Nav3 条目。

## 6. 配色方案

主色天蓝 `#0EA5E9`、辅助青绿 `#14B8A6`，基于 Material 3 `lightColorScheme / darkColorScheme` 生成
Primary / Secondary / Surface / Container 等全套色阶，深色模式使用提亮变体保证对比度。
详见 `ui/theme/Color.kt` 与 `ui/theme/Theme.kt`。

## 7. 常见问题

- **首次运行首页一直显示加载中**：确认 `assets/jlpt_n2_words.json` 存在且未损坏（词库为空时 `isReady=false`）。
- **TTS 无声音**：请确认系统已安装日语语音包（设置 → 系统 → 无障碍 → 文字转语音）；无 Google Play Store 的设备可能缺少日语语音。
- **学习提醒不弹通知**：Android 13+ 需要在系统设置中允许应用发送通知；提醒使用不精确的 `AlarmManager.set()`，可能延迟数分钟。
- **命令行构建时 Gradle 守护进程崩溃**：`C:\environment` 下的 IBM Semeru/OpenJ9 JDK 存在兼容问题，
  请改用 HotSpot JDK 21（如 Android Studio 自带 JBR `D:\Apps\Android Studio\jbr`）。
- **手写识别不准**：手写识别为轻量离线方案，请尽量工整书写；识别器返回 Top-8 候选，点选正确字符即可加入查询。
- **依赖下载慢**：已配置阿里云镜像；如遇 Maven Central TLS 握手失败，确认 `gradle.properties` 中 IPv4 优先参数存在。
