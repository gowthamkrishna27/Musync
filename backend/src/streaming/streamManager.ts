import http from "http";
import https from "https";
import axios, { AxiosInstance } from "axios";
import { Request, Response } from "express";
import { exec } from "child_process";
import { promisify } from "util";
import path from "path";
import { cacheService, StreamCacheEntry } from "../cache/cacheService";
import { metricsService } from "../metrics/metricsService";

const execAsync = promisify(exec);
const PYTHON_BIN = process.platform === "win32" ? "python" : "python3";

// High-capacity HTTP/HTTPS agents with socket pooling and keep-alive
const httpAgent = new http.Agent({
  keepAlive: true,
  keepAliveMsecs: 30000,
  maxSockets: 10000,
  maxFreeSockets: 512,
  timeout: 30000
});

const httpsAgent = new https.Agent({
  keepAlive: true,
  keepAliveMsecs: 30000,
  maxSockets: 10000,
  maxFreeSockets: 512,
  timeout: 30000
});

// Dedicated high-throughput Axios instance
const streamHttpClient: AxiosInstance = axios.create({
  httpAgent,
  httpsAgent,
  timeout: 25000,
  maxRedirects: 5,
  validateStatus: (status) => status < 400
});

export interface StreamResolutionResult {
  entry: StreamCacheEntry | null;
  error?: string;
  source?: "l1" | "redis" | "resolver";
}

export class StreamManager {
  // Semaphore: cap concurrent yt-dlp Python spawns to prevent OOM on Railway
  // Each Python process uses ~50-80MB RAM. 8 = max ~640MB, safe for Railway hobby tier.
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
            // Remove this waiter from the queue to avoid dangling references
            const idx = StreamManager.resolveQueue.indexOf(waiter);
            if (idx !== -1) StreamManager.resolveQueue.splice(idx, 1);
            reject(new Error(`Stream resolver semaphore timeout after ${timeoutMs}ms`));
          }
        }, timeoutMs);

        const waiter = () => {
          if (!settled) {
            settled = true;
            clearTimeout(timer);
            // Slot ownership is transferred directly from releaseResolveSlot;
            // do NOT increment activeResolves here — the slot count stays the same.
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
      // Transfer slot ownership directly to the next waiter — activeResolves stays the same.
      next();
    } else {
      // No waiter queued — free the slot.
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
    const cacheKey = `stream:v4:audio:${videoId}:${safeQuality}`;

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

        // Acquire semaphore slot — caps concurrent Python spawns to MAX_CONCURRENT_RESOLVES
        // acquireResolveSlot may reject with a timeout error; if it does, we do NOT hold
        // a slot, so releaseResolveSlot must NOT be called in that case.
        let slotAcquired = false;
        await StreamManager.acquireResolveSlot(20000, highPriority);
        slotAcquired = true;
        let stdout: string, stderr: string;
        try {
          ({ stdout, stderr } = await execAsync(
            `"${PYTHON_BIN}" "${scriptPath}" "${videoId}" "${safeQuality}" "audio"`,
            { timeout: 25000 }
          ));
        } finally {
          if (slotAcquired) StreamManager.releaseResolveSlot();
        }
        
        if (stderr && stderr.trim()) {
          console.warn(`[StreamResolver stderr for ${videoId} (${safeQuality})]:`, stderr.trim());
        }

        const parsed = JSON.parse(stdout.trim());
        if (parsed.url) {
          const ttlSeconds = 2 * 3600; // 2 hours TTL — keeps signed URLs well within their expiry window
          const entry: StreamCacheEntry = {
            url: parsed.url,
            headers: parsed.headers || {},
            expiresAt: Date.now() + ttlSeconds * 1000,
            format: `audio/${parsed.ext || "m4a"}`,
            ext: parsed.ext || "m4a",
            source: parsed.client || "ios"
          };

          // Store in L1 and L2
          await cacheService.set(cacheKey, entry, ttlSeconds);
          // Increment popularity
          await cacheService.recordTrackPlay(videoId);

          return {
            entry,
            source: "resolver"
          };
        } else if (parsed.error) {
          return { entry: null, error: parsed.error };
        }
      } catch (err: any) {
        return { entry: null, error: err.message || "Failed to resolve audio stream" };
      }

      return { entry: null, error: "Unknown audio stream resolution error" };
    });
  }

  static async resolveAudioStream(videoId: string, quality: string = "low"): Promise<StreamResolutionResult> {
    return this.resolveStream(videoId, quality);
  }

  /**
   * Audio Stream Handler — Redirect Mode
   *
   * Resolves the signed YouTube CDN URL server-side (with caching & single-flight
   * coalescing) then issues a 302 redirect so ExoPlayer fetches the audio bytes
   * directly from YouTube's CDN. This avoids server-side proxying which caused 502s
   * because YouTube CDN blocks requests originating from Railway server IPs.
   *
   * Falls back to the legacy proxy path only if the client sends a Range header
   * (some players require byte-range proxying for seek support).
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

    // Resolve the CDN URL (cached up to 2h)
    const resolution = await StreamManager.resolveStream(videoId, quality, true);
    if (!resolution.entry) {
      console.warn(`[Stream 502] Resolution failed for ${videoId}: ${resolution.error}`);
      res.status(502).json({
        error: `Could not resolve stream for track: ${videoId}`,
        details: resolution.error
      });
      return;
    }

    const streamStartTime = Date.now();
    let timeToFirstByte = 0;
    let bytesReceived = 0;
    let bytesSent = 0;
    let isClientDisconnected = false;
    let isUpstreamDisconnected = false;
    let isStreamFinished = false;

    // Proxy cleanup — metrics already incremented at top of method
    const proxyCleanup = () => {
      if (!isStreamFinished) {
        isStreamFinished = true;

        const durationSec = Math.max(0.001, (Date.now() - streamStartTime) / 1000);
        const downstreamRateKbps = (bytesSent * 8) / (1000 * durationSec);
        const upstreamRateKbps = (bytesReceived * 8) / (1000 * durationSec);

        const estimatedBitrateKbps = quality === "saver" || quality === "low" ? 48 : (quality === "standard" ? 96 : 128);
        const throughputRatio = (downstreamRateKbps / estimatedBitrateKbps).toFixed(2);
        const healthStatus = parseFloat(throughputRatio) >= 1.0 ? "HEALTHY (Buffer growing)" : "RISK (Throughput < Bitrate, buffer depleting)";

        console.log(JSON.stringify({
          streamDiagnostic: {
            trackId: videoId, mediaType: "audio", quality,
            streamStartTime: new Date(streamStartTime).toISOString(),
            timeToFirstByteMs: timeToFirstByte,
            bytesReceived, bytesSent,
            connectionDurationSec: parseFloat(durationSec.toFixed(2)),
            upstreamRateKbps: parseFloat(upstreamRateKbps.toFixed(1)),
            downstreamRateKbps: parseFloat(downstreamRateKbps.toFixed(1)),
            bitrateKbps: estimatedBitrateKbps,
            throughputRatio: `${throughputRatio}x`,
            streamHealth: healthStatus,
            clientDisconnect: isClientDisconnected,
            upstreamDisconnect: isUpstreamDisconnected
          }
        }));
      }
    };

    req.on("close", () => {
      if (!res.writableEnded) isClientDisconnected = true;
      proxyCleanup();
    });
    res.on("error", proxyCleanup);

    const cacheKey = `stream:v4:audio:${videoId}:${quality.toLowerCase()}`;

    // Use the already-resolved stream entry from the top of the method
    let streamEntry = resolution.entry;

    // 2. Prepare Upstream Request with AbortController & Automatic Retry
    const abortController = new AbortController();
    req.on("close", () => {
      if (!res.writableEnded) {
        abortController.abort();
      }
    });

    const rangeHeader = req.headers.range;

    const executeUpstreamRequest = async (entry: StreamCacheEntry) => {
      const reqHeaders: Record<string, string> = {
        ...entry.headers,
        "Accept": "*/*",
        "Sec-Fetch-Mode": "navigate"
      };

      if (rangeHeader) {
        reqHeaders["Range"] = rangeHeader;
      }

      return await streamHttpClient({
        method: "GET",
        url: entry.url,
        headers: reqHeaders,
        responseType: "stream",
        signal: abortController.signal,
        validateStatus: (status) => status < 400
      });
    };

    let audioResponse;
    try {
      audioResponse = await executeUpstreamRequest(streamEntry);
    } catch (firstErr: any) {
      if (axios.isCancel(firstErr) || firstErr.name === "AbortError" || req.destroyed) {
        isClientDisconnected = true;
        cleanup();
        return;
      }

      console.warn(`[Upstream retry] Audio stream request failed for ${videoId} (${firstErr.message}). Purging cache and re-resolving fresh stream...`);
      await cacheService.delete(cacheKey);

      try {
        // Force fresh resolution bypassing cache
        const freshResolution = await StreamManager.resolveStream(videoId, quality);
        if (freshResolution.entry) {
          streamEntry = freshResolution.entry;
          audioResponse = await executeUpstreamRequest(streamEntry);
        } else {
          throw new Error(freshResolution.error || "Failed fresh resolution");
        }
      } catch (retryErr: any) {
        if (axios.isCancel(retryErr) || retryErr.name === "AbortError" || req.destroyed) {
          isClientDisconnected = true;
          cleanup();
          return;
        }

        isUpstreamDisconnected = true;
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
      // Pass-through standard status and Range response headers
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

      // Hint ExoPlayer with audio duration so it can size its buffer correctly
      // even when Content-Length is absent (e.g., chunked transfer encoding)
      if (!contentLength) {
        // Provide a conservative estimated size hint based on format & typical duration
        // This lets ExoPlayer allocate its buffer without waiting for the full transfer
        const estimatedBitrateKbps = quality === "saver" || quality === "low" ? 48 : (quality === "standard" ? 96 : 128);
        const estimatedBytes = estimatedBitrateKbps * 1000 / 8 * 240; // 4 minutes max estimate
        res.setHeader("X-Content-Duration", "240"); // seconds hint
        res.setHeader("X-Estimated-Content-Length", String(Math.round(estimatedBytes)));
      }

      // Add cache-control to prevent intermediaries from corrupting range streams
      res.setHeader("Cache-Control", "public, max-age=7200, no-transform");

      // Monitor chunk streams for TTFB, byte counts, and backpressure
      audioResponse.data.on("data", (chunk: Buffer) => {
        if (!timeToFirstByte) {
          timeToFirstByte = Date.now() - streamStartTime;
        }
        bytesReceived += chunk.length;
        bytesSent += chunk.length;
      });

      audioResponse.data.on("end", () => {
        // Stream completed upstream
      });

      audioResponse.data.on("error", (streamErr: any) => {
        isUpstreamDisconnected = true;
        cleanup();
        if (!res.headersSent) {
          res.status(500).json({ error: "Stream transmission error", details: streamErr.message });
        }
      });

      // Pipe upstream to client response with native Node backpressure
      audioResponse.data.pipe(res);
    } catch (pipeErr: any) {
      cleanup();
    }
  }
}
