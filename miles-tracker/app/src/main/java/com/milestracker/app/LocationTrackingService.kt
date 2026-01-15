package com.milestracker.app

import android.Manifest
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
import android.media.RingtoneManager
import android.net.Uri
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var repository: LocationRepository
    private lateinit var notificationManager: NotificationManager
    
    private var currentSpeed: Float = 0f
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    
    private var isTrackingActive = false
    private var isManuallyStarted = false
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }
    
    companion object {
        const val CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_LOCATION_UPDATE = "com.milestracker.LOCATION_UPDATE"
        const val ACTION_TRIP_FINISHED = "com.milestracker.TRIP_FINISHED"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_UPDATE_COUNT = "update_count"
        const val SPEED_THRESHOLD_KMH = 10f
        private const val LOCATION_INTERVAL_MS = 3000L
        private const val FASTEST_INTERVAL_MS = 1000L
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
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Immediately promote the service to a foreground service
        startForeground(NOTIFICATION_ID, buildNotification(isTracking = false))
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

        val finishChannel = NotificationChannel(
            "trip_finished_channel",
            "Trip Finished Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
             description = "Notifications when a trip is classified or finished"
             setShowBadge(true)
        }
        notificationManager.createNotificationChannel(finishChannel)
    }
    
    fun startManualTracking() {
        isManuallyStarted = true

        if (!isTrackingActive) {
            isTrackingActive = true
            repository.setTrackingActive(true)
            updateNotification()
        }
    }

    fun stopManualTracking() {
        isManuallyStarted = false
        val speedKmh = currentSpeed * 3.6f
        if (isTrackingActive && speedKmh < SPEED_THRESHOLD_KMH) {
            // Instead of immediate stop, maybe we should also use the timer? 
            // But manual stop usually means "I am done now".
            // Let's keep manual stop immediate for now, or user choice.
            // Prompt implied manual stop is manual.
            
            // Actually, if they hit stop, they probably want it saved. 
            // But the prompt said "auto save and clear when speed less than 10".
            // So this manual button logic remains strictly manual control.
            
            isTrackingActive = false
            repository.setTrackingActive(false)
            updateNotification()
            playStopRingtone()
            showTripFinishedNotification()
        }
    }
    
    private fun buildNotification(isTracking: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title: String
        val text: String

        if (isTracking) {
            val speedKmh = currentSpeed * 3.6f
            val speedText = "%.1f km/h".format(speedKmh)
            val coordsText = "%.6f, %.6f".format(currentLat, currentLng)
            val updateCount = repository.getUpdateCount()
            title = "🚗 Speed: $speedText"
            text = "📍 $coordsText • #$updateCount updates"
        } else {
            title = "MilesTracker is active"
            text = "Waiting for speed to exceed 10 km/h..."
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
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
                result.lastLocation?.let { processLocation(it) }
            }
        }
    }
    
    private fun processLocation(location: Location) {
        currentLat = location.latitude
        currentLng = location.longitude
        currentSpeed = if (location.hasSpeed()) location.speed else 0f
        val speedKmh = currentSpeed * 3.6f
        
        broadcastLocationUpdate()
        
        val shouldBeTracking = speedKmh >= SPEED_THRESHOLD_KMH || isManuallyStarted
        
        if (shouldBeTracking) {
             if (!isTrackingActive) {
                isTrackingActive = true
                repository.setTrackingActive(true)
                updateNotification()
             }
        } else {
            // Speed is low and not manually started
            if (isTrackingActive) {
                // Immediate Auto-Save
                finishTripAutoSave()
            }
        }

        if (isTrackingActive) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.addLocationPoint(currentLat, currentLng, currentSpeed)
            }
        }
        updateNotification()
    }


    
    private fun finishTripAutoSave() {
        isTrackingActive = false

        repository.setTrackingActive(false)
        updateNotification()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.saveCurrentRouteToHistory()
                repository.clearRoute()
                
                // Broadcast that we finished
                val intent = Intent(ACTION_TRIP_FINISHED)
                LocalBroadcastManager.getInstance(this@LocationTrackingService).sendBroadcast(intent)
                
                playStopRingtone()
                showTripFinishedNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    
    private fun updateNotification() {
        // Updated to show "Auto-stop pending" if applicable?
        // Keeping it simple for now or parsing "isAutoStopScheduled"
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isTracking = isTrackingActive))
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
        }.build()
        
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }
    
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun isTracking(): Boolean = isTrackingActive

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        stopForeground(true)
    }
    private fun playStopRingtone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showTripFinishedNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "trip_finished_channel")
            .setContentTitle("Trip Finished")
            .setContentText("Trip saved. Tap to classify if needed.")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
}
