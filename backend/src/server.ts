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
import { cacheService } from "./cache/cacheService";
import { metricsService } from "./metrics/metricsService";
import { StreamManager } from "./streaming/streamManager";
import { RecommendationEngine } from "./recommendations/recommendationEngine";
import { sessionService } from "./recommendations/sessionService";
import { DiscoveryWorker } from "./discovery/discoveryWorker";

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
const recommendationEngine = new RecommendationEngine(ytmusic);
let isInitialized = false;

const PYTHON_BIN = process.platform === "win32" ? "python" : "python3";

async function upgradeYtDlp() {
  try {
    console.log("Checking and upgrading yt-dlp to latest release...");
    const { stdout } = await execAsync(`"${PYTHON_BIN}" -m pip install --no-cache-dir --break-system-packages -U yt-dlp`);
    console.log("✓ yt-dlp check complete:", stdout.trim().split("\n").slice(-1)[0]);
  } catch (err: any) {
    console.warn("⚠ yt-dlp runtime upgrade notice:", err.message);
  }
}

async function initYTMusic() {
  await upgradeYtDlp();
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

  let thumbnail = `https://i.ytimg.com/vi/${videoId}/hq720.jpg`;
  if (Array.isArray(item.thumbnails) && item.thumbnails.length > 0) {
    thumbnail = item.thumbnails[item.thumbnails.length - 1].url;
  }

  // Elevate Google/YouTube CDN thumbnails to HD (800x800 / hq720)
  if (thumbnail.includes("googleusercontent.com") || thumbnail.includes("ggpht.com")) {
    thumbnail = thumbnail
      .replace(/=w\d+-h\d+[^=]*/, "=w800-h800-l90-rj")
      .replace(/=s\d+[^=]*/, "=s800-c-k-c0x00ffffff-no-rj");
  } else if (thumbnail.includes("i.ytimg.com")) {
    thumbnail = thumbnail
      .replace("/mqdefault.jpg", "/hq720.jpg")
      .replace("/default.jpg", "/hq720.jpg")
      .replace("/sddefault.jpg", "/hq720.jpg");
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
      recommendations: "/recommendations?trackId=<video_id>&limit=<5..10>",
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
    const { stdout: pyVer } = await execAsync(`"${PYTHON_BIN}" --version`, { timeout: 18000 });
    diagnostics.pythonVersion = pyVer.trim();
  } catch (e: any) {
    diagnostics.pythonError = e.message;
  }

  try {
    const { stdout: ytdlpVer } = await execAsync(`"${PYTHON_BIN}" -m yt_dlp --version`, { timeout: 18000 });
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

  const testId = "dQw4w9WgXcQ";
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

// 4a. Live stream diagnostic test
app.get("/debug/stream-test", async (req: Request, res: Response) => {
  const testId = (req.query.id as string) || "dQw4w9WgXcQ";
  const results: Record<string, any> = { testId };
  try {
    const resolution = await StreamManager.resolveStream(testId, "low");
    results.resolution = {
      success: Boolean(resolution.entry?.url),
      url: resolution.entry?.url ? resolution.entry.url.substring(0, 100) + "..." : null,
      headers: resolution.entry?.headers,
      ext: resolution.entry?.ext,
      source: resolution.source,
      error: resolution.error || null
    };

    if (resolution.entry?.url) {
      try {
        const https = require("https");
        const fetchRes = await axios.get(resolution.entry.url, {
          headers: {
            "User-Agent": resolution.entry.headers?.["User-Agent"] || "Mozilla/5.0",
            "Range": "bytes=0-1000",
            "Accept": "*/*",
            "Accept-Encoding": "identity"
          },
          httpsAgent: new https.Agent({ family: 4, keepAlive: true }),
          timeout: 10000,
          validateStatus: () => true
        });
        results.upstreamFetch = {
          status: fetchRes.status,
          statusText: fetchRes.statusText,
          contentType: fetchRes.headers["content-type"],
          contentRange: fetchRes.headers["content-range"],
          contentLength: fetchRes.headers["content-length"],
          dataSnippet: typeof fetchRes.data === "string" ? fetchRes.data.substring(0, 200) : null
        };
      } catch (fetchErr: any) {
        results.upstreamFetchError = {
          message: fetchErr.message,
          status: fetchErr.response?.status,
          responseHeaders: fetchErr.response?.headers,
          responseData: fetchErr.response?.data ? String(fetchErr.response.data).substring(0, 200) : null
        };
      }
    }
  } catch (err: any) {
    results.generalError = err.message;
  }
  res.json(results);
});

// 5. In-App OTA Update endpoints
app.get("/update/check", async (req: Request, res: Response) => {
  const reqHost = `${req.protocol}://${req.get("host")}`;
  const updateCacheKey = "update:latest:v2";

  try {
    // Serve cached response for 5 minutes to avoid GitHub rate-limits under burst load
    const cachedUpdate = await cacheService.get<any>(updateCacheKey);
    if (cachedUpdate) {
      return res.json({ ...cachedUpdate, direct_url: `${reqHost}/update/latest.apk` });
    }

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

    const payload = { version, tag_name: tagName, changelog, download_url: downloadUrl };
    await cacheService.set(updateCacheKey, payload, 300); // 5-minute cache

    res.json({ ...payload, direct_url: `${reqHost}/update/latest.apk` });
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

// 6. Advanced Relevance-Ranked Search Engine with Multi-Tier Fallback & Deduplication
app.get(["/search", "/result/"], apiLimiter, async (req: Request, res: Response) => {
  const rawQuery = (req.query.query || req.query.q || "Trending") as string;
  const query = rawQuery.trim();
  const reqHost = `${req.protocol}://${req.get("host")}`;
  const cacheKey = `search:v2:${query.toLowerCase()}`;

  try {
    const cached = await cacheService.get<any[]>(cacheKey);
    if (cached && Array.isArray(cached) && cached.length > 0) {
      // Pre-warm top 5 from cache hit too (noop if already warm)
      setImmediate(() => prewarmSearchResults(cached));
      return res.json(cached);
    }

    // 1. Parallel Multi-Query Search
    const normalizedQuery = query.toLowerCase();
    const queryTokens = normalizedQuery.split(/\s+/).filter(t => t.length > 0);

    let songResults: any[] = [];
    try {
      songResults = await ytmusic.searchSongs(query);
    } catch (_e) {
      songResults = [];
    }

    let generalResults: any[] = [];
    if (songResults.length < 5) {
      try {
        const general = await ytmusic.search(query);
        generalResults = general.filter((item: any) => item.type === "song" || item.type === "video" || !item.type);
      } catch (_e) {
        generalResults = [];
      }
    }

    // Combine and deduplicate by track ID and Title + Artist
    const seenIds = new Set<string>();
    const seenSignatures = new Set<string>();
    const allRawItems: any[] = [];

    for (const item of [...songResults, ...generalResults]) {
      const id = item.videoId || item.id || "";
      if (!id || seenIds.has(id)) continue;

      const title = (item.name || item.title || "").toLowerCase().trim();
      const artist = (item.artist?.name || item.artists?.[0]?.name || item.author || "").toLowerCase().trim();
      const sig = `${title}:${artist}`;

      if (seenSignatures.has(sig)) continue;
      seenIds.add(id);
      seenSignatures.add(sig);
      allRawItems.push(item);
    }

    // 2. Intelligent Relevance Scoring
    const scoredItems = allRawItems.map((item) => {
      const title = (item.name || item.title || "").toLowerCase();
      const artist = (item.artist?.name || item.artists?.[0]?.name || item.author || "").toLowerCase();
      const album = (item.album?.name || "").toLowerCase();
      let score = 0;

      // Exact match
      if (title === normalizedQuery) score += 100;
      else if (title.startsWith(normalizedQuery)) score += 60;
      else if (title.includes(normalizedQuery)) score += 40;

      // Artist match
      if (artist === normalizedQuery) score += 50;
      else if (artist.includes(normalizedQuery)) score += 30;

      // Album match
      if (album && album.includes(normalizedQuery)) score += 15;

      // Token matching
      for (const token of queryTokens) {
        if (title.includes(token)) score += 10;
        if (artist.includes(token)) score += 8;
      }

      return { item, score };
    });

    // Sort by relevance score descending
    scoredItems.sort((a, b) => b.score - a.score);

    const formatted = scoredItems.map((s) => formatTrack(s.item, reqHost));

    // Cache search results for 45 minutes
    await cacheService.set(cacheKey, formatted, 2700);
    res.json(formatted);

    // Fire-and-forget: pre-warm top 5 stream URLs in background so user's first tap is instant
    setImmediate(() => prewarmSearchResults(formatted));
  } catch (error: any) {
    console.error("Search error:", error);
    res.status(500).json({ error: error.message || "Failed to search songs", data: [] });
  }
});

/** Pre-warms the top 2 stream URLs gently after a search/trending response, non-blocking. */
function prewarmSearchResults(tracks: any[], topN: number = 2): void {
  const ids = tracks
    .slice(0, topN)
    .map((t: any) => (t.videoId || t.id || "").toString().trim())
    .filter((id: string) => id.length >= 3);

  if (ids.length === 0) return;

  (async () => {
    for (const id of ids) {
      try {
        await StreamManager.resolveStream(id, "low", false);
        // Small 250ms spacing between background prewarms to keep event loop free
        await new Promise((r) => setTimeout(r, 250));
      } catch (_e) {}
    }
  })().catch(() => {});
}


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

// 9. Direct high-performance progressive audio stream endpoint
app.get(["/stream", "/stream/"], streamLimiter, async (req: Request, res: Response) => {
  await StreamManager.handleStreamRequest(req, res);
});

// 9a. Next-track early stream pre-warm & resolution endpoint
app.get(["/stream/preload", "/preload"], apiLimiter, async (req: Request, res: Response) => {
  const videoId = (req.query.id || req.query.query || req.query.videoId) as string;
  const quality = (req.query.quality || "low") as string;
  if (!videoId) {
    return res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
  }

  const start = Date.now();
  const resolution = await StreamManager.resolveStream(videoId, quality);
  const durationMs = Date.now() - start;

  if (resolution.entry) {
    res.json({
      success: true,
      videoId,
      quality,
      mediaType: "audio",
      source: resolution.source || "resolver",
      durationMs,
      cached: resolution.source === "l1" || resolution.source === "redis"
    });
  } else {
    res.status(502).json({
      success: false,
      videoId,
      error: resolution.error || "Failed to resolve audio stream"
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
    if (cached) {
      setImmediate(() => prewarmSearchResults(cached));
      return res.json(cached);
    }

    const results = await ytmusic.searchSongs("Top Global Hits 2026");
    const formatted = results.map((item) => formatTrack(item, reqHost));
    await cacheService.set(cacheKey, formatted, 3600); // 1 hour TTL
    res.json(formatted);

    // Fire-and-forget: pre-warm top 5 trending stream URLs
    setImmediate(() => prewarmSearchResults(formatted));
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to fetch trending songs", data: [] });
  }
});

// 14. Lightweight Current-Track Recommendation Engine
app.get(["/recommendations", "/api/recommendations"], apiLimiter, async (req: Request, res: Response) => {
  const trackId = (req.query.trackId || req.query.id || req.query.videoId) as string;
  const limitParam = parseInt((req.query.limit as string) || "5", 10);
  const limit = isNaN(limitParam) ? 5 : Math.min(10, Math.max(1, limitParam));

  if (!trackId || typeof trackId !== "string" || trackId.trim().length === 0) {
    return res.status(400).json({
      error: "Missing required parameter 'trackId' (e.g. ?trackId=3_g2un5M350)",
      recommendations: []
    });
  }

  const reqHost = `${req.protocol}://${req.get("host")}`;

  try {
    const result = await recommendationEngine.getRecommendations(trackId.trim(), limit, reqHost);
    res.json(result);
  } catch (error: any) {
    console.error(`Recommendation route error for ${trackId}:`, error);
    res.status(500).json({
      error: error.message || "Failed to generate recommendations",
      trackId,
      recommendations: []
    });
  }
});

// 15. Real-Time Listening Event Ingest
// Receives playback events from Android client to update session profiles.
app.post("/api/listening-events", apiLimiter, async (req: Request, res: Response) => {
  try {
    const { userId, trackId, artistName, genre, eventType, durationMs, positionMs } = req.body;

    if (!userId || !trackId || !eventType) {
      return res.status(400).json({ error: "Missing required fields: userId, trackId, eventType" });
    }

    const updatedProfile = await sessionService.processEvent({
      userId,
      trackId,
      artistName,
      genre,
      eventType,
      durationMs,
      positionMs,
      timestampMs: Date.now()
    });

    res.json({
      success: true,
      totalEvents: updatedProfile.totalEvents,
      artistAffinity: updatedProfile.artistAffinities,
      genreAffinity: updatedProfile.genreAffinities
    });
  } catch (error: any) {
    console.error("[listening-events] Error:", error.message);
    res.status(500).json({ error: error.message || "Failed to process event" });
  }
});

// 16. Session-Aware Next Track Recommendations
// Returns personalised recommendations incorporating real-time session profile.
app.get("/api/recommendations/next", apiLimiter, async (req: Request, res: Response) => {
  const trackId = (req.query.trackId || req.query.id) as string;
  const userId = (req.query.userId || "anonymous") as string;
  const limitParam = parseInt((req.query.limit as string) || "6", 10);
  const limit = isNaN(limitParam) ? 6 : Math.min(10, Math.max(1, limitParam));

  if (!trackId || typeof trackId !== "string" || trackId.trim().length === 0) {
    return res.status(400).json({
      error: "Missing required parameter 'trackId'",
      recommendations: []
    });
  }

  const reqHost = `${req.protocol}://${req.get("host")}`;

  try {
    const sessionProfile = await sessionService.getSession(userId);
    const result = await recommendationEngine.getRecommendationsWithSession(
      trackId.trim(),
      limit,
      reqHost,
      sessionProfile
    );
    res.json(result);
  } catch (error: any) {
    console.error(`[recommendations/next] Error for ${trackId}:`, error);
    res.status(500).json({
      error: error.message || "Failed to generate session-aware recommendations",
      trackId,
      recommendations: []
    });
  }
});

// 17. Intelligent Shuffle Queue Generator
// Takes a list of tracks and returns an intelligently shuffled ordering.
app.post("/api/shuffle/generate", apiLimiter, async (req: Request, res: Response) => {
  try {
    const { tracks, currentTrackId, userId, temperature } = req.body;

    if (!Array.isArray(tracks) || tracks.length === 0) {
      return res.status(400).json({ error: "Missing or empty 'tracks' array" });
    }

    const sessionProfile = userId
      ? await sessionService.getSession(userId as string)
      : undefined;

    const shuffled = await recommendationEngine.generateShuffledQueue(
      tracks,
      currentTrackId || null,
      sessionProfile,
      typeof temperature === "number" ? temperature : 0.7
    );

    res.json({ shuffled, count: shuffled.length });
  } catch (error: any) {
    console.error("[shuffle/generate] Error:", error.message);
    res.status(500).json({ error: error.message || "Failed to generate shuffle" });
  }
});


const discoveryWorker = new DiscoveryWorker(ytmusic);

// 18. Live Discovery Home API
// Returns multi-section discovery payload (Trending Now, New Releases, Rising Fast, Regional)
app.get("/api/discover/home", apiLimiter, async (req: Request, res: Response) => {
  const language = (req.query.language as string) || "All";
  const reqHost = `${req.protocol}://${req.get("host")}`;

  try {
    const result = await discoveryWorker.getHomeDiscovery(language, reqHost);
    res.json(result);
  } catch (error: any) {
    console.error("[discover/home] Error:", error.message);
    res.status(500).json({ error: error.message || "Failed to fetch discovery home" });
  }
});

// 19. Live Trending Music API
app.get("/api/discover/trending", apiLimiter, async (req: Request, res: Response) => {
  const region = (req.query.region as string) || "global";
  const language = (req.query.language as string) || "All";
  const force = req.query.force === "true";
  const reqHost = `${req.protocol}://${req.get("host")}`;

  try {
    const tracks = await discoveryWorker.getTrending(region, language, force);
    const withStreams = tracks.map((t) => ({
      ...t,
      streamUrl: t.videoId ? `${reqHost}/stream?id=${t.videoId}` : undefined,
      mediaUrl: t.videoId ? `${reqHost}/stream?id=${t.videoId}` : undefined
    }));
    res.json(withStreams);
  } catch (error: any) {
    console.error("[discover/trending] Error:", error.message);
    res.status(500).json({ error: error.message || "Failed to fetch trending music" });
  }
});

// 20. Live New Releases API
app.get("/api/discover/new", apiLimiter, async (req: Request, res: Response) => {
  const language = (req.query.language as string) || "All";
  const force = req.query.force === "true";
  const reqHost = `${req.protocol}://${req.get("host")}`;

  try {
    const tracks = await discoveryWorker.getNewReleases(language, force);
    const withStreams = tracks.map((t) => ({
      ...t,
      streamUrl: t.videoId ? `${reqHost}/stream?id=${t.videoId}` : undefined,
      mediaUrl: t.videoId ? `${reqHost}/stream?id=${t.videoId}` : undefined
    }));
    res.json(withStreams);
  } catch (error: any) {
    console.error("[discover/new] Error:", error.message);
    res.status(500).json({ error: error.message || "Failed to fetch new releases" });
  }
});

// 21. Live Rising Breakout Hits API
app.get("/api/discover/rising", apiLimiter, async (req: Request, res: Response) => {
  const force = req.query.force === "true";
  const reqHost = `${req.protocol}://${req.get("host")}`;

  try {
    const tracks = await discoveryWorker.getRising(force);
    const withStreams = tracks.map((t) => ({
      ...t,
      streamUrl: t.videoId ? `${reqHost}/stream?id=${t.videoId}` : undefined,
      mediaUrl: t.videoId ? `${reqHost}/stream?id=${t.videoId}` : undefined
    }));
    res.json(withStreams);
  } catch (error: any) {
    console.error("[discover/rising] Error:", error.message);
    res.status(500).json({ error: error.message || "Failed to fetch rising hits" });
  }
});


// Start server and handle graceful shutdown
let server: http.Server | null = null;

initYTMusic().then(() => {
  // Start the background discovery worker
  discoveryWorker.start();

  // Pre-warm top 15 popular tracks into L1 cache 5s after startup (non-blocking)
  setTimeout(() => StreamManager.preWarmPopularTracks(15), 5000);

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
