package com.azz.colorskip.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azz.colorskip.core.RuleSource
import com.azz.colorskip.ui.MainViewModel
import com.azz.colorskip.ui.components.ConfirmDialog
import com.azz.colorskip.ui.components.CoordinateRuleDialog
import com.azz.colorskip.ui.components.EmptyState
import com.azz.colorskip.ui.components.InfoCard
import com.azz.colorskip.ui.components.LinkImportDialog
import com.azz.colorskip.ui.components.PageHeader
import com.azz.colorskip.ui.components.Pill
import com.azz.colorskip.ui.components.PrivacyNote
import com.azz.colorskip.ui.components.RuleRow
import com.azz.colorskip.ui.components.SectionTitle
import com.azz.colorskip.ui.theme.Lime
import com.azz.colorskip.ui.theme.TextMuted

@Composable
fun RulesScreen(vm: MainViewModel) {
    val rules by vm.repository.rules.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var showLink by rememberSaveable { mutableStateOf(false) }
    var showCoordinate by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.importFromUri(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) vm.exportRules(uri)
    }

    val imported = remember(rules) { rules.filter { it.source != RuleSource.BUILTIN } }
    val builtin = remember(rules) { rules.filter { it.source == RuleSource.BUILTIN } }

    if (showLink) {
        LinkImportDialog(onDismiss = { showLink = false })
    }
    if (showCoordinate) {
        CoordinateRuleDialog(
            apps = apps,
            onDismiss = { showCoordinate = false },
            onSave = { name, pkg, x, y, delay, requireContext, keywords ->
                vm.addCoordinateRule(name, pkg, x, y, delay, requireContext, keywords)
            },
        )
    }
    if (showClear) {
        ConfirmDialog(
            title = "清空导入规则？",
            body = "内置规则不受影响。已导入的 JSON/YAML 和坐标规则会从本机删除。",
            confirmText = "清空",
            danger = true,
            onConfirm = { vm.clearImportedRules() },
            onDismiss = { showClear = false },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "Rule engine",
                title = "规则",
                subtitle = "本地 JSON / YAML 规则，支持节点文本、控件 ID 与坐标盲点。",
                trailing = { Pill("${rules.count { it.enabled }} 条生效", Lime) },
            )
        }

        item {
            Button(
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/json",
                            "application/yaml",
                            "application/x-yaml",
                            "text/yaml",
                            "text/plain",
                            "application/octet-stream",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("导入 JSON / YAML 规则")
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showLink = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("从链接获取") }
                OutlinedButton(
                    onClick = { showCoordinate = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("添加坐标规则") }
            }
        }

        item {
            InfoCard(
                title = "订阅但不联网",
                body = "真正的自动订阅需要网络权限。为满足「零 INTERNET」约束，ColorSkip 将远程规则设计为：链接交给系统浏览器下载，再由你导入本地文件；也支持浏览器分享文件给 ColorSkip。",
                icon = Icons.Default.ArrowForward,
                action = {
                    TextButton(onClick = { showLink = true }) {
                        Text("查看导入方式", color = Lime)
                    }
                },
            )
        }

        item {
            SectionTitle(
                "导入 / 自定义",
                if (imported.isNotEmpty()) "清空" else null,
                onAction = { showClear = true },
            )
        }

        if (imported.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有导入规则",
                    body = "导入一个 JSON/YAML 文件，或为 Flutter/游戏应用添加坐标盲点规则。",
                    icon = Icons.Default.Add,
                )
            }
        } else {
            items(imported, key = { it.id }) { rule ->
                RuleRow(rule, onDelete = { vm.deleteRule(rule.id) })
            }
        }

        item {
            SectionTitle("内置基础规则", "${builtin.size} 条")
        }

        items(builtin, key = { it.id }) { rule ->
            RuleRow(rule)
        }

        item {
            Text(
                "规则格式：rules[] 中每项支持 keywords / viewIds / contentDescriptions / packages / excludeKeywords；坐标规则使用 clickMode=coordinate 与 xPercent、yPercent（0–1 或 0–100）。",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }

        item { PrivacyNote() }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("colorskip-rules.json") },
                    modifier = Modifier.weight(1f),
                ) { Text("导出我的规则") }
            }
        }
    }
}
