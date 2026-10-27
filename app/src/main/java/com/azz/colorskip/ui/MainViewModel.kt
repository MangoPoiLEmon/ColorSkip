package com.azz.colorskip.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azz.colorskip.core.RuleParser
import com.azz.colorskip.core.ServiceBridge
import com.azz.colorskip.core.SkipRepository
import com.azz.colorskip.service.GuardService
import com.azz.colorskip.util.KeepAliveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class PermissionSnapshot(
    val accessibilityEnabled: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val ignoringBattery: Boolean = false,
    val recentsLocked: Boolean = false,
    val isColorOs: Boolean = false,
    val loaded: Boolean = false,
) {
    val allReady: Boolean
        get() = accessibilityEnabled && overlay && notifications && ignoringBattery
}

data class InstalledApp(val label: String, val packageName: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = SkipRepository.get(application)

    private val _permissions = MutableStateFlow(PermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _snapshot = MutableStateFlow<String?>(null)
    val snapshot: StateFlow<String?> = _snapshot.asStateFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    init {
        refreshPermissions()
        loadApps()
        viewModelScope.launch {
            while (isActive) {
                delay(1500)
                refreshPermissions()
            }
        }
    }

    fun refreshPermissions() {
        val status = KeepAliveHelper.readStatus(getApplication())
        _permissions.value = PermissionSnapshot(
            accessibilityEnabled = status.accessibilityEnabled,
            accessibilityConnected = status.accessibilityConnected,
            overlay = status.overlayGranted,
            notifications = status.notificationsGranted,
            ignoringBattery = status.ignoringBattery,
            recentsLocked = repository.settings.value.recentsLocked,
            isColorOs = status.isColorOs,
            loaded = true,
        )
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val (text, name) = withContext(Dispatchers.IO) { readText(uri) }
                importText(text, name ?: "规则文件")
            } catch (t: Throwable) {
                message("导入失败：${t.message ?: "无法读取文件"}")
            }
        }
    }

    fun importText(raw: String, label: String = "粘贴的规则") {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                RuleParser.parse(raw)
            }
            if (result.error != null) {
                message("${result.error}${result.warnings.firstOrNull()?.let { "\n$it" } ?: ""}")
                return@launch
            }
            val count = withContext(Dispatchers.IO) {
                repository.addImportedRules(result.rules)
            }
            val warnings = result.warnings.take(2).joinToString("\n")
            message(
                buildString {
                    append("已导入 $count 条 ${result.format} 规则")
                    if (warnings.isNotBlank()) append("\n").append(warnings)
                },
            )
        }
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { importFromUri(it) }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) {
                    importFromUri(stream)
                    return
                }
                val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
                if (shared.isNullOrBlank()) {
                    message("没有找到可导入的规则内容")
                } else if (shared.startsWith("{") || shared.startsWith("[")) {
                    importText(shared, "分享的 JSON")
                } else {
                    message("收到的是链接或文本：请用「从链接获取」在浏览器下载规则，再选择文件导入。")
                }
            }
        }
    }

    fun addCoordinateRule(
        name: String,
        packageName: String,
        xPercent: Float,
        yPercent: Float,
        delayMs: Long,
        requireAdContext: Boolean,
        keywords: List<String>,
    ): Boolean {
        if (packageName.isBlank()) {
            message("请先选择或填写包名")
            return false
        }
        if (xPercent !in 1f..99f || yPercent !in 1f..99f) {
            message("坐标百分比需在 1–99 之间")
            return false
        }
        repository.addCoordinateRule(
            name = name,
            packageName = packageName,
            xPercent = xPercent / 100f,
            yPercent = yPercent / 100f,
            delayMs = delayMs,
            requireAdContext = requireAdContext,
            keywords = keywords,
        )
        message("坐标规则已保存")
        return true
    }

    fun deleteRule(id: String) {
        repository.deleteRule(id)
        message("已删除导入规则")
    }

    fun clearImportedRules() {
        repository.clearImportedRules()
        message("已清空所有导入规则")
    }

    fun setOverlayEnabled(enabled: Boolean) {
        repository.setOverlayEnabled(enabled)
        ServiceBridge.refreshOverlay()
    }

    fun setBubbleEnabled(enabled: Boolean) {
        repository.setBubbleEnabled(enabled)
        ServiceBridge.refreshOverlay()
    }

    fun setGuardEnabled(enabled: Boolean) {
        repository.setGuardEnabled(enabled)
        if (enabled) GuardService.start(getApplication()) else GuardService.stop(getApplication())
    }

    fun setRecentsLocked(locked: Boolean) {
        repository.setRecentsLocked(locked)
        refreshPermissions()
        message(if (locked) "已记录：最近任务已锁定" else "已取消最近任务锁定确认")
    }

    fun captureSnapshot() {
        _snapshot.value = ServiceBridge.captureSnapshot()
            ?: "无障碍服务未连接。请先开启 ColorSkip 的无障碍权限，并保持目标 App 在前台。"
    }

    fun dismissSnapshot() {
        _snapshot.value = null
    }

    fun scanNow() {
        if (!ServiceBridge.scanNow()) {
            message("无障碍服务未连接，请先在系统设置中开启")
        }
    }

    fun openRecents() {
        if (!ServiceBridge.openRecents()) {
            message("无法打开最近任务：无障碍服务未连接")
        } else {
            message("已打开最近任务：把 ColorSkip 卡片向下拉，选择「锁定」")
        }
    }

    fun clearLogs() {
        repository.clearLogs()
        message("日志已清空")
    }

    fun resetStats() {
        repository.resetStats()
        message("统计与日志已重置")
    }

    fun exportLogs(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use { it.write(repository.exportLogsText()) }
                    true
                }.getOrDefault(false)
            }
            message(if (ok) "日志已导出" else "导出失败，请重试")
        }
    }

    fun exportRules(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use { it.write(repository.exportUserRulesJson()) }
                    true
                }.getOrDefault(false)
            }
            message(if (ok) "规则已导出" else "导出失败，请重试")
        }
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = runCatching {
                pm.queryIntentActivities(intent, 0)
                    .mapNotNull { info ->
                        val activity = info.activityInfo ?: return@mapNotNull null
                        val pkg = activity.packageName ?: return@mapNotNull null
                        if (pkg == getApplication<Application>().packageName) return@mapNotNull null
                        InstalledApp(info.loadLabel(pm).toString(), pkg)
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
            _apps.value = list
        }
    }

    private fun readText(uri: Uri): Pair<String, String?> {
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) throw IllegalArgumentException("文件超过 2 MB")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw IllegalArgumentException("无法打开文件")

        var text = bytes.toString(Charsets.UTF_8)
        if (text.startsWith('\uFEFF')) text = text.substring(1)
        return text to name
    }

    private fun message(text: String) {
        messageChannel.trySend(text)
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 2_000_000
    }
}
