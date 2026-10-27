# Changelog

## 1.0.2 — 2026-10-27

### Fixed

- 英文关键词改为整词匹配：`Skip` 不再命中 `ColorSkip`、`colorskip-logs.txt`、`ColorSkip-1.0.1-release.apk`、`Skipping`。
- 移除通用规则中的裸「关闭」，避免误点 Outlook 的 `account_button` 等正常按钮；只接受「关闭广告 / 广告关闭 / 关闭此广告 / 关闭该广告」。
- 扩充英文排除词（`skip to`、`skip next`、`skip previous`、`skip forward`、`skip back`、`skip song`、`skip track`、`skip video`、`skip step`、`skip setup`、`skip sign`）。
- 增加权限管理、安装器、通知管理、流量管理等 OEM 系统页面拦截。
- 新增 8 个单元测试，覆盖整词匹配、关闭规则与 Launcher/Recents 拦截。

## 1.0.1 — 2026-10-27

### Fixed

- 硬拦截 Launcher / Recents / Quickstep，避免从任务管理页误点 ColorSkip 任务卡。
- 动态枚举 ColorOS/OPlus 自启动页面 Activity 与 action，减少直接落到应用详情的情况。
- 最近任务锁定改为手动确认并持久化，保活向导可显示完成状态。

## 1.0.0 — 2026-10-27

### Added

- 初始版本：无障碍跳过、节点/坐标规则、JSON/YAML 导入、悬浮窗、日志/快照、前台守护、ColorOS 保活向导。
