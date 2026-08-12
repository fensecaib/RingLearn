# AGENTS.md（跨工具协作指南：Codex / Claude Code / opencode）

> 产品功能与模块说明见 [README.md](README.md)。本文只写「新会话/新 Agent 需要立刻知道」的命令、架构不变量与陷阱。

## 项目一句话
RingLearn：Kotlin + Jetpack Compose 的日语（JLPT N2）背单词 Android 应用。
`applicationId=com.ringlearn.app`，`compileSdk=37 / targetSdk=36 / minSdk=31`（Android 12+），`versionName=1.0.0`。
内置 1000 词词库、SM-2 间隔重复、自研内置罗马音输入法、离线手写识别、AI 对话（OpenAI 兼容）。

## 常用命令（Windows PowerShell）
```powershell
$env:JAVA_HOME='D:\Apps\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=2   # 单测 + debug
.\gradlew.bat :app:assembleRelease --no-daemon --max-workers=2                          # release(R8)
adb -s 01772412127937 install -r app\build\outputs\apk\debug\app-debug.apk             # 真机安装
```
- 单测共 **68 个**（RomajiEngine 18 + SM-2 6 + 其余），改动后必须全绿。
- Gradle 输出偶被吞：用 `*> build_out.txt` 重定向再查看。
- release 用 **debug 签名**（学习交流、不上架），R8 minify + 资源压缩（APK ≈3.4MB）。

## 架构不变量（改代码前必读，破坏即回归）
1. **内置键盘覆盖层**：底部 `NavigationBar` **常驻**（仅系统 IME 可见时隐藏）；内置键盘是根层覆盖物（`InAppKeyboardOverlay`）盖在底栏之上，键盘**常驻组合 + graphicsLayer 位移**开合；默认收起、点输入框弹出。禁止改回「移除底栏 + 页内键盘」。
2. **输入抬升量公式**：`lift = pageContentBottomPx - overlayTopPx`——nav host 与覆盖层 Column 用 `positionInRoot()` **同坐标系实测**（`RingLearnApp` / `InAppImeController`）。不要改回 `keyboardHeightPx - dockHeightPx` 推导（有 inset 偏差）。
3. **纯离线约束**：除 AI 对话外全部离线。真机**无 Google Play services**（UROVO i6310 Pro）——凡依赖 GMS 的方案（ML Kit 等）不可用。动画/TTS/手写用系统 API 或自研（零重型第三方库）。
4. **AI 对话**：OpenAI 兼容格式 + SSE 流式 + 完整上下文（不压缩）；**API Key 绝不入库**（DataStore + Keystore AES/GCM 加密，测试阶段 adb 输入）。
5. **性能不变量**：`KeepAliveNavHost` 6 Tab 常驻组合；首页火焰动画仅首页激活时运行；AI 流式节流（80ms/16 字符）且结束必 flush；候选栏空态收起（纯 fade）；弱机（i6310 Pro）上 16.6ms 预算难达成，以「优化前后相对变化」验收。

## 关键文件地图
| 路径 | 职责 |
| --- | --- |
| `app/src/main/java/com/ringlearn/app/ui/navigation/RingLearnApp.kt` | 根：6 Tab、KeepAliveNavHost、键盘覆盖层、抬升量实测 |
| `ui/ime/` | 内置键盘：`InAppImeController`、`RomajiKeyboard`、`CandidateBar`、`RingLearnTextField`（RomajiEngine 组合）、`InAppImeBinding` |
| `ui/ai/AiChatScreen.kt` | AI 对话（懒加载/字号/滚动导航/上下文徽章） |
| `ui/lookup|wordbook|study|quiz|home/` | 查词 / 生词本 / 学习 / 测验 / 首页 |
| `data/` | Room（words/ai_chat/review_log）、Repository、AI 客户端；`assets/jlpt_n2_words.json` 1000 词 |
| `domain/ime/RomajiEngine.kt` | 罗马音→假名引擎（有单测） |
| `util/handwriting/HandwritingRecognizer.kt` | 离线手写（字形模板 + Chamfer） |

## 真机测试与工具
- 设备 `01772412127937`（720x1440 / density 320 / Android 12 / 无 GMS），另有 `01772412127998`——**多设备务必 `adb -s`**。
- 项目内 `.codex/skills/`（**已 gitignore，仅本地**）沉淀了 5 个测试/调试 skill：`ringlearn-device-test`、`ringlearn-keyboard-check`、`ringlearn-perf-measure`、`ringlearn-build-test`、`ringlearn-debug-runtime`。新会话直接调用其脚本（如 `occlusion_check.py`、`measure_gfxinfo.py`），SKILL.md 含完整流程与基线。
- 键盘遮挡验收标准：输入「ふ」触发候选栏后，输入框 bottom < 候选栏 top（留 6dp 间隙）；键盘表面直达系统导航栏上沿。

## 陷阱速查
- PowerShell 写 Kotlin 代码（含引号）用**单引号字符串或 here-string**；文件 CRLF/LF 混用导致 `.Replace()` 锚点失配时**优先整体重写文件**。
- 候选栏有 `[0,0][0,0]` 的残留 accessibility 节点（AnimatedVisibility 隐藏后短暂存在），断言脚本须过滤零面积节点。
- `adb shell input text` 只在 ADB Keyboard 类输入法生效；内置键盘是 Compose 组件，用 `input tap` 点按键坐标。
- 重启应用可能恢复到任意上次 Tab——自动化脚本需显式导航并校验页面标题。
