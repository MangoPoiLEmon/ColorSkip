package com.azz.colorskip.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azz.colorskip.ui.InstalledApp
import com.azz.colorskip.ui.PermissionSnapshot
import com.azz.colorskip.ui.findActivity
import com.azz.colorskip.ui.theme.Danger
import com.azz.colorskip.ui.theme.Lime
import com.azz.colorskip.ui.theme.TextMuted
import com.azz.colorskip.ui.theme.TextMain
import com.azz.colorskip.ui.toast
import com.azz.colorskip.util.KeepAliveHelper

@Composable
fun KeepAliveDialog(
    permissions: PermissionSnapshot,
    onDismiss: () -> Unit,
    onOpenRecents: () -> Unit,
    onToggleRecentsLocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ColorOS 17 保活向导") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "ColorOS 会收紧后台服务和无障碍权限。系统不允许第三方 App 自行重新开启无障碍，也不能用代码锁定最近任务；下面每一步都需要你在系统界面确认。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        if (permissions.accessibilityEnabled) "无障碍已开启" else "无障碍未开启",
                        if (permissions.accessibilityEnabled) Lime else Danger,
                    )
                    Pill(
                        if (permissions.ignoringBattery) "电池已放行" else "电池受限制",
                        if (permissions.ignoringBattery) Lime else Danger,
                    )
                }
                OemStep(
                    number = 1,
                    title = "电池用量设为「无限制」",
                    body = "ColorOS 设置 → 电池 → 应用耗电管理 → ColorSkip → 允许后台活动 / 无限制。",
                    done = permissions.ignoringBattery,
                    action = "电池设置",
                    onClick = {
                        val act = activity
                        if (act == null || !KeepAliveHelper.openBatteryOptimization(act)) {
                            context.toast("未找到电池设置页，已尝试打开应用详情")
                        }
                    },
                )
                OemStep(
                    number = 2,
                    title = "允许自启动 / 关联启动",
                    body = "优先路径：设置 → 应用 → ColorSkip → 自启动/关联启动。部分 ColorOS 版本在 手机管家 → 权限隐私 → 自启动管理。",
                    done = false,
                    action = "自启动/关联启动",
                    onClick = {
                        val act = activity
                        if (act == null || !KeepAliveHelper.openAutoStart(act)) {
                            context.toast("未找到系统直接开放的自启动页，已打开应用详情；请点「自启动/关联启动」")
                        }
                    },
                )
                OemStep(
                    number = 3,
                    title = "耗电管理",
                    body = "把 ColorSkip 的后台行为设为「允许」，避免系统在锁屏后冻结进程。",
                    done = permissions.ignoringBattery,
                    action = "耗电管理",
                    onClick = {
                        val act = activity
                        if (act == null || !KeepAliveHelper.openPowerManager(act)) {
                            context.toast("未找到耗电管理页，已尝试打开电池设置")
                        }
                    },
                )
                OemStep(
                    number = 4,
                    title = "最近任务锁定",
                    body = "打开最近任务，把 ColorSkip 卡片向下拉，点击锁图标。ColorOS 17 可能把该入口放在卡片右上角。",
                    done = permissions.recentsLocked,
                    action = "打开最近任务",
                    onClick = onOpenRecents,
                )
                TextButton(
                    onClick = onToggleRecentsLocked,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                ) {
                    Text(
                        if (permissions.recentsLocked) "取消「已锁定」确认" else "我已锁定，标记完成",
                        color = if (permissions.recentsLocked) TextMuted else Lime,
                    )
                }
                Text(
                    "说明：ColorOS 没有提供查询最近任务锁定状态的公开 API，所以第 4 步需要你手动确认；确认后会显示绿色大勾并保存在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Text(
                    "提示：如果系统仍然关闭无障碍，ColorSkip 前台守护会发通知提醒；点通知可回到系统设置重新开启。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun OemStep(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        if (done) {
            Text("✓", style = MaterialTheme.typography.titleMedium, color = Lime)
        } else {
            StepNumber(number, active = true)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Spacer(Modifier.height(5.dp))
            TextButton(
                onClick = onClick,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
            ) {
                Text(action, color = Lime)
            }
        }
    }
}

@Composable
fun CoordinateRuleDialog(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        packageName: String,
        xPercent: Float,
        yPercent: Float,
        delayMs: Long,
        requireAdContext: Boolean,
        keywords: List<String>,
    ) -> Boolean,
) {
    var name by rememberSaveable { mutableStateOf("坐标规则") }
    var pkg by rememberSaveable { mutableStateOf("") }
    var x by rememberSaveable { mutableStateOf("90") }
    var y by rememberSaveable { mutableStateOf("8") }
    var delay by rememberSaveable { mutableStateOf("500") }
    var requireContext by rememberSaveable { mutableStateOf(true) }
    var keywords by rememberSaveable { mutableStateOf("广告,跳过") }
    var showApps by rememberSaveable { mutableStateOf(false) }

    if (showApps) {
        AppPickerDialog(
            apps = apps,
            onDismiss = { showApps = false },
            onPick = {
                pkg = it.packageName
                if (name == "坐标规则") name = "${it.label} 坐标"
                showApps = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加坐标盲点规则") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "适用于 Flutter、游戏引擎等不暴露节点树的界面。坐标是屏幕百分比，只在指定包名生效；先抓快照/手动测试，避免误点。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("包名，例如 com.example.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { showApps = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("从已安装应用选择")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = x,
                        onValueChange = { x = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("X %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = y,
                        onValueChange = { y = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Y %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = delay,
                        onValueChange = { delay = it.filter(Char::isDigit) },
                        label = { Text("延迟 ms") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1.1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("仅在出现广告/跳过文字时点击", style = MaterialTheme.typography.titleMedium, color = TextMain)
                        Text(
                            "Flutter 页面可能读不到文字；读不到时可关闭，但误点风险更高。",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                    Switch(checked = requireContext, onCheckedChange = { requireContext = it })
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("触发关键词（逗号分隔，可为空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ok = onSave(
                        name,
                        pkg.trim(),
                        x.toFloatOrNull() ?: 0f,
                        y.toFloatOrNull() ?: 0f,
                        delay.toLongOrNull() ?: 500L,
                        requireContext,
                        keywords.split(',', '，').map { it.trim() }.filter { it.isNotBlank() },
                    )
                    if (ok) onDismiss()
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun AppPickerDialog(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onPick: (InstalledApp) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用 / 包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的启动器应用。可手动输入包名。",
                                modifier = Modifier.padding(12.dp),
                                color = TextMuted,
                            )
                        }
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppBadge(app.label)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, color = TextMain, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun LinkImportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从链接获取规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "ColorSkip 没有 INTERNET 权限，不会在后台下载任何内容。这里只把链接交给系统浏览器：下载 JSON/YAML 后，回到「规则 → 导入文件」选择它即可。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://…/rules.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "也可以在浏览器里长按文件选择「分享」→ ColorSkip，直接导入。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = url.trim()
                    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                        context.toast("请输入 http(s) 链接")
                        return@TextButton
                    }
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { context.toast("没有可打开该链接的浏览器") }
                },
            ) { Text("在浏览器打开") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun SnapshotDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("界面节点快照") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    ),
                    color = TextMain,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ColorSkip snapshot", text))
                    context.toast("快照已复制")
                },
            ) { Text("复制") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = TextMuted) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (danger) Danger else Lime,
                    contentColor = com.azz.colorskip.ui.theme.Ink,
                ),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
