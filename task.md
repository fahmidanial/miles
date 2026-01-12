# Android Location Tracking App - Task List

## Planning Phase
- [x] Review PLAN.md requirements
- [x] Create implementation plan
- [x] Get user approval on plan

## Project Setup
- [x] Create Android project structure with Kotlin
- [x] Configure Gradle dependencies (Location, OSM, etc.)
- [x] Set up AndroidManifest with required permissions

## Core Features Implementation
- [x] Implement Location Service (Foreground Service)
  - [x] Speed display in notification
  - [x] Lat/Long display in notification
  - [x] Auto-start when speed > 10km/h (SpeedDetector)
  - [x] Manual start/stop control
- [x] Implement Location Updates Counter
- [x] Implement OSM Map Integration
  - [x] Display current location accurately
  - [x] Draw route line when moving
  - [x] Persist route data

## UI Implementation
- [x] Create main activity with Start button
- [x] Add location update counter display
- [x] Integrate OSM MapView
- [x] Design intuitive prototyping UI

## Data Persistence
- [x] Save location/route data (SharedPreferences + Gson)
- [x] Restore route on app reopen

## Verification
- [ ] Open in Android Studio and build
- [ ] Test on physical device
