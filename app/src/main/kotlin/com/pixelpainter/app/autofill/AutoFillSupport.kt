package com.pixelpainter.app.autofill

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast

object AutoFillSupport {

    const val ACTION_START_SETUP = "com.pixelpainter.app.action.AUTOFILL_START_SETUP"
    const val EXTRA_GRID_SIZE = "autofill_grid_size"
    const val EXTRA_PALETTE_COLUMNS = "autofill_palette_columns"
    const val EXTRA_PALETTE_ROWS = "autofill_palette_rows"
    const val EXTRA_VISIBLE_COLORS = "autofill_visible_colors"
    const val EXTRA_PAGE_OVERLAP = "autofill_page_overlap"
    const val EXTRA_SWIPE_UP_NEXT = "autofill_swipe_up_next"
    const val EXTRA_TAP_DELAY = "autofill_tap_delay"
    const val EXTRA_PALETTE_DELAY = "autofill_palette_delay"
    const val EXTRA_SWIPE_DELAY = "autofill_swipe_delay"
    const val EXTRA_COUNTDOWN = "autofill_countdown"
    const val EXTRA_HAS_ART = "autofill_has_art"
    const val EXTRA_PALETTE_SIZE = "autofill_palette_size"
    const val EXTRA_PALETTE_COLORS = "autofill_palette_colors"
    const val EXTRA_INDICES = "autofill_indices"

    @Volatile
    var serviceConnected = false

    val serviceComponent: ComponentName
        get() = ComponentName(
            "com.pixelpainter.app",
            "com.pixelpainter.app.autofill.AutoFillAccessibilityService"
        )

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (serviceConnected) return true
        val expected = serviceComponent
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (manager != null) {
            val fromManager = manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    info.resolveInfo?.serviceInfo != null &&
                        expected == ComponentName(
                            info.resolveInfo.serviceInfo.packageName,
                            info.resolveInfo.serviceInfo.name
                        )
                }
            if (fromManager) return true
        }
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull() ?: return false
        return enabled.split(':').any { it.equals(expected.flattenToString(), ignoreCase = true) }
    }

    fun openAccessibilitySettings(context: Context) {
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
            putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(details) }.isSuccess
        if (!opened) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun notifyOpenSettings(context: Context) {
        Toast.makeText(
            context,
            "请先在系统无障碍中开启“像素画助手自动填充”，再返回并重新点击准备填充",
            Toast.LENGTH_LONG
        ).show()
    }
}
