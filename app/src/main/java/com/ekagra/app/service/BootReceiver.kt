package com.ekagra.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ekagra.app.utils.PreferencesManager

/**
 * BootReceiver
 *
 * Restarts the Ekagra foreground service automatically after device reboot,
 * but only if focus mode was enabled before the reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PreferencesManager.isFocusModeEnabled(context)) {
                EkagraForegroundService.start(context)
            }
        }
    }
}
