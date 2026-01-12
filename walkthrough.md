# Miles Tracker - Android App Walkthrough

## Project Created
**Location**: [miles-tracker](file:///c:/Users/fahmi/Desktop/miles/miles-tracker)

## Features Implemented

| Feature | Status |
|---------|--------|
| Foreground service with persistent notification | ✅ |
| Speed display (km/h) in notification | ✅ |
| Lat/Long coordinates in notification | ✅ |
| Manual start/stop button | ✅ |
| Auto-start at speed > 10km/h | ✅ |
| OSM map with current location | ✅ |
| Route line drawing | ✅ |
| Location update counter (#value) | ✅ |
| Data persistence (route saved) | ✅ |
| Route restoration on app reopen | ✅ |

---

## Project Structure

```
miles-tracker/
├── app/src/main/
│   ├── java/com/milestracker/app/
│   │   ├── MainActivity.kt          # Main UI with map and controls
│   │   ├── LocationTrackingService.kt  # Foreground service
│   │   ├── LocationRepository.kt     # Data persistence
│   │   └── SpeedDetector.kt          # Auto-start detection
│   ├── res/
│   │   ├── layout/activity_main.xml  # UI layout
│   │   ├── values/                   # Colors, strings, themes
│   │   └── drawable/                 # Icons and backgrounds
│   └── AndroidManifest.xml           # Permissions & components
├── build.gradle.kts                   # Dependencies
└── settings.gradle.kts
```

---

## How to Build & Test

1. **Open in Android Studio**
   - File → Open → Select `miles-tracker` folder
   - Wait for Gradle sync to complete

2. **Build the app**
   - Build → Make Project (or Ctrl+F9)

3. **Run on device**
   - Connect Android phone via USB (enable USB debugging)
   - Click Run (green play button)
   - Grant all permissions when prompted

4. **Test Features**
   - Tap "START TRACKING" to begin
   - Walk/drive to see location updates and route line
   - Check notification shows speed and coordinates
   - Close and reopen app - route should persist

---

## Key Files

| File | Purpose |
|------|---------|
| [MainActivity.kt](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/java/com/milestracker/app/MainActivity.kt) | UI, permissions, map integration |
| [LocationTrackingService.kt](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/java/com/milestracker/app/LocationTrackingService.kt) | Foreground service, notification |
| [LocationRepository.kt](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/java/com/milestracker/app/LocationRepository.kt) | SharedPreferences data storage |
| [activity_main.xml](file:///c:/Users/fahmi/Desktop/miles/miles-tracker/app/src/main/res/layout/activity_main.xml) | Dark theme UI layout |
