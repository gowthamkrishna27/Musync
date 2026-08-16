# Search & Discovery API

## 1. Search Tracks

Performs an intelligent, relevance-ranked multi-query search with deduplication across songs and video items.

* **Method**: `GET`
* **Path**: `/search` or `/result/`
* **Rate Limit**: 600 requests / minute
* **Cache TTL**: 45 minutes (`search:v2:<query>`)
* **Background Action**: Automatically pre-warms audio stream resolution for top 5 search hits in the background.

### Query Parameters

| Parameter | Type | Required | Default | Description |
| :--- | :---: | :---: | :---: | :--- |
| `query` or `q` | `string` | No | `"Trending"` | Search term (song title, artist, album, or keywords) |

### Response Example (`200 OK`)

```json
[
  {
    "videoId": "foQxargfUb8",
    "id": "foQxargfUb8",
    "songid": "foQxargfUb8",
    "title": "Starboy",
    "song": "Starboy",
    "singers": "The Weeknd, Daft Punk",
    "artist": "The Weeknd, Daft Punk",
    "album": "Starboy",
    "image_url": "https://i.ytimg.com/vi/foQxargfUb8/hq720.jpg",
    "image": "https://i.ytimg.com/vi/foQxargfUb8/hq720.jpg",
    "duration": "230",
    "duration_seconds": 230,
    "url": "http://localhost:5000/stream?id=foQxargfUb8",
    "media_url": "http://localhost:5000/stream?id=foQxargfUb8",
    "stream_url": "http://localhost:5000/stream?id=foQxargfUb8"
  }
]
```

### Error Response (`500 Internal Server Error`)

```json
{
  "error": "Failed to search songs",
  "data": []
}
```

---

## 2. Search Suggestions

Returns real-time autocomplete search predictions.

* **Method**: `GET`
* **Path**: `/suggestions`
* **Rate Limit**: 600 requests / minute
* **Cache TTL**: 1 hour (`sug:<query>`)

### Query Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `query` or `q` | `string` | Yes | Prefix string or partial query |

### Response Example (`200 OK`)

```json
[
  "starboy",
  "starboy lyrics",
  "starboy audio",
  "starboy remix",
  "starboy slowed"
]
```

---

## 3. Trending Tracks

Returns curated top global trending tracks.

* **Method**: `GET`
* **Path**: `/trending` or `/charts`
* **Rate Limit**: 600 requests / minute
* **Cache TTL**: 1 hour (`trending:global`)
* **Background Action**: Automatically pre-warms top 5 trending track stream URLs.

### Response Example (`200 OK`)

```json
[
  {
    "videoId": "kJQP7kiw5Fk",
    "id": "kJQP7kiw5Fk",
    "songid": "kJQP7kiw5Fk",
    "title": "Despacito",
    "song": "Despacito",
    "singers": "Luis Fonsi, Daddy Yankee",
    "artist": "Luis Fonsi, Daddy Yankee",
    "album": "VIDA",
    "image_url": "https://i.ytimg.com/vi/kJQP7kiw5Fk/hq720.jpg",
    "image": "https://i.ytimg.com/vi/kJQP7kiw5Fk/hq720.jpg",
    "duration": "229",
    "duration_seconds": 229,
    "url": "http://localhost:5000/stream?id=kJQP7kiw5Fk",
    "media_url": "http://localhost:5000/stream?id=kJQP7kiw5Fk",
    "stream_url": "http://localhost:5000/stream?id=kJQP7kiw5Fk"
  }
]
```
