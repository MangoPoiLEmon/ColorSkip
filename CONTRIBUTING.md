# 贡献指南

感谢参与 ColorSkip。这个项目是一个本地、无网络权限的 Android 无障碍工具，提交前请先确认改动符合以下约束。

## 开发环境

- Android Studio 或命令行 JDK 17+
- Android SDK Platform 37.0
- Build-Tools 37.0.0
- Gradle wrapper（仓库自带）

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## 硬性约束

1. **不添加 `INTERNET` 权限，不引入联网依赖。**
2. **不要求 Root、不调用 shell、不修改系统设置。**
3. 不把无障碍服务用于读取密码、输入框内容或与广告跳过无关的页面。
4. 不添加分析、埋点、广告、账号或云配置 SDK。
5. 不提交签名密钥、`keystore.properties`、`local.properties`、构建产物或个人日志。

## 代码风格

- Kotlin，遵循现有包结构与命名。
- 规则匹配优先使用 `TextMatcher`；英文关键词必须整词匹配，避免 `Skip` 命中 `ColorSkip`。
- 新增会点击正常界面的规则时，必须提供 `packages`、`excludeKeywords` 或 `requireAdContext` 之一，并在 PR 中说明误点风险。
- 涉及无障碍事件、点击、权限、OEM 设置页时，补充对应的单元测试或说明无法自动测试的原因。

## 提交规则

- 规则文件放在 `samples/` 或说明其来源；不要提交从闭源订阅中复制的规则。
- 附上目标 App、包名、ColorOS/Android 版本、节点文本与命中日志。
- 日志请先删除个人信息（邮箱、账号、文件名、聊天内容等）。
- 新增内置规则时同步更新 [docs/RULES.md](docs/RULES.md) 和相关测试。

## Pull Request

- 一个 PR 尽量只解决一个问题。
- 描述中写清楚：问题、复现步骤、修改点、测试方式、残余风险。
- 提交前运行：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- 贡献代码默认按 [Apache License 2.0](LICENSE) 授权。

## 安全问题

请不要在公开 Issue 中披露可利用的无障碍/权限绕过细节，使用 [SECURITY.md](SECURITY.md) 中的私密渠道。
