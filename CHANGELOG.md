2025-08-30 (Contribute: @0yufeng)
- Refactored Android permission handling: split checkAndRequestPermissions into checkPermissions() and requestPermissions().
- Currently only checks ACCESS_FINE_LOCATION; storage permissions ignored to avoid errors.
- Enables RSSI and light intensity signal collection on actual devices.

2025-09-01
- Added train function to generate model weights.
- Added transform_inference to process CSV raw data from mobile and obtain predictions.
- Training/testing on original data succeeded with expected accuracy.
- Considered varying Wi-Fi APs in training/testing; not yet tested on different APs.

2025-09-03
- Added datacollector Android module from previous 0830 app.
- Converted buttons in original app into callable functions for integration.
- Integrated datacollector into main app with automatic scan & CSV export loop.
- Added UI section for displaying datacollector status.
- Known issues: scan loop conflicts with original 3-second scan interval; CSV export overwrites old data; floor/coordinate buttons not yet understood; GPT API integration untested; CSV upload to PC for inference not implemented.

2025-09-04 (Contribute: @0yufeng)
- Updated CSV handling in DataCollector.java to overwrite file continuously, keeping only latest timestamp.
- Added CSV path handling, upload service, and minor Retrofit client adjustments.
- Added loca.py for modeling calculations based on uploaded CSV.

2025-09-04
Python inference: each CSV upload produces a predicted position; multiple predictions aggregated by majority vote.

2025-09-05 (Contribute: @0yufeng)
- Fixed loca.py; tested successfully.
- Added README with usage instructions: measuring Wi-Fi/light signals, CSV export, CSV upload, AI response.
- Pending improvements: UI, CSV path initialization, AI API token setup.
- Incomplete: sending data to AI for current position; hosting Python server on non-local IP.

2025-09-06 (Contribute: @0yufeng)
- Added uploadedPosition variable to store coordinates from signals.
- Integrated coordinates with chinese_api.py.
- Removed unused AIService.kt.

2025-09-06
- Added map view: zoom, pan, display current position, updates on each CSV upload.
- Added developer mode toggle to hide datacollector info.
- Added two quick question shortcuts: “Where is the restroom?” and “What is nearby?”

2025-10-27 (Contribute: @0yufeng)
- Merged two apps into one.
- Improved continuous CSV update: single file overwritten, reducing memory usage.
