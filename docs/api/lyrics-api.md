# Lyrics API Documentation

## 1. Track Lyrics

Fetches synchronized or plain-text lyrics for a given YouTube video ID.

* **Method**: `GET`
* **Path**: `/lyrics`
* **Rate Limit**: 600 requests / minute
* **Cache TTL**: 7 Days (`lyrics:<videoId>`)

### Query Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `id` or `videoId` | `string` | Yes | Target YouTube video ID |

### Response Example (`200 OK`)

```json
{
  "videoId": "3_g2un5M350",
  "lyrics": "I'm tryna put you in the worst mood, ah\nP1 cleaner than your church shoes, ah\nMilli point two just to hurt you, ah\nAll red Lamb' just to tease you, ah..."
}
```

### Error Response (`404 Not Found`)

```json
{
  "error": "Lyrics not found",
  "videoId": "invalid_id"
}
```

### Error Response (`400 Bad Request`)

```json
{
  "error": "Missing video ID parameter (?id=...)"
}
```
