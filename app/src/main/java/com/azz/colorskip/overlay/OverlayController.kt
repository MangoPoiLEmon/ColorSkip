package com.azz.colorskip.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.azz.colorskip.core.AppSettings

/**
 * Small, non-focusable overlay: a draggable "skip" bubble plus transient
 * "skipped" toasts. It never covers the whole screen and never takes focus.
 */
class OverlayController(
    private val context: Context,
    private val onManualScan: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var toast: TextView? = null
    private var currentCount = 0
    private var settings = AppSettings()

    fun refresh(newSettings: AppSettings, count: Int) {
        settings = newSettings
        currentCount = count
        if (!Settings.canDrawOverlays(context) || !newSettings.overlayEnabled) {
            hide()
            return
        }
        if (newSettings.bubbleEnabled) showBubble() else removeBubble()
    }

    fun updateCount(count: Int) {
        currentCount = count
        bubble?.text = label()
    }

    fun flash(message: String) {
        if (!settings.overlayEnabled || !Settings.canDrawOverlays(context)) return
        removeToast()
        val view = TextView(context).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xF21A241E.toInt())
                setStroke(dp(1), 0x66B8F36B)
            }
            elevation = dp(8).toFloat()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(120)
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess { toast = view }
        handler.postDelayed({ removeToast() }, 2300L)
    }

    fun hide() {
        removeBubble()
        removeToast()
    }

    private fun showBubble() {
        if (bubble != null) {
            bubble?.text = label()
            return
        }
        val view = TextView(context).apply {
            text = label()
            setTextColor(0xFFE9FFD8.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = dp(58)
            minHeight = dp(44)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6101B15.toInt())
                setStroke(dp(1), 0x99B8F36B.toInt())
            }
            elevation = dp(8).toFloat()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, dp(16))
            y = prefs.getInt(KEY_Y, dp(260))
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val slop = (context.resources.displayMetrics.density * 6f)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = (startX + dx).toInt().coerceAtLeast(0)
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
                    } else {
                        onManualScan()
                    }
                    true
                }
                else -> false
            }
        }
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (added) {
            bubble = view
            bubbleParams = params
        }
    }

    private fun removeBubble() {
        val view = bubble ?: return
        runCatching { windowManager.removeView(view) }
        bubble = null
        bubbleParams = null
    }

    private fun removeToast() {
        val view = toast ?: return
        runCatching { windowManager.removeView(view) }
        toast = null
    }

    private fun label(): String = "⏭ $currentCount"

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_X = "bubble_x"
        private const val KEY_Y = "bubble_y"
    }
}
