package com.milestracker.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

/**
 * Detects when speed exceeds threshold and auto-starts tracking.
 * This is a passive listener that monitors location for speed detection.
 */
class SpeedDetector(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private var isMonitoring = false
    private var onSpeedThresholdListener: (() -> Unit)? = null
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                if (location.hasSpeed()) {
                    val speedKmh = location.speed * 3.6f
                    if (speedKmh >= LocationTrackingService.SPEED_THRESHOLD_KMH) {
                        // Speed threshold exceeded, trigger auto-start
                        onSpeedThresholdListener?.invoke()
                        stopMonitoring() // Stop after triggering
                    }
                }
            }
        }
    }
    
    /**
     * Start passive speed monitoring
     */
    fun startMonitoring(onThresholdExceeded: () -> Unit) {
        if (isMonitoring) return
        
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        onSpeedThresholdListener = onThresholdExceeded
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            10000L // Check every 10 seconds for battery efficiency
        ).apply {
            setMinUpdateIntervalMillis(5000L)
        }.build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isMonitoring = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    /**
     * Stop speed monitoring
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isMonitoring = false
        onSpeedThresholdListener = null
    }
    
    fun isMonitoring() = isMonitoring
}
