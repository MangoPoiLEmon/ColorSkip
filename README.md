# ColorSkip

面向 **ColorOS 17 / Android 17（API 37）** 的本地开屏广告跳过工具。核心是一个无 Root、无网络权限的 `AccessibilityService`：在本机规则库中匹配「跳过 / 关闭广告 / Skip」节点并点击；节点树不可用时，可用坐标盲点兜底。

> **独立开源项目**：与 OPPO、OnePlus、realme、ColorOS 没有任何隶属或背书关系。
> ColorOS 只是本项目重点适配的系统环境，相关名称归其权利人所有。

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![minSdk](https://img.shields.io/badge/minSdk-30-green.svg)
![targetSdk](https://img.shields.io/badge/targetSdk-37-green.svg)

## 特性

- **完全离线**：Manifest 中没有 `INTERNET` 权限；规则、日志、统计都写入本机 `SharedPreferences`。
- **节点规则**：`text` / `contentDescription` / `hintText` / `viewIdResourceName` 匹配，支持包含、前缀、精确匹配与排除词。
- **英文整词匹配**：`Skip` 不会误命中 `ColorSkip`、`colorskip-logs.txt`、`Skipping`；中文仍按子串匹配。
- **广告语境判定**：通用关闭规则只接受「关闭广告 / 广告关闭 / 关闭此广告 / 关闭该广告」，不接受裸「关闭」。
- **坐标盲点**：按屏幕百分比点击，适用于 Flutter、Unity、游戏引擎等 `rootInActiveWindow` 几乎为空的界面。
- **规则导入**：内置 9 条基础规则；支持本地 JSON / YAML、浏览器分享文件、`ACTION_VIEW` 打开文件。
- **系统界面保护**：硬拦截 Launcher / Recents / Quickstep、权限管理、安装器、通知管理、流量管理等敏感界面。
- **悬浮窗**：可拖动的计数小球（点一下手动扫描）和「已跳过 · App」瞬时提示。
- **日志与快照**：记录包名、节点文本、viewId、规则、点击方式；密码框和 `EditText` 文本会脱敏。
- **前台守护**：独立的 `specialUse` 前台服务 + 常驻通知；无障碍被系统关闭时发通知提醒。
- **ColorOS 保活向导**：电池优化、自启动、耗电管理、悬浮窗、最近任务锁定的多级 Intent 回退。

## 环境要求

- Android 11+（`minSdk 30`），目标 Android 17（`targetSdk 37`）
- JDK 17+（CI 使用 Temurin 21）
- Android SDK Platform 37.0、Build-Tools 37.0.0
- Gradle 9.8.2（随项目 wrapper 提供）

## 构建

1. 配置 `local.properties`：

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

2. 运行测试并构建 debug APK：

   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleDebug
   ```

   Windows：

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
   ```

3. 产物：

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

> `compileSdk 37`、`targetSdk 37`、`buildToolsVersion 37.0.0` 已在 `app/build.gradle.kts` 中固定。若你的 Android Studio 尚未安装 API 37，请先在 SDK Manager 中安装 `Android SDK Platform 37.0` 与 `Build-Tools 37.0.0`。

## 发布签名

仓库**不包含**签名密钥。`app/build.gradle.kts` 会在仓库根目录存在 `keystore.properties` 时启用 release 签名；没有该文件时 `assembleRelease` 只生成未签名 APK。

```bash
keytool -genkeypair \
  -v -keystore keystore/colorskip-release.jks \
  -alias colorskip -keyalg RSA -keysize 4096 -validity 10000

cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填入真实密码
./gradlew :app:assembleRelease
```

`keystore.properties`、`*.jks`、`*.keystore` 都已加入 `.gitignore`，**不要提交**。GitHub Actions 只构建 debug 包；正式 release 请在本地或受保护的 CI Secret 中签名。

## 安装与首次设置

1. 安装 debug / release APK。
2. 打开 ColorSkip，依次开启：
   - 无障碍：系统设置 → 无障碍 → 已下载的应用 → ColorSkip
   - 悬浮窗：应用内「悬浮窗」卡片，或系统设置 → 应用 → 特殊权限 → 显示在其他应用上层
   - 通知：Android 13+ 首次启动会请求
3. ColorOS 保活：
   - 电池用量 → 无限制
   - 设置 → 应用 → ColorSkip → 自启动/关联启动
   - 最近任务 → 下拉 ColorSkip 卡片 → 锁定；回到保活向导点「我已锁定，标记完成」
4. 打开带开屏广告的 App 测试；回到「日志」查看命中详情。

## 工作方式

```text
AccessibilityEvent
   └─ 220ms 防抖 / 敏感包过滤
        └─ RuleEngine 遍历当前窗口（最多 1800 节点 / 26 层）
             ├─ 文本 / 描述 / viewId 评分
             ├─ 广告语境检查
             ├─ ACTION_CLICK（必要时只点节点中心）
             └─ 失败则 dispatchGesture 单击
                  └─ 冷却 / 日志 / 悬浮窗提示
```

### 关键限制

- **无障碍不能由应用自行重新开启。** Android 没有第三方可用 API；ColorSkip 只检测、发通知、深链到设置页。
- **最近任务锁定不能由代码完成。** 只能打开最近任务，再由用户下拉卡片；ColorOS 没有查询锁定状态的公开 API，所以第 4 步是手动确认。
- **OEM 设置页组件名会变。** `KeepAliveHelper` 维护 ColorOS / OPlus 多套组件，并动态枚举 exported Activity；失败时回退到应用详情。
- **只显示「关闭」两个字的广告不会被内置规则命中。** 这是为了避免误点 Outlook 等正常应用的关闭按钮；需要时请导入 App 专属规则。

更多细节：[ColorOS 17 适配笔记](docs/COLOROS17.md)。

## 规则格式

完整字段与匹配语义见 [docs/RULES.md](docs/RULES.md)。

```json
{
  "rules": [
    {
      "name": "某应用开屏",
      "packages": ["com.example.app"],
      "keywords": ["跳过", "Skip"],
      "excludeKeywords": ["跳过教程"],
      "viewIds": ["skip", "splash_skip"],
      "cooldownMs": 2500,
      "priority": 100
    }
  ]
}
```

```yaml
rules:
  - name: 某 Flutter 应用
    packages: [com.example.flutter]
    clickMode: coordinate
    xPercent: 90
    yPercent: 8
    delayMs: 600
    requireAdContext: false
```

- 规则页 →「导入 JSON / YAML 规则」→ 选择本地文件。
- 浏览器下载规则文件 → 分享 → ColorSkip。
- 「从链接获取」把 URL 交给系统浏览器；App 本身没有 `INTERNET` 权限，不会后台下载。

内置规则位于 [app/src/main/assets/rules/builtin_rules.json](app/src/main/assets/rules/builtin_rules.json)，示例见 [samples/](samples/)。

## 项目结构

| 路径 | 说明 |
| --- | --- |
| [app/src/main/java/com/azz/colorskip/service/AdSkipService.kt](app/src/main/java/com/azz/colorskip/service/AdSkipService.kt) | 无障碍服务、事件防抖、点击/手势、日志、快照 |
| [core/RuleEngine.kt](app/src/main/java/com/azz/colorskip/core/RuleEngine.kt) | 节点树 BFS、关键词/ID 评分、广告语境 |
| [core/TextMatcher.kt](app/src/main/java/com/azz/colorskip/core/TextMatcher.kt) | 英文整词 / 中文子串匹配 |
| [core/RuleParser.kt](app/src/main/java/com/azz/colorskip/core/RuleParser.kt) | JSON / YAML 解析与安全限制 |
| [util/KeepAliveHelper.kt](app/src/main/java/com/azz/colorskip/util/KeepAliveHelper.kt) | ColorOS 设置页 Intent 与动态发现 |
| [service/GuardService.kt](app/src/main/java/com/azz/colorskip/service/GuardService.kt) | `specialUse` 前台守护 |
| [overlay/OverlayController.kt](app/src/main/java/com/azz/colorskip/overlay/OverlayController.kt) | 悬浮球与跳过提示 |
| [ui/](app/src/main/java/com/azz/colorskip/ui/) | Compose Material 3 界面 |

## 隐私

- 无 `INTERNET` 权限，无分析 SDK、广告 SDK、云配置。
- 日志最多 200 条，只包含包名、应用名、节点文本、viewId、规则 ID、点击方式。
- 快照是节点树文本，不包含截图；密码框和 `EditText` 文本替换为 `<redacted>`。
- `allowBackup=false`。

详见 [PRIVACY.md](PRIVACY.md)。

## 已知限制

- 未在真实 ColorOS 17 机器上做全机型验证；OEM 页面只做 best-effort 深链。
- 坐标规则天然有误点风险，默认只在检测到广告语境时触发；对 Flutter 可关闭该限制，但请先小范围测试。
- 不解析 GKD 的完整规则 DSL；本项目规则是 GKD 思路的简化实现。
- 本项目通过 GitHub / 侧载分发，不面向 Google Play 的无障碍政策做适配；使用前请自行确认当地法律、应用条款和设备风险。

## 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。从本地工程发布到 GitHub 的完整步骤（仓库创建、签名、Release、CI、截图规范）见 [docs/OPEN_SOURCE.md](docs/OPEN_SOURCE.md)。

提交规则时请附上目标 App、ColorOS 版本、节点文本和日志，不要提交个人邮件、账号或截图中的隐私信息。

## 许可证

[Apache License 2.0](LICENSE)。Copyright 2026 ColorSkip contributors。

第三方组件许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
