package com.milestracker.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * This receiver starts the LocationTrackingService when the device boots up.
 * This ensures that auto-tracking can work even if the app is not manually opened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = LocationRepository(context)
            // We only restart the service if tracking was active before reboot,
            // or if the user has enabled auto-start functionality implicitly.
            // For simplicity, we restart it if it was tracking before.
            if (repository.isTrackingActive()) {
                val serviceIntent = Intent(context, LocationTrackingService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
