# AGENTS.md

RingLearn 是 Kotlin + Jetpack Compose 的日语（JLPT N2）背单词 Android 应用。
`applicationId=com.ringlearn.app`，`compileSdk=37 / targetSdk=36 / minSdk=31`（Android 12+），`versionName=1.0.0`。
内置 1000 词词库、SM-2 间隔重复、自研内置罗马音输入法、离线手写识别、AI 对话（OpenAI 兼容）。
产品功能与设计见 [README.md](README.md)；本文只写新会话立刻需要知道的命令、风格、结构、边界与陷阱。

## 1. 构建与测试命令（Windows PowerShell）

```powershell
# 单测 + debug 构建（默认命令，任何改动后必须跑）
$env:JAVA_HOME='D:\Apps\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=2

# 只跑单个测试类（按需定位失败）
.\gradlew.bat :app:testDebugUnitTest --tests "com.ringlearn.app.domain.ime.RomajiEngineTest" --no-daemon --max-workers=2

# release（R8 minify + 资源压缩，debug 签名，学习交流不上架）
.\gradlew.bat :app:assembleRelease --no-daemon --max-workers=2

# 真机安装
adb -s 01772412127937 install -r app\build\outputs\apk\debug\app-debug.apk
```

- 单测共 **68 个**，改动后必须全绿；Gradle 输出偶被吞时用 `*> build_out.txt` 重定向查看。
- 测试类 FQCN（供 `--tests` 过滤）：
  - `com.ringlearn.app.domain.ime.RomajiEngineTest`（18 个，核心）
  - `com.ringlearn.app.domain.algorithm.Sm2SchedulerTest`（6 个）
  - `com.ringlearn.app.data.ai.AiClientTest`、`com.ringlearn.app.data.ai.StreamThrottleTest`
  - `com.ringlearn.app.data.repository.StartOfDayTest`
  - `com.ringlearn.app.ui.ai.ChatHistoryWindowTest`、`ContextStatsTest`、`MarkdownTextTest`

## 2. 代码风格（Code Style）

- Kotlin + Jetpack Compose，遵循 Android Kotlin 官方风格（4 空格缩进、`val` 优先、公开 API 写显式返回类型）。
- Compose 约定：
  - 组合函数 PascalCase；状态提升到页面级 ViewModel（StateFlow），页面内用 `remember`/`rememberSaveable`。
  - 每帧变化的状态（动画、拖拽、流式文本）在 `graphicsLayer {}` lambda 或 `derivedStateOf` 中读取，避免顶层重组。
  - 列表用 `key(item.id)`；候选可能重复文本时用 `itemsIndexed`（索引 key），避免重复 key 崩溃。
- 异步：Room/DataStore 一律 Flow + Coroutines，禁止主线程 `runBlocking`。
- 架构：MVVM + StateFlow + Hilt；新功能放 `ui/<feature>/` + `data/`，不引入重型第三方库。

## 3. 项目结构（Project Structure）

| 路径 | 职责 |
| --- | --- |
| `ui/navigation/RingLearnApp.kt` | 根：6 Tab、KeepAliveNavHost、键盘覆盖层、抬升量实测 |
| `ui/ime/` | 内置键盘：InAppImeController、RomajiKeyboard、CandidateBar、RingLearnTextField、InAppImeBinding |
| `ui/ai/AiChatScreen.kt` | AI 对话（懒加载 / 字号 / 滚动导航 / 上下文徽章） |
| `ui/lookup\|wordbook\|study\|quiz\|home/` | 查词 / 生词本 / 学习 / 测验 / 首页 |
| `data/` | Room（words/ai_chat/review_log）、Repository、AI 客户端；`assets/jlpt_n2_words.json` 1000 词 |
| `domain/ime/RomajiEngine.kt` | 罗马音→假名引擎（有单测） |
| `util/handwriting/HandwritingRecognizer.kt` | 离线手写（字形模板 + Chamfer） |

## 4. 架构不变量（破坏即回归）

1. **内置键盘覆盖层**：底部 `NavigationBar` **常驻**（仅系统 IME 可见时隐藏）；内置键盘是根层覆盖物（`InAppKeyboardOverlay`）盖在底栏之上，常驻组合 + graphicsLayer 位移开合；默认收起、点输入框弹出。禁止改回「移除底栏 + 页内键盘」。
2. **输入抬升量公式**：`lift = pageContentBottomPx - overlayTopPx`——nav host 与覆盖层 Column 用 `positionInRoot()` **同坐标系实测**（RingLearnApp / InAppImeController）。不要改回 `keyboardHeightPx - dockHeightPx` 推导（有 inset 偏差）。
3. **纯离线约束**：除 AI 对话外全部离线。真机**无 Google Play services**（UROVO i6310 Pro）——凡依赖 GMS 的方案（ML Kit 等）不可用。动画/TTS/手写用系统 API 或自研，零重型第三方库。
4. **AI 对话**：OpenAI 兼容 + SSE 流式 + **完整上下文（不压缩）**；API Key 绝不入库（DataStore + Keystore AES/GCM）。
5. **性能不变量**：`KeepAliveNavHost` 6 Tab 常驻组合；首页火焰动画仅首页激活时运行；AI 流式节流（80ms/16 字符）且结束必 flush；候选栏空态收起（纯 fade）。弱机（i6310 Pro）16.6ms 预算难达成，以「优化前后相对变化」验收。

## 5. 真机测试与工具

- 设备 `01772412127937`（720x1440 / density 320 / Android 12 / 无 GMS），另有 `01772412127998`——**多设备务必 `adb -s`**。
- 项目内 `.codex/skills/`（**已 gitignore，仅本地**）沉淀 5 个测试/调试 skill：`ringlearn-device-test`、`ringlearn-keyboard-check`、`ringlearn-perf-measure`、`ringlearn-build-test`、`ringlearn-debug-runtime`。均符合 2026-08 skill 规范（`quick_validate.py` 校验通过、含 `agents/openai.yaml` UI 元数据）；并已以 junction 安装到 `~/.codex/skills/`（Codex 不扫描项目级 `.codex/skills/`，需在用户级目录才能自动发现；更新后需重启 Codex）。直接调用其脚本（`occlusion_check.py`、`measure_gfxinfo.py` 等），SKILL.md 含流程与基线。
- 键盘遮挡验收：输入「ふ」触发候选栏后，输入框 bottom < 候选栏 top（留 6dp 间隙）；键盘表面直达系统导航栏上沿。
- 陷阱：
  - PowerShell 写 Kotlin 用**单引号字符串或 here-string**；文件 CRLF/LF 混用导致 `.Replace()` 失配时**优先整体重写文件**。
  - 候选栏有 `[0,0][0,0]` 残留 accessibility 节点（隐藏后短暂存在），断言脚本须过滤零面积节点。
  - `adb shell input text` 只在 ADB Keyboard 类输入法生效；内置键盘是 Compose 组件，用 `input tap` 点按键坐标。
  - 重启应用可能恢复到任意上次 Tab——自动化脚本需显式导航并校验页面标题。

## 6. Git 工作流（Git Workflow）

- 分支：功能分支 `codex/<topic>`；提交用 **Conventional Commits**（`feat:` / `fix:` / `perf:` / `chore:` / `docs:` / `refactor:`），描述用中文。
- 提交前：`git status` 干净，`.codex/` 与 `*.log` 不入库；保持线性历史（rebase 优先）。
- 远端：push 到 `origin/main`（`fensecaib/RingLearn`）。

## 7. 边界（Boundaries）

**✅ Always（直接做）**
- 改动核心逻辑后跑全部单测 + `assembleDebug`。
- 触碰键盘覆盖层 / 抬升量 / 导航时，真机验证遮挡与 dock 显隐。
- 新密钥一律 DataStore 加密持久化，绝不硬编码或提交。

**⚠️ Ask first（先问）**
- 引入第三方库（尤其重库：动画 / 图表 / ML）——项目坚持零重型依赖。
- 修改 Room schema / 升数据库版本（需 Migration 与回归）。
- 改变键盘覆盖层架构、导航常驻策略、AI 上下文策略。

**🚫 Never（禁止）**
- 提交 `sk-*` API Key、Keystore 密文、`.codex/`、构建产物与日志。
- 改回「移除底栏 + 页内键盘」；改回 `keyboardHeightPx - dockHeightPx` 推导。
- 引入 GMS / ML Kit 依赖（真机无 Play services）。
- 主线程 `runBlocking`；破坏 68 个单测的全绿状态。

## 8. 安全（Security）

- AI API Key 仅存 DataStore，经 Android Keystore AES/GCM 加密；测试阶段经设置页 / adb 输入，**禁止写入源码与 git 历史**。
- `INTERNET` 权限仅用于 AI 对话；其余功能纯离线。
- release 用 debug 签名（学习交流，不上架）；上架前需替换正式签名并复核 R8 keep 规则。
