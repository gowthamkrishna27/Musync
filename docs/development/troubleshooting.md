# Troubleshooting Guide

## Common Issues & Solutions

---

### 1. Android Build: `JAVA_HOME is not set`

**Symptom**:
```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

**Resolution**:
Set `JAVA_HOME` to your JDK 21 installation path.
* **Windows (PowerShell)**:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  ```
* **Linux/macOS**:
  ```bash
  export JAVA_HOME=/path/to/jdk-21
  ```

---

### 2. Android: Stream Playback 502 Bad Gateway / Network Error

**Symptom**:
ExoPlayer logs `PlayerException` or HTTP 502 when attempting to play tracks.

**Cause & Resolution**:
1. Verify the backend service is running and accessible at the URL configured in Settings.
2. If running locally on an emulator, use `http://10.0.2.2:5000` (not `http://localhost:5000`).
3. If running on a physical device, ensure both phone and PC are on the same Wi-Fi network and firewalls allow inbound traffic on port 5000.
4. Check backend environment by visiting `http://<backend-host>:5000/debug/env` to verify `yt-dlp` and `python` status.

---

### 3. Backend: Python Resolver Semaphore Timeout

**Symptom**:
Backend logs `Stream resolver semaphore timeout after 20000ms`.

**Cause**:
More than 8 concurrent resolutions were queued, and Python processes took longer than 20 seconds to extract stream URLs.

**Resolution**:
1. Check upstream internet connectivity.
2. Upgrade `yt-dlp` to the latest release using `pip install -U yt-dlp`.
3. Enable Redis L2 caching (`REDIS_URL`) so stream URLs are shared across workers and do not require repeated extraction.

---

### 4. Android: Notification Permission Not Granted

**Symptom**:
Playback notification does not appear on Android 13+ (API 33+).

**Resolution**:
Musync requests `POST_NOTIFICATIONS` at startup in `MainActivity`. Ensure notification permissions are granted in the device settings under **Settings > Apps > Musync > Notifications**.
