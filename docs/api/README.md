# Musync API Documentation

## Overview

The Musync backend provides a RESTful HTTP API for audio streaming, search aggregation, metadata resolution, autocomplete suggestions, lyrics, server health monitoring, telemetry metrics, and OTA updates.

Base URL (Production): `https://musync-production-2fc5.up.railway.app`  
Base URL (Local): `http://localhost:5000`

---

## API Endpoints Index

| Endpoint | HTTP Method | Rate Limit | Description |
| :--- | :---: | :---: | :--- |
| [`/`](file:///c:/Users/gowth/Downloads/Musync/docs/api/endpoints.md#root--service-status) | `GET` | None | Service discovery and status overview |
| [`/health`](file:///c:/Users/gowth/Downloads/Musync/docs/api/health-api.md#health-check) | `GET` | None | Health check with cache and uptime metrics |
| [`/metrics`](file:///c:/Users/gowth/Downloads/Musync/docs/api/health-api.md#metrics) | `GET` | None | Performance metrics & p50/p95/p99 latency histogram |
| [`/debug/env`](file:///c:/Users/gowth/Downloads/Musync/docs/api/health-api.md#environment-diagnostics) | `GET` | None | Diagnostic endpoint inspecting runtime binaries (Node, Python, yt-dlp, ffmpeg) |
| [`/search`](file:///c:/Users/gowth/Downloads/Musync/docs/api/search-api.md#search-tracks) | `GET` | 600/min | Multi-query relevance-ranked song search |
| [`/suggestions`](file:///c:/Users/gowth/Downloads/Musync/docs/api/search-api.md#search-suggestions) | `GET` | 600/min | Real-time autocomplete search suggestions |
| [`/song`](file:///c:/Users/gowth/Downloads/Musync/docs/api/metadata-api.md#song-details) | `GET` | 600/min | Single track details and direct stream endpoint |
| [`/stream`](file:///c:/Users/gowth/Downloads/Musync/docs/api/streaming-api.md#progressive-audio-stream) | `GET` | 60,000/min | Progressive range-aware audio streaming proxy |
| [`/stream/preload`](file:///c:/Users/gowth/Downloads/Musync/docs/api/streaming-api.md#stream-preload--pre-warm) | `GET` | 600/min | Proactive stream URL pre-warming into L1/L2 Redis cache |
| [`/lyrics`](file:///c:/Users/gowth/Downloads/Musync/docs/api/lyrics-api.md#track-lyrics) | `GET` | 600/min | Track lyrics retrieval |
| [`/album`](file:///c:/Users/gowth/Downloads/Musync/docs/api/metadata-api.md#album-details) | `GET` | 600/min | Album metadata and tracklist |
| [`/artist`](file:///c:/Users/gowth/Downloads/Musync/docs/api/metadata-api.md#artist-details) | `GET` | 600/min | Artist details and song catalog |
| [`/trending`](file:///c:/Users/gowth/Downloads/Musync/docs/api/search-api.md#trending-tracks) | `GET` | 600/min | Top global trending tracks |
| [`/update/check`](file:///c:/Users/gowth/Downloads/Musync/docs/api/update-api.md#check-for-updates) | `GET` | None | In-app OTA version check |
| [`/update/latest.apk`](file:///c:/Users/gowth/Downloads/Musync/docs/api/update-api.md#download-latest-apk) | `GET` | None | Direct redirect to latest APK asset download |

---

## Global Headers & Behavior

* **`trust proxy`**: Enabled for reverse proxies (Railway, Cloudflare, Nginx).
* **CORS**: `cors()` middleware enabled globally.
* **JSON Body Parser**: Enabled for JSON payloads.
* **Rate Limiting**:
  * Standard API queries: 600 requests per minute per IP.
  * Audio stream range chunks: 60,000 chunks per minute per IP.
