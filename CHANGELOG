# Indoor Localization App

Light & WiFi indoor positioning system with Python backend for coordinate estimation and GPT-powered location-aware question answering.

This project is an indoor localization system combining an Android app and a Python inference server.  
It measures Wi-Fi and light signal strengths, collects data via the Android app, exports it to CSV, and predicts positions using Python. Additional AI features provide responses based on the detected location.

## Features

- Android App
  - Measures Wi-Fi RSSI and ambient light intensity
  - Data collection using `datacollector` module
  - CSV export (overwrites previous file to keep latest record)
  - Flat map display with current position
  - Developer Mode toggle to hide internal status
  - Quick questions: "Where is the restroom?" and "What is nearby?"
  
- Python Server
  - Receives CSV uploads from the app
  - Performs inference to determine current location
  - Aggregates multiple timestamps using majority vote
  - AI response feature based on user input and location

## Installation

### Android App
1. Open the project in **Android Studio**
2. Ensure necessary permissions are granted:
   - `ACCESS_FINE_LOCATION`
   - Ignore storage permissions for now (`WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`) to avoid errors
3. Build and run the app on a real device (emulator may not produce scan results)

### Python Server
1. Navigate to the server directory
2. Install required dependencies:
```bash
pip install -r requirements.txt
```
3. Run the server:
```bash
python -m uvicorn server:app --host 0.0.0.0 --port 8000
```
4.Ensure the app is connected to the correct IP address and port

### Usage
1. Launch the Android app
2. The app will start scanning Wi-Fi and light signals automatically
3. CSV files are continuously overwritten with the latest measurement
4. Python server processes the CSV and returns predicted location
5. Locations are updated on the app map in real time
6. Use quick questions to get AI-based responses

### Known Issues / Limitations
- First CSV upload may fail if csvPath is null
- Original scan logic may conflict if background scan intervals differ
- GPT API token configuration required for AI responses
- Hosting on network other than local IP is not fully tested
- Floor definition and coordinate modification buttons functionality unclear

Contributing
- Contributor usernames are listed in the CHANGELOG
- Please follow existing coding conventions and test features on real devices

© 2026 NCKU MCSLab All rights reserved.
This software is provided for research and educational purposes only. 
Commercial use, redistribution, or modification without prior written permission is prohibited.

