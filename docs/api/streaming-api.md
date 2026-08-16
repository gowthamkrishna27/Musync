# Streaming API Documentation

## 1. Progressive Audio Stream

Direct high-performance progressive audio streaming proxy. Supports HTTP Range requests, automatic backpressure piping, client abort detection, and resilient stale-cache retries.

* **Method**: `GET`
* **Path**: `/stream` or `/stream/`
* **Rate Limit**: 60,000 requests / minute (allows high-frequency byte range chunks)
* **Status Codes**:
  * `200 OK` (Full audio stream)
  * `206 Partial Content` (Range chunk audio stream)
  * `400 Bad Request` (Missing track ID)
  * `502 Bad Gateway` (Upstream audio resolution failure)

### Query Parameters

| Parameter | Type | Required | Default | Allowed Values | Description |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `id` or `query` or `videoId` | `string` | Yes | - | YouTube Video ID (e.g. `3_g2un5M350`) | Target audio identifier |
| `quality` | `string` | No | `"low"` | `saver`, `low`, `standard`, `high` | Target audio bitrate / codec profile |

### Request Headers Supported

* `Range`: `bytes=0-1024`, `bytes=500000-`, `bytes=1000000-2000000`

### Response Headers

* `Content-Type`: `audio/webm` or `audio/mp4` or `video/mp4`
* `Accept-Ranges`: `bytes`
* `Content-Length`: `1025` (or total size of the requested range chunk)
* `Content-Range`: `bytes 0-1024/5622073`
* `Cache-Control`: `public, max-age=7200, no-transform`
* `X-Content-Duration`: `240` (present when chunked transfer encoding is active)

### Example Request

```http
GET /stream?id=3_g2un5M350&quality=low HTTP/1.1
Host: localhost:5000
Range: bytes=0-1024
User-Agent: Musync-Android/1.0
```

### Example Response Headers

```http
HTTP/1.1 206 Partial Content
Content-Type: video/mp4
Content-Range: bytes 0-1024/5622073
Content-Length: 1025
Accept-Ranges: bytes
Cache-Control: public, max-age=7200, no-transform
Date: Sun, 16 Aug 2026 14:27:34 GMT
Connection: keep-alive
```

---

## 2. Stream Preload & Pre-Warm

Proactively resolves and warms a signed audio stream URL in the backend's L1 in-memory cache and L2 Redis cache without streaming the raw audio payload.

* **Method**: `GET`
* **Path**: `/stream/preload` or `/preload`
* **Rate Limit**: 600 requests / minute

### Query Parameters

| Parameter | Type | Required | Default | Description |
| :--- | :---: | :---: | :---: | :--- |
| `id` or `query` or `videoId` | `string` | Yes | - | Target YouTube video ID |
| `quality` | `string` | No | `"low"` | Target audio quality |

### Response Example (`200 OK`)

```json
{
  "success": true,
  "videoId": "3_g2un5M350",
  "quality": "low",
  "mediaType": "audio",
  "source": "resolver",
  "durationMs": 742,
  "cached": false
}
```

### Cached Response Example (`200 OK`)

```json
{
  "success": true,
  "videoId": "3_g2un5M350",
  "quality": "low",
  "mediaType": "audio",
  "source": "l1",
  "durationMs": 1,
  "cached": true
}
```
