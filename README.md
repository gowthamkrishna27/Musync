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
  <img src="https://img.shields.io/badge/License-MIT-lightgrey.svg?style=flat-square" alt="License" />
</p>

---

## ✨ Features

- **Pure Audio Pipeline**: Single-player AndroidX Media3 / ExoPlayer architecture optimized for ultra-low latency playback.
- **Gapless Continuous Playback**: Zero-gap transitions between tracks with intelligent background preloading.
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

## 📦 Project Structure

```text
Musync/
├── app/                      # Android Application (Kotlin / Jetpack Compose)
│   ├── src/main/java/com/musync/app/
│   │   ├── data/             # Repositories, Room Database & Remote API Providers
│   │   ├── di/               # MusyncContainer Dependency Injection
│   │   ├── domain/           # Models, State & Core Contracts
│   │   ├── playback/         # Media3 Service, ExoPlayer, AudioEffects, Preload
│   │   ├── ui/               # Compose Screens, Navigation, Sheets & Components
│   │   └── util/             # Image Quality & Network Helpers
│   └── build.gradle.kts      # Android Build Configuration
├── src/                      # Backend TypeScript Source
│   ├── cache/                # L1 / L2 Redis & In-Memory Cache Service
│   ├── metrics/              # Stream Diagnostics & Latency Metrics
│   └── streaming/            # Range-Aware Progressive Audio Stream Manager
├── server.ts                 # High-Capacity Express API Server
├── stream_resolver.py        # yt-dlp Audio Format Stream Extractor
├── Dockerfile                # Production Container Build Config
└── package.json              # Node.js Dependencies
```

---

## 🚀 Getting Started

### Android App
1. Open the project in **Android Studio Ladybug (or newer)**.
2. Ensure JDK 21+ is configured (`JAVA_HOME`).
3. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```

### Backend Server
1. Install Node.js dependencies:
   ```bash
   npm install
   ```
2. Start development server:
   ```bash
   npx tsx server.ts
   ```

---

## 📄 License
This project is licensed under the MIT License.
