package com.azz.colorskip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.azz.colorskip.core.SkipRepository
import com.azz.colorskip.service.GuardService
import com.azz.colorskip.ui.ColorSkipApp
import com.azz.colorskip.ui.MainViewModel
import com.azz.colorskip.ui.theme.ColorSkipTheme
import com.azz.colorskip.util.NotificationHelper

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var keepAliveRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        NotificationHelper.ensureChannels(this)
        if (SkipRepository.get(this).settings.value.guardEnabled) {
            GuardService.start(this)
        }
        handleIntent(intent)
        setContent {
            ColorSkipTheme {
                ColorSkipApp(vm = vm, keepAliveRequest = keepAliveRequest)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_KEEPALIVE, false)) {
            keepAliveRequest++
        }
        vm.handleIntent(intent)
    }

    companion object {
        const val EXTRA_OPEN_KEEPALIVE = "open_keepalive"
    }
}
