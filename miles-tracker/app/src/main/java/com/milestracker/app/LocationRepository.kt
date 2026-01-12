package com.milestracker.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.GeoPoint

/**
 * Repository for storing and retrieving location tracking data.
 * Uses SharedPreferences with JSON serialization for simplicity.
 */
class LocationRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "miles_tracker_prefs"
        private const val KEY_ROUTE_POINTS = "route_points"
        private const val KEY_UPDATE_COUNT = "update_count"
        private const val KEY_IS_TRACKING = "is_tracking"
    }
    
    /**
     * Data class for storing location points
     */
    data class LocationPoint(
        val latitude: Double,
        val longitude: Double,
        val speed: Float,
        val timestamp: Long
    )
    
    /**
     * Save a new location point to the route
     */
    fun addLocationPoint(lat: Double, lng: Double, speed: Float) {
        val points = getRoutePoints().toMutableList()
        points.add(LocationPoint(lat, lng, speed, System.currentTimeMillis()))
        
        val json = gson.toJson(points)
        prefs.edit().putString(KEY_ROUTE_POINTS, json).apply()
        
        // Increment update counter
        incrementUpdateCount()
    }
    
    /**
     * Get all saved route points
     */
    fun getRoutePoints(): List<LocationPoint> {
        val json = prefs.getString(KEY_ROUTE_POINTS, null) ?: return emptyList()
        val type = object : TypeToken<List<LocationPoint>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get route points as GeoPoints for OSM
     */
    fun getRouteAsGeoPoints(): List<GeoPoint> {
        return getRoutePoints().map { GeoPoint(it.latitude, it.longitude) }
    }
    
    /**
     * Get location update count
     */
    fun getUpdateCount(): Int {
        return prefs.getInt(KEY_UPDATE_COUNT, 0)
    }
    
    /**
     * Increment location update count
     */
    private fun incrementUpdateCount() {
        val current = getUpdateCount()
        prefs.edit().putInt(KEY_UPDATE_COUNT, current + 1).apply()
    }
    
    /**
     * Clear all route data
     */
    fun clearRoute() {
        prefs.edit()
            .remove(KEY_ROUTE_POINTS)
            .putInt(KEY_UPDATE_COUNT, 0)
            .apply()
    }
    
    /**
     * Check if tracking was active (for service restart)
     */
    fun isTrackingActive(): Boolean {
        return prefs.getBoolean(KEY_IS_TRACKING, false)
    }
    
    /**
     * Set tracking state
     */
    fun setTrackingActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_IS_TRACKING, active).apply()
    }
}
