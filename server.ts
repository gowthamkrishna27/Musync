import express, { Request, Response } from "express";
import cors from "cors";
import dotenv from "dotenv";
import YTMusic from "ytmusic-api";
import { exec, spawn } from "child_process";
import { promisify } from "util";
import axios from "axios";
import path from "path";

dotenv.config();

const execAsync = promisify(exec);
const app = express();
app.use(cors());
app.use(express.json());

const PORT = parseInt(process.env.PORT || "5000", 10);
const ytmusic = new YTMusic();

let isInitialized = false;

// Cross-platform Python binary discovery
const PYTHON_BIN = process.platform === "win32" ? "python" : "python3";

interface StreamCacheEntry {
  url: string;
  headers: Record<string, string>;
  expiresAt: number;
}

// In-memory cache for resolved audio stream URLs and session headers (4 hours)
const streamCache = new Map<string, StreamCacheEntry>();

async function initYTMusic() {
  try {
    await ytmusic.initialize();
    isInitialized = true;
    console.log("✓ YTMusic API initialized successfully.");
  } catch (error) {
    console.error("⚠ Warning initializing YTMusic API:", error);
  }
}

interface StreamResolutionResult {
  entry: StreamCacheEntry | null;
  error?: string;
}

/**
 * High-Speed Audio Stream Resolver using stream_resolver.py
 */
async function resolveAudioStream(videoId: string): Promise<StreamResolutionResult> {
  const cached = streamCache.get(videoId);
  if (cached && cached.expiresAt > Date.now()) {
    return { entry: cached };
  }

  // 1. Resolve via python stream_resolver.py
  try {
    const scriptPath = path.join(process.cwd(), "stream_resolver.py");
    const { stdout, stderr } = await execAsync(`"${PYTHON_BIN}" "${scriptPath}" ${videoId}`, { timeout: 25000 });
    if (stderr && stderr.trim()) {
      console.log(`[StreamResolver stderr for ${videoId}]:`, stderr.trim());
    }
    const parsed = JSON.parse(stdout.trim());
    if (parsed.url) {
      const entry: StreamCacheEntry = {
        url: parsed.url,
        headers: parsed.headers || {},
        expiresAt: Date.now() + 4 * 3600 * 1000
      };
      streamCache.set(videoId, entry);
      return { entry };
    } else if (parsed.error) {
      return { entry: null, error: parsed.error };
    }
  } catch (e: any) {
    return { entry: null, error: e.message };
  }

  return { entry: null, error: "Unknown stream resolution failure" };
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
    service: "Musync TypeScript Audio Gateway & Stream Proxy",
    version: "3.5.0",
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
      debug: "/debug/env"
    }
  });
});

// 2. Health check
app.get("/health", (_req: Request, res: Response) => {
  res.json({
    status: "healthy",
    ytmusic: isInitialized,
    cachedStreams: streamCache.size,
    runtime: "Node.js / TypeScript"
  });
});

// 3. Diagnostic endpoint to compare environments
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
  const scriptPath = path.join(__dirname, "stream_resolver.py");
  try {
    const { stdout: resolverOut, stderr: resolverErr } = await execAsync(`"${PYTHON_BIN}" "${scriptPath}" ${testId}`, { timeout: 15000 });
    const parsed = JSON.parse(resolverOut.trim());
    diagnostics.streamResolverTest = {
      success: Boolean(parsed.url),
      hasHeaders: Boolean(parsed.headers),
      error: parsed.error || null,
      stderr: resolverErr ? resolverErr.trim() : null
    };
  } catch (e: any) {
    diagnostics.streamResolverError = e.message;
  }

  res.json(diagnostics);
});

// In-App OTA Update endpoints
app.get("/update/check", async (req: Request, res: Response) => {
  const reqHost = `${req.protocol}://${req.get("host")}`;
  try {
    const ghRes = await axios.get("https://api.github.com/repos/gowthamkrishna27/Musync/releases/latest", {
      headers: { "User-Agent": "Musync-Server/1.0" },
      timeout: 5000
    });
    const tagName = ghRes.data.tag_name || "v1.0.0";
    const version = tagName.replace(/^v/, "");
    const changelog = ghRes.data.body || "New performance updates and fixes";
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

// 4. Search endpoint
app.get(["/search", "/result/"], async (req: Request, res: Response) => {
  const query = (req.query.query || req.query.q || "Trending") as string;
  const reqHost = `${req.protocol}://${req.get("host")}`;

  try {
    const results = await ytmusic.searchSongs(query);
    const formatted = results.map((item) => formatTrack(item, reqHost));
    res.json(formatted);
  } catch (error: any) {
    console.error("Search error:", error);
    res.status(500).json({ error: error.message || "Failed to search songs", data: [] });
  }
});

// 5. Search autocomplete suggestions
app.get("/suggestions", async (req: Request, res: Response) => {
  const query = (req.query.query || req.query.q || "") as string;
  if (!query) {
    return res.json([]);
  }

  try {
    const suggestions = await ytmusic.getSearchSuggestions(query);
    res.json(suggestions);
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to get suggestions", data: [] });
  }
});

// 6. Get Song info & direct stream URL
app.get(["/song", "/song/"], async (req: Request, res: Response) => {
  const videoId = (req.query.id || req.query.query) as string;
  if (!videoId) {
    return res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
  }

  const reqHost = `${req.protocol}://${req.get("host")}`;
  const streamUrl = `${reqHost}/stream?id=${videoId}`;

  try {
    const songData = await ytmusic.getSong(videoId);
    res.json({
      videoId,
      id: videoId,
      title: songData?.name || "Unknown Title",
      artist: songData?.artist?.name || "YouTube Artist",
      url: streamUrl,
      media_url: streamUrl,
      stream_url: streamUrl
    });
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

// 7. Direct audio stream proxy with Range support & session header pass-through
app.get(["/stream", "/stream/"], async (req: Request, res: Response) => {
  const videoId = (req.query.id || req.query.query) as string;
  if (!videoId) {
    return res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
  }

  const resolution = await resolveAudioStream(videoId);
  const streamEntry = resolution.entry;
  if (!streamEntry) {
    console.warn(`[Stream 502] Failed resolving stream for video: ${videoId}, error: ${resolution.error}`);
    return res.status(502).json({ error: "Could not resolve stream for video: " + videoId, details: resolution.error });
  }

  try {
    const rangeHeader = req.headers.range;
    const reqHeaders: Record<string, string> = {
      ...streamEntry.headers,
      "Accept": "*/*",
      "Sec-Fetch-Mode": "navigate"
    };
    if (rangeHeader) {
      reqHeaders["Range"] = rangeHeader;
    }

    const audioStream = await axios({
      method: "GET",
      url: streamEntry.url,
      headers: reqHeaders,
      responseType: "stream",
      validateStatus: (status) => status < 400
    });

    res.status(audioStream.status);
    if (audioStream.headers["content-type"]) res.setHeader("Content-Type", audioStream.headers["content-type"]);
    if (audioStream.headers["content-length"]) res.setHeader("Content-Length", audioStream.headers["content-length"]);
    if (audioStream.headers["content-range"]) res.setHeader("Content-Range", audioStream.headers["content-range"]);
    if (audioStream.headers["accept-ranges"]) res.setHeader("Accept-Ranges", audioStream.headers["accept-ranges"]);

    audioStream.data.pipe(res);
  } catch (error: any) {
    console.warn(`[Axios stream failed for ${videoId}]: ${error.message}, spawning direct fallback`);
    streamCache.delete(videoId);
    
    // Fallback: spawn direct yt-dlp audio stream
    try {
      res.setHeader("Content-Type", "audio/webm");
      const ytProc = spawn(PYTHON_BIN, [
        "-m", "yt_dlp",
        "-o", "-",
        "-f", "ba/b",
        "--extractor-args", "youtube:player_client=android_vr,android",
        `https://www.youtube.com/watch?v=${videoId}`
      ]);
      ytProc.stdout.pipe(res);
      req.on("close", () => ytProc.kill());
    } catch (procErr: any) {
      console.error("Direct yt-dlp spawn failed:", procErr.message);
      res.status(500).json({ error: "Failed to stream audio" });
    }
  }
});

// 8. Lyrics endpoint
app.get("/lyrics", async (req: Request, res: Response) => {
  const videoId = (req.query.id || req.query.videoId) as string;
  if (!videoId) {
    return res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
  }

  try {
    const lyrics = await ytmusic.getLyrics(videoId);
    res.json({
      videoId,
      lyrics: lyrics || "Lyrics not available for this track."
    });
  } catch (error: any) {
    res.status(404).json({ error: "Lyrics not found", videoId });
  }
});

// 9. Album details
app.get("/album", async (req: Request, res: Response) => {
  const albumId = (req.query.id || req.query.albumId) as string;
  if (!albumId) {
    return res.status(400).json({ error: "Missing album ID parameter (?id=...)" });
  }

  try {
    const album = await ytmusic.getAlbum(albumId);
    res.json(album);
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to fetch album" });
  }
});

// 10. Artist details & songs
app.get("/artist", async (req: Request, res: Response) => {
  const artistId = (req.query.id || req.query.artistId) as string;
  if (!artistId) {
    return res.status(400).json({ error: "Missing artist ID parameter (?id=...)" });
  }

  try {
    const artist = await ytmusic.getArtist(artistId);
    res.json(artist);
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to fetch artist" });
  }
});

// 11. Trending & Charts
app.get(["/trending", "/charts"], async (req: Request, res: Response) => {
  const reqHost = `${req.protocol}://${req.get("host")}`;
  try {
    const results = await ytmusic.searchSongs("Top Global Hits 2026");
    const formatted = results.map((item) => formatTrack(item, reqHost));
    res.json(formatted);
  } catch (error: any) {
    res.status(500).json({ error: error.message || "Failed to fetch trending songs", data: [] });
  }
});

// Start server
initYTMusic().then(() => {
  app.listen(PORT, "0.0.0.0", () => {
    console.log(`🚀 Musync TypeScript YTMusic Server listening on port ${PORT}`);
  });
});
