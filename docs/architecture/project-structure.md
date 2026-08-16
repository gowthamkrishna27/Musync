# Project Structure Documentation

## Root Overview

```text
Musync/
├── android/                             # Native Android client (Kotlin / Jetpack Compose / Media3)
├── backend/                             # Streaming proxy, API gateway & cache cluster (Node.js / TS / Python)
├── docs/                                # Project documentation (API, Architecture, Development)
├── README.md                            # GitHub project introduction & quickstart
├── LICENSE                              # Open-source MIT License
├── .gitignore                           # Git ignore rules for Android, Node.js, Python, and OS files
└── .editorconfig                        # Code formatting configuration across IDEs
```

---

## Department Ownership

### 1. `android/`
Contains the complete native Android project.
* **`app/src/main/AndroidManifest.xml`**: Defines application permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `VIBRATE`, `POST_NOTIFICATIONS`, `READ_MEDIA_AUDIO`), `MainActivity`, `MusicPlaybackService`, and deep links.
* **`app/src/main/java/com/musync/app/`**:
  * `MainActivity.kt`: Entry activity hosting Jetpack Compose navigation.
  * `MusyncApplication.kt`: Application class creating `MusyncContainer` and setting up notification channels.
  * `auth/`: Authentication manager and user state.
  * `core/`: Dependency container, network quality monitor, image sizing, and media item mapper.
  * `data/`: Room database, entities, DAOs, DataStore preferences, local media scanner, remote music providers, repositories, and Firestore cloud sync.
  * `domain/`: Pure Kotlin data models (`Track`, `Artist`, `Album`, `Playlist`), provider interfaces, and repository contracts.
  * `playback/`: Media3 `MusicPlaybackService`, `PlaybackManager`, 200MB `SimpleCache`, `TrackPreloadManager`, `BeatDetectorAudioProcessor`, `BeatHapticManager`, and hardware `AudioEffectManager`.
  * `ui/`: Compose design system, themes, icons, screens (Home, Search, Library, Settings, Playlist Detail), sheets (Now Playing, Queue, Auth), and floating glassmorphic navigation bar.
  * `update/`: `AppUpdateManager` for GitHub Releases OTA updates.
* **`app/src/main/res/`**: Vector drawables, mipmaps, colors, strings, themes, and file provider XMLs.
* **`app/src/test/`**: Unit and integration test suites (`AudiusMappingTest`, `PlaybackIntegrationTest`, `QueueTest`, `MusicRepositoryTest`).
* **`build.gradle.kts` & `settings.gradle.kts`**: Android Gradle build configuration targeting SDK 35 and Java 21.

### 2. `backend/`
Contains the server-side API, streaming proxy, and audio extractor.
* **`src/server.ts`**: Express application exposing search, stream, metadata, metrics, health, and OTA update endpoints.
* **`src/streaming/streamManager.ts`**: High-concurrency audio range proxy with process semaphore and socket connection pooling.
* **`src/cache/cacheService.ts`**: Two-tier caching (L1 in-memory + L2 Redis) with single-flight request coalescing.
* **`src/metrics/metricsService.ts`**: Performance metrics and latency histogram collection.
* **`scripts/stream_resolver.py`**: Python 3 script using `yt-dlp` to resolve signed audio streams.
* **`Dockerfile` & `Dockerfile.node`**: Container configurations for production deployments.
* **`railway.json`**: Railway cloud deployment blueprint.
* **`package.json` & `tsconfig.json`**: Node.js dependencies and TypeScript compiler settings.
* **`requirements.txt`**: Python dependencies (`yt-dlp`).

### 3. `docs/`
Structured technical documentation:
* **`docs/architecture/`**: System architecture, Android client, backend gateway, playback engine, data layer, auth, caching, streaming pipeline, and project structure.
* **`docs/api/`**: Endpoint documentation for search, streaming, metadata, lyrics, health, and updates.
* **`docs/development/`**: Local setup, Android development, backend development, environment variables, build steps, testing, deployment, troubleshooting, and contributing guidelines.
