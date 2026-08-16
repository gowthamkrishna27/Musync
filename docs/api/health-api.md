# Health & Telemetry API Documentation

## 1. Health Check

Returns the operational status of the streaming server, initialization state of the YouTube Music client, active stream count, and cache statistics.

* **Method**: `GET`
* **Path**: `/health`
* **Rate Limit**: None
* **Status Code**: `200 OK`

### Response Example

```json
{
  "status": "healthy",
  "ytmusic": true,
  "cache": {
    "l1Size": 12,
    "l1Hits": 38,
    "l1Misses": 5,
    "l2Hits": 0,
    "l2Misses": 0,
    "singleFlightJoins": 2,
    "redisConnected": false
  },
  "activeStreams": 0,
  "uptimeSeconds": 142,
  "runtime": "Node.js / TypeScript / Enterprise Cluster"
}
```

---

## 2. Performance Metrics

Exposes live Prometheus-compatible performance metrics, rolling latency percentiles (p50, p95, p99), memory usage, and Node.js event loop lag.

* **Method**: `GET`
* **Path**: `/metrics`
* **Rate Limit**: None
* **Status Code**: `200 OK`

### Response Example

```json
{
  "timestamp": "2026-08-16T14:27:35.120Z",
  "metrics": {
    "uptimeSeconds": 145,
    "totalRequests": 120,
    "activeStreams": 1,
    "bytesServed": 1048576,
    "rangeRequestsCount": 85,
    "latencyHistogram": {
      "p50Ms": 4,
      "p95Ms": 42,
      "p99Ms": 120,
      "avgMs": 12,
      "maxMs": 722,
      "sampleSize": 120
    },
    "memory": {
      "rssMb": 115,
      "heapUsedMb": 62,
      "heapTotalMb": 85,
      "externalMb": 8
    },
    "eventLoopLagMs": 0
  },
  "cache": {
    "l1Size": 12,
    "l1Hits": 38,
    "l1Misses": 5,
    "l2Hits": 0,
    "l2Misses": 0,
    "singleFlightJoins": 2,
    "redisConnected": false
  }
}
```

---

## 3. Environment Diagnostics

Inspects host system binaries, Python runtime, `yt-dlp` availability, `ffmpeg` version, and executes an audio stream extraction test.

* **Method**: `GET`
* **Path**: `/debug/env`
* **Rate Limit**: None
* **Status Code**: `200 OK`

### Response Example

```json
{
  "platform": "win32",
  "arch": "x64",
  "nodeVersion": "v22.10.1",
  "port": 5000,
  "pythonBin": "python",
  "pythonVersion": "Python 3.14.0",
  "ytdlpVersion": "2026.07.04",
  "ffmpegVersion": "ffmpeg version 7.1",
  "streamResolverTest": {
    "success": true,
    "source": "resolver",
    "error": null
  }
}
```
