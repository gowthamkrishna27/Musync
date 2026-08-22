import http from "http";
import https from "https";
import axios from "axios";
import { Request, Response } from "express";
import { exec } from "child_process";
import { promisify } from "util";
import path from "path";
import { cacheService, StreamCacheEntry } from "../cache/cacheService";
import { metricsService } from "../metrics/metricsService";
import { youtubeService } from "../services/youtube/youtube.service";

const execAsync = promisify(exec);
const PYTHON_BIN = process.platform === "win32" ? "python" : "python3";

// High-capacity HTTP/HTTPS agents with IPv4 socket pooling and keep-alive
const httpAgent = new http.Agent({
  keepAlive: true,
  keepAliveMsecs: 30000,
  maxSockets: 10000,
  maxFreeSockets: 512,
  timeout: 30000,
  family: 4
});

const httpsAgent = new https.Agent({
  keepAlive: true,
  keepAliveMsecs: 30000,
  maxSockets: 10000,
  maxFreeSockets: 512,
  timeout: 30000,
  family: 4
});

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
    const cacheKey = `stream:v7:audio:${videoId}:${safeQuality}`;

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

      // Primary: Python stream_resolver.py (with android_vr / challenge solver)
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
            format: `audio/${parsed.ext || "webm"}`,
            ext: parsed.ext || "webm",
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
   * Audio Stream Handler — High-Performance Direct Stream Proxy with Byte-Range & CORS Support
   *
   * Fetches the audio bytes from YouTube CDN using the server's signed IPv4 connection and
   * pipes raw audio directly to the browser / Android player with complete CORS & Range headers.
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

    // Set full CORS headers for Web, Android WebView, Capacitor & ExoPlayer
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Range, Content-Type, Accept, Origin, User-Agent");
    res.setHeader("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges, Content-Type, X-Content-Duration");
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

    const streamStartTime = Date.now();
    let timeToFirstByte = 0;
    let bytesSent = 0;
    let isStreamFinished = false;

    const proxyCleanup = () => {
      if (!isStreamFinished) {
        isStreamFinished = true;
        const durationSec = Math.max(0.001, (Date.now() - streamStartTime) / 1000);
        const downstreamRateKbps = (bytesSent * 8) / (1000 * durationSec);
        const estimatedBitrateKbps = quality === "saver" || quality === "low" ? 48 : (quality === "standard" ? 96 : 128);
        const throughputRatio = (downstreamRateKbps / estimatedBitrateKbps).toFixed(2);
        const healthStatus = parseFloat(throughputRatio) >= 1.0 ? "HEALTHY" : "BUFFERING";

        console.log(JSON.stringify({
          streamDiagnostic: {
            trackId: videoId, quality,
            timeToFirstByteMs: timeToFirstByte,
            bytesSent,
            connectionDurationSec: parseFloat(durationSec.toFixed(2)),
            downstreamRateKbps: parseFloat(downstreamRateKbps.toFixed(1)),
            throughputRatio: `${throughputRatio}x`,
            streamHealth: healthStatus
          }
        }));
      }
    };

    req.on("close", proxyCleanup);
    res.on("error", proxyCleanup);

    const cacheKey = `stream:v7:audio:${videoId}:${quality.toLowerCase()}`;
    let streamEntry = resolution.entry;

    // Upstream Request configuration
    const abortController = new AbortController();
    req.on("close", () => {
      if (!res.writableEnded) {
        abortController.abort();
      }
    });

    const executeUpstreamRequest = async (entry: StreamCacheEntry) => {
      const reqHeaders: Record<string, string> = {
        "User-Agent": entry.headers?.["User-Agent"] || "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "*/*",
        "Accept-Encoding": "identity",
        "Connection": "keep-alive"
      };

      if (req.headers.range) {
        reqHeaders["Range"] = req.headers.range;
      }

      return await axios({
        method: "GET",
        url: entry.url,
        headers: reqHeaders,
        responseType: "stream",
        signal: abortController.signal,
        httpAgent,
        httpsAgent,
        timeout: 25000,
        validateStatus: (status) => status < 400
      });
    };

    let audioResponse;
    try {
      audioResponse = await executeUpstreamRequest(streamEntry);
    } catch (firstErr: any) {
      if (axios.isCancel(firstErr) || firstErr.name === "AbortError" || req.destroyed) {
        cleanup();
        return;
      }

      console.warn(`[Upstream retry] Audio stream request failed for ${videoId} (${firstErr.message}). Purging cache and re-resolving fresh stream...`);
      await cacheService.delete(cacheKey);

      try {
        const freshResolution = await StreamManager.resolveStream(videoId, quality);
        if (freshResolution.entry) {
          streamEntry = freshResolution.entry;
          audioResponse = await executeUpstreamRequest(streamEntry);
        } else {
          throw new Error(freshResolution.error || "Failed fresh resolution");
        }
      } catch (retryErr: any) {
        if (axios.isCancel(retryErr) || retryErr.name === "AbortError" || req.destroyed) {
          cleanup();
          return;
        }

        cleanup();
        await cacheService.delete(cacheKey);

        if (!res.headersSent) {
          res.status(502).json({
            error: "Upstream audio delivery error after retry",
            message: retryErr.message
          });
        }
        return;
      }
    }

    try {
      const status = audioResponse.status;
      res.status(status);

      let contentType = String(audioResponse.headers["content-type"] || "");
      if (!contentType || contentType === "application/octet-stream" || contentType.startsWith("video/")) {
        if (streamEntry.ext === "webm" || contentType.includes("webm") || streamEntry.format?.includes("webm")) {
          contentType = "audio/webm";
        } else if (streamEntry.ext === "aac" || streamEntry.format?.includes("aac")) {
          contentType = "audio/aac";
        } else {
          contentType = "audio/mp4";
        }
      }
      res.setHeader("Content-Type", contentType);
      res.setHeader("Accept-Ranges", "bytes");

      const contentLength = audioResponse.headers["content-length"];
      if (contentLength) {
        res.setHeader("Content-Length", String(contentLength));
        metricsService.recordRangeRequest(parseInt(String(contentLength), 10) || 0);
      }

      const contentRange = audioResponse.headers["content-range"];
      if (contentRange) {
        res.setHeader("Content-Range", String(contentRange));
      }

      if (!contentLength) {
        const estimatedBitrateKbps = quality === "saver" || quality === "low" ? 48 : (quality === "standard" ? 96 : 128);
        const estimatedBytes = estimatedBitrateKbps * 1000 / 8 * 240;
        res.setHeader("X-Content-Duration", "240");
        res.setHeader("X-Estimated-Content-Length", String(Math.round(estimatedBytes)));
      }

      audioResponse.data.on("data", (chunk: Buffer) => {
        if (!timeToFirstByte) {
          timeToFirstByte = Date.now() - streamStartTime;
        }
        bytesSent += chunk.length;
      });

      audioResponse.data.on("error", (streamErr: any) => {
        cleanup();
        if (!res.headersSent) {
          res.status(500).json({ error: "Stream transmission error", details: streamErr.message });
        }
      });

      audioResponse.data.pipe(res);
    } catch (pipeErr: any) {
      cleanup();
    }
  }
}
