package com.azz.colorskip.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azz.colorskip.ui.MainViewModel
import com.azz.colorskip.ui.components.ConfirmDialog
import com.azz.colorskip.ui.components.EmptyState
import com.azz.colorskip.ui.components.InfoCard
import com.azz.colorskip.ui.components.LogRow
import com.azz.colorskip.ui.components.PageHeader
import com.azz.colorskip.ui.components.Pill
import com.azz.colorskip.ui.components.PrivacyNote
import com.azz.colorskip.ui.theme.Lime
import com.azz.colorskip.ui.theme.Mint

@Composable
fun LogsScreen(vm: MainViewModel) {
    val logs by vm.repository.logs.collectAsStateWithLifecycle()
    val stats by vm.repository.stats.collectAsStateWithLifecycle()
    var showClear by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) vm.exportLogs(uri)
    }

    if (showClear) {
        ConfirmDialog(
            title = "清空跳过日志？",
            body = "只删除本机日志，累计统计不受影响。此操作无法撤销。",
            confirmText = "清空",
            danger = true,
            onConfirm = { vm.clearLogs() },
            onDismiss = { showClear = false },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "Diagnostics",
                title = "日志与快照",
                subtitle = "记录包名、节点文本和点击方式；快照只在本机生成。",
                trailing = { Pill("${logs.size} 条", Mint) },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.captureSnapshot() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                ) { Text("抓取快照") }
                OutlinedButton(
                    onClick = { vm.scanNow() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                ) { Text("手动扫描") }
            }
        }

        item {
            InfoCard(
                title = "为什么有些广告没跳过？",
                body = "先打开目标 App 并停在广告页，再点「抓取快照」。查看节点树里是否有 text=跳过、contentDescription 或 skip 控件 ID；如果整棵树为空（Flutter/游戏引擎），在规则页添加坐标盲点。",
                icon = Icons.Default.Info,
                accent = Mint,
            )
        }

        item {
            if (logs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "累计 ${stats.total} 次 · 今日 ${stats.normalized().today} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = com.azz.colorskip.ui.theme.TextMuted,
                    )
                    Text(
                        "导出",
                        style = MaterialTheme.typography.labelLarge,
                        color = Lime,
                        modifier = Modifier.clickable { exportLauncher.launch("colorskip-logs.txt") },
                    )
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                EmptyState(
                    title = "日志是空的",
                    body = "成功跳过时才会写入日志。打开目标 App 后回来，或先点「抓取快照」排查节点。",
                    icon = Icons.Default.Warning,
                )
            }
        } else {
            items(logs, key = { it.id }) { log ->
                LogRow(log)
            }
            item {
                Text(
                    "点「清空」删除全部日志",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.azz.colorskip.ui.theme.Danger.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClear = true }
                        .padding(vertical = 10.dp),
                )
            }
        }

        item { PrivacyNote() }
    }
}
