package com.azz.colorskip

import android.app.Application
import com.azz.colorskip.core.SkipRepository
import com.azz.colorskip.util.NotificationHelper

class ColorSkipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        // Warm the rule store so the first accessibility event does not pay
        // for asset parsing. Everything is local and small.
        SkipRepository.get(this)
    }
}
