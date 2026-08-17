# Android Development Guide

## Build & Tooling Specifications

| Component | Target Version | Source Configuration |
| :--- | :--- | :--- |
| **Android Gradle Plugin (AGP)** | `8.7.3` | `android/build.gradle.kts` |
| **Kotlin** | `2.0.21` | `android/build.gradle.kts` |
| **Kotlin Symbol Processing (KSP)**| `2.0.21-1.0.28` | `android/build.gradle.kts` |
| **Compose Compiler Plugin** | Kotlin 2.0 Compose Plugin | `android/app/build.gradle.kts` |
| **Google Services Plugin** | `4.4.2` | `android/build.gradle.kts` |
| **compileSdk** | `35` (Android 15) | `android/app/build.gradle.kts` |
| **targetSdk** | `35` (Android 15) | `android/app/build.gradle.kts` |
| **minSdk** | `26` (Android 8.0 Oreo) | `android/app/build.gradle.kts` |
| **Java Compatibility** | `JavaVersion.VERSION_21` | `android/app/build.gradle.kts` |
| **JVM Target** | `21` | `android/app/build.gradle.kts` |
| **Version Code** | `1147` | `android/app/build.gradle.kts` |
| **Version Name** | `1.1.4.7` | `android/app/build.gradle.kts` |

---

## Key Android Dependencies

* **Compose UI & Material 3**: `androidx.compose:compose-bom:2024.09.02`
* **AndroidX Media3**: `1.4.1` (`media3-exoplayer`, `media3-session`, `media3-common`, `media3-ui`)
* **Room Database**: `2.6.1` (`room-runtime`, `room-ktx`, `room-compiler` via KSP)
* **Jetpack DataStore**: `1.1.1` (`datastore-preferences`)
* **Security**: `1.1.0-alpha06` (`security-crypto`)
* **Networking**: Retrofit `2.11.0`, OkHttp `4.12.0`, Gson converter
* **Image Loading**: Coil Compose `2.7.0`
* **Firebase BOM**: `33.9.0` (`firebase-auth`, `firebase-firestore`, `firebase-analytics`)
* **Coroutines**: `1.8.1`

---

## Building the Android Project

Navigate to the `android/` directory:

```bash
cd android
```

### Build Debug APK

On Windows:
```powershell
.\gradlew.bat assembleDebug
```

On Linux/macOS:
```bash
./gradlew assembleDebug
```

The compiled APK will be located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests

```bash
./gradlew testDebugUnitTest
```

### Clean Build Cache

```bash
./gradlew clean
```

---

## Connecting to a Local Backend

By default, the Android app connects to the remote Railway cloud gateway (`https://musync-production-2fc5.up.railway.app`).

To test against your local backend server:
1. Open the app on your phone or emulator.
2. Navigate to **Settings** (Gear icon on the bottom navigation pill).
3. Under **Backend Server Configuration**, enter your local IP address:
   * **Android Emulator**: `http://10.0.2.2:5000`
   * **Physical Device**: `http://<YOUR_LOCAL_IP>:5000` (e.g. `http://192.168.1.100:5000`)
4. Tap **Save Base URL** or **Test Connection**.
