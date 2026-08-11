package com.pixelpainter.app.autofill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest-declared receiver used to wake the accessibility service with the
 * latest pixel-art payload while the user is on the game screen.
 */
class SetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoFillStateHolder.applyIntent(intent)
        runCatching {
            intent.setClass(context, AutoFillAccessibilityService::class.java)
            context.startService(intent)
        }
    }
}
