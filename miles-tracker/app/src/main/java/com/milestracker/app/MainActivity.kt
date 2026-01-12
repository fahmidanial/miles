package com.milestracker.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.milestracker.app.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: LocationRepository
    
    private var locationService: LocationTrackingService? = null
    private var isServiceBound = false
    private var isTracking = false
    
    private var currentMarker: Marker? = null
    private var routePolyline: Polyline? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationTrackingService.LocalBinder
            locationService = binder.getService()
            isServiceBound = true
            updateTrackingState(true)
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            isServiceBound = false
            updateTrackingState(false)
        }
    }
    
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val lat = it.getDoubleExtra(LocationTrackingService.EXTRA_LATITUDE, 0.0)
                val lng = it.getDoubleExtra(LocationTrackingService.EXTRA_LONGITUDE, 0.0)
                val speed = it.getFloatExtra(LocationTrackingService.EXTRA_SPEED, 0f)
                val updateCount = it.getIntExtra(LocationTrackingService.EXTRA_UPDATE_COUNT, 0)
                
                updateUI(lat, lng, speed, updateCount)
                updateMapLocation(lat, lng)
                updateRoute()
            }
        }
    }
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            checkBackgroundLocationPermission()
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
        }
    }
    
    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkNotificationPermission()
        } else {
            // Still allow tracking without background permission
            checkNotificationPermission()
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Start tracking regardless of notification permission
        startTracking()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OSMDroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = LocationRepository(this)
        
        setupMap()
        setupButtons()
        loadSavedRoute()
        updateUIFromRepository()
        
        // Check if service was running
        if (repository.isTrackingActive()) {
            bindToService()
        }
    }
    
    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
            
            // Default location (will be updated when tracking starts)
            controller.setCenter(GeoPoint(0.0, 0.0))
        }
        
        // Initialize route polyline
        routePolyline = Polyline().apply {
            outlinePaint.color = Color.parseColor("#2196F3")
            outlinePaint.strokeWidth = 8f
        }
        binding.mapView.overlays.add(routePolyline)
    }
    
    private fun setupButtons() {
        binding.startStopButton.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                checkPermissionsAndStart()
            }
        }
        
        binding.clearButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Route")
                .setMessage("This will clear all saved route data. Continue?")
                .setPositiveButton("Clear") { _, _ ->
                    repository.clearRoute()
                    routePolyline?.actualPoints?.clear()
                    binding.mapView.invalidate()
                    updateUIFromRepository()
                    Toast.makeText(this, "Route cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun checkPermissionsAndStart() {
        when {
            hasLocationPermission() -> {
                checkBackgroundLocationPermission()
            }
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }
    
    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Background Location")
                    .setMessage("For continuous tracking, please allow 'All the time' location access.")
                    .setPositiveButton("Grant") { _, _ ->
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        checkNotificationPermission()
                    }
                    .show()
            } else {
                checkNotificationPermission()
            }
        } else {
            checkNotificationPermission()
        }
    }
    
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startTracking()
            }
        } else {
            startTracking()
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startTracking() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        startForegroundService(serviceIntent)
        bindToService()
    }
    
    private fun bindToService() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun stopTracking() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        stopService(Intent(this, LocationTrackingService::class.java))
        locationService = null
        updateTrackingState(false)
    }
    
    private fun updateTrackingState(tracking: Boolean) {
        isTracking = tracking
        
        binding.startStopButton.apply {
            text = if (tracking) getString(R.string.stop_tracking) else getString(R.string.start_tracking)
            setBackgroundColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (tracking) R.color.error else R.color.success
                )
            )
            setIconResource(
                if (tracking) android.R.drawable.ic_media_pause 
                else android.R.drawable.ic_media_play
            )
        }
        
        binding.trackingStatus.apply {
            text = if (tracking) "● TRACKING" else "● INACTIVE"
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (tracking) R.color.success else R.color.text_secondary
                )
            )
        }
    }
    
    private fun updateUI(lat: Double, lng: Double, speed: Float, updateCount: Int) {
        val speedKmh = speed * 3.6f
        binding.speedValue.text = "%.1f km/h".format(speedKmh)
        binding.coordinatesValue.text = "%.6f, %.6f".format(lat, lng)
        binding.updateCountValue.text = "#$updateCount"
        
        // Change speed color based on threshold
        binding.speedValue.setTextColor(
            ContextCompat.getColor(
                this,
                if (speedKmh >= LocationTrackingService.SPEED_THRESHOLD_KMH) R.color.success else R.color.secondary
            )
        )
    }
    
    private fun updateUIFromRepository() {
        val updateCount = repository.getUpdateCount()
        binding.updateCountValue.text = "#$updateCount"
        
        val points = repository.getRoutePoints()
        if (points.isNotEmpty()) {
            val last = points.last()
            binding.coordinatesValue.text = "%.6f, %.6f".format(last.latitude, last.longitude)
            binding.speedValue.text = "%.1f km/h".format(last.speed * 3.6f)
        }
    }
    
    private fun updateMapLocation(lat: Double, lng: Double) {
        val geoPoint = GeoPoint(lat, lng)
        
        // Update or create marker
        if (currentMarker == null) {
            currentMarker = Marker(binding.mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Current Location"
            }
            binding.mapView.overlays.add(currentMarker)
        }
        currentMarker?.position = geoPoint
        
        // Center map on current location
        binding.mapView.controller.animateTo(geoPoint)
        binding.mapView.invalidate()
    }
    
    private fun updateRoute() {
        val geoPoints = repository.getRouteAsGeoPoints()
        routePolyline?.setPoints(geoPoints)
        binding.mapView.invalidate()
    }
    
    private fun loadSavedRoute() {
        val geoPoints = repository.getRouteAsGeoPoints()
        if (geoPoints.isNotEmpty()) {
            routePolyline?.setPoints(geoPoints)
            
            // Center on last known position
            val lastPoint = geoPoints.last()
            binding.mapView.controller.setCenter(lastPoint)
            
            // Add marker at last position
            currentMarker = Marker(binding.mapView).apply {
                position = lastPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Last Location"
            }
            binding.mapView.overlays.add(currentMarker)
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationReceiver,
            IntentFilter(LocationTrackingService.ACTION_LOCATION_UPDATE)
        )
        
        // Refresh route on resume
        updateRoute()
        updateUIFromRepository()
    }
    
    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
}
