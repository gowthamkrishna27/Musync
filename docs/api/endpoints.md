# Musync API Endpoints Reference

## Overview

Complete specification for all REST endpoints provided by the Musync backend gateway (`server.ts`).

---

## Root & Service Status

### `GET /`

Returns the service operational status and endpoint registry.

* **Method**: `GET`
* **Path**: `/`
* **Authentication**: None
* **Status Code**: `200 OK`

#### Response Example
```json
{
  "status": "online",
  "service": "Musync High-Performance Audio Gateway & Streaming Cluster",
  "version": "4.0.0",
  "initialized": true,
  "endpoints": {
    "search": "/search?query=<song_or_artist>",
    "suggestions": "/suggestions?query=<text>",
    "song": "/song?id=<video_id>",
    "stream": "/stream?id=<video_id>",
    "lyrics": "/lyrics?id=<video_id>",
    "album": "/album?id=<album_id>",
    "artist": "/artist?id=<artist_id>",
    "trending": "/trending",
    "metrics": "/metrics",
    "health": "/health",
    "debug": "/debug/env"
  }
}
```

---

## Detailed Endpoint Documentation

For specific endpoint specifications, refer to:

* [Search & Discovery API (`/search`, `/suggestions`, `/trending`)](file:///c:/Users/gowth/Downloads/Musync/docs/api/search-api.md)
* [Streaming API (`/stream`, `/stream/preload`)](file:///c:/Users/gowth/Downloads/Musync/docs/api/streaming-api.md)
* [Metadata API (`/song`, `/album`, `/artist`)](file:///c:/Users/gowth/Downloads/Musync/docs/api/metadata-api.md)
* [Lyrics API (`/lyrics`)](file:///c:/Users/gowth/Downloads/Musync/docs/api/lyrics-api.md)
* [Health & Telemetry API (`/health`, `/metrics`, `/debug/env`)](file:///c:/Users/gowth/Downloads/Musync/docs/api/health-api.md)
* [OTA Update API (`/update/check`, `/update/latest.apk`)](file:///c:/Users/gowth/Downloads/Musync/docs/api/update-api.md)
