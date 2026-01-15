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
        private const val KEY_CURRENT_CLASSIFICATION = "current_classification"
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
        val classification: String = "Unclassified",
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

    fun setTripClassification(classification: String) {
        prefs.edit().putString(KEY_CURRENT_CLASSIFICATION, classification).apply()
    }

    fun getTripClassification(): String {
        return prefs.getString(KEY_CURRENT_CLASSIFICATION, "Unclassified") ?: "Unclassified"
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

    private val tripsCollection = firestore.collection("users").document(getDeviceId()).collection("trips")

    // ... existing code ...

    suspend fun saveCurrentRouteToHistory() {
        withContext(Dispatchers.IO) {
            try {
                val points = getLocalRoutePoints()
                if (points.isNotEmpty()) {
                    val route = Route(
                        deviceId = getDeviceId(),
                        startTimestamp = points.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                        points = points,
                        classification = getTripClassification(),
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    // Use start timestamp as document ID for easy sorting/finding
                    tripsCollection.document(route.startTimestamp.toString())
                        .set(route)
                        .await()
                        
                    Log.d(TAG, "Trip saved to history: ${route.startTimestamp}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save trip history", e)
                throw e
            }
        }
    }

    suspend fun getTripHistory(): List<Route> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = tripsCollection
                    .orderBy("startTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()
                
                return@withContext snapshot.documents.mapNotNull { it.toObject(Route::class.java) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get trip history", e)
                emptyList()
            }
        }
    }

    suspend fun updateTripClassification(timestamp: Long, newClassification: String) {
        withContext(Dispatchers.IO) {
            try {
                tripsCollection.document(timestamp.toString())
                    .update("classification", newClassification)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update classification", e)
            }
        }
    }

    suspend fun deleteTrip(timestamp: Long) {
        withContext(Dispatchers.IO) {
            try {
                tripsCollection.document(timestamp.toString())
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete trip", e)
            }
        }
    }

    suspend fun exportRouteToCsv(routeToExport: Route? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val points = if (routeToExport != null) {
                    routeToExport.points
                } else {
                    // Use local points directly to ensure we export the latest tracked data without network delay/staleness
                    getLocalRoutePoints()
                }
                
                if (points.isEmpty()) return@withContext null

                val sortedPoints = points.sortedBy { it.timestamp }
                val startPoint = sortedPoints.first()
                val endPoint = sortedPoints.last()

                var totalDistanceMeters = 0.0
                for (i in 0 until sortedPoints.size - 1) {
                    totalDistanceMeters += calculateDistance(
                        sortedPoints[i].latitude, sortedPoints[i].longitude,
                        sortedPoints[i+1].latitude, sortedPoints[i+1].longitude
                    )
                }
                val totalDistanceKm = totalDistanceMeters / 1000.0

                val startDate = java.util.Date(startPoint.timestamp)
                val endDate = java.util.Date(endPoint.timestamp)
                
                // Format: YYYY-MM-DD
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val dateStr = dateFormat.format(startDate)

                // Format: h:mm a
                val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                val startTimeStr = timeFormat.format(startDate)
                val endTimeStr = timeFormat.format(endDate)

                val durationMillis = endPoint.timestamp - startPoint.timestamp
                val durationHours = durationMillis / (1000.0 * 60.0 * 60.0)

                // Kilometers,From,To,Date,Purpose,Business Line,Time Started,Time Ended,Duration (hours),s,To Full Address,From Full Address
                
                // Using "lat, lng" format for address fields as requested
                val fromAddress = "${startPoint.latitude}, ${startPoint.longitude}"
                val toAddress = "${endPoint.latitude}, ${endPoint.longitude}"
                
                val row = StringBuilder()
                row.append(String.format("%.2f", totalDistanceKm)).append(",") // Kilometers
                row.append("\"$fromAddress\"").append(",") // From
                row.append("\"$toAddress\"").append(",") // To
                row.append(dateStr).append(",") // Date
                row.append(if (routeToExport != null) routeToExport.classification else getTripClassification()).append(",") // Purpose (Food Services etc)
                row.append("").append(",") // Business Line
                row.append(startTimeStr).append(",") // Time Started
                row.append(endTimeStr).append(",") // Time Ended
                row.append(String.format("%.1f", durationHours)).append(",") // Duration (hours)
                row.append("").append(",") // s
                row.append("\"$toAddress\"").append(",") // To Full Address
                row.append("\"$fromAddress\"") // From Full Address

                return@withContext row.toString()
            } catch (e: Exception) {
                Log.e(TAG, "CSV Export failed", e)
                null
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return r * c
    }
}
