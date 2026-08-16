# Musync 🎵

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="100" height="100" alt="Musync Logo" />
</p>

<p align="center">
  <b>A modern, high-performance Android music streaming application built with Kotlin, Jetpack Compose, AndroidX Media3/ExoPlayer, and a high-throughput TypeScript backend.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-v1.1.3.1-blue.svg?style=flat-square" alt="Version" />
  <img src="https://img.shields.io/badge/Platform-Android_8.0+-green.svg?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.0+-purple.svg?style=flat-square" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Media3-ExoPlayer-orange.svg?style=flat-square" alt="Media3" />
  <img src="https://img.shields.io/badge/Backend-TypeScript_/_Node.js-blue.svg?style=flat-square" alt="Backend" />
  <img src="https://img.shields.io/badge/License-MIT-lightgrey.svg?style=flat-square" alt="License" />
</p>

---

## ✨ Features

- **Pure Audio Pipeline**: Single-player AndroidX Media3 / ExoPlayer architecture optimized for ultra-low latency playback.
- **Gapless Continuous Playback**: Zero-gap transitions between tracks with intelligent background preloading ($A \to B \to C$).
- **Atmospheric Ambient Glow**: GPU-accelerated frosted glass ambient blur based on cached HD artwork (zero extra bandwidth).
- **Hardware Equalizer & Effects**: Direct ExoPlayer audio session binding supporting a 5-band parametric equalizer, sub-woofer bass boost, 3D spatial surround virtualizer, and loudness enhancement.
- **Smart Relevance Search**: Multi-tier search engine with exact-match scoring (+100), prefix match (+60), artist match (+50), token matching, and deduplication.
- **Progressive Streaming Proxy**: Range-aware HTTP 206 audio streaming with automatic stale cache purging and retry resilience.
- **Offline Favorites & Playlists**: Room-backed local persistence for playlists, favorite tracks, and search history.
- **Background & Lock Screen Playback**: Full MediaSession integration with lockscreen controls, notification actions, and Bluetooth headset integration.

---

## 🏗️ Architecture Overview

```text
┌─────────────────────────────────────────────────────────────┐
│                    Musync Android Client                    │
│                                                             │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │   Jetpack Compose UI  │       │  MusicPlaybackService │  │
│  │   - NowPlayingSheet   │ ────> │  - AndroidX Media3    │  │
│  │   - AtmosphericGlow   │       │  - ExoPlayer Audio    │  │
│  │   - AudioEffectPanel  │       │  - AudioEffectManager │  │
│  └───────────────────────┘       └───────────────────────┘  │
│             │                                │              │
└─────────────┼────────────────────────────────┼──────────────┘
              │ Range Audio Streams            │
              ▼                                ▼
┌─────────────────────────────────────────────────────────────┐
│                  Node.js / Express Backend                  │
│                                                             │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │     StreamManager     │       │  Search / Meta Engine │  │
│  │  - Progressive HTTP   │ <───> │  - Multi-tier queries │  │
│  │  - L1 / L2 Caching    │       │  - Relevance Scoring  │  │
│  │  - 403 Auto-Recovery  │       │  - ytmusic-api        │  │
│  └───────────────────────┘       └───────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📂 Pristine Project Structure on `main`

```text
Musync/
├── app/                           # Android Kotlin Application
│   ├── src/main/java/com/musync/app/
│   │   ├── data/                  # Room DB, Providers, Repositories
│   │   ├── di/                    # MusyncContainer Dependency Injection
│   │   ├── domain/                # Pure Audio Domain Models & Repositories
│   │   ├── playback/              # Media3 Service, ExoPlayer, AudioEffects, Preload
│   │   ├── ui/                    # Compose UI Screens, Navigation, Player & Sheets
│   │   ├── update/                # App Update Manager
│   │   └── util/                  # Image Quality & Network Helpers
│   ├── src/main/res/              # Android Vector Drawables, Layouts & Themes
│   ├── src/test/                  # Unit & Integration Tests
│   └── build.gradle.kts           # Unified Application ID & Keystore Configuration
├── src/                           # Backend TypeScript Engine
│   ├── cache/                     # L1 / L2 Shared Redis & In-Memory Cache Service
│   ├── metrics/                   # Stream Diagnostics & Latency Metrics
│   └── streaming/                 # Range-Aware Progressive Audio Stream Manager
├── server.ts                      # High-Capacity Express API Server (Relevance Search)
├── stream_resolver.py             # yt-dlp Pure Audio Stream Extractor
├── Dockerfile & Dockerfile.node   # Production Container Configs (Python 3 + yt-dlp)
├── railway.json                   # Railway Production Deployment Config
├── requirements.txt               # Python Dependencies (yt-dlp)
├── package.json                   # Node.js Dependencies & Scripts
├── tsconfig.json                  # TypeScript Compiler Configuration
└── README.md                      # Project Documentation
```

---

## 💻 Complete Local Server Setup Guide

Follow these steps to run your own high-performance Musync streaming backend locally.

### 1. Prerequisites
Ensure you have the following installed on your system:
- **Node.js**: v20.0.0 or newer ([Download Node.js](https://nodejs.org/))
- **Python**: v3.10 or newer ([Download Python](https://www.python.org/))
- **ffmpeg**: Installed and added to system `PATH` ([Download FFmpeg](https://ffmpeg.org/download.html))
- **Git**: Installed ([Download Git](https://git-scm.com/))
- *(Optional)* **Redis**: For distributed caching (the server automatically uses in-memory L1 cache if Redis is absent).

---

### 2. Clone the Repository
```bash
git clone https://github.com/gowthamkrishna27/Musync.git
cd Musync
```

---

### 3. Install Dependencies

#### Install Node.js Packages:
```bash
npm install
```

#### Install Python Stream Resolver Dependency (`yt-dlp`):
```bash
# Windows
pip install -r requirements.txt

# macOS / Linux
pip3 install -r requirements.txt
```

---

### 4. Configure Environment Variables (Optional)
Create a `.env` file in the project root:
```env
PORT=5000
NODE_ENV=development
# Optional: Connect to your Redis server (defaults to in-memory L1 if unset)
# REDIS_URL=redis://localhost:6379
```

---

### 5. Start the Server

#### Development Mode (with Live Hot-Reloading):
```bash
npm run dev
```

#### Production Mode:
```bash
npm start
```

The server will initialize `ytmusic-api` and start listening on port `5000`:
```text
✓ YTMusic API initialized successfully.
🚀 Musync High-Performance Streaming Server listening on port 5000
```

---

### 6. Verify Server Endpoints

Test that the local server is operating correctly using your browser or `curl`:

| Endpoint | Purpose | Example |
| :--- | :--- | :--- |
| `GET /search` | Relevance-ranked song search | `http://localhost:5000/search?query=Starboy` |
| `GET /suggestions` | Real-time query autocomplete | `http://localhost:5000/suggestions?query=The+Weeknd` |
| `GET /stream` | High-throughput audio range stream | `http://localhost:5000/stream?id=dQw4w9WgXcQ` |
| `GET /trending` | Global Trending Hits | `http://localhost:5000/trending` |

---

### 7. Connect the Android App to Your Local Server

1. Find your machine's local IP address (e.g. `192.168.1.100` via `ipconfig` on Windows or `ifconfig` on macOS/Linux).
2. Open the **Musync** app on your Android device (ensure device is on the same Wi-Fi).
3. Go to **Settings** → **Custom Server URL** and enter:
   ```text
   http://192.168.1.100:5000
   ```
4. The Android app will instantly route all search, metadata, and audio streams through your local server!

---

### 🐳 Running with Docker

You can also run the entire backend with Docker in a single command:

```bash
# Build the Docker image
docker build -t musync-server .

# Run the container
docker run -p 5000:5000 musync-server
```

---

## 📱 Android Client Build Guide

1. Open the project in **Android Studio Ladybug (or newer)**.
2. Ensure JDK 21 is configured.
3. Build the release or debug APK:
   ```bash
   # Windows
   .\gradlew.bat assembleRelease

   # macOS / Linux
   ./gradlew assembleRelease
   ```
4. The APK will be generated at:
   ```text
   app/build/outputs/apk/release/app-release.apk
   ```

---

## 📄 License
This project is licensed under the MIT License.
