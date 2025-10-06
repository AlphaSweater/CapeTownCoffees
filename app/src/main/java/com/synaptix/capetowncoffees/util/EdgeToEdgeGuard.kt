package com.synaptix.capetowncoffees.util

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import timber.log.Timber

object EdgeToEdgeGuard {

    /**
     * Adds status-bar height as top padding to the given root view whenever the system reports
     * non-zero status/cutout insets. On devices that already avoid overlay, inset is 0 -> no gap.
     */
    fun install(
        activity: Activity,
        @IdRes rootId: Int
    ) {
        val root = activity.findViewById<View>(rootId) ?: return

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val types = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            val sb = insets.getInsets(types)
            val needTopPad = sb.top > 0

            // Only add padding if there *is* a status/cutout overlap reported
            if (needTopPad) {
                val newTop = sb.top + v.paddingTop
                if (v.paddingTop != newTop) {
                    Timber.tag("E2E-GUARD").i("Applying top padding=%d (cutout/status)", sb.top)
                    v.updatePadding(top = newTop)
                }
            } else {
                // keep original padding; don't force extra gap on good devices
                Timber.tag("E2E-GUARD").i("No status/cutout inset; leaving paddingTop=%d", v.paddingTop)
            }

            insets
        }

        // trigger first pass
        ViewCompat.requestApplyInsets(root)

        // Hard reset any rogue fullscreen flags someone may have set
        activity.window.clearFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        activity.window.decorView.systemUiVisibility = 0
    }
}