package com.azz.colorskip.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.azz.colorskip.core.ClickMethod
import com.azz.colorskip.core.PackageBlocklist
import com.azz.colorskip.core.RuleEngine
import com.azz.colorskip.core.ServiceBridge
import com.azz.colorskip.core.SkipLog
import com.azz.colorskip.core.SkipRepository
import com.azz.colorskip.core.SkipRule
import com.azz.colorskip.overlay.OverlayController
import com.azz.colorskip.util.NotificationHelper
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * System-bound accessibility service.
 *
 * Design constraints:
 *  - No INTERNET permission, no shell, no root.
 *  - Only the active window is inspected.
 *  - A short debounce plus per-rule cooldown prevents click loops.
 *  - Security/permission/system-UI packages are hard-blocked.
 */
class AdSkipService : AccessibilityService() {

    private lateinit var repository: SkipRepository
    private lateinit var ruleEngine: RuleEngine
    private lateinit var overlay: OverlayController
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logCounter = AtomicLong(0)

    private var scanScheduled = false
    private var lastScanAt = 0L
    private var lastSuccessAt = 0L
    private var pendingPackage = ""
    private var pendingActivity: String? = null
    private val actionCooldowns = HashMap<String, Long>()

    /**
     * Some ColorOS builds use a launcher package that does not contain
     * "launcher"/"recents" in its name. Resolve all HOME handlers as well.
     */
    private val homePackages: Set<String> by lazy {
        runCatching {
            val pm = packageManager
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            buildSet {
                pm.queryIntentActivities(home, 0).forEach { info ->
                    info.activityInfo?.packageName?.let { add(it) }
                }
                pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName?.let { add(it) }
            }
        }.getOrDefault(emptySet())
    }

    override fun onCreate() {
        super.onCreate()
        repository = SkipRepository.get(this)
        ruleEngine = RuleEngine { repository.rules.value }
        overlay = OverlayController(this) { requestManualScan() }
        NotificationHelper.ensureChannels(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceBridge.attach(this)
        NotificationHelper.clearAccessibilityAlert(this)
        refreshOverlay()
        if (repository.settings.value.guardEnabled) {
            GuardService.start(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && (pkg == packageName || isProtectedSurface(pkg))) {
            return
        }
        if (pkg.isEmpty() && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString()
            if (!className.isNullOrBlank() && className.contains('.')) {
                pendingActivity = className
            }
        }
        scheduleScan(pkg)
    }

    override fun onInterrupt() {
        // Nothing to interrupt: all work is short and main-thread bounded.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceBridge.detach(this)
        overlay.hide()
        NotificationHelper.postAccessibilityLost(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { overlay.hide() }
        ServiceBridge.detach(this)
        super.onDestroy()
    }

    fun requestManualScan() {
        mainHandler.post {
            val root = activeRoot()
            if (root == null) {
                overlay.flash("当前没有可读取的窗口")
                return@post
            }
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg.isBlank() || isProtectedSurface(pkg)) {
                overlay.flash("当前界面受保护，不执行点击")
                return@post
            }
            val acted = scanRoot(root, pkg, pendingActivity, manual = true)
            if (!acted) overlay.flash("已扫描 · 未发现可跳过控件")
        }
    }

    fun refreshOverlay() {
        mainHandler.post {
            overlay.refresh(repository.settings.value, repository.stats.value.total)
        }
    }

    private fun scheduleScan(pkg: String) {
        if (pkg.isNotEmpty()) pendingPackage = pkg
        if (scanScheduled) return
        val now = SystemClock.uptimeMillis()
        val delay = max(0L, MIN_SCAN_INTERVAL_MS - (now - lastScanAt))
        scanScheduled = true
        mainHandler.postDelayed({
            scanScheduled = false
            lastScanAt = SystemClock.uptimeMillis()
            if (!isInteractiveUnlocked()) return@postDelayed
            val root = activeRoot() ?: return@postDelayed
            val actualPkg = root.packageName?.toString().orEmpty()
            if (actualPkg.isBlank() || actualPkg == packageName || isProtectedSurface(actualPkg)) {
                return@postDelayed
            }
            val activity = if (actualPkg == pendingPackage) pendingActivity else null
            scanRoot(root, actualPkg, activity, manual = false)
        }, delay)
    }

    private fun scanRoot(
        root: AccessibilityNodeInfo,
        pkg: String,
        activity: String?,
        manual: Boolean,
    ): Boolean {
        if (isProtectedSurface(pkg)) return false
        val now = SystemClock.uptimeMillis()
        if (!manual && now - lastSuccessAt < SUCCESS_QUIET_MS) return false

        val match = ruleEngine.findNodeMatch(root, pkg)
        if (match != null) {
            val cooldownKey = "node:$pkg:${match.rule.id}:${match.label}"
            if (canAct(cooldownKey, match.rule.cooldownMs)) {
                if (clickNode(match, pkg, activity)) return true
            }
        }

        // Coordinate / blind tap fallback for Flutter, games, and other trees
        // that expose no usable text node.
        val coordRule = ruleEngine.findCoordinateRule(pkg)
        if (coordRule != null && coordRule.xPercent != null && coordRule.yPercent != null) {
            if (coordRule.requireAdContext && !ruleEngine.hasAdContext(root)) return false
            val cooldownKey = "coord:$pkg:${coordRule.id}"
            if (!canAct(cooldownKey, coordRule.cooldownMs)) return false
            scheduleCoordinateTap(root, pkg, activity, coordRule)
            return true
        }
        return false
    }

    private fun clickNode(
        match: RuleEngine.NodeMatch,
        pkg: String,
        activity: String?,
    ): Boolean {
        val screen = Rect().also { rootInActiveWindow?.getBoundsInScreen(it) }
        val target = match.clickTarget
        val targetBounds = Rect().also { target.getBoundsInScreen(it) }
        val targetArea = targetBounds.width().toLong() * targetBounds.height()
        val screenArea = screen.width().toLong() * screen.height()
        val safeToClickTarget = screenArea <= 0 || targetArea < screenArea * 55 / 100

        if (safeToClickTarget) {
            val clicked = runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                .getOrDefault(false)
            if (clicked) {
                recordSkip(pkg, activity, match.rule, match.label, match.viewId, match.matchedBy, ClickMethod.NODE_CLICK)
                return true
            }
        }
        if (target !== match.node) {
            val clickedNode = runCatching {
                match.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }.getOrDefault(false)
            if (clickedNode) {
                recordSkip(pkg, activity, match.rule, match.label, match.viewId, match.matchedBy, ClickMethod.NODE_CLICK)
                return true
            }
        }

        val tapBounds = if (match.node.isVisibleToUser) {
            Rect().also { match.node.getBoundsInScreen(it) }
        } else {
            targetBounds
        }
        if (tapBounds.width() <= 0 || tapBounds.height() <= 0) return false
        return dispatchTap(tapBounds.centerX().toFloat(), tapBounds.centerY().toFloat()) {
            recordSkip(pkg, activity, match.rule, match.label, match.viewId, match.matchedBy, ClickMethod.GESTURE)
        }
    }

    private fun scheduleCoordinateTap(
        root: AccessibilityNodeInfo,
        pkg: String,
        activity: String?,
        rule: SkipRule,
    ) {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val width = if (rootBounds.width() > 0) rootBounds.width() else resources.displayMetrics.widthPixels
        val height = if (rootBounds.height() > 0) rootBounds.height() else resources.displayMetrics.heightPixels
        val x = rootBounds.left + width * (rule.xPercent ?: return)
        val y = rootBounds.top + height * (rule.yPercent ?: return)

        mainHandler.postDelayed({
            // Screen may have changed while waiting for the configured delay.
            val current = rootInActiveWindow ?: return@postDelayed
            if (current.packageName?.toString() != pkg) return@postDelayed
            if (!isInteractiveUnlocked()) return@postDelayed
            dispatchTap(x, y) {
                recordSkip(
                    pkg = pkg,
                    activity = activity,
                    rule = rule,
                    label = rule.name,
                    viewId = null,
                    matchedBy = "coordinate:${"%.0f".format(rule.xPercent * 100)}%,${"%.0f".format(rule.yPercent * 100)}%",
                    method = ClickMethod.COORDINATE,
                )
            }
        }, rule.delayMs)
    }

    private fun dispatchTap(x: Float, y: Float, onSuccess: () -> Unit): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        val dispatched = runCatching {
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onSuccess()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        // Do not count cancelled gestures as skips.
                    }
                },
                null,
            )
        }.getOrDefault(false)
        return dispatched
    }

    private fun recordSkip(
        pkg: String,
        activity: String?,
        rule: SkipRule,
        label: String,
        viewId: String?,
        matchedBy: String,
        method: ClickMethod,
    ) {
        lastSuccessAt = SystemClock.uptimeMillis()
        val now = System.currentTimeMillis()
        val appLabel = appLabel(pkg)
        val log = SkipLog(
            id = "$now-${logCounter.incrementAndGet()}",
            timestamp = now,
            packageName = pkg,
            appLabel = appLabel,
            activity = activity,
            ruleId = rule.id,
            ruleName = rule.name,
            nodeText = label,
            viewId = viewId,
            matchedBy = matchedBy,
            method = method,
        )
        repository.recordSkip(log)
        overlay.updateCount(repository.stats.value.total)
        overlay.flash("已跳过 · $appLabel")
    }

    private fun canAct(key: String, cooldownMs: Long): Boolean {
        val now = SystemClock.uptimeMillis()
        actionCooldowns.entries.removeAll { now - it.value > 60_000L }
        val last = actionCooldowns[key] ?: 0L
        if (now - last < cooldownMs) return false
        actionCooldowns[key] = now
        return true
    }

    private fun activeRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }
        return windows.firstOrNull { it.isActive }?.root
    }

    private fun isProtectedSurface(pkg: String): Boolean =
        PackageBlocklist.isBlocked(pkg) || pkg in homePackages

    private fun isInteractiveUnlocked(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return false
        val keyguard = getSystemService(KeyguardManager::class.java)
        return keyguard?.isKeyguardLocked != true
    }

    private fun appLabel(pkg: String): String = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrElse { pkg.substringAfterLast('.').ifBlank { pkg } }

    fun captureSnapshot(): String {
        val root = activeRoot() ?: return "当前没有可读取的窗口（rootInActiveWindow = null）。\n\n请确认：\n1. 无障碍服务已开启\n2. 屏幕已解锁\n3. 目标 App 在前台"
        val sb = StringBuilder(4096)
        val pkg = root.packageName?.toString().orEmpty()
        sb.appendLine("ColorSkip 界面快照")
        sb.appendLine("package: $pkg")
        sb.appendLine("activity: ${pendingActivity ?: "-"}")
        sb.appendLine("capturedAt: ${System.currentTimeMillis()}")
        sb.appendLine("hint: 仅本机生成，不含截图，不上传")
        sb.appendLine("----------------------------------------")
        var count = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (count >= MAX_SNAPSHOT_NODES || sb.length >= MAX_SNAPSHOT_CHARS) return
            count++
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            sb.append("  ".repeat(depth.coerceAtMost(18)))
            sb.append(node.className?.toString()?.substringAfterLast('.') ?: "?")
            val sensitive = node.isPassword ||
                node.className?.toString()?.contains("EditText", ignoreCase = true) == true
            val text = if (sensitive) "<redacted>" else {
                node.text?.toString()?.replace('\n', ' ')?.take(100)
            }
            val desc = if (sensitive) "<redacted>" else {
                node.contentDescription?.toString()?.replace('\n', ' ')?.take(100)
            }
            val id = node.viewIdResourceName?.substringAfterLast('/')
            if (!text.isNullOrBlank()) sb.append(" text=\"${text.replace('"', '\'')}\"")
            if (!desc.isNullOrBlank()) sb.append(" desc=\"${desc.replace('"', '\'')}\"")
            if (!id.isNullOrBlank()) sb.append(" id=$id")
            sb.append(" clickable=${node.isClickable}")
            sb.append(" visible=${node.isVisibleToUser}")
            sb.append(" bounds=[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
            sb.appendLine()
            if (depth < MAX_SNAPSHOT_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                    visit(child, depth + 1)
                }
            }
        }
        visit(root, 0)
        if (count >= MAX_SNAPSHOT_NODES) sb.appendLine("…（已截断，最多 $MAX_SNAPSHOT_NODES 个节点）")
        return sb.toString().take(MAX_SNAPSHOT_CHARS)
    }

    companion object {
        private const val MIN_SCAN_INTERVAL_MS = 220L
        private const val SUCCESS_QUIET_MS = 700L
        private const val MAX_SNAPSHOT_NODES = 420
        private const val MAX_SNAPSHOT_DEPTH = 22
        private const val MAX_SNAPSHOT_CHARS = 22_000
    }
}
