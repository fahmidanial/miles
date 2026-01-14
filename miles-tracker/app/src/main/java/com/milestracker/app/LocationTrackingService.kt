package com.milestracker.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*

class LocationTrackingService : Service() {
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var repository: LocationRepository
    private lateinit var notificationManager: NotificationManager
    
    private var currentSpeed: Float = 0f
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private var isAutoTracking = false
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }
    
    companion object {
        const val CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_LOCATION_UPDATE = "com.milestracker.LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_UPDATE_COUNT = "update_count"
        
        const val SPEED_THRESHOLD_KMH = 10f // Auto-start threshold
        
        private const val LOCATION_INTERVAL_MS = 3000L // 3 seconds
        private const val FASTEST_INTERVAL_MS = 1000L // 1 second
    }
    
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = LocationRepository(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        setupLocationCallback()
        startLocationUpdates()
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    fun startManualTracking() {
        if (!isAutoTracking) {
            startForegroundService()
        }
    }

    fun stopManualTracking() {
        if (isAutoTracking) {
            stopForegroundService()
        }
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        isAutoTracking = true
        repository.setTrackingActive(true)
    }
    
    private fun stopForegroundService() {
        stopForeground(true)
        isAutoTracking = false
        repository.setTrackingActive(false)
    }
    
    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val speedKmh = currentSpeed * 3.6f
        val speedText = "%.1f km/h".format(speedKmh)
        val coordsText = "%.6f, %.6f".format(currentLat, currentLng)
        val updateCount = repository.getUpdateCount()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 Speed: $speedText")
            .setContentText("📍 $coordsText • #$updateCount updates")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    processLocation(location)
                }
            }
        }
    }
    
    private fun processLocation(location: Location) {
        currentLat = location.latitude
        currentLng = location.longitude
        currentSpeed = if (location.hasSpeed()) location.speed else 0f
        val speedKmh = currentSpeed * 3.6f
        
        // Always broadcast to keep UI updated
        broadcastLocationUpdate()
        
        // Auto-tracking logic
        if (speedKmh >= SPEED_THRESHOLD_KMH && !isAutoTracking) {
            startForegroundService()
        } else if (speedKmh < SPEED_THRESHOLD_KMH && isAutoTracking) {
            // Add a check to not stop if manually started - this will be handled in MainActivity
        }
        
        // Save data and update notification only when tracking
        if (isAutoTracking) {
            repository.addLocationPoint(currentLat, currentLng, currentSpeed)
            updateNotification()
        }
    }
    
    private fun updateNotification() {
        if (isAutoTracking) {
            val notification = buildNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    private fun broadcastLocationUpdate() {
        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, currentLat)
            putExtra(EXTRA_LONGITUDE, currentLng)
            putExtra(EXTRA_SPEED, currentSpeed)
            putExtra(EXTRA_UPDATE_COUNT, repository.getUpdateCount())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopSelf()
        }
    }
    
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun isTracking(): Boolean = isAutoTracking

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        if (isAutoTracking) {
            stopForegroundService()
        }
    }
}
