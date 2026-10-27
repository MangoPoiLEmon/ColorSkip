package com.azz.colorskip.core

import java.time.LocalDate
import java.time.ZoneId

enum class ClickMode { NODE, COORDINATE }

enum class RuleSource { BUILTIN, IMPORTED, USER }

enum class ClickMethod(val label: String) {
    NODE_CLICK("节点点击"),
    GESTURE("手势点击"),
    COORDINATE("坐标盲点"),
}

data class SkipRule(
    val id: String,
    val name: String,
    val packages: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val viewIds: List<String> = emptyList(),
    val contentDescriptions: List<String> = emptyList(),
    val clickMode: ClickMode = ClickMode.NODE,
    val xPercent: Float? = null,
    val yPercent: Float? = null,
    val delayMs: Long = 0L,
    val cooldownMs: Long = 2500L,
    val priority: Int = 0,
    val requireAdContext: Boolean = false,
    val enabled: Boolean = true,
    val source: RuleSource = RuleSource.BUILTIN,
) {
    val isCoordinate: Boolean
        get() = clickMode == ClickMode.COORDINATE && xPercent != null && yPercent != null

    fun matchesPackage(packageName: String): Boolean =
        packages.isEmpty() || packages.any { Glob.matches(it, packageName) }

    fun hasNodeMatcher(): Boolean =
        keywords.isNotEmpty() || viewIds.isNotEmpty() || contentDescriptions.isNotEmpty()
}

data class SkipLog(
    val id: String,
    val timestamp: Long,
    val packageName: String,
    val appLabel: String,
    val activity: String?,
    val ruleId: String,
    val ruleName: String,
    val nodeText: String,
    val viewId: String?,
    val matchedBy: String,
    val method: ClickMethod,
)

data class SkipStats(
    val total: Int = 0,
    val today: Int = 0,
    val dayKey: String = "",
    val lastSkipAt: Long = 0L,
) {
    fun normalized(): SkipStats {
        val todayKey = LocalDate.now(ZoneId.systemDefault()).toString()
        return if (dayKey == todayKey) this else copy(today = 0, dayKey = todayKey)
    }
}

data class AppSettings(
    val overlayEnabled: Boolean = true,
    val bubbleEnabled: Boolean = true,
    val guardEnabled: Boolean = true,
    /**
     * Android/ColorOS exposes no API to query the recents "lock" state.
     * This flag is the user's explicit confirmation after performing the
     * gesture, so the onboarding checklist can show a truthful check mark.
     */
    val recentsLocked: Boolean = false,
)

object Glob {
    fun matches(pattern: String, value: String): Boolean {
        val p = pattern.trim()
        if (p.isEmpty()) return false
        if (p == "*") return true
        val sb = StringBuilder("^")
        for (ch in p) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> sb.append(Regex.escape(ch.toString()))
            }
        }
        sb.append('$')
        return runCatching { Regex(sb.toString(), RegexOption.IGNORE_CASE).matches(value) }
            .getOrDefault(false)
    }
}

object PackageBlocklist {
    /**
     * Never automate security / permission / system UI surfaces. A blind tap on
     * a ColorOS permission dialog is much worse than a missed ad.
     */
    private val blocked = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.coloros.oppoguardelf",
        "com.oplus.battery",
        "com.oplus.securitypermission",
        "com.oplusos.securitypermission",
        "com.coloros.securitypermission",
        "com.oplus.notificationmanager",
        "com.coloros.notificationmanager",
        "com.oplus.trafficmonitor",
        "com.coloros.trafficmonitor",
        "com.oplus.appdetail",
        "com.coloros.appdetail",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.azz.colorskip",
    )

    private val systemSurfaceTokens = listOf("launcher", "recents", "quickstep")

    fun isBlocked(packageName: String): Boolean {
        if (packageName in blocked) return true
        // Launchers/recents differ by ROM (ColorOS, OPlus, Pixel, third-party).
        // Never automate a task switcher: a mistaken click there can reopen
        // ColorSkip itself. This is intentionally broader than a package list.
        val lower = packageName.lowercase()
        return systemSurfaceTokens.any { lower.contains(it) }
    }
}
