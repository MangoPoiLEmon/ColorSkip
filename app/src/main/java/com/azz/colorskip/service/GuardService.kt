package com.azz.colorskip.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.azz.colorskip.core.SkipRepository
import com.azz.colorskip.util.KeepAliveHelper
import com.azz.colorskip.util.NotificationHelper

/**
 * Foreground "guard" service. The AccessibilityService itself is bound by the
 * system and cannot be made sticky; this companion keeps the process alive and
 * shows a persistent status/notification.
 */
class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var alerted = false

    private val heartbeat = object : Runnable {
        override fun run() {
            val enabled = KeepAliveHelper.isAccessibilityServiceEnabled(this@GuardService)
            updateNotification(enabled)
            if (!enabled && !alerted) {
                alerted = true
                NotificationHelper.postAccessibilityLost(this@GuardService)
            } else if (enabled) {
                alerted = false
            }
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val enabled = KeepAliveHelper.isAccessibilityServiceEnabled(this)
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.GUARD_NOTIFICATION_ID,
                NotificationHelper.buildGuardNotification(this, enabled),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
            true
        }.getOrDefault(false)

        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }

        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(accessibilityEnabled: Boolean) {
        runCatching {
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(
                NotificationHelper.GUARD_NOTIFICATION_ID,
                NotificationHelper.buildGuardNotification(this, accessibilityEnabled),
            )
        }
    }

    companion object {
        private const val HEARTBEAT_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GuardService::class.java)) }
        }

        fun syncWithSettings(context: Context) {
            val repo = SkipRepository.get(context)
            if (repo.settings.value.guardEnabled) start(context) else stop(context)
        }
    }
}
