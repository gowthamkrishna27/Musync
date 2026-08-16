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
  /**
   * High-Speed Audio Stream Resolver with L1/L2 Shared Caching and Single-Flight Coalescing.
   * If 100 users request track X at once, only 1 Python resolver runs!
   */
  static async resolveAudioStream(videoId: string, quality: string = "high"): Promise<StreamResolutionResult> {
    if (!videoId || videoId.length < 3) {
      return { entry: null, error: "Invalid videoId parameter" };
    }

    const safeQuality = ["low", "saver", "standard", "high"].includes(quality.toLowerCase()) ? quality.toLowerCase() : "high";
    const cacheKey = `stream:v2:${videoId}:${safeQuality}`;

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
        const scriptPath = path.join(process.cwd(), "stream_resolver.py");
        const { stdout, stderr } = await execAsync(`"${PYTHON_BIN}" "${scriptPath}" "${videoId}" "${safeQuality}"`, { timeout: 25000 });
        
        if (stderr && stderr.trim()) {
          console.warn(`[StreamResolver stderr for ${videoId} (${safeQuality})]:`, stderr.trim());
        }

        const parsed = JSON.parse(stdout.trim());
        if (parsed.url) {
          const ttlSeconds = 4 * 3600; // 4 hours TTL
          const entry: StreamCacheEntry = {
            url: parsed.url,
            headers: parsed.headers || {},
            expiresAt: Date.now() + ttlSeconds * 1000,
            format: "audio/webm",
            source: parsed.client || "android_vr"
          };

          // Store in L1 and L2
          await cacheService.set(cacheKey, entry, ttlSeconds);
          // Increment popularity
          await cacheService.recordTrackPlay(videoId);

          return { entry, source: "resolver" };
        } else if (parsed.error) {
          return { entry: null, error: parsed.error };
        }
      } catch (err: any) {
        return { entry: null, error: err.message || "Failed to resolve stream" };
      }

      return { entry: null, error: "Unknown stream resolution error" };
    });
  }

  /**
   * High-Performance Range-Aware Streaming Proxy
   * - Full support for Range: bytes=0-, bytes=1000000-, bytes=1000000-2000000
   * - Immediate upstream abort when client disconnects (zero socket leaks)
   * - Backpressure propagation
   * - Comprehensive safe streaming diagnostics & download throughput vs bitrate analysis
   */
  static async handleStreamRequest(req: Request, res: Response): Promise<void> {
    const videoId = (req.query.id || req.query.query || req.query.videoId) as string;
    const quality = (req.query.quality || "high") as string;
    if (!videoId) {
      res.status(400).json({ error: "Missing video ID parameter (?id=...)" });
      return;
    }

    const streamStartTime = Date.now();
    let timeToFirstByte = 0;
    let bytesReceived = 0;
    let bytesSent = 0;
    let isClientDisconnected = false;
    let isUpstreamDisconnected = false;
    let isStreamFinished = false;

    metricsService.incrementActiveStreams();

    const cleanup = () => {
      if (!isStreamFinished) {
        isStreamFinished = true;
        metricsService.decrementActiveStreams();

        const durationSec = Math.max(0.001, (Date.now() - streamStartTime) / 1000);
        const downstreamRateKbps = (bytesSent * 8) / (1000 * durationSec);
        const upstreamRateKbps = (bytesReceived * 8) / (1000 * durationSec);

        // Estimate estimated audio bitrate (approx 128 kbps for high, 96 for standard, 48 for saver)
        const estimatedBitrateKbps = quality === "saver" || quality === "low" ? 48 : (quality === "standard" ? 96 : 128);
        const throughputRatio = (downstreamRateKbps / estimatedBitrateKbps).toFixed(2);
        const healthStatus = parseFloat(throughputRatio) >= 1.0 ? "HEALTHY (Buffer growing)" : "RISK (Throughput < Bitrate, buffer depleting)";

        // Safe structured stream diagnostics (NO secrets/tokens logged)
        console.log(JSON.stringify({
          streamDiagnostic: {
            trackId: videoId,
            quality,
            streamStartTime: new Date(streamStartTime).toISOString(),
            timeToFirstByteMs: timeToFirstByte,
            bytesReceived,
            bytesSent,
            connectionDurationSec: parseFloat(durationSec.toFixed(2)),
            upstreamRateKbps: parseFloat(upstreamRateKbps.toFixed(1)),
            downstreamRateKbps: parseFloat(downstreamRateKbps.toFixed(1)),
            audioBitrateKbps: estimatedBitrateKbps,
            throughputRatio: `${throughputRatio}x`,
            streamHealth: healthStatus,
            clientDisconnect: isClientDisconnected,
            upstreamDisconnect: isUpstreamDisconnected
          }
        }));
      }
    };

    req.on("close", () => {
      if (!res.writableEnded) {
        isClientDisconnected = true;
      }
      cleanup();
    });
    res.on("finish", cleanup);
    res.on("error", cleanup);

    // 1. Resolve Audio Source
    const resolution = await StreamManager.resolveAudioStream(videoId, quality);
    const streamEntry = resolution.entry;

    if (!streamEntry) {
      cleanup();
      console.warn(`[Stream 502] Resolution failed for video ${videoId}: ${resolution.error}`);
      res.status(502).json({
        error: `Could not resolve stream for video: ${videoId}`,
        details: resolution.error
      });
      return;
    }

    // 2. Prepare Upstream Request with AbortController
    const abortController = new AbortController();
    req.on("close", () => {
      if (!res.writableEnded) {
        abortController.abort();
      }
    });

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

      const audioResponse = await streamHttpClient({
        method: "GET",
        url: streamEntry.url,
        headers: reqHeaders,
        responseType: "stream",
        signal: abortController.signal,
        validateStatus: (status) => status < 400
      });

      // Pass-through standard status and Range response headers
      const status = audioResponse.status;
      res.status(status);

      const contentType = audioResponse.headers["content-type"] || "audio/webm";
      res.setHeader("Content-Type", String(contentType));
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

      // Add cache-control to prevent intermediaries from corrupting range streams
      res.setHeader("Cache-Control", "public, max-age=14400, no-transform");

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
    } catch (err: any) {
      if (axios.isCancel(err) || err.name === "AbortError" || req.destroyed) {
        isClientDisconnected = true;
        cleanup();
        return;
      }

      isUpstreamDisconnected = true;
      cleanup();

      console.warn(`[Axios stream error for ${videoId}]: ${err.message}. Purging cache entry.`);
      await cacheService.delete(`stream:v2:${videoId}`);

      if (!res.headersSent) {
        res.status(502).json({
          error: "Upstream audio delivery error",
          message: err.message
        });
      }
    }
  }
}
