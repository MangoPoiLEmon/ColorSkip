package com.azz.colorskip.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.azz.colorskip.service.AdSkipService

/**
 * ColorOS 17 / Android 17 adaptation helpers.
 *
 * Reality check: no third-party app can silently re-enable an accessibility
 * service, whitelist itself from battery optimisation, or lock its own recents
 * card. This class therefore only *detects* state and opens the closest
 * official settings screen, with a chain of fallbacks for OPPO/OnePlus/realme
 * builds whose settings activities are renamed between releases.
 */
object KeepAliveHelper {

    data class SystemStatus(
        val accessibilityEnabled: Boolean,
        val accessibilityConnected: Boolean,
        val overlayGranted: Boolean,
        val notificationsGranted: Boolean,
        val ignoringBattery: Boolean,
        val isColorOs: Boolean,
    )

    private val oppoFamilyTokens = listOf("oppo", "oneplus", "realme", "oplus", "coloros")

    fun isOplusFamily(): Boolean {
        val identity = "${Build.BRAND} ${Build.MANUFACTURER} ${Build.DISPLAY} ${Build.PRODUCT}"
            .lowercase()
        return oppoFamilyTokens.any { identity.contains(it) }
    }

    fun isColorOs(): Boolean {
        val display = "${Build.DISPLAY} ${Build.PRODUCT} ${Build.BRAND}".lowercase()
        return display.contains("coloros") || display.contains("oplus") ||
            display.contains("oppo") || display.contains("oneplus")
    }

    fun romLabel(): String {
        val vendor = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val base = "$vendor · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        return if (isColorOs()) "$base · ColorOS" else base
    }

    fun readStatus(context: Context): SystemStatus = SystemStatus(
        accessibilityEnabled = isAccessibilityServiceEnabled(context),
        accessibilityConnected = com.azz.colorskip.core.ServiceBridge.isConnected,
        overlayGranted = Settings.canDrawOverlays(context),
        notificationsGranted = notificationsEnabled(context),
        ignoringBattery = isIgnoringBatteryOptimizations(context),
        isColorOs = isColorOs(),
    )

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val component = ComponentName(context, AdSkipService::class.java)
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val viaManager = manager?.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        )?.any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
            serviceInfo.packageName == component.packageName &&
                serviceInfo.name == component.className
        } == true
        if (viaManager) return true

        // Fallback for ROMs that return a stale AccessibilityManager snapshot.
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull().orEmpty()
        return enabled.split(':').any { entry ->
            entry.contains(component.packageName, ignoreCase = true) &&
                entry.contains("AdSkipService", ignoreCase = true)
        }
    }

    fun notificationsEnabled(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.areNotificationsEnabled()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAccessibilitySettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    fun openNotificationSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, intent)) return true
        return openAppDetails(context)
    }

    /**
     * Standard Android overlay page first (it carries the package extra and is
     * usually the correct per-app page). If a particular ColorOS build does not
     * export it, try the legacy ColorOS / OPlus float-window activities.
     */
    fun openOverlaySettings(activity: Activity): Boolean {
        val standard = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(activity, standard)) return true

        val components = listOf(
            "com.coloros.safecenter" to
                "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity",
            "com.oplus.safecenter" to
                "com.oplus.safecenter.permission.floatwindow.FloatWindowListActivity",
        )
        for ((pkg, cls) in components) {
            if (startComponent(activity, pkg, cls)) return true
        }
        for (pkg in STARTUP_PACKAGES) {
            discoveredActivities(activity, pkg, listOf("floatwindow", "overlay"))
                .forEach { if (startComponent(activity, it.packageName, it.className)) return true }
        }
        return openAppDetails(activity)
    }

    /**
     * Opens the battery-optimisation whitelist. The direct request dialog is
     * preferred because it lets the user pick "Don't optimise" in one tap.
     */
    @RequiresPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    fun openBatteryOptimization(activity: Activity): Boolean {
        if (isIgnoringBatteryOptimizations(activity)) {
            return openBatteryOptimizationList(activity)
        }
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${activity.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(activity, direct)) return true
        if (openBatteryOptimizationList(activity)) return true
        if (openPowerManager(activity)) return true
        return openAppDetails(activity)
    }

    fun openBatteryOptimizationList(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    /**
     * ColorOS "自启动" / "允许后台活动" pages. Component names differ across
     * ColorOS 12-17, so try both the safecenter and phonemanager families.
     */
    fun openAutoStart(activity: Activity): Boolean {
        val targets = mutableListOf<Pair<String, String>>(
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.phonemanager" to "com.coloros.phonemanager.startupapp.StartupAppListActivity",
            "com.oplus.phonemanager" to "com.oplus.phonemanager.startupapp.StartupAppListActivity",
            "com.coloros.oppoguardelf" to
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.battery" to "com.oplus.battery.startupapp.StartupAppListActivity",
        )
        // ColorOS 17 may rename the activity. Enumerate exported activities of
        // the security-center packages and match startup/autostart/boot names.
        for (pkg in STARTUP_PACKAGES) {
            discoveredActivities(
                activity,
                pkg,
                listOf("startup", "autostart", "auto_start", "boot"),
            ).forEach { targets += it.packageName to it.className }
        }
        for ((pkg, cls) in targets.distinct()) {
            if (startComponent(activity, pkg, cls)) return true
        }

        // Some builds expose the page only through an action, not a stable
        // activity class. Try the known OPlus/ColorOS action names.
        for (action in STARTUP_ACTIONS) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(activity.packageManager) != null &&
                start(activity, intent)
            ) {
                return true
            }
        }

        // Honest fallback: app details. The guide tells the user to tap the
        // "自启动 / 关联启动" row there. Return false so the UI can say so.
        openAppDetails(activity)
        return false
    }

    /**
     * ColorOS "耗电管理 / 后台耗电" pages, plus the generic battery list.
     */
    fun openPowerManager(activity: Activity): Boolean {
        val targets = listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.powermanager.PowerManagerActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.powermanager.PowerManagerActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.power.PowerConsumptionActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.power.PowerConsumptionActivity",
            "com.coloros.oppoguardelf" to
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            "com.oplus.battery" to "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity",
            "com.coloros.oppoguardelf" to
                "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity",
            "com.oplus.battery" to "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity",
        )
        for ((pkg, cls) in targets) {
            if (startComponent(activity, pkg, cls)) return true
        }
        for (pkg in STARTUP_PACKAGES) {
            discoveredActivities(
                activity,
                pkg,
                listOf("powermanager", "powerconsumption", "fuelgaue", "battery"),
            ).forEach { if (startComponent(activity, it.packageName, it.className)) return true }
        }
        return openBatteryOptimizationList(activity)
    }

    fun openAppDetails(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    /**
     * ColorOS 17 may hide the "lock card" gesture in Recents. There is no public
     * API to lock it; the UI offers this shortcut and then tells the user to
     * swipe down on the ColorSkip card.
     */
    fun openRecentsThroughService(): Boolean =
        com.azz.colorskip.core.ServiceBridge.openRecents()

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Enumerate exported activities and match name fragments. This is the
     * pragmatic answer to OEM components being renamed between ColorOS builds.
     */
    @Suppress("DEPRECATION")
    private fun discoveredActivities(
        context: Context,
        pkg: String,
        tokens: List<String>,
    ): List<ComponentName> {
        val info = runCatching {
            context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
        }.getOrNull() ?: return emptyList()
        return info.activities.orEmpty()
            .asSequence()
            .filter { it.exported }
            .filter { activity ->
                tokens.any { token -> activity.name.contains(token, ignoreCase = true) }
            }
            .map { ComponentName(pkg, it.name) }
            .distinct()
            .toList()
    }

    private fun start(context: Context, intent: Intent): Boolean {
        val resolved = intent.resolveActivity(context.packageManager) ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun startComponent(activity: Activity, pkg: String, cls: String): Boolean {
        val intent = Intent().setComponent(ComponentName(pkg, cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(activity.packageManager) == null) return false
        return runCatching {
            activity.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private val STARTUP_PACKAGES = listOf(
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.coloros.phonemanager",
        "com.oplus.phonemanager",
        "com.coloros.oppoguardelf",
        "com.oplus.battery",
        "com.android.settings",
    )

    private val STARTUP_ACTIONS = listOf(
        "com.oplus.safecenter.action.STARTUP_APP_LIST",
        "com.coloros.safecenter.action.STARTUP_APP_LIST",
        "com.oplus.safecenter.action.AUTOSTART",
        "com.coloros.safecenter.action.AUTOSTART",
        "com.oplus.safecenter.action.STARTUP_MANAGER",
    )
}
