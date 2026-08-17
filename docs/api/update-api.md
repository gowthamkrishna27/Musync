# OTA Update API Documentation

## 1. Check for Updates

Queries the latest GitHub Releases tag and returns update metadata with direct download links. Cached for 5 minutes to prevent upstream rate limiting.

* **Method**: `GET`
* **Path**: `/update/check`
* **Rate Limit**: None
* **Cache TTL**: 5 Minutes (`update:latest:v2`)

### Response Example (`200 OK`)

```json
{
  "version": "1.1.4.3",
  "tag_name": "v1.1.4.3",
  "changelog": "Dual-band haptic engine, 200MB local audio cache, and performance improvements.",
  "download_url": "https://github.com/gowthamkrishna27/Musync/releases/latest/download/Musync.apk",
  "direct_url": "http://localhost:5000/update/latest.apk"
}
```

---

## 2. Download Latest APK

Redirects the client directly to the release APK asset download URL on GitHub.

* **Method**: `GET`
* **Path**: `/update/latest.apk`
* **Status Code**: `302 Found`
* **Redirect Location**: `https://github.com/gowthamkrishna27/Musync/releases/latest/download/Musync.apk`
