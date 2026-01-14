package com.milestracker.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.UUID

class LocationRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val firestore = FirebaseFirestore.getInstance()
    private val routesCollection = firestore.collection("routes")

    companion object {
        private const val PREFS_NAME = "miles_tracker_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LOCAL_ROUTE_POINTS = "local_route_points"
        private const val KEY_UPDATE_COUNT = "update_count"
        private const val KEY_IS_TRACKING = "is_tracking"
        private const val TAG = "LocationRepository"
    }

    data class LocationPoint(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val speed: Float = 0f,
        val timestamp: Long = 0L,
        val formattedTime: String = ""
    )

    data class Route(
        val deviceId: String = "",
        val startTimestamp: Long = 0L,
        val points: List<LocationPoint> = emptyList(),
        val updatedAt: Long = 0L
    )

    private fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    suspend fun addLocationPoint(lat: Double, lng: Double, speed: Float) {
        val timestamp = System.currentTimeMillis()
        val formattedTime = DateUtils.formatTimestamp(timestamp)
        val point = LocationPoint(lat, lng, speed, timestamp, formattedTime)

        saveLocally(listOf(point))

        try {
            withContext(Dispatchers.IO) {
                syncToCloud()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync to Firestore", e)
        }

        incrementUpdateCount()
    }

    suspend fun getRoutePoints(): List<LocationPoint> {
        return withContext(Dispatchers.IO) {
            try {
                val cloudPoints = getFromCloud()
                if (cloudPoints.isNotEmpty()) {
                    val localPoints = getLocalRoutePoints()
                    val merged = (cloudPoints + localPoints).distinctBy { it.timestamp }
                    return@withContext merged.sortedBy { it.timestamp }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get from Firestore, using local", e)
            }

            getLocalRoutePoints()
        }
    }

    suspend fun syncToCloud() {
        withContext(Dispatchers.IO) {
            val localPoints = getLocalRoutePoints()
            if (localPoints.isNotEmpty()) {
                val deviceId = getDeviceId()
                val route = Route(
                    deviceId = deviceId,
                    startTimestamp = localPoints.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                    points = localPoints,
                    updatedAt = System.currentTimeMillis()
                )

                try {
                    routesCollection.document(deviceId)
                        .set(route, SetOptions.merge())
                        .await()
                    Log.d(TAG, "Synced ${localPoints.size} points to Firestore")
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore sync failed", e)
                    throw e
                }
            }
        }
    }

    private suspend fun getFromCloud(): List<LocationPoint> {
        return withContext(Dispatchers.IO) {
            try {
                val document = routesCollection.document(getDeviceId()).get().await()
                if (document.exists()) {
                    val route = document.toObject(Route::class.java)
                    return@withContext route?.points ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get from Firestore", e)
            }
            emptyList()
        }
    }

    private fun saveLocally(points: List<LocationPoint>) {
        val existing = getLocalRoutePoints()
        val combined = existing + points
        val json = gson.toJson(combined)
        prefs.edit().putString(KEY_LOCAL_ROUTE_POINTS, json).apply()
    }

    private fun getLocalRoutePoints(): List<LocationPoint> {
        val json = prefs.getString(KEY_LOCAL_ROUTE_POINTS, null) ?: return emptyList()
        val type = object : TypeToken<List<LocationPoint>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun clearLocalData() {
        prefs.edit().remove(KEY_LOCAL_ROUTE_POINTS).apply()
    }

    fun getRouteAsGeoPoints(): List<GeoPoint> {
        return getLocalRoutePoints().map { GeoPoint(it.latitude, it.longitude) }
    }

    fun getUpdateCount(): Int {
        return prefs.getInt(KEY_UPDATE_COUNT, 0)
    }

    private fun incrementUpdateCount() {
        val current = getUpdateCount()
        prefs.edit().putInt(KEY_UPDATE_COUNT, current + 1).apply()
    }

    suspend fun clearRoute() {
        clearLocalData()
        prefs.edit().putInt(KEY_UPDATE_COUNT, 0).apply()

        try {
            withContext(Dispatchers.IO) {
                routesCollection.document(getDeviceId()).delete().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear Firestore data", e)
        }
    }

    fun isTrackingActive(): Boolean {
        return prefs.getBoolean(KEY_IS_TRACKING, false)
    }

    fun setTrackingActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_IS_TRACKING, active).apply()
    }

    suspend fun exportData(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val points = getRoutePoints()
                if (points.isNotEmpty()) {
                    val route = Route(
                        deviceId = getDeviceId(),
                        startTimestamp = points.firstOrNull()?.timestamp ?: 0L,
                        points = points,
                        updatedAt = System.currentTimeMillis()
                    )
                    gson.toJson(route)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                null
            }
        }
    }
}
