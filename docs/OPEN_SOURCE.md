# 开源发布清单

这份清单把 ColorSkip 从本地工程发布到 GitHub 的步骤串起来。仓库已经准备好
`LICENSE`、`README`、`PRIVACY`、`SECURITY`、`CONTRIBUTING`、Issue/PR 模板和 CI，
但 **GitHub 用户名、远程地址、签名密钥、截图** 需要你本人填写或生成。

## 1. 先确定许可证

当前默认是 **Apache License 2.0**：

- 允许商用、修改、再分发，包含专利授权；
- 需要保留版权与许可声明；
- 与 AndroidX / Compose / Kotlin / SnakeYAML 的 Apache-2.0 兼容。

如果你想用 GPL-3.0（与 GKD 同类项目的 copyleft 路线）或 MIT：

1. 替换根目录 `LICENSE`；
2. 更新 `README.md` 的许可证段落；
3. 确认所有贡献者同意该许可证。

`LICENSE` 里的版权人是 `ColorSkip contributors`。如果你希望写真实姓名或
GitHub 用户名，替换为：

```text
Copyright 2026 <你的名字或 GitHub 用户名>
```

## 2. 清理不能公开的内容

已经忽略、但仍建议确认：

```bash
git status --ignored
```

必须确认没有提交：

- `local.properties`（含本机 SDK 路径）
- `.gradle/`、`build/`、`.kotlin/`
- `dist/`、`*.apk`、`*.aab`
- `keystore.properties`、`*.jks`、`*.keystore`
- 个人日志、截图、账号、邮箱

再做一次全局检查：

```bash
git grep -n -i "password\|secret\|keystore\|F:\\\\\|AZZONE\|59541" || true
```

`README.md` 已经移除本机路径和“沙箱构建”说明。当前 `dist/` 下的 APK 是
**debug key 签名**，只适合本机测试，不要作为 GitHub 正式 Release 资产上传。

## 3. 初始化 Git 仓库

```bash
cd ColorSkip
git init
git add .
git status
git commit -m "Initial open-source release: ColorSkip 1.0.2"
git branch -M main
```

如果还没配置 Git 身份：

```bash
git config --global user.name "你的名字"
git config --global user.email "你的 GitHub 邮箱"
```

## 4. 在 GitHub 创建仓库

网页方式：

1. GitHub → **New repository**
2. 名称建议：`ColorSkip`
3. 选择 **Public**
4. **不要**勾选 “Add a README / .gitignore / license”，仓库里已经有了
5. 创建后复制仓库地址

然后：

```bash
git remote add origin https://github.com/<你的用户名>/ColorSkip.git
git push -u origin main
```

如果你用 SSH：

```bash
git remote add origin git@github.com:<你的用户名>/ColorSkip.git
git push -u origin main
```

## 5. 仓库设置

建议在 GitHub 仓库页面设置：

- **Description**：`Local, offline ad-skipper for ColorOS 17 / Android 17, built on AccessibilityService.`
- **Topics**：`android`, `accessibility-service`, `ad-skipper`, `coloros`, `kotlin`, `jetpack-compose`, `privacy`
- **Issues**：开启
- **Discussions**：可选
- **Wiki**：关闭（文档放 `docs/`）
- **Require a pull request before merging**：建议开启
- **Require status checks**：选择 `Android CI / build`

不要把 `dist/` 或签名密钥加进仓库。

## 6. 生成正式签名并构建 Release

```bash
mkdir -p keystore
keytool -genkeypair \
  -v -keystore keystore/colorskip-release.jks \
  -alias colorskip -keyalg RSA -keysize 4096 -validity 10000

cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填入真实密码（不要提交）
./gradlew clean :app:testDebugUnitTest :app:assembleRelease
```

构建后：

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

把 `app-release.apk` 改名为 `ColorSkip-1.0.2-release.apk` 作为 Release 资产。
再次强调：`keystore/`、`keystore.properties`、`*.jks` 都不能进 Git。

## 7. 创建 GitHub Release

```bash
git tag -a v1.0.2 -m "ColorSkip 1.0.2"
git push origin v1.0.2
```

GitHub → **Releases → Draft a new release**：

- Tag：`v1.0.2`
- Title：`ColorSkip 1.0.2`
- 说明：从 [CHANGELOG.md](CHANGELOG.md) 复制 `1.0.2` 段落
- 附件：签名后的 `ColorSkip-1.0.2-release.apk`
- 可选：`SHA256SUMS.txt`

**不要**把 debug 包作为正式附件。需要提供测试包时，可以单独标注
`debug-signed, not for production`。

## 8. CI

`.github/workflows/android-ci.yml` 会在 push / PR 时：

1. 安装 JDK 21；
2. 安装 Android SDK Platform 37.0 与 Build-Tools 37.0.0；
3. 运行 `:app:testDebugUnitTest`；
4. 构建 debug APK；
5. 上传 workflow artifact。

CI 不会签名 release，也不会读取你的 keystore。若要在 CI 中发布 release，
请把 keystore 转成 base64 存入 **GitHub Actions Secrets**，在 workflow 中临时解码，
并确保 fork PR 无法读取 secrets。对个人项目，建议本地签名后手动上传 Release 更简单。

## 9. 截图与演示

仓库目前没有放截图，避免使用带个人信息的真实邮箱/聊天页面。建议自己补：

- `docs/screenshots/home.png`
- `docs/screenshots/rules.png`
- `docs/screenshots/logs.png`
- `docs/screenshots/keepalive.png`

要求：

- 使用干净的测试账号，遮掉邮箱、联系人、文件名；
- 不要展示真实广告主或未授权 App 的完整界面；
- 可以录一段 10–15 秒 GIF 放在 README 顶部；
- 截图不要带状态栏手机号、通知内容。

加完后在 README 的“特性”下方插入：

```markdown
![首页](docs/screenshots/home.png)
```

## 10. 发布前的最终检查

- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过
- [ ] `git status` 干净，`local.properties` / `dist/` / `keystore` 未跟踪
- [ ] README 没有本机路径、密码、个人邮箱
- [ ] `LICENSE` 的版权人已替换
- [ ] Release APK 使用正式 key 签名
- [ ] 没有新增 `INTERNET` 权限
- [ ] `SECURITY.md` 的私密报告入口可用
- [ ] 明确声明“非 OPPO/ColorOS 官方项目”
- [ ] 说明“不面向 Google Play 无障碍政策做适配”

## 11. 维护建议

- 规则更新单独提交，附目标 App 与日志；
- 对误点问题优先收紧规则，而不是扩大关键词；
- 每次版本更新 `versionCode`，并更新 [CHANGELOG.md](CHANGELOG.md)；
- 不把用户的完整日志收进仓库或公开 Issue；
- 发现无障碍/权限绕过类问题走私密 Security Advisory。
