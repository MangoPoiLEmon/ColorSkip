package com.azz.colorskip.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.azz.colorskip.service.AdSkipService
import java.lang.ref.WeakReference

/**
 * Same-process bridge between the Compose UI and the system-bound
 * AccessibilityService. The service is held weakly: the UI must never keep it
 * alive, and all calls are best-effort because the system can unbind it at any
 * time.
 */
object ServiceBridge {
    @Volatile
    private var serviceRef: WeakReference<AdSkipService>? = null

    fun attach(service: AdSkipService) {
        serviceRef = WeakReference(service)
    }

    fun detach(service: AdSkipService) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    val isConnected: Boolean
        get() = serviceRef?.get() != null

    fun scanNow(): Boolean {
        val service = serviceRef?.get() ?: return false
        service.requestManualScan()
        return true
    }

    fun captureSnapshot(): String? = serviceRef?.get()?.captureSnapshot()

    fun openRecents(): Boolean {
        val service = serviceRef?.get() ?: return false
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun refreshOverlay() {
        serviceRef?.get()?.refreshOverlay()
    }

    fun activeRoot(): AccessibilityNodeInfo? = serviceRef?.get()?.rootInActiveWindow
}
