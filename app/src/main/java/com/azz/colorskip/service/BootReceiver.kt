package com.azz.colorskip.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azz.colorskip.core.SkipRepository
import com.azz.colorskip.util.KeepAliveHelper
import com.azz.colorskip.util.NotificationHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            -> {
                NotificationHelper.ensureChannels(context)
                if (SkipRepository.get(context).settings.value.guardEnabled) {
                    GuardService.start(context)
                }
                if (!KeepAliveHelper.isAccessibilityServiceEnabled(context)) {
                    NotificationHelper.postAccessibilityLost(context)
                }
            }
        }
    }
}
