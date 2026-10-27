# 隐私说明 / Privacy

ColorSkip 是一个完全离线的本机无障碍工具。

## 不收集、不上传

- 应用没有声明 `android.permission.INTERNET`，无法发起网络请求。
- 没有分析 SDK、广告 SDK、崩溃上报 SDK、云配置或账号系统。
- 规则、统计、设置、日志只保存在本机 `SharedPreferences`。

## 无障碍数据

- 无障碍服务只读取当前活动窗口的节点树，用于判断是否存在「跳过 / 关闭广告」控件。
- 匹配和点击都在本机内存中完成，不截图、不录屏、不上传。
- 日志最多保存 200 条，字段包括：时间、包名、应用名、节点文本、viewId、规则 ID、点击方式。
- 「抓取快照」生成的是节点树文本；密码框和 `EditText` 的文本会替换为 `<redacted>`，不包含截图。分享快照前仍建议自行检查。
- 卸载应用会删除本机保存的日志、规则和设置；应用内也提供清空日志/重置统计。

## 权限用途

| 权限 | 用途 |
| --- | --- |
| `BIND_ACCESSIBILITY_SERVICE` | 读取当前窗口并点击跳过按钮 |
| `SYSTEM_ALERT_WINDOW` | 显示跳过提示与手动扫描悬浮球 |
| `POST_NOTIFICATIONS` | 前台守护通知与无障碍丢失提醒 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 保持本地守护服务运行 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 引导用户把应用加入电池白名单 |
| `RECEIVE_BOOT_COMPLETED` | 开机后恢复本地守护与无障碍状态检查 |

## 联系方式

隐私问题请通过仓库的 GitHub Issues 或 Security Advisory 联系维护者。请不要在公开 Issue 中粘贴包含个人邮件、账号或其他敏感信息的日志。
