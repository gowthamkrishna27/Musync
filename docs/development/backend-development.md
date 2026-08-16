# Backend Development Guide

## Environment Requirements

* **Node.js**: v20.x or v22.x
* **TypeScript**: v5.7.2
* **Python**: 3.10+ (with `pip`)
* **yt-dlp**: Auto-updated at runtime and managed via `pip`
* **FFmpeg**: Required in PATH for audio stream parsing

---

## Package Scripts (`backend/package.json`)

| Script | Command | Purpose |
| :--- | :--- | :--- |
| `npm run dev` | `tsx watch src/server.ts` | Starts the server in development mode with live hot-reloading |
| `npm run build` | `tsc` | Compiles TypeScript source files from `src/` to `dist/` |
| `npm start` | `tsx src/server.ts` | Runs the server in production mode |

---

## Local Development Setup

### 1. Install Node Dependencies

```bash
cd backend
npm install
```

### 2. Install Python Dependencies

```bash
pip install -r requirements.txt
```

### 3. Configure Environment Variables

Create `backend/.env` (optional for local dev, defaults to port 5000 and in-memory L1 cache):

```env
PORT=5000
NODE_ENV=development
# Optional Redis L2 Cache
# REDIS_URL=redis://localhost:6379
```

### 4. Start Development Server

```bash
npm run dev
```

Output:
```text
ℹ Redis not configured. Operating with high-performance L1 In-Memory Cache.
Checking and upgrading yt-dlp to latest release...
✓ yt-dlp check complete: Requirement already satisfied: yt-dlp
✓ YTMusic API initialized successfully.
🚀 Musync High-Performance Streaming Server listening on port 5000
```

---

## Testing Python Audio Stream Extraction

You can verify the Python resolver script directly from the CLI:

```bash
python scripts/stream_resolver.py 3_g2un5M350 low audio
```

Expected JSON Output:
```json
{
  "url": "https://rr2---sn-....googlevideo.com/videoplayback?...",
  "headers": {
    "User-Agent": "...",
    "Accept": "*/*"
  },
  "client": "android",
  "quality": "low",
  "media_type": "audio",
  "ext": "mp4"
}
```
