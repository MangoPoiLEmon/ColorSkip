package com.azz.colorskip.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azz.colorskip.ui.MainViewModel
import com.azz.colorskip.ui.PermissionSnapshot
import com.azz.colorskip.ui.components.EmptyState
import com.azz.colorskip.ui.components.InfoCard
import com.azz.colorskip.ui.components.LogRow
import com.azz.colorskip.ui.components.PageHeader
import com.azz.colorskip.ui.components.Pill
import com.azz.colorskip.ui.components.PrivacyNote
import com.azz.colorskip.ui.components.SectionTitle
import com.azz.colorskip.ui.components.StatusDot
import com.azz.colorskip.ui.components.StatusTile
import com.azz.colorskip.ui.components.StepNumber
import com.azz.colorskip.ui.components.relativeTime
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
fun DashboardScreen(
    vm: MainViewModel,
    permissions: PermissionSnapshot,
    onOpenKeepAlive: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val stats by vm.repository.stats.collectAsStateWithLifecycle()
    val logs by vm.repository.logs.collectAsStateWithLifecycle()
    val rules by vm.repository.rules.collectAsStateWithLifecycle()

    val enabledRules = rules.count { it.enabled }
    val today = stats.normalized().today

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "ColorOS 17 ready",
                title = "ColorSkip",
                subtitle = "本地规则跳过开屏广告，离线、无网络权限。",
                trailing = {
                    Surface(
                        color = Mint.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(100),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Mint.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDot(active = permissions.accessibilityConnected)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (permissions.accessibilityConnected) "在线" else "离线",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (permissions.accessibilityConnected) Mint else TextMuted,
                            )
                        }
                    }
                },
            )
        }

        item {
            HeroCard(
                today = today,
                total = stats.total,
                lastSkipAt = stats.lastSkipAt,
                connected = permissions.accessibilityConnected,
                accessibilityEnabled = permissions.accessibilityEnabled,
                onPrimary = {
                    if (permissions.accessibilityEnabled) {
                        vm.scanNow()
                    } else {
                        if (!KeepAliveHelper.openAccessibilitySettings(context)) {
                            context.toast("无法打开无障碍设置")
                        }
                    }
                },
                onSnapshot = { vm.captureSnapshot() },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusTile(
                    title = "无障碍",
                    value = when {
                        permissions.accessibilityConnected -> "已连接"
                        permissions.accessibilityEnabled -> "待连接"
                        else -> "未开启"
                    },
                    active = permissions.accessibilityEnabled,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!KeepAliveHelper.openAccessibilitySettings(context)) {
                            context.toast("无法打开无障碍设置")
                        }
                    },
                )
                StatusTile(
                    title = "悬浮窗",
                    value = if (permissions.overlay) "已授权" else "未授权",
                    active = permissions.overlay,
                    icon = Icons.Default.Settings,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val act = activity
                        if (act != null) KeepAliveHelper.openOverlaySettings(act)
                        else context.toast("请回到应用内操作")
                    },
                )
                StatusTile(
                    title = "生效规则",
                    value = "$enabledRules 条",
                    active = true,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenRules,
                )
            }
        }

        if (!permissions.accessibilityEnabled || !permissions.overlay) {
            item {
                SetupCard(
                    permissions = permissions,
                    onAccessibility = {
                        if (!KeepAliveHelper.openAccessibilitySettings(context)) {
                            context.toast("无法打开无障碍设置")
                        }
                    },
                    onOverlay = {
                        val act = activity
                        if (act != null) KeepAliveHelper.openOverlaySettings(act)
                        else context.toast("请回到应用内操作")
                    },
                    onKeepAlive = onOpenKeepAlive,
                )
            }
        }

        item {
            InfoCard(
                title = if (permissions.isColorOs) "ColorOS 17 保活" else "后台保活建议",
                body = if (permissions.ignoringBattery) {
                    "电池优化已放行。建议再到最近任务里下拉 ColorSkip 卡片并锁定，减少 ColorOS 清理无障碍服务的概率。"
                } else {
                    "ColorOS 会限制后台无障碍服务。请将电池用量设为「无限制」，允许自启动，并在最近任务中锁定 ColorSkip。"
                },
                icon = Icons.Default.Warning,
                accent = if (permissions.ignoringBattery) Mint else Amber,
                action = {
                    OutlinedButton(onClick = onOpenKeepAlive) {
                        Text("打开保活向导")
                    }
                },
            )
        }

        item {
            SectionTitle("最近跳过", "全部日志", onOpenLogs)
        }

        if (logs.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有跳过记录",
                    body = "开启无障碍后，打开一个带开屏广告的 App。命中规则时，这里会记录包名、节点文本和点击方式。",
                )
            }
        } else {
            items(logs.take(5), key = { it.id }) { log ->
                LogRow(log, compact = true)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceHigh.copy(alpha = 0.55f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("隐私优先", style = MaterialTheme.typography.titleMedium, color = TextMain)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "没有 INTERNET 权限。规则文件由你手动导入，跳过记录只保存在本机 SharedPreferences 中。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                }
            }
        }

        item { PrivacyNote() }
    }
}

@Composable
private fun HeroCard(
    today: Int,
    total: Int,
    lastSkipAt: Long,
    connected: Boolean,
    accessibilityEnabled: Boolean,
    onPrimary: () -> Unit,
    onSnapshot: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1D3824), Color(0xFF10251B), Color(0xFF0B1711)),
                    start = Offset.Zero,
                    end = Offset(1200f, 1000f),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Lime.copy(alpha = 0.09f),
                radius = size.width * 0.44f,
                center = Offset(size.width * 1.02f, size.height * -0.08f),
            )
            drawCircle(
                color = Mint.copy(alpha = 0.05f),
                radius = size.width * 0.27f,
                center = Offset(size.width * 0.94f, size.height * 0.9f),
            )
        }
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "今日已跳过",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted,
                )
                Pill(
                    when {
                        connected -> "守护中"
                        accessibilityEnabled -> "等待系统连接"
                        else -> "未开启"
                    },
                    if (connected) Lime else if (accessibilityEnabled) Amber else Danger,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$today",
                    style = MaterialTheme.typography.displayLarge,
                    color = TextMain,
                )
                Text(
                    " 次",
                    style = MaterialTheme.typography.titleLarge,
                    color = Lime,
                    modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "累计 $total 次 · ${relativeTime(lastSkipAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lime,
                        contentColor = Ink,
                    ),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text(if (accessibilityEnabled) "扫描一次" else "开启无障碍")
                }
                OutlinedButton(
                    onClick = onSnapshot,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text("抓取快照")
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    permissions: PermissionSnapshot,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onKeepAlive: () -> Unit,
) {
    Surface(
        color = SurfaceHigh,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "完成设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain,
                    modifier = Modifier.weight(1f),
                )
                Pill("首次使用", Lime)
            }
            Spacer(Modifier.height(14.dp))
            SetupStep(
                number = 1,
                title = "开启无障碍服务",
                body = "系统设置 → 无障碍 → 已下载的应用 → ColorSkip。",
                done = permissions.accessibilityEnabled,
                action = "去开启",
                onClick = onAccessibility,
            )
            Spacer(Modifier.height(12.dp))
            SetupStep(
                number = 2,
                title = "允许悬浮窗",
                body = "用于显示跳过结果，以及手动扫描悬浮球。",
                done = permissions.overlay,
                action = "去授权",
                onClick = onOverlay,
            )
            Spacer(Modifier.height(12.dp))
            SetupStep(
                number = 3,
                title = "ColorOS 保活",
                body = "电池无限制 + 自启动 + 最近任务锁定。",
                done = permissions.ignoringBattery,
                action = "去设置",
                onClick = onKeepAlive,
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        if (done) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Lime.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = MaterialTheme.typography.labelLarge, color = Lime)
            }
        } else {
            StepNumber(number, active = true)
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        if (done) {
            Text("已完成", style = MaterialTheme.typography.labelSmall, color = Mint)
        } else {
            TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(action, color = Lime, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
