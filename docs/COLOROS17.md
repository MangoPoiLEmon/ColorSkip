# ColorOS 17 适配笔记

ColorOS 17 基于 Android 17（API 37）。系统对无障碍、后台服务、悬浮窗的限制主要来自 Android 平台本身，再叠加 OPPO 的省电与权限管理。以下是本项目采用的处理方式，以及无法绕过的边界。

## 1. 无障碍服务保活

### 能做的

- `AdSkipService` 在 `onServiceConnected` 时把自身注册到 `ServiceBridge`，UI 可实时显示「已连接 / 待连接」。
- `KeepAliveHelper.isAccessibilityServiceEnabled()` 同时使用：
  - `AccessibilityManager.getEnabledAccessibilityServiceList(...)`
  - `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 字符串兜底
- `GuardService` 每 60 秒检查一次无障碍状态；发现被关闭时发高优先级通知。
- `BootReceiver` 在开机后检查状态，并尝试启动前台守护。
- 通知点击进入 App 的保活向导。

### 不能做的

**没有第三方 API 可以重新开启无障碍服务。** 任何声称「代码自动恢复无障碍」的方案，本质上要么需要 Root / `WRITE_SECURE_SETTINGS`，要么是误导。ColorSkip 不做这件事，只负责检测和引导。

### 建议的用户操作

1. 电池 → 应用耗电管理 → ColorSkip → 允许后台活动 / 无限制。
2. 设置 → 应用 → ColorSkip → 自启动/关联启动（部分版本在 手机管家 → 权限隐私 → 自启动管理）。
3. 最近任务 → ColorSkip 卡片下拉 → 锁定；回到 ColorSkip 后手动点「我已锁定，标记完成」。
4. 关闭「睡眠待机优化」对 ColorSkip 的影响（如系统提供该选项）。

## 1.1 桌面与最近任务保护

从 ColorSkip 滑到桌面/任务管理页时，前台包名会变成 ColorOS Launcher、SystemUI 或 Quickstep。如果通用规则在这些界面误命中，就会把 ColorSkip 的任务卡重新点开，表现为「已跳过 · 系统桌面」后跳回 ColorSkip。

1.0.1 起：

- `PackageBlocklist` 对包名包含 `launcher` / `recents` / `quickstep` 的包一律拦截。
- `AdSkipService` 额外通过 `queryIntentActivities(ACTION_MAIN + CATEGORY_HOME)` 解析所有桌面包并拦截，避免 OEM 使用不含上述关键词的包名。
- 事件入口、延迟扫描入口、手动扫描入口三处都做同一判断。

## 2. 最近任务锁定

`ActivityManager` 没有公开的「锁定任务」API，ColorOS 也没有公开查询锁定状态的接口。ColorSkip 在设置页/保活向导提供「打开最近任务」按钮：

```kotlin
performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
```

随后提示用户下拉卡片。不同 ColorOS 版本的锁图标位置不同（卡片下拉后出现，或在右上角菜单）。

因为系统不提供查询接口，第 4 步无法可靠自动识别。1.0.1 起改为**用户手动确认**：点「我已锁定，标记完成」后显示绿色大勾，并保存到本机；设置页可取消确认。这比伪造一个自动检测结果诚实。

## 3. 悬浮窗

标准入口：

```kotlin
Intent(
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    Uri.parse("package:$packageName"),
)
```

ColorOS 部分版本不导出标准页，`KeepAliveHelper.openOverlaySettings()` 会依次尝试：

- `com.coloros.safecenter/.permission.floatwindow.FloatWindowListActivity`
- `com.coloros.safecenter/com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity`
- `com.oplus.safecenter/.permission.floatwindow.FloatWindowListActivity`
- `com.oplus.safecenter/com.oplus.safecenter.permission.floatwindow.FloatWindowListActivity`

最后回退到应用详情。每个 Intent 都先 `resolveActivity`，再 `startActivity`，捕获 `ActivityNotFoundException` / `SecurityException`。

## 4. 电池优化

`openBatteryOptimization()` 顺序：

1. `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `package:`（一步确认）
2. `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`（列表页）
3. ColorOS / OPlus 耗电管理页
4. 应用详情

Manifest 需要：

```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

该权限会触发应用商店审核关注；侧载工具可接受，若要上架请自行评估并准备隐私/功能说明。

## 5. 自启动 / 耗电管理 Intent 表

`KeepAliveHelper` 中维护的候选组件（ColorOS 12–17 常见命名）：

| 功能 | 包名 | Activity |
| --- | --- | --- |
| 自启动 | `com.coloros.safecenter` | `com.coloros.safecenter.startupapp.StartupAppListActivity` |
| 自启动 | `com.coloros.safecenter` | `com.coloros.safecenter.permission.startup.StartupAppListActivity` |
| 自启动 | `com.oplus.safecenter` | `com.oplus.safecenter.startupapp.StartupAppListActivity` |
| 自启动 | `com.oplus.safecenter` | `com.oplus.safecenter.permission.startup.StartupAppListActivity` |
| 自启动 | `com.coloros.phonemanager` | `com.coloros.phonemanager.startupapp.StartupAppListActivity` |
| 自启动 | `com.oplus.phonemanager` | `com.oplus.phonemanager.startupapp.StartupAppListActivity` |
| 耗电 | `com.coloros.safecenter` | `com.coloros.safecenter.powermanager.PowerManagerActivity` |
| 耗电 | `com.oplus.safecenter` | `com.oplus.safecenter.powermanager.PowerManagerActivity` |
| 耗电 | `com.coloros.safecenter` | `com.coloros.safecenter.power.PowerConsumptionActivity` |
| 耗电 | `com.oplus.safecenter` | `com.oplus.safecenter.power.PowerConsumptionActivity` |
| 耗电 | `com.coloros.oppoguardelf` | `com.coloros.powermanager.fuelgaue.PowerUsageModelActivity` |
| 耗电 | `com.oplus.battery` | `com.oplus.powermanager.fuelgaue.PowerUsageModelActivity` |

ColorOS 17 的实际组件可能变化。1.0.1 起 `KeepAliveHelper` 会先按硬编码组件尝试，再枚举 `com.coloros.safecenter` / `com.oplus.safecenter` / 手机管家等包中所有 exported Activity，按 `startup`、`autostart`、`boot` 命名片段动态查找；还会尝试 `STARTUP_APP_LIST` / `AUTOSTART` 等 action。只有全部失败时才打开应用详情，并明确提示用户点「自启动/关联启动」，不会再把应用详情伪装成“已打开自启动页”。

排查：

```bash
adb shell dumpsys package com.coloros.safecenter | findstr Activity
adb shell dumpsys package com.oplus.safecenter | findstr Activity
```

也可以让用户手动从「设置 → 应用 → ColorSkip → 电池 / 自启动」进入；保活向导始终提供应用详情兜底。

## 6. 前台服务

`GuardService` 声明：

```xml
android:foregroundServiceType="specialUse"
```

并声明权限：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

`specialUse` 适用于「不属于 dataSync/mediaPlayback/… 的本地用户可感知服务」。本项目没有网络权限，不传输数据，因此不使用 `dataSync`。Android 14+ 需要 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 说明用途。

注意：

- Android 12+ 从后台启动前台服务可能抛 `ForegroundServiceStartNotAllowedException`；`GuardService.start()` 用 `runCatching` 吞掉异常。
- Android 15+ 对 `BOOT_COMPLETED` 启动某些前台服务类型有限制；开机广播只做 best-effort，失败不崩溃。
- 通知权限被拒绝时，前台服务仍能运行，但通知可能不在抽屉显示。

## 7. 无障碍配置

```xml
android:canRetrieveWindowContent="true"
android:canPerformGestures="true"
android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged"
android:accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
```

不要设置 `android:packageNames`：

- 一旦设置，系统只向服务发送这些包的事件，静态列表无法在运行时扩展。
- ColorSkip 需要导入规则后立即覆盖新 App。
- 包过滤放在 Kotlin 代码中，并且对系统 UI/权限控制器做硬拦截。

## 8. ColorOS 17 真机验收清单

- [ ] 开启无障碍后，首页显示「已连接」。
- [ ] 杀进程/锁屏 10 分钟后，无障碍仍是「已连接」。
- [ ] 关闭无障碍，通知栏出现「无障碍服务被关闭」。
- [ ] 悬浮窗授权后，浮动小球出现且可拖动。
- [ ] 点击小球能手动扫描，并显示「未发现」或跳过提示。
- [ ] 电池优化显示「已放行」，重启后仍然生效。
- [ ] 最近任务锁定后，一键清理不会杀掉 ColorSkip。
- [ ] 打开目标 App 开屏广告，日志出现包名、文本、viewId、点击方式。
- [ ] 对 Flutter 页面添加坐标规则后，能按百分比点击。
- [ ] 系统权限弹窗期间不会发生误点（拦截包生效）。
