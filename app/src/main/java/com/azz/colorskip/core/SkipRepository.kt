package com.azz.colorskip.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SkipRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    private val builtinRules: List<SkipRule> = loadBuiltinRules()
    private var importedRules: List<SkipRule> = loadImportedRules()

    private val _rules = MutableStateFlow(combineRules())
    val rules: StateFlow<List<SkipRule>> = _rules.asStateFlow()

    private val _logs = MutableStateFlow(loadLogs())
    val logs: StateFlow<List<SkipLog>> = _logs.asStateFlow()

    private val _stats = MutableStateFlow(loadStats().normalized())
    val stats: StateFlow<SkipStats> = _stats.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        // Persist a rolled-over day so a long-running process does not show
        // yesterday's count after midnight.
        persistStats(_stats.value)
    }

    fun addImportedRules(newRules: List<SkipRule>): Int {
        synchronized(lock) {
            val builtinIds = builtinRules.mapTo(HashSet()) { it.id }
            val existing = importedRules.associateByTo(LinkedHashMap()) { it.id }
            var changed = 0
            for (rule in newRules) {
                var id = rule.id.ifBlank { "imp.${System.currentTimeMillis()}.${changed}" }
                if (id.startsWith("builtin.") || id in builtinIds) id = "imp.$id"
                id = id.take(120)
                var unique = id
                var suffix = 2
                while (unique in existing && existing[unique]?.name != rule.name) {
                    unique = "${id.take(112)}.$suffix"
                    suffix++
                }
                existing[unique] = rule.copy(
                    id = unique,
                    source = if (rule.source == RuleSource.USER) RuleSource.USER else RuleSource.IMPORTED,
                    enabled = true,
                )
                changed++
            }
            importedRules = existing.values.toList().takeLast(MAX_IMPORTED_RULES)
            saveImportedRules(importedRules)
            _rules.value = combineRules()
            return changed
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
    ): SkipRule {
        val rule = SkipRule(
            id = "user.coord.${System.currentTimeMillis()}",
            name = name.ifBlank { "坐标规则" },
            packages = listOf(packageName.trim()),
            keywords = keywords,
            clickMode = ClickMode.COORDINATE,
            xPercent = xPercent.coerceIn(0.01f, 0.99f),
            yPercent = yPercent.coerceIn(0.01f, 0.99f),
            delayMs = delayMs.coerceIn(0L, 12_000L),
            cooldownMs = 5000L,
            priority = 200,
            requireAdContext = requireAdContext,
            source = RuleSource.USER,
        )
        synchronized(lock) {
            importedRules = importedRules + rule
            saveImportedRules(importedRules)
            _rules.value = combineRules()
        }
        return rule
    }

    fun deleteRule(id: String) {
        synchronized(lock) {
            val next = importedRules.filterNot { it.id == id }
            if (next.size != importedRules.size) {
                importedRules = next
                saveImportedRules(importedRules)
                _rules.value = combineRules()
            }
        }
    }

    fun clearImportedRules() {
        synchronized(lock) {
            importedRules = emptyList()
            saveImportedRules(importedRules)
            _rules.value = combineRules()
        }
    }

    fun recordSkip(log: SkipLog) {
        synchronized(lock) {
            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            val old = _stats.value.normalized()
            val total = old.total + 1
            val todayCount = if (old.dayKey == today) old.today + 1 else 1
            val stats = SkipStats(
                total = total,
                today = todayCount,
                dayKey = today,
                lastSkipAt = log.timestamp,
            )
            val logs = (listOf(log) + _logs.value).take(MAX_LOGS)
            _stats.value = stats
            _logs.value = logs
            persistStats(stats)
            persistLogs(logs)
        }
    }

    fun clearLogs() {
        synchronized(lock) {
            _logs.value = emptyList()
            persistLogs(emptyList())
        }
    }

    fun resetStats() {
        synchronized(lock) {
            val stats = SkipStats(dayKey = LocalDate.now(ZoneId.systemDefault()).toString())
            _stats.value = stats
            _logs.value = emptyList()
            persistStats(stats)
            persistLogs(emptyList())
        }
    }

    fun setOverlayEnabled(enabled: Boolean) = updateSettings { it.copy(overlayEnabled = enabled) }
    fun setBubbleEnabled(enabled: Boolean) = updateSettings { it.copy(bubbleEnabled = enabled) }
    fun setGuardEnabled(enabled: Boolean) = updateSettings { it.copy(guardEnabled = enabled) }
    fun setRecentsLocked(locked: Boolean) = updateSettings { it.copy(recentsLocked = locked) }

    fun exportLogsText(): String {
        val stats = _stats.value.normalized()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val sb = StringBuilder()
        sb.appendLine("ColorSkip 跳过日志")
        sb.appendLine("导出时间: ${formatter.format(Instant.now())}")
        sb.appendLine("累计跳过: ${stats.total}  今日: ${stats.today}")
        sb.appendLine("说明: 全部数据来自本机，应用没有 INTERNET 权限。")
        sb.appendLine()
        for (log in _logs.value) {
            sb.appendLine("[${formatter.format(Instant.ofEpochMilli(log.timestamp))}] ${log.appLabel} (${log.packageName})")
            sb.appendLine("  规则: ${log.ruleName} [${log.ruleId}]")
            sb.appendLine("  方式: ${log.method.label}  匹配: ${log.matchedBy}")
            sb.appendLine("  文本: ${log.nodeText}")
            log.viewId?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  viewId: $it") }
            log.activity?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  activity: $it") }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun exportUserRulesJson(): String = RuleParser.toJson(importedRules)

    fun availableRuleCount(): Int = _rules.value.count { it.enabled }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_OVERLAY, next.overlayEnabled)
            .putBoolean(KEY_BUBBLE, next.bubbleEnabled)
            .putBoolean(KEY_GUARD, next.guardEnabled)
            .putBoolean(KEY_RECENTS_LOCKED, next.recentsLocked)
            .apply()
    }

    private fun combineRules(): List<SkipRule> {
        val map = LinkedHashMap<String, SkipRule>()
        builtinRules.forEach { map[it.id] = it }
        importedRules.forEach { map[it.id] = it }
        return map.values.sortedWith(
            compareByDescending<SkipRule> { it.priority }.thenBy { it.name },
        )
    }

    private fun loadBuiltinRules(): List<SkipRule> {
        return runCatching {
            val raw = appContext.assets.open("rules/builtin_rules.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            RuleParser.parse(raw, RuleSource.BUILTIN).rules.map { it.copy(source = RuleSource.BUILTIN) }
        }.getOrDefault(emptyList())
    }

    private fun loadImportedRules(): List<SkipRule> {
        val raw = prefs.getString(KEY_IMPORTED_RULES, null) ?: return emptyList()
        return runCatching {
            RuleParser.parse(raw, RuleSource.IMPORTED).rules.map { it.copy(source = RuleSource.IMPORTED) }
        }.getOrDefault(emptyList())
    }

    private fun saveImportedRules(rules: List<SkipRule>) {
        prefs.edit().putString(KEY_IMPORTED_RULES, RuleParser.toJson(rules)).apply()
    }

    private fun loadStats(): SkipStats {
        val raw = prefs.getString(KEY_STATS, null) ?: return SkipStats(
            dayKey = LocalDate.now(ZoneId.systemDefault()).toString(),
        )
        return runCatching {
            val obj = JSONObject(raw)
            SkipStats(
                total = obj.optInt("total", 0).coerceAtLeast(0),
                today = obj.optInt("today", 0).coerceAtLeast(0),
                dayKey = obj.optString("dayKey"),
                lastSkipAt = obj.optLong("lastSkipAt", 0L),
            )
        }.getOrDefault(SkipStats(dayKey = LocalDate.now(ZoneId.systemDefault()).toString()))
    }

    private fun persistStats(stats: SkipStats) {
        val obj = JSONObject()
            .put("total", stats.total)
            .put("today", stats.today)
            .put("dayKey", stats.dayKey)
            .put("lastSkipAt", stats.lastSkipAt)
        prefs.edit().putString(KEY_STATS, obj.toString()).apply()
    }

    private fun loadLogs(): List<SkipLog> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val method = runCatching {
                        ClickMethod.valueOf(obj.optString("method"))
                    }.getOrDefault(ClickMethod.NODE_CLICK)
                    add(
                        SkipLog(
                            id = obj.optString("id", "log-$i"),
                            timestamp = obj.optLong("timestamp", 0L),
                            packageName = obj.optString("packageName"),
                            appLabel = obj.optString("appLabel").ifBlank { obj.optString("packageName") },
                            activity = obj.optString("activity").takeIf { it.isNotBlank() },
                            ruleId = obj.optString("ruleId"),
                            ruleName = obj.optString("ruleName"),
                            nodeText = obj.optString("nodeText"),
                            viewId = obj.optString("viewId").takeIf { it.isNotBlank() },
                            matchedBy = obj.optString("matchedBy"),
                            method = method,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistLogs(logs: List<SkipLog>) {
        val arr = JSONArray()
        for (log in logs) {
            arr.put(
                JSONObject()
                    .put("id", log.id)
                    .put("timestamp", log.timestamp)
                    .put("packageName", log.packageName)
                    .put("appLabel", log.appLabel)
                    .put("activity", log.activity ?: "")
                    .put("ruleId", log.ruleId)
                    .put("ruleName", log.ruleName)
                    .put("nodeText", log.nodeText)
                    .put("viewId", log.viewId ?: "")
                    .put("matchedBy", log.matchedBy)
                    .put("method", log.method.name),
            )
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    private fun loadSettings(): AppSettings = AppSettings(
        overlayEnabled = prefs.getBoolean(KEY_OVERLAY, true),
        bubbleEnabled = prefs.getBoolean(KEY_BUBBLE, true),
        guardEnabled = prefs.getBoolean(KEY_GUARD, true),
        recentsLocked = prefs.getBoolean(KEY_RECENTS_LOCKED, false),
    )

    companion object {
        private const val PREFS = "colorskip_prefs"
        private const val KEY_IMPORTED_RULES = "imported_rules_v1"
        private const val KEY_LOGS = "logs_v1"
        private const val KEY_STATS = "stats_v1"
        private const val KEY_OVERLAY = "overlay_enabled"
        private const val KEY_BUBBLE = "bubble_enabled"
        private const val KEY_GUARD = "guard_enabled"
        private const val KEY_RECENTS_LOCKED = "recents_locked_confirmed"
        private const val MAX_LOGS = 200
        private const val MAX_IMPORTED_RULES = 300

        @Volatile
        private var instance: SkipRepository? = null

        fun get(context: Context): SkipRepository {
            return instance ?: synchronized(this) {
                instance ?: SkipRepository(context).also { instance = it }
            }
        }
    }
}
