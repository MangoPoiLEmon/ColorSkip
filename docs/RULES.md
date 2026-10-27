# 规则格式

ColorSkip 支持 JSON 与 YAML。解析入口是 [`RuleParser.kt`](../app/src/main/java/com/azz/colorskip/core/RuleParser.kt)，两种格式最终都归一化成同一套字段。

## 根结构

推荐：

```json
{
  "schema": "colorskip.rules/1",
  "name": "我的规则",
  "version": 1,
  "rules": []
}
```

也接受：

- 直接一个数组：`[ { ... }, { ... } ]`
- 单个规则对象
- `{ "items": [] }`
- `{ "data": { "rules": [] } }`

YAML 示例：

```yaml
schema: colorskip.rules/1
name: 我的规则
rules:
  - name: 示例
    packages: [com.example.app]
    keywords: [跳过, Skip]
```

YAML 使用 SnakeYAML `SafeConstructor`，禁止任意类实例化；单文件上限 2 MB、最多 300 条规则。

## 字段

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 自动生成 | 唯一 ID；导入时若与内置规则冲突会自动加 `imp.` 前缀 |
| `name` | string | `导入规则 N` | 显示名称 |
| `packages` / `packageNames` / `package` | string / string[] | 空 | 包名，支持 `*`、`?` 通配；空表示所有应用 |
| `keywords` / `texts` / `text` | string[] | 空 | 节点文本或 contentDescription 关键词 |
| `excludeKeywords` | string[] | 空 | 文本包含任一排除词时跳过该候选 |
| `viewIds` | string[] | 空 | `viewIdResourceName` 的短 ID 或完整 ID，支持 `*`、`?` 通配；非通配时按包含匹配 |
| `contentDescriptions` | string[] | 空 | 直接匹配 contentDescription |
| `clickMode` / `action` | string | `node` | `node` 或 `coordinate`（也接受 `tap` / `position` / `point`） |
| `xPercent` | number | – | 坐标 X，`0.9` 或 `90` 都表示 90% |
| `yPercent` | number | – | 坐标 Y |
| `coordinates` | [x, y] | – | 坐标数组，二选一 |
| `position` | {x, y} | – | 坐标对象，二选一 |
| `delayMs` | number | 0 | 点击前延迟，0–12000 ms |
| `cooldownMs` | number | 2500 | 同一规则/文本的点击冷却，300–60000 ms |
| `priority` | number | 0 | 越高越优先；参与评分 |
| `requireAdContext` | boolean | 节点规则 false / 坐标规则 true | 只有界面出现广告标记时才允许点击 |
| `enabled` | boolean | true | 是否启用 |

### 匹配评分

- 文本与关键词完全相等：100
- 文本以关键词开头：90
- 文本包含关键词：75；文本明显更长时减 18
- contentDescription 明确匹配：92
- viewId 命中：84
- 规则优先级：`priority / 2`，限制在 -40…60
- 可点击祖先：+6
- 位于屏幕上方 1/3：+4；位于右侧 2/3：+3

最终取分数最高且满足广告语境要求的候选。候选节点本身不可点击时，会向上找最多 4 层可点击祖先；如果该祖先面积超过屏幕 55%，不点容器，改为点击节点中心。

### 英文关键词必须整词匹配

这是 1.0.2 的重要安全修复：

- `Skip` **不会**命中 `ColorSkip`、`colorskip-logs.txt`、`ColorSkip-1.0.1.apk`、`Skipping`。
- `Skip` 会命中 `Skip`、`Skip 5`、`Skip Ad`、`跳过 Skip`。
- 中文关键词仍按子串匹配：`跳过` 会命中 `跳过 05`。

如果确实要匹配 `SkipAd` 这种无空格写法，请把 `SkipAd` 作为单独关键词加入，而不是依赖 `Skip` 子串。

### 通用「关闭」不再匹配裸词

1.0.2 起内置「广告关闭按钮」只接受：

```text
关闭广告 / 广告关闭 / 关闭此广告 / 关闭该广告
```

不再接受单独的 `关闭`。Outlook 的 `account_button` 的 contentDescription 是「关闭」，旧规则会误点并退出当前页面；现在不会。代价是只显示「关闭」的普通弹窗广告不会被内置规则命中，需要导入 App 专属规则，并至少要求 `viewIds` 或 `requireAdContext`。

### 广告语境

`requireAdContext` 会在当前节点树中查找以下标记：

- 中文：`广告`、`赞助`、`推广`、`开屏`
- 英文：`advert`、`sponsored`、独立的 `ad` 单词
- viewId 中常见的 `ad` / `splash` 片段

通用「关闭」规则应开启它，否则容易误点普通对话框。内置的「广告关闭按钮」与广告 SDK 关闭 ID 规则默认开启。

## 坐标规则

坐标规则必须包含 `packages` 和有效坐标，且坐标不能是 0 或 100%。计算基于当前窗口 `getBoundsInScreen()`，因此分屏、刘海屏也能工作。

```yaml
rules:
  - name: Flutter 开屏
    packages: [com.example.flutterapp]
    clickMode: coordinate
    xPercent: 91
    yPercent: 7
    delayMs: 800
    cooldownMs: 5000
    requireAdContext: false
    priority: 200
```

安全建议：

1. `requireAdContext: true` 最安全，但 Flutter/游戏可能读不到文字，导致永远不触发。
2. 关闭语境要求前，先用目标 App 的固定开屏页测试；不要在含支付/删除按钮的页面使用坐标规则。
3. 坐标规则只在节点规则完全未命中时执行；它不会覆盖正常的文本跳过。
4. 延迟期间会重新检查前台包名，切换到其他 App 不会点击。

## 导入

- App 内：规则 →「导入 JSON / YAML 规则」→ SAF 选择文件。
- 浏览器/文件管理器：分享文件给 ColorSkip，或直接用 ColorSkip 打开 `.json` / `.yaml`。
- 远程：规则页「从链接获取」把 URL 交给系统浏览器；下载后再导入。App 本身没有 INTERNET 权限。
- 导出：规则页「导出我的规则」只导出导入/自定义规则，不含内置规则。

导入成功后新规则立即生效（`SkipRepository.rules` 是 `StateFlow`，`RuleEngine` 每次扫描读取最新列表），无需重启无障碍服务。

## 与 GKD 的关系

本项目借鉴 GKD 的「本地规则 + 订阅文件」思路，但**不兼容 GKD 的完整 DSL**。GKD 规则中的 `matches`、`action`、`position`、`snapshot` 等复杂结构不会原样执行。ColorSkip 的字段是精简后的自有 schema，便于在单文件解析器中做安全限制。

如需迁移 GKD 规则，建议只提取：

- `packageName` → `packages`
- `text` / `contentDescription` → `keywords`
- `vid` → `viewIds`
- 点击坐标 → `clickMode: coordinate` + `xPercent` / `yPercent`

然后按本页格式重写。
