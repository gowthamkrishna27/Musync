import http from "http";
import https from "https";
import { Request, Response } from "express";
import { exec } from "child_process";
import { promisify } from "util";
import path from "path";
import { URL } from "url";
import { cacheService, StreamCacheEntry } from "../cache/cacheService";
import { metricsService } from "../metrics/metricsService";
import { youtubeService } from "../services/youtube/youtube.service";
import { musicProxyService } from "../services/proxy/musicProxy.service";

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
    const cacheKey = `stream:v8:audio:${videoId}:${safeQuality}`;

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

        const lines = stdout.trim().split("\n");
        let parsed: any = null;
        for (let i = lines.length - 1; i >= 0; i--) {
          const l = lines[i].trim();
          if (l.startsWith("{") && l.endsWith("}")) {
            try {
              parsed = JSON.parse(l);
              break;
            } catch {}
          }
        }

        if (parsed && parsed.url && parsed.url.startsWith("http")) {
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
        } else if (parsed && parsed.error) {
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

      // Fallback: Piped Audio Gateway Resolver
      try {
        const pipedUrl = await musicProxyService.resolvePipedStreamUrl(videoId);
        if (pipedUrl) {
          const ttlSeconds = 3600;
          const entry: StreamCacheEntry = {
            url: pipedUrl,
            headers: {
              "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
              "Accept": "*/*"
            },
            expiresAt: Date.now() + ttlSeconds * 1000,
            format: "audio/webm",
            ext: "webm",
            source: "piped"
          };

          await cacheService.set(cacheKey, entry, ttlSeconds);
          await cacheService.recordTrackPlay(videoId);

          return { entry, source: "resolver" };
        }
      } catch (pipedErr: any) {
        console.warn(`[Piped resolver error for ${videoId}]:`, pipedErr.message);
      }

      // Fallback: Invidious Audio Gateway Resolver
      try {
        const invidiousUrl = await musicProxyService.resolveInvidiousStreamUrl(videoId);
        if (invidiousUrl) {
          const ttlSeconds = 3600;
          const entry: StreamCacheEntry = {
            url: invidiousUrl,
            headers: {
              "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
              "Accept": "*/*"
            },
            expiresAt: Date.now() + ttlSeconds * 1000,
            format: "audio/mp4",
            ext: "m4a",
            source: "invidious"
          };

          await cacheService.set(cacheKey, entry, ttlSeconds);
          await cacheService.recordTrackPlay(videoId);

          return { entry, source: "resolver" };
        }
      } catch (invErr: any) {
        console.warn(`[Invidious resolver error for ${videoId}]:`, invErr.message);
      }

      return { entry: null, error: "Failed to resolve audio stream across all resolvers" };
    });
  }

  static async resolveAudioStream(videoId: string, quality: string = "low"): Promise<StreamResolutionResult> {
    return this.resolveStream(videoId, quality);
  }

  /**
   * Native Stream Proxy with Byte-Range, Redirect & CORS Support
   *
   * Streams audio chunks directly from CDN / gateway endpoints with IPv4 binding
   * and auto-redirect handling for Invidious/Piped CDN tokens.
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

    // Set full CORS headers
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

    const streamEntry = resolution.entry;
    const rangeHeader = req.headers.range || "bytes=0-";

    // Direct redirect mode option (zero proxy overhead, direct stream on mobile):
    if (req.query.redirect === "true" || req.query.direct === "true") {
      cleanup();
      res.redirect(302, streamEntry.url);
      return;
    }

    const streamUpstream = (targetUrl: string, redirectCount = 0) => {
      if (redirectCount > 5) {
        cleanup();
        if (!res.headersSent) res.status(502).json({ error: "Too many redirects from stream provider" });
        return;
      }

      try {
        const u = new URL(targetUrl);
        const protocolLib = u.protocol === "http:" ? http : https;
        const reqHeaders: Record<string, string> = {
          "User-Agent": streamEntry.headers?.["User-Agent"] || "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
          "Accept": "*/*",
          "Accept-Encoding": "identity",
          "Range": rangeHeader
        };

        const upstreamReq = protocolLib.request({
          protocol: u.protocol,
          hostname: u.hostname,
          port: u.port ? parseInt(u.port, 10) : (u.protocol === "http:" ? 80 : 443),
          path: u.pathname + u.search,
          method: "GET",
          family: 4,
          headers: reqHeaders
        }, (upstreamRes) => {
          const status = upstreamRes.statusCode || 200;

          // Follow redirect if 3xx
          if (status >= 300 && status < 400 && upstreamRes.headers.location) {
            const nextUrl = upstreamRes.headers.location.startsWith("http")
              ? upstreamRes.headers.location
              : new URL(upstreamRes.headers.location, targetUrl).toString();
            return streamUpstream(nextUrl, redirectCount + 1);
          }

          let contentType = String(upstreamRes.headers["content-type"] || "");

          // If Google CDN returned a bot challenge HTML page or 403 to Render's datacenter IP:
          // Immediately redirect the client to Google CDN (ExoPlayer will stream directly from mobile IP)
          if (contentType.includes("text/html") || status === 403) {
            console.log(`[StreamManager] Datacenter IP challenge detected (${contentType}, ${status}). Redirecting mobile client directly to CDN.`);
            cleanup();
            if (!res.headersSent) {
              res.redirect(302, targetUrl);
            }
            return;
          }

          if (status >= 400) {
            console.error(`[Upstream CDN ${status} for ${videoId}]:`, upstreamRes.statusMessage);
            cleanup();
            if (!res.headersSent) {
              res.status(502).json({
                error: "Upstream CDN error",
                status,
                statusMessage: upstreamRes.statusMessage
              });
            }
            return;
          }

          res.status(status);

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

          if (upstreamRes.headers["content-length"]) {
            res.setHeader("Content-Length", String(upstreamRes.headers["content-length"]));
          }
          if (upstreamRes.headers["content-range"]) {
            res.setHeader("Content-Range", String(upstreamRes.headers["content-range"]));
          }

          upstreamRes.pipe(res);
        });

        upstreamReq.on("error", (err: any) => {
          cleanup();
          if (!res.headersSent) {
            res.status(502).json({ error: "Upstream request failed", message: err.message });
          }
        });

        req.on("close", () => {
          upstreamReq.destroy();
        });

        upstreamReq.end();
      } catch (e: any) {
        cleanup();
        if (!res.headersSent) {
          res.status(500).json({ error: "Stream proxy error", message: e.message });
        }
      }
    };

    streamUpstream(streamEntry.url);
  }
}
