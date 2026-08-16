import express, { Request, Response, NextFunction } from "express";
import http from "http";
import cors from "cors";
import dotenv from "dotenv";
import YTMusic from "ytmusic-api";
import { exec } from "child_process";
import { promisify } from "util";
import axios from "axios";
import path from "path";
import rateLimit from "express-rate-limit";
import { cacheService } from "./src/cache/cacheService";
import { metricsService } from "./src/metrics/metricsService";
import { StreamManager } from "./src/streaming/streamManager";

dotenv.config();

const execAsync = promisify(exec);
const app = express();

// High-capacity Express configuration
app.disable("x-powered-by");
app.set("trust proxy", 1);

app.use(cors());
app.use(express.json());

// Latency & Metrics Tracking Middleware
app.use((req: Request, res: Response, next: NextFunction) => {
  const start = Date.now();
  res.on("finish", () => {
    const duration = Date.now() - start;
    metricsService.recordRequest(duration);
  });
  next();
});

// Rate limiters
const apiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 600, // 600 requests per minute per IP for standard API queries
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "Too many requests, please try again later." }
});

const streamLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60000, // 60,000 range chunks per minute per IP cluster (safe for 2,000+ simultaneous listeners behind CGNAT/WiFi)
  standardHeaders: true,
  legacyHeaders: false
});

const PORT = parseInt(process.env.PORT || "5000", 10);
const ytmusic = new YTMusic();
let isInitialized = false;

const PYTHON_BIN = process.platform === "win32" ? "python" : "python3";

async function initYTMusic() {
  try {
    await ytmusic.initialize();
    isInitialized = true;
    console.log("✓ YTMusic API initialized successfully.");
  } catch (error) {
    console.error("⚠ Warning initializing YTMusic API:", error);
  }
}

// Helper to format track for Musync Mobile App schema
function formatTrack(item: any, reqHost: string) {
  const videoId = item.videoId || item.id || "";
  const title = item.name || item.title || "Unknown Title";
  const artistName = item.artist?.name || (Array.isArray(item.artists) ? item.artists.map((a: any) => a.name).join(", ") : "YouTube Artist");
  const albumName = item.album?.name || (typeof item.album === "string" ? item.album : title);

  let thumbnail = `https://i.ytimg.com/vi/${videoId}/mqdefault.jpg`;
  if (Array.isArray(item.thumbnails) && item.thumbnails.length > 0) {
    thumbnail = item.thumbnails[item.thumbnails.length - 1].url;
  }

  const durationSec = item.duration || 180;
  const streamUrl = `${reqHost}/stream?id=${videoId}`;

  return {
    videoId,
    id: videoId,
    songid: videoId,
    title,
    song: title,
    singers: artistName,
    artist: artistName,
    album: albumName,
    image_url: thumbnail,
    image: thumbnail,
    duration: String(durationSec),
    duration_seconds: durationSec,
    url: streamUrl,
    media_url: streamUrl,
    stream_url: streamUrl
  };
}

// 1. Root & Status
app.get("/", (_req: Request, res: Response) => {
  res.json({
    status: "online",
    service: "Musync High-Performance Audio Gateway & Streaming Cluster",
    version: "4.0.0",
    initialized: isInitialized,
    endpoints: {
      search: "/search?query=<song_or_artist>",
      suggestions: "/suggestions?query=<text>",
      song: "/song?id=<video_id>",
      stream: "/stream?id=<video_id>",
      lyrics: "/lyrics?id=<video_id>",
      album: "/album?id=<album_id>",
      artist: "/artist?id=<artist_id>",
      trending: "/trending",
      metrics: "/metrics",
      health: "/health",
      debug: "/debug/env"
    }
  });
});

// 2. Health check
app.get("/health", (_req: Request, res: Response) => {
  const cacheStats = cacheService.getStats();
  const perfMetrics = metricsService.getMetrics();
  res.json({
    status: "healthy",
    ytmusic: isInitialized,
    cache: cacheStats,
    activeStreams: perfMetrics.activeStreams,
    uptimeSeconds: perfMetrics.uptimeSeconds,
    runtime: "Node.js / TypeScript / Enterprise Cluster"
  });
});

// 3. Prometheus / Performance Metrics Endpoint
app.get("/metrics", (_req: Request, res: Response) => {
  const cacheStats = cacheService.getStats();
  const perfMetrics = metricsService.getMetrics();

  res.json({
    timestamp: new Date().toISOString(),
    metrics: perfMetrics,
    cache: cacheStats
  });
});

// 4. Diagnostic endpoint to inspect environment
app.get("/debug/env", async (_req: Request, res: Response) => {
  const diagnostics: Record<string, any> = {
    platform: process.platform,
    arch: process.arch,
    nodeVersion: process.version,
    port: PORT,
    pythonBin: PYTHON_BIN,
  };

  try {
    const { stdout: pyVer } = await execAsync(`"${PYTHON_BIN}" --version`);
    diagnostics.pythonVersion = pyVer.trim();
  } catch (e: any) {
    diagnostics.pythonError = e.message;
  }

  try {
    const { stdout: ytdlpVer } = await execAsync(`"${PYTHON_BIN}" -m yt_dlp --version`);
    diagnostics.ytdlpVersion = ytdlpVer.trim();
  } catch (e: any) {
    diagnostics.ytdlpError = e.message;
  }

  try {
    const { stdout: ffmpegVer } = await execAsync(`ffmpeg -version`);
    diagnostics.ffmpegVersion = ffmpegVer.split("\n")[0];
  } catch (e: any) {
    diagnostics.ffmpegError = e.message;
  }

  const testId = "3_g2un5M350";
  try {
    const resolution = await StreamManager.resolveAudioStream(testId);
    diagnostics.streamResolverTest = {
      success: Boolean(resolution.entry?.url),
      source: resolution.source,
      error: resolution.error || null
    };
  } catch (e: any) {
    diagnostics.streamResolverError = e.message;
  }

  res.json(diagnostics);
});

// 5. In-App OTA Update endpoints
app.get("/update/check", async (req: Request, res: Response) => {
  const reqHost = `${req.protocol}://${req.get("host")}`;
  try {
    const ghRes = await axios.get("https://api.github.com/repos/gowthamkrishna27/Musync/releases/latest", {
      headers: { "User-Agent": "Musync-Server/1.0" },
      timeout: 5000
    });
    const tagName = ghRes.data.tag_name || "v1.0.0";
    const version = tagName.replace(/^v/, "");
    const changelog = ghRes.data.body || "High-concurrency streaming and performance engine";
    let downloadUrl = "https://github.com/gowthamkrishna27/Musync/releases/latest/download/Musync.apk";

    if (ghRes.data.assets && ghRes.data.assets.length > 0) {
      const apkAsset = ghRes.data.assets.find((a: any) => a.name.endsWith(".apk"));
      if (apkAsset) downloadUrl = apkAsset.browser_download_url;
    }

    res.json({
      version,
      tag_name: tagName,
      changelog,
      download_url: downloadUrl,
      direct_url: `${reqHost}/update/latest.apk`
    });
  } catch (_e: any) {
    res.json({
      version: "1.0.0",
      tag_name: "v1.0.0",
      changelog: "Musync release update",
      download_url: "https://github.com/gowthamkrishna27/Musync/releases/latest/download/Musync.apk",
      direct_url: `${reqHost}/update/latest.apk`
    });
  }
});

app.get("/update/latest.apk", (_req: Request, res: Response) => {
  res.redirect("https://github.com/gowthamkrishna27/Musync/releases/latest/download/Musync.apk");
});

// 6. Search endpoint with L1/L2 caching
app.get(["/search", "/result/"], apiLimiter, async (req: Request, res: Response) => {
  const query = (req.query.query || req.query.q || "Trending") as string;
  const reqHost = `${req.protocol}://${req.get("host")}`;
  const cacheKey = `search:${query.toLowerCase().trim()}`;

  try {
    const cached = await cacheService.get<any[]>(cacheKey);
    if (cached) {
      return res.json(cached);
    }

    const results = await ytmusic.searchSongs(query);
    const formatted = results.map((item) => formatTrack(item, reqHost));

    // Cache search results for 30 minutes
    await cacheService.set(cacheKey, formatted, 1800);
    res.json(formatted);
  } catch (error: any) {
    console.error("Search error:", error);
    res.status(500).json({ error: error.message || "Failed to search songs", data: [] });
  }
});

// 7. Search autocomplete suggestions with caching
app.get("/suggestions", apiLimiter, async (req: Request, res: Response) => {
  const query = (req.query.query || req.query.q || "") as string;
  if (!query) {
    return res.json([]);
  }

  const cacheKey = `sug:${query.toLowerCase().trim()}`;
  try {
    const cached = await cacheService.get<string[]>(cacheKey);
    if (cached) {
      return res.json(cached);
    }

    const suggestions = await ytmusic.getSearchSuggestions(query);
    await cacheService.set(cacheKey, suggestions, 3600); // 1 hour TTL
    res.json(suggestions);
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to get suggestions", data: [] });
  }
});

// 8. Get Song info & direct stream URL with caching
app.get(["/song", "/song/"], apiLimiter, async (req: Request, res: Response) => {
  const videoId = (req.query.id || req.query.query) as string;
  if (!videoId) {
    return res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
  }

  const reqHost = `${req.protocol}://${req.get("host")}`;
  const streamUrl = `${reqHost}/stream?id=${videoId}`;
  const cacheKey = `song_meta:${videoId}`;

  try {
    const cached = await cacheService.get<any>(cacheKey);
    if (cached) {
      return res.json({ ...cached, url: streamUrl, media_url: streamUrl, stream_url: streamUrl });
    }

    const songData = await ytmusic.getSong(videoId);
    const result = {
      videoId,
      id: videoId,
      title: songData?.name || "Unknown Title",
      artist: songData?.artist?.name || "YouTube Artist",
      url: streamUrl,
      media_url: streamUrl,
      stream_url: streamUrl
    };

    await cacheService.set(cacheKey, result, 86400); // 24 hours TTL
    res.json(result);
  } catch (_e) {
    res.json({
      videoId,
      id: videoId,
      url: streamUrl,
      media_url: streamUrl,
      stream_url: streamUrl
    });
  }
});

// 9. Direct high-performance audio & video stream endpoint
app.get(["/stream", "/stream/"], streamLimiter, async (req: Request, res: Response) => {
  await StreamManager.handleStreamRequest(req, res);
});

// 9a. Explicit Video Stream Endpoint
app.get(["/video/stream", "/video"], streamLimiter, async (req: Request, res: Response) => {
  req.query.type = "video";
  await StreamManager.handleStreamRequest(req, res);
});

// 9b. Video Info / Stream Resolution Endpoint
app.get(["/video/info", "/stream/info"], apiLimiter, async (req: Request, res: Response) => {
  const videoId = (req.query.id || req.query.query || req.query.videoId) as string;
  const quality = (req.query.quality || "auto") as string;
  if (!videoId) {
    return res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
  }

  const resolution = await StreamManager.resolveVideoStream(videoId, quality);
  if (resolution.entry) {
    res.json({
      success: true,
      videoId,
      quality,
      mediaType: "video",
      availableQualities: resolution.availableQualities || ['Auto', '1080p', '720p', '480p', '360p', '144p'],
      source: resolution.source
    });
  } else {
    res.status(502).json({
      success: false,
      videoId,
      error: resolution.error || "Video stream unavailable"
    });
  }
});

// 9c. Next-track early stream pre-warm & resolution endpoint
app.get(["/stream/preload", "/preload"], apiLimiter, async (req: Request, res: Response) => {
  const videoId = (req.query.id || req.query.query || req.query.videoId) as string;
  const quality = (req.query.quality || "low") as string;
  const type = ((req.query.type || req.query.mediaType || "audio") as string).toLowerCase();
  if (!videoId) {
    return res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
  }

  const start = Date.now();
  const resolution = await StreamManager.resolveStream(videoId, quality, type === "video" ? "video" : "audio");
  const durationMs = Date.now() - start;

  if (resolution.entry) {
    res.json({
      success: true,
      videoId,
      quality,
      mediaType: resolution.mediaType || "audio",
      source: resolution.source || "resolver",
      durationMs,
      cached: resolution.source === "l1" || resolution.source === "redis"
    });
  } else {
    res.status(502).json({
      success: false,
      videoId,
      error: resolution.error || "Failed to resolve stream"
    });
  }
});

// 10. Lyrics endpoint with caching
app.get("/lyrics", apiLimiter, async (req: Request, res: Response) => {
  const videoId = (req.query.id || req.query.videoId) as string;
  if (!videoId) {
    return res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
  }

  const cacheKey = `lyrics:${videoId}`;
  try {
    const cached = await cacheService.get<string>(cacheKey);
    if (cached) {
      return res.json({ videoId, lyrics: cached });
    }

    const lyrics = await ytmusic.getLyrics(videoId);
    const lyricsContent = lyrics || "Lyrics not available for this track.";
    await cacheService.set(cacheKey, lyricsContent, 86400 * 7); // 7 days TTL
    res.json({
      videoId,
      lyrics: lyricsContent
    });
  } catch (error: any) {
    res.status(404).json({ error: "Lyrics not found", videoId });
  }
});

// 11. Album details with caching
app.get("/album", apiLimiter, async (req: Request, res: Response) => {
  const albumId = (req.query.id || req.query.albumId) as string;
  if (!albumId) {
    return res.status(400).json({ error: "Missing album ID parameter (?id=...)" });
  }

  const cacheKey = `album:${albumId}`;
  try {
    const cached = await cacheService.get<any>(cacheKey);
    if (cached) return res.json(cached);

    const album = await ytmusic.getAlbum(albumId);
    await cacheService.set(cacheKey, album, 86400); // 24 hours TTL
    res.json(album);
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to fetch album" });
  }
});

// 12. Artist details & songs with caching
app.get("/artist", apiLimiter, async (req: Request, res: Response) => {
  const artistId = (req.query.id || req.query.artistId) as string;
  if (!artistId) {
    return res.status(400).json({ error: "Missing artist ID parameter (?id=...)" });
  }

  const cacheKey = `artist:${artistId}`;
  try {
    const cached = await cacheService.get<any>(cacheKey);
    if (cached) return res.json(cached);

    const artist = await ytmusic.getArtist(artistId);
    await cacheService.set(cacheKey, artist, 86400); // 24 hours TTL
    res.json(artist);
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to fetch artist" });
  }
});

// 13. Trending & Charts
app.get(["/trending", "/charts"], apiLimiter, async (req: Request, res: Response) => {
  const reqHost = `${req.protocol}://${req.get("host")}`;
  const cacheKey = "trending:global";
  try {
    const cached = await cacheService.get<any[]>(cacheKey);
    if (cached) return res.json(cached);

    const results = await ytmusic.searchSongs("Top Global Hits 2026");
    const formatted = results.map((item) => formatTrack(item, reqHost));
    await cacheService.set(cacheKey, formatted, 3600); // 1 hour TTL
    res.json(formatted);
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to fetch trending songs", data: [] });
  }
});

// Start server and handle graceful shutdown
let server: http.Server | null = null;

initYTMusic().then(() => {
  server = app.listen(PORT, "0.0.0.0", () => {
    console.log(`🚀 Musync High-Performance Streaming Server listening on port ${PORT}`);
  });

  // Enable HTTP keep-alive timeouts tuned for high concurrency
  server.keepAliveTimeout = 65000;
  server.headersTimeout = 66000;
});

// Graceful Shutdown
async function handleGracefulShutdown(signal: string) {
  console.log(`\n🛑 Received ${signal}. Starting graceful shutdown...`);
  if (server) {
    server.close(async () => {
      console.log("✓ HTTP server stopped accepting new connections.");
      metricsService.close();
      await cacheService.close();
      console.log("✓ All cache and connection pools drained. Clean exit.");
      process.exit(0);
    });

    // Force exit after 10s if hanging connections remain
    setTimeout(() => {
      console.error("⚠ Forcing exit after timeout.");
      process.exit(1);
    }, 10000).unref();
  } else {
    process.exit(0);
  }
}

process.on("SIGTERM", () => handleGracefulShutdown("SIGTERM"));
process.on("SIGINT", () => handleGracefulShutdown("SIGINT"));
