 # BackgroundLocationTracking
# Important feature that must
- I want to view speed on foreground service notification
- I want to view lat log that always running on foreground service notification. Make sure it is not static and always changing if phone is moving
- Automatic turn on the location tracking if speed more than 10kms and also have button for start manually
- I want OSM to view accurate location based on lat log on the map. In OSM I also want route direction if phone is moving
- 
- # UI behavior
- When first time user open the application I want to see button start that can start manually the location tracking and OSM 
- If phone is moving in speed 10kms it will run in foreground service notification and make a route direction line everytime the phone is moving
- I want to see everytime phone detect movement it will create value how may location have been updated #<value> 
- Make the ui just for prototyping but intuitive

- # Data saving
- make it simple for testing purpose to see data is saving and route line direction can be seen by user always when close and open again the map

- # Tech Stack
- Kotlin
- database sqlite