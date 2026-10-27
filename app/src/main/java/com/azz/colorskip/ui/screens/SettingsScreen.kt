package com.azz.colorskip.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azz.colorskip.BuildConfig
import com.azz.colorskip.ui.MainViewModel
import com.azz.colorskip.ui.PermissionSnapshot
import com.azz.colorskip.ui.components.ConfirmDialog
import com.azz.colorskip.ui.components.PageHeader
import com.azz.colorskip.ui.components.Pill
import com.azz.colorskip.ui.components.PrivacyNote
import com.azz.colorskip.ui.findActivity
import com.azz.colorskip.ui.theme.Amber
import com.azz.colorskip.ui.theme.Danger
import com.azz.colorskip.ui.theme.Ink
import com.azz.colorskip.ui.theme.Lime
import com.azz.colorskip.ui.theme.Mint
import com.azz.colorskip.ui.theme.Outline
import com.azz.colorskip.ui.theme.SurfaceHigh
import com.azz.colorskip.ui.theme.TextMain
import com.azz.colorskip.ui.theme.TextMuted
import com.azz.colorskip.ui.toast
import com.azz.colorskip.util.KeepAliveHelper

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    permissions: PermissionSnapshot,
    onOpenKeepAlive: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val settings by vm.repository.settings.collectAsStateWithLifecycle()
    val stats by vm.repository.stats.collectAsStateWithLifecycle()
    var showReset by rememberSaveable { mutableStateOf(false) }
    var showClearRules by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }
    val exportLogs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) vm.exportLogs(uri) }
    val exportRules = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.exportRules(uri) }

    if (showReset) {
        ConfirmDialog(
            title = "重置全部统计？",
            body = "累计次数、今日次数和所有日志都会被删除。规则不受影响。",
            confirmText = "重置",
            danger = true,
            onConfirm = { vm.resetStats() },
            onDismiss = { showReset = false },
        )
    }
    if (showClearRules) {
        ConfirmDialog(
            title = "清空导入规则？",
            body = "内置规则不受影响；所有 JSON/YAML 与坐标规则会被删除。",
            confirmText = "清空",
            danger = true,
            onConfirm = { vm.clearImportedRules() },
            onDismiss = { showClearRules = false },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "Settings",
                title = "设置",
                subtitle = "守护开关、ColorOS 权限入口与本机数据管理。",
                trailing = { Pill("API 37", Mint) },
            )
        }

        item {
            SettingsSection("守护") {
                SettingSwitch(
                    title = "悬浮窗",
                    body = "显示跳过结果与手动扫描悬浮球所需的权限。",
                    checked = settings.overlayEnabled,
                    onCheckedChange = { enabled ->
                        vm.setOverlayEnabled(enabled)
                        if (enabled && !permissions.overlay) {
                            val act = activity
                            if (act != null) KeepAliveHelper.openOverlaySettings(act)
                            else context.toast("请回到应用内授权悬浮窗")
                        }
                    },
                )
                SettingDivider()
                SettingSwitch(
                    title = "悬浮球",
                    body = "可拖动的小球；点一下立即扫描当前界面。",
                    checked = settings.bubbleEnabled,
                    enabled = permissions.overlay && settings.overlayEnabled,
                    onCheckedChange = { vm.setBubbleEnabled(it) },
                )
                SettingDivider()
                SettingSwitch(
                    title = "前台守护",
                    body = "常驻通知 + specialUse 前台服务，降低 ColorOS 杀进程概率。",
                    checked = settings.guardEnabled,
                    onCheckedChange = { vm.setGuardEnabled(it) },
                )
            }
        }

        item {
            SettingsSection("ColorOS 17 保活") {
                SettingsActionRow(
                    title = "电池优化",
                    status = if (permissions.ignoringBattery) "已放行" else "受限制",
                    statusColor = if (permissions.ignoringBattery) Lime else Danger,
                    onClick = {
                        val act = activity
                        if (act == null || !KeepAliveHelper.openBatteryOptimization(act)) {
                            context.toast("未找到电池优化页面")
                        }
                    },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "自启动管理",
                    status = "去设置",
                    onClick = {
                        val act = activity
                        if (act == null || !KeepAliveHelper.openAutoStart(act)) {
                            context.toast("未找到自启动页面，已打开应用详情")
                        }
                    },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "耗电管理",
                    status = "去设置",
                    onClick = {
                        val act = activity
                        if (act == null || !KeepAliveHelper.openPowerManager(act)) {
                            context.toast("未找到耗电管理页面")
                        }
                    },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "打开最近任务",
                    status = "去锁定",
                    onClick = { vm.openRecents() },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "锁定状态",
                    status = if (permissions.recentsLocked) "已手动确认" else "待确认",
                    statusColor = if (permissions.recentsLocked) Lime else Amber,
                    onClick = { vm.setRecentsLocked(!permissions.recentsLocked) },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "完整保活向导",
                    status = "4 步",
                    onClick = onOpenKeepAlive,
                )
            }
        }

        item {
            SettingsSection("权限") {
                SettingsActionRow(
                    title = "无障碍服务",
                    status = if (permissions.accessibilityEnabled) "已开启" else "未开启",
                    statusColor = if (permissions.accessibilityEnabled) Lime else Danger,
                    onClick = {
                        if (!KeepAliveHelper.openAccessibilitySettings(context)) {
                            context.toast("无法打开无障碍设置")
                        }
                    },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "悬浮窗权限",
                    status = if (permissions.overlay) "已授权" else "未授权",
                    statusColor = if (permissions.overlay) Lime else Danger,
                    onClick = {
                        val act = activity
                        if (act == null || !KeepAliveHelper.openOverlaySettings(act)) {
                            context.toast("无法打开悬浮窗设置")
                        }
                    },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "通知权限",
                    status = if (permissions.notifications) "已允许" else "去允许",
                    statusColor = if (permissions.notifications) Lime else Amber,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !permissions.notifications) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            KeepAliveHelper.openNotificationSettings(context)
                        }
                    },
                )
            }
        }

        item {
            SettingsSection("本机数据") {
                SettingsActionRow(
                    title = "导出日志",
                    status = "${stats.total} 次记录",
                    onClick = { exportLogs.launch("colorskip-logs.txt") },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "导出我的规则",
                    status = "JSON",
                    onClick = { exportRules.launch("colorskip-rules.json") },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "清空导入规则",
                    status = "不会删除内置规则",
                    danger = true,
                    onClick = { showClearRules = true },
                )
                SettingDivider()
                SettingsActionRow(
                    title = "重置统计与日志",
                    status = "不可撤销",
                    danger = true,
                    onClick = { showReset = true },
                )
            }
        }

        item {
            SettingsSection("关于") {
                InfoLine("应用", "ColorSkip ${BuildConfig.VERSION_NAME}")
                InfoLine("适配", "Android 11 (API 30) – Android 17 (API 37)")
                InfoLine("编译", "compileSdk 37 · targetSdk 37")
                InfoLine("网络", "无 INTERNET 权限")
                InfoLine("实现", "AccessibilityService · 无 Root")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "ColorOS 的组件名可能随版本变化；找不到页面时会回退到应用详情。",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
            }
        }

        item { PrivacyNote() }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = TextMain,
            modifier = Modifier.padding(bottom = 9.dp, start = 2.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceHigh.copy(alpha = 0.7f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Outline.copy(alpha = 0.65f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    status: String,
    onClick: () -> Unit,
    statusColor: Color = TextMuted,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (danger) Danger else TextMain,
            modifier = Modifier.weight(1f),
        )
        Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.ArrowForward,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun SettingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Outline.copy(alpha = 0.55f)),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.labelSmall, color = TextMain)
    }
}
