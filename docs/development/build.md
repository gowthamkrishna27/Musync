# Build Guide

## Android Client Builds

All Android builds are managed via Gradle from the `android/` directory.

### Build Requirements
* **JDK**: 21 (with `JAVA_HOME` pointing to JDK 21 installation)
* **Android SDK**: API 35 Build Tools

### 1. Build Debug APK

```bash
cd android
./gradlew assembleDebug
```

Output:
`android/app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Release APK

```bash
cd android
./gradlew assembleRelease
```

Output:
`android/app/build/outputs/apk/release/app-release.apk`

---

## Backend Builds

Managed via `npm` from the `backend/` directory.

### 1. TypeScript Production Compilation

```bash
cd backend
npm run build
```

This invokes `tsc` (TypeScript compiler) and emits compiled JavaScript into `backend/dist/`.

### 2. Docker Container Build

```bash
cd backend
docker build -t musync-backend:latest .
```

To run the container locally:

```bash
docker run -d -p 5000:5000 --name musync-api musync-backend:latest
```
