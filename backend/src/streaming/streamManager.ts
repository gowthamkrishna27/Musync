import { Request, Response } from "express";
import { exec } from "child_process";
import { promisify } from "util";
import path from "path";
import { cacheService, StreamCacheEntry } from "../cache/cacheService";
import { metricsService } from "../metrics/metricsService";
import { youtubeService } from "../services/youtube/youtube.service";

const execAsync = promisify(exec);
const PYTHON_BIN = process.platform === "win32" ? "python" : "python3";

export interface StreamResolutionResult {
  entry: StreamCacheEntry | null;
  error?: string;
  source?: "l1" | "redis" | "resolver";
}

export class StreamManager {
  // Semaphore: cap concurrent yt-dlp Python spawns to prevent OOM on Railway
  private static activeResolves = 0;
  private static readonly MAX_CONCURRENT_RESOLVES = 8;
  private static resolveQueue: Array<() => void> = [];

  private static acquireResolveSlot(timeoutMs: number = 20000, highPriority: boolean = false): Promise<void> {
    return new Promise((resolve, reject) => {
      if (StreamManager.activeResolves < StreamManager.MAX_CONCURRENT_RESOLVES) {
        StreamManager.activeResolves++;
        resolve();
      } else {
        let settled = false;
        const timer = setTimeout(() => {
          if (!settled) {
            settled = true;
            const idx = StreamManager.resolveQueue.indexOf(waiter);
            if (idx !== -1) StreamManager.resolveQueue.splice(idx, 1);
            reject(new Error(`Stream resolver semaphore timeout after ${timeoutMs}ms`));
          }
        }, timeoutMs);

        const waiter = () => {
          if (!settled) {
            settled = true;
            clearTimeout(timer);
            resolve();
          }
        };

        if (highPriority) {
          StreamManager.resolveQueue.unshift(waiter);
        } else {
          StreamManager.resolveQueue.push(waiter);
        }
      }
    });
  }

  private static releaseResolveSlot(): void {
    const next = StreamManager.resolveQueue.shift();
    if (next) {
      next();
    } else {
      StreamManager.activeResolves--;
    }
  }

  /**
   * Pre-warm the top N most-played tracks from Redis so their stream URLs
   * are hot in L1 cache before any user requests them.
   */
  static async preWarmPopularTracks(topN: number = 15): Promise<void> {
    try {
      const popularIds = await cacheService.getPopularTracks(topN);
      if (popularIds.length === 0) return;
      console.log(`[PreWarm] Warming ${popularIds.length} popular tracks...`);
      for (const id of popularIds) {
        await StreamManager.resolveStream(id, "low", false);
      }
      console.log(`[PreWarm] ✓ Tracks pre-warmed in L1 cache.`);
    } catch (err: any) {
      console.warn("[PreWarm] Failed to pre-warm popular tracks:", err.message);
    }
  }

  /**
   * Universal Audio Stream Resolver with L1/L2 Shared Caching and Single-Flight Coalescing.
   */
  static async resolveStream(videoId: string, quality: string = "low", highPriority: boolean = false): Promise<StreamResolutionResult> {
    if (!videoId || videoId.length < 3) {
      return { entry: null, error: "Invalid videoId parameter" };
    }

    const safeQuality = ["low", "saver", "standard", "high"].includes(quality.toLowerCase()) ? quality.toLowerCase() : "low";
    const cacheKey = `stream:v5:audio:${videoId}:${safeQuality}`;

    // 1. Check L1 / L2 Cache
    const cached = await cacheService.get<StreamCacheEntry>(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      return { entry: cached, source: "l1" };
    }

    // 2. Coalesced Resolution (Single-Flight)
    return await cacheService.coalesce<StreamResolutionResult>(cacheKey, async () => {
      // Re-check cache in case another worker just resolved it
      const recheck = await cacheService.get<StreamCacheEntry>(cacheKey);
      if (recheck && recheck.expiresAt > Date.now()) {
        return { entry: recheck, source: "redis" };
      }

      // Primary: Python stream_resolver.py (with android/web_embedded & challenge solver)
      try {
        const fs = require("fs");
        const candidatePaths = [
          path.join(process.cwd(), "scripts", "stream_resolver.py"),
          path.join(process.cwd(), "stream_resolver.py"),
          path.join(__dirname, "../../scripts/stream_resolver.py"),
          path.join(__dirname, "../scripts/stream_resolver.py"),
          path.join(__dirname, "stream_resolver.py")
        ];
        const scriptPath = candidatePaths.find((p: string) => fs.existsSync(p)) || candidatePaths[0];

        let slotAcquired = false;
        await StreamManager.acquireResolveSlot(20000, highPriority);
        slotAcquired = true;
        let stdout: string, stderr: string;
        try {
          ({ stdout, stderr } = await execAsync(
            `"${PYTHON_BIN}" "${scriptPath}" "${videoId}" "${safeQuality}" "audio"`,
            {
              timeout: 25000,
              env: { ...process.env }
            }
          ));
        } finally {
          if (slotAcquired) StreamManager.releaseResolveSlot();
        }

        const parsed = JSON.parse(stdout.trim());
        if (parsed.url) {
          const ttlSeconds = 2 * 3600;
          const entry: StreamCacheEntry = {
            url: parsed.url,
            headers: parsed.headers || {},
            expiresAt: Date.now() + ttlSeconds * 1000,
            format: `audio/${parsed.ext || "m4a"}`,
            ext: parsed.ext || "m4a",
            source: parsed.client || "resolver"
          };

          await cacheService.set(cacheKey, entry, ttlSeconds);
          await cacheService.recordTrackPlay(videoId);

          return { entry, source: "resolver" };
        } else if (parsed.error) {
          const errType = parsed.error_type || "RESOLVER_ERROR";
          console.warn(`[StreamResolver ${errType} for ${videoId}]:`, parsed.error);
        }
      } catch (err: any) {
        console.warn(`[StreamResolver execution error for ${videoId}]:`, err.message);
      }

      // Fallback: Pure TypeScript YouTube.js Service
      try {
        const resolved = await youtubeService.resolveAudioStream(videoId, safeQuality);
        if (resolved && resolved.url) {
          const ttlSeconds = 2 * 3600; // 2 hours
          const entry: StreamCacheEntry = {
            url: resolved.url,
            headers: resolved.headers,
            expiresAt: resolved.expiresAt,
            format: resolved.format,
            ext: resolved.ext,
            bitrate: resolved.bitrate,
            source: "youtube.js"
          };

          await cacheService.set(cacheKey, entry, ttlSeconds);
          await cacheService.recordTrackPlay(videoId);

          return { entry, source: "resolver" };
        }
      } catch (ytjsErr: any) {
        console.warn(`[YouTube.js resolver error for ${videoId}]:`, ytjsErr.message);
      }

      return { entry: null, error: "Failed to resolve audio stream across all resolvers" };
    });
  }

  static async resolveAudioStream(videoId: string, quality: string = "low"): Promise<StreamResolutionResult> {
    return this.resolveStream(videoId, quality);
  }

  /**
   * Audio Stream Handler — Direct High-Performance CDN Redirect Mode
   *
   * Resolves the signed YouTube CDN URL server-side (with L1 caching & single-flight
   * coalescing) then issues a 302 redirect so ExoPlayer / HTML5 Audio fetches the audio bytes
   * directly from YouTube's high-speed CDN.
   *
   * This provides instant startup (<150ms TTFB), eliminates server-side proxying bottlenecks,
   * supports full hardware-accelerated seeking, and ensures complete client compatibility.
   */
  static async handleStreamRequest(req: Request, res: Response): Promise<void> {
    const videoId = (req.query.id || req.query.query || req.query.videoId) as string;
    const quality = (req.query.quality || "low") as string;

    if (!videoId) {
      res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
      return;
    }

    metricsService.incrementActiveStreams();
    const cleanup = () => metricsService.decrementActiveStreams();
    res.on("finish", cleanup);
    res.on("close", cleanup);

    // Set full CORS headers for Web, Capacitor WebView & Android ExoPlayer
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Range, Content-Type, Accept, Origin, User-Agent");
    res.setHeader("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges, Content-Type, X-Content-Duration, Location");
    res.setHeader("Cache-Control", "public, max-age=7200, no-transform");

    if (req.method === "OPTIONS") {
      res.status(204).end();
      return;
    }

    // Resolve the CDN URL (cached up to 2h)
    const resolution = await StreamManager.resolveStream(videoId, quality, true);
    if (!resolution.entry || !resolution.entry.url) {
      console.warn(`[Stream 502] Resolution failed for ${videoId}: ${resolution.error}`);
      res.status(502).json({
        error: `Could not resolve stream for track: ${videoId}`,
        details: resolution.error
      });
      return;
    }

    // 302 Found redirect to direct CDN streaming URL
    res.redirect(302, resolution.entry.url);
  }
}
