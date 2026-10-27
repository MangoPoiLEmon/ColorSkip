package com.azz.colorskip.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.azz.colorskip.MainActivity
import com.azz.colorskip.R

object NotificationHelper {
    const val CHANNEL_GUARD = "guard"
    const val CHANNEL_ALERTS = "alerts"
    const val GUARD_NOTIFICATION_ID = 1001
    const val ALERT_NOTIFICATION_ID = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val guard = NotificationChannel(
            CHANNEL_GUARD,
            context.getString(R.string.channel_guard),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_guard_desc)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_alerts_desc)
            setShowBadge(true)
        }
        manager.createNotificationChannels(listOf(guard, alerts))
    }

    fun buildGuardNotification(
        context: Context,
        accessibilityEnabled: Boolean,
    ): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openAccessibility = PendingIntent.getActivity(
            context,
            11,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_stat_skip)
            .setContentTitle(context.getString(R.string.guard_title))
            .setContentText(
                context.getString(
                    if (accessibilityEnabled) R.string.guard_text_ok else R.string.guard_text_off,
                ),
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (!accessibilityEnabled) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "打开无障碍设置",
                    openAccessibility,
                ).build(),
            )
        }
        return builder.build()
    }

    fun clearAccessibilityAlert(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(ALERT_NOTIFICATION_ID)
        }
    }

    fun postAccessibilityLost(context: Context) {
        ensureChannels(context)
        val openApp = PendingIntent.getActivity(
            context,
            20,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_KEEPALIVE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_skip)
            .setContentTitle(context.getString(R.string.accessibility_lost_title))
            .setContentText(context.getString(R.string.accessibility_lost_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.accessibility_lost_text)),
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(ALERT_NOTIFICATION_ID, notification)
        }
    }
}
