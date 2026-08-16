# Testing Guide

## Android Test Suite

The Android project includes unit and integration tests located under `android/app/src/test/java/com/musync/app/`.

### Testing Libraries Used
* **JUnit 4**: `junit:junit:4.13.2`
* **Kotlin Coroutines Test**: `kotlinx-coroutines-test:1.8.1`
* **Google Truth**: `com.google.truth:truth:1.4.4`

### Existing Test Suites

| Test File | Test Class | Focus Area |
| :--- | :--- | :--- |
| `data/AudiusMappingTest.kt` | `AudiusMappingTest` | Tests mapping from Audius API DTO models to Domain `Track` and `Artist` entities |
| `playback/QueueTest.kt` | `QueueTest` | Tests playback queue indexing, shuffling, repeating, next-track queuing, and edge cases |
| `repository/MusicRepositoryTest.kt` | `MusicRepositoryTest` | Tests `MusicRepositoryImpl` interactions with remote providers and caching DAOs |
| `playback/PlaybackIntegrationTest.kt` | `PlaybackIntegrationTest` | End-to-end integration test querying `/search` on a live backend server and validating audio chunk range streaming |

---

## Running Android Tests

### Run All Unit Tests

From `android/`:

```bash
./gradlew testDebugUnitTest
```

### Run Specific Test Classes

```bash
./gradlew testDebugUnitTest --tests "com.musync.app.data.AudiusMappingTest"
./gradlew testDebugUnitTest --tests "com.musync.app.playback.QueueTest"
./gradlew testDebugUnitTest --tests "com.musync.app.repository.MusicRepositoryTest"
```

### Test Reports

After running tests, Gradle generates an HTML report at:
`android/app/build/reports/tests/testDebugUnitTest/index.html`

---

## Backend Validation

### 1. TypeScript Compiler Check

```bash
cd backend
npm run build
```

### 2. Stream Resolver CLI Test

```bash
cd backend
python scripts/stream_resolver.py 3_g2un5M350 low audio
```
