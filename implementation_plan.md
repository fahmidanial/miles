# Android Location Tracking App - Implementation Plan

Build an Android app with background location tracking, speed display, OSM map integration, and route visualization.

## User Review Required

> [!IMPORTANT]
> **Android Studio Required**: You'll need Android Studio installed to build and test this app on a physical device or emulator.

> [!IMPORTANT]
> **Physical Device Recommended**: Testing location tracking with speed detection works best on a real Android device during movement.

---

## Proposed Changes

### 1. Project Setup

#### [NEW] [miles-tracker](file:///c:/Users/fahmi/Desktop/miles/miles-tracker)
Create new Android project using Kotlin with:
- Minimum SDK: API 26 (Android 8.0)
- Target SDK: API 34
- Gradle with Kotlin DSL

#### [NEW] [build.gradle.kts](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/build.gradle.kts)
Dependencies:
- `com.google.android.gms:play-services-location` - Fused Location Provider
- `org.osmdroid:osmdroid-android` - OpenStreetMap Android
- AndroidX Core, AppCompat, ConstraintLayout

#### [NEW] [AndroidManifest.xml](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/AndroidManifest.xml)
Permissions:
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`
- `INTERNET`, `ACCESS_NETWORK_STATE`

---

### 2. Core Location Service

#### [NEW] [LocationTrackingService.kt](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/java/.../LocationTrackingService.kt)
Foreground service that:
- Uses FusedLocationProviderClient for accurate location
- Shows persistent notification with speed (km/h) and lat/long
- Auto-starts when speed exceeds 10 km/h
- Saves location points for route drawing
- Tracks location update counter

#### [NEW] [LocationRepository.kt](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/java/.../LocationRepository.kt)
- Stores route points using SharedPreferences (JSON)
- Provides location update count
- Clears/retrieves route data

---

### 3. UI Components

#### [NEW] [MainActivity.kt](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/java/.../MainActivity.kt)
- Start/Stop tracking button
- Location update counter display
- OSM MapView with current location
- Route polyline overlay

#### [NEW] [activity_main.xml](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/res/layout/activity_main.xml)
Intuitive prototyping UI with:
- Large "START TRACKING" button
- Counter badge showing updates (#value)
- Full-screen OSM map

---

## Verification Plan

### Build Verification
1. Open project in Android Studio
2. Build project: `./gradlew assembleDebug`
3. Verify no compilation errors

### Manual Testing on Device
1. **Install app** on Android device
2. **Grant all permissions** when prompted
3. **Test manual start**: Tap "Start" button, verify notification appears with speed/lat-long
4. **Test location updates**: Walk/drive, verify counter increments and lat-long changes in notification
5. **Test route drawing**: Verify blue line appears on map as you move
6. **Test persistence**: Close app, reopen, verify route is still visible
7. **Test auto-start**: Set speed threshold, move at >10km/h, verify service auto-starts

> [!NOTE]
> For speed testing, you may need to drive or use a GPS spoofing app on emulator.
