package com.azz.colorskip.core

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Tolerant parser for ColorSkip's own JSON/YAML rule format. It deliberately
 * does not deserialize arbitrary classes: YAML goes through SafeConstructor,
 * then both formats are normalized to org.json objects.
 */
object RuleParser {
    private const val MAX_RULES = 300
    private const val MAX_KEYWORDS = 24
    private const val MAX_KEYWORD_LENGTH = 48

    data class ParseResult(
        val rules: List<SkipRule>,
        val warnings: List<String>,
        val format: String,
        val error: String? = null,
    )

    fun parse(raw: String, source: RuleSource = RuleSource.IMPORTED): ParseResult {
        if (raw.isBlank()) {
            return ParseResult(emptyList(), emptyList(), "未知", "规则文件是空的")
        }
        if (raw.length > 2_000_000) {
            return ParseResult(emptyList(), emptyList(), "未知", "规则文件超过 2 MB，已拒绝")
        }

        val trimmed = raw.trimStart()
        val looksJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        val warnings = mutableListOf<String>()
        var format = if (looksJson) "JSON" else "YAML"
        var root: Any? = null
        var lastError: String? = null

        if (looksJson) {
            runCatching { JSONTokener(raw).nextValue() }
                .onSuccess { root = it }
                .onFailure { lastError = it.message }
        }
        if (root == null) {
            runCatching {
                val options = LoaderOptions().apply {
                    maxAliasesForCollections = 50
                    codePointLimit = 2_000_000
                    isAllowDuplicateKeys = false
                }
                Yaml(SafeConstructor(options)).load<Any?>(raw)
            }.onSuccess {
                root = it
                format = "YAML"
            }.onFailure {
                lastError = it.message
            }
        }

        if (root == null) {
            return ParseResult(
                emptyList(),
                warnings,
                format,
                "无法解析规则文件：${lastError ?: "格式不正确"}",
            )
        }

        val array = try {
            normalizeRoot(root)
        } catch (t: Throwable) {
            return ParseResult(emptyList(), warnings, format, "规则结构不正确：${t.message}")
        }

        if (array == null) {
            return ParseResult(
                emptyList(),
                warnings,
                format,
                "找不到 rules 数组，或这不是一条有效规则",
            )
        }
        if (array.length() > MAX_RULES) {
            return ParseResult(emptyList(), warnings, format, "单次最多导入 $MAX_RULES 条规则")
        }

        val rules = mutableListOf<SkipRule>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
            if (obj == null) {
                warnings += "第 ${i + 1} 条不是对象，已跳过"
                continue
            }
            try {
                val rule = parseRule(obj, i, source, warnings)
                if (rule != null) rules += rule
            } catch (t: Throwable) {
                warnings += "第 ${i + 1} 条规则解析失败：${t.message ?: "未知错误"}"
            }
        }

        if (rules.isEmpty()) {
            return ParseResult(
                emptyList(),
                warnings.take(20),
                format,
                "没有可用规则。节点规则需要 keywords / viewIds / contentDescriptions，坐标规则需要 xPercent 与 yPercent。",
            )
        }
        return ParseResult(rules, warnings.take(20), format)
    }

    private fun normalizeRoot(root: Any?): JSONArray? {
        val obj: JSONObject = when (root) {
            is JSONObject -> root
            is JSONArray -> return root
            is Map<*, *> -> mapToJson(root)
            is List<*> -> return JSONArray().apply { root.forEach { put(toJsonValue(it)) } }
            else -> return null
        }

        obj.optJSONArray("rules")?.let { return it }
        obj.optJSONArray("items")?.let { return it }
        obj.optJSONObject("data")?.let { data ->
            data.optJSONArray("rules")?.let { return it }
        }
        if (obj.has("keywords") || obj.has("viewIds") || obj.has("packages") ||
            obj.has("packageNames") || obj.has("clickMode") || obj.has("coordinates")
        ) {
            return JSONArray().put(obj)
        }
        return null
    }

    private fun mapToJson(map: Map<*, *>): JSONObject = JSONObject().apply {
        for ((key, value) in map) {
            put(key?.toString() ?: "null", toJsonValue(value))
        }
    }

    private fun toJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> mapToJson(value)
        is List<*> -> JSONArray().apply { value.forEach { put(toJsonValue(it)) } }
        is String, is Number, is Boolean -> value
        else -> value.toString()
    }

    private fun parseRule(
        obj: JSONObject,
        index: Int,
        source: RuleSource,
        warnings: MutableList<String>,
    ): SkipRule? {
        val name = obj.optString("name").ifBlank { "导入规则 ${index + 1}" }
        val packages = stringList(obj, "packages", "packageNames", "package")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(50)
        val keywords = stringList(obj, "keywords", "texts", "text")
            .map { it.trim().take(MAX_KEYWORD_LENGTH) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_KEYWORDS)
        val excludeKeywords = stringList(obj, "excludeKeywords", "excludeTexts")
            .map { it.trim().take(MAX_KEYWORD_LENGTH) }
            .filter { it.isNotEmpty() }
            .take(MAX_KEYWORDS)
        val viewIds = stringList(obj, "viewIds", "viewId", "ids")
            .map { it.trim().take(120) }
            .filter { it.isNotEmpty() }
            .take(MAX_KEYWORDS)
        val contentDescriptions = stringList(obj, "contentDescriptions", "descriptions")
            .map { it.trim().take(MAX_KEYWORD_LENGTH) }
            .filter { it.isNotEmpty() }
            .take(MAX_KEYWORDS)

        val action = obj.optString("clickMode", obj.optString("action", "node"))
            .lowercase()
        val clickMode = when (action) {
            "coordinate", "tap", "position", "point", "blind" -> ClickMode.COORDINATE
            else -> ClickMode.NODE
        }

        val coords = readCoordinates(obj)
        if (clickMode == ClickMode.COORDINATE) {
            if (packages.isEmpty()) {
                warnings += "「$name」是坐标规则但没有 packages，已跳过"
                return null
            }
            if (coords == null) {
                warnings += "「$name」缺少有效的 xPercent / yPercent，已跳过"
                return null
            }
        }

        if (clickMode == ClickMode.NODE && keywords.isEmpty() && viewIds.isEmpty() && contentDescriptions.isEmpty()) {
            warnings += "「$name」没有任何节点匹配条件，已跳过"
            return null
        }

        val id = obj.optString("id").ifBlank {
            "import.${source.name.lowercase()}.${name.hashCode().toUInt().toString(16)}.$index"
        }.take(120)

        return SkipRule(
            id = id,
            name = name.take(80),
            packages = packages,
            keywords = keywords,
            excludeKeywords = excludeKeywords,
            viewIds = viewIds,
            contentDescriptions = contentDescriptions,
            clickMode = clickMode,
            xPercent = coords?.first,
            yPercent = coords?.second,
            delayMs = obj.optLong("delayMs", 0L).coerceIn(0L, 12_000L),
            cooldownMs = obj.optLong("cooldownMs", 2500L).coerceIn(300L, 60_000L),
            priority = obj.optInt("priority", 0).coerceIn(-1000, 1000),
            requireAdContext = obj.optBoolean(
                "requireAdContext",
                clickMode == ClickMode.COORDINATE,
            ),
            enabled = obj.optBoolean("enabled", true),
            source = source,
        )
    }

    private fun readCoordinates(obj: JSONObject): Pair<Float, Float>? {
        var x: Double? = null
        var y: Double? = null

        fun readNumber(key: String): Double? = when (val v = obj.opt(key)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }

        x = readNumber("xPercent")
        y = readNumber("yPercent")
        if (x == null || y == null) {
            x = readNumber("x")
            y = readNumber("y")
        }
        obj.optJSONObject("position")?.let { pos ->
            if (x == null) x = pos.optDouble("x", Double.NaN).takeUnless { it.isNaN() }
            if (y == null) y = pos.optDouble("y", Double.NaN).takeUnless { it.isNaN() }
        }
        obj.optJSONArray("coordinates")?.let { arr ->
            if (arr.length() >= 2) {
                if (x == null) x = arr.optDouble(0, Double.NaN).takeUnless { it.isNaN() }
                if (y == null) y = arr.optDouble(1, Double.NaN).takeUnless { it.isNaN() }
            }
        }
        if (x == null || y == null) return null

        fun normalize(v: Double): Float {
            val percent = if (v in 0.0..1.0) v * 100.0 else v
            return (percent / 100.0).toFloat().coerceIn(0f, 1f)
        }
        val nx = normalize(x!!)
        val ny = normalize(y!!)
        if (nx <= 0f || nx >= 1f || ny <= 0f || ny >= 1f) return null
        return nx to ny
    }

    private fun stringList(obj: JSONObject, vararg keys: String): List<String> {
        val out = mutableListOf<String>()
        for (key in keys) {
            when (val value = obj.opt(key)) {
                is JSONArray -> for (i in 0 until value.length()) {
                    val s = value.optString(i).trim()
                    if (s.isNotEmpty()) out += s
                }
                is String -> {
                    val s = value.trim()
                    if (s.isNotEmpty()) out += s.split(',', '\n').map { it.trim() }
                }
                is Collection<*> -> value.forEach { item ->
                    item?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
                }
            }
        }
        return out
    }

    fun toJson(rules: List<SkipRule>, includeDisabled: Boolean = true): String {
        val array = JSONArray()
        for (rule in rules) {
            if (!includeDisabled && !rule.enabled) continue
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("name", rule.name)
            if (rule.packages.isNotEmpty()) obj.put("packages", JSONArray(rule.packages))
            if (rule.keywords.isNotEmpty()) obj.put("keywords", JSONArray(rule.keywords))
            if (rule.excludeKeywords.isNotEmpty()) obj.put("excludeKeywords", JSONArray(rule.excludeKeywords))
            if (rule.viewIds.isNotEmpty()) obj.put("viewIds", JSONArray(rule.viewIds))
            if (rule.contentDescriptions.isNotEmpty()) {
                obj.put("contentDescriptions", JSONArray(rule.contentDescriptions))
            }
            obj.put("clickMode", if (rule.isCoordinate) "coordinate" else "node")
            rule.xPercent?.let { obj.put("xPercent", it.toDouble()) }
            rule.yPercent?.let { obj.put("yPercent", it.toDouble()) }
            obj.put("delayMs", rule.delayMs)
            obj.put("cooldownMs", rule.cooldownMs)
            obj.put("priority", rule.priority)
            obj.put("requireAdContext", rule.requireAdContext)
            obj.put("enabled", rule.enabled)
            array.put(obj)
        }
        return JSONObject()
            .put("schema", "colorskip.rules/1")
            .put("name", "ColorSkip 导出规则")
            .put("version", 1)
            .put("rules", array)
            .toString(2)
    }
}
