# Metadata API Documentation

## 1. Song Details

Retrieves metadata for a specific song and generates its direct streaming URL.

* **Method**: `GET`
* **Path**: `/song` or `/song/`
* **Rate Limit**: 600 requests / minute
* **Cache TTL**: 24 hours (`song_meta:<videoId>`)

### Query Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `id` or `query` | `string` | Yes | Target YouTube video ID |

### Response Example (`200 OK`)

```json
{
  "videoId": "3_g2un5M350",
  "id": "3_g2un5M350",
  "title": "Starboy",
  "artist": "The Weeknd",
  "url": "http://localhost:5000/stream?id=3_g2un5M350",
  "media_url": "http://localhost:5000/stream?id=3_g2un5M350",
  "stream_url": "http://localhost:5000/stream?id=3_g2un5M350"
}
```

---

## 2. Album Details

Retrieves complete tracklist and metadata for a specified album ID.

* **Method**: `GET`
* **Path**: `/album`
* **Rate Limit**: 600 requests / minute
* **Cache TTL**: 24 hours (`album:<albumId>`)

### Query Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `id` or `albumId` | `string` | Yes | YouTube Music Browse Album ID (e.g. `MPREb_...`) |

### Response Example (`200 OK`)

```json
{
  "albumId": "MPREb_kS2q68c1z9X",
  "title": "After Hours",
  "artist": {
    "name": "The Weeknd",
    "artistId": "UC0WP5P-ufpRfjbNrmOWwLBQ"
  },
  "year": "2020",
  "thumbnails": [
    {
      "url": "https://lh3.googleusercontent.com/...",
      "width": 544,
      "height": 544
    }
  ],
  "tracks": [
    {
      "videoId": "4Zt84XdQ47s",
      "title": "Blinding Lights",
      "duration": 200,
      "artists": [{ "name": "The Weeknd" }]
    }
  ]
}
```

---

## 3. Artist Details

Retrieves artist profile information, top songs, and albums.

* **Method**: `GET`
* **Path**: `/artist`
* **Rate Limit**: 600 requests / minute
* **Cache TTL**: 24 hours (`artist:<artistId>`)

### Query Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `id` or `artistId` | `string` | Yes | YouTube Music Artist Channel ID (e.g. `UC...`) |

### Response Example (`200 OK`)

```json
{
  "artistId": "UC0WP5P-ufpRfjbNrmOWwLBQ",
  "name": "The Weeknd",
  "description": "Canadian singer and record producer.",
  "thumbnails": [
    {
      "url": "https://lh3.googleusercontent.com/...",
      "width": 544,
      "height": 544
    }
  ],
  "topSongs": [],
  "albums": []
}
```
