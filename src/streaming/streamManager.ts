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
  availableQualities?: string[];
  mediaType?: "audio" | "video";
}

export class StreamManager {
  /**
   * Universal Audio/Video Stream Resolver with L1/L2 Shared Caching and Single-Flight Coalescing.
   */
  static async resolveStream(videoId: string, quality: string = "low", mediaType: "audio" | "video" = "audio"): Promise<StreamResolutionResult> {
    if (!videoId || videoId.length < 3) {
      return { entry: null, error: "Invalid videoId parameter" };
    }

    const isVideo = mediaType === "video" || ["144p", "360p", "480p", "720p", "1080p"].includes(quality.toLowerCase());
    const safeQuality = isVideo 
      ? (["144p", "360p", "480p", "720p", "1080p", "auto"].includes(quality.toLowerCase()) ? quality.toLowerCase() : "auto")
      : (["low", "saver", "standard", "high"].includes(quality.toLowerCase()) ? quality.toLowerCase() : "low");

    const cacheKey = `stream:v3:${isVideo ? "video" : "audio"}:${videoId}:${safeQuality}`;

    // 1. Check L1 / L2 Cache
    const cached = await cacheService.get<StreamCacheEntry>(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      return { entry: cached, source: "l1", mediaType: isVideo ? "video" : "audio", availableQualities: ['Auto', '1080p', '720p', '480p', '360p', '144p'] };
    }

    // 2. Coalesced Resolution (Single-Flight)
    return await cacheService.coalesce<StreamResolutionResult>(cacheKey, async () => {
      // Re-check cache in case another worker just resolved it
      const recheck = await cacheService.get<StreamCacheEntry>(cacheKey);
      if (recheck && recheck.expiresAt > Date.now()) {
        return { entry: recheck, source: "redis", mediaType: isVideo ? "video" : "audio", availableQualities: ['Auto', '1080p', '720p', '480p', '360p', '144p'] };
      }

      try {
        const scriptPath = path.join(process.cwd(), "stream_resolver.py");
        const { stdout, stderr } = await execAsync(
          `"${PYTHON_BIN}" "${scriptPath}" "${videoId}" "${safeQuality}" "${isVideo ? "video" : "audio"}"`,
          { timeout: 25000 }
        );
        
        if (stderr && stderr.trim()) {
          console.warn(`[StreamResolver stderr for ${videoId} (${safeQuality}, ${isVideo ? "video" : "audio"})]:`, stderr.trim());
        }

        const parsed = JSON.parse(stdout.trim());
        if (parsed.url) {
          const ttlSeconds = 4 * 3600; // 4 hours TTL
          const format = isVideo ? `video/${parsed.ext || "mp4"}` : `audio/${parsed.ext || "webm"}`;
          const entry: StreamCacheEntry = {
            url: parsed.url,
            headers: parsed.headers || {},
            expiresAt: Date.now() + ttlSeconds * 1000,
            format: format,
            source: parsed.client || "android_vr"
          };

          // Store in L1 and L2
          await cacheService.set(cacheKey, entry, ttlSeconds);
          // Increment popularity
          await cacheService.recordTrackPlay(videoId);

          return {
            entry,
            source: "resolver",
            mediaType: isVideo ? "video" : "audio",
            availableQualities: parsed.available_qualities || ['Auto', '1080p', '720p', '480p', '360p', '144p']
          };
        } else if (parsed.error) {
          return { entry: null, error: parsed.error };
        }
      } catch (err: any) {
        return { entry: null, error: err.message || "Failed to resolve stream" };
      }

      return { entry: null, error: "Unknown stream resolution error" };
    });
  }

  static async resolveAudioStream(videoId: string, quality: string = "low"): Promise<StreamResolutionResult> {
    return this.resolveStream(videoId, quality, "audio");
  }

  static async resolveVideoStream(videoId: string, quality: string = "auto"): Promise<StreamResolutionResult> {
    return this.resolveStream(videoId, quality, "video");
  }

  /**
   * High-Performance Range-Aware Streaming Proxy
   * - Full support for Range: bytes=0-, bytes=1000000-, bytes=1000000-2000000
   * - Supports both Audio and Video stream delivery
   * - Immediate upstream abort when client disconnects (zero socket leaks)
   * - Backpressure propagation
   */
  static async handleStreamRequest(req: Request, res: Response): Promise<void> {
    const videoId = (req.query.id || req.query.query || req.query.videoId) as string;
    const quality = (req.query.quality || "low") as string;
    const type = ((req.query.type || req.query.mediaType || "audio") as string).toLowerCase();
    const isVideo = type === "video" || ["144p", "360p", "480p", "720p", "1080p"].includes(quality.toLowerCase());

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

        const estimatedBitrateKbps = isVideo
          ? (quality === "1080p" ? 2500 : (quality === "720p" ? 1500 : (quality === "480p" ? 800 : 500)))
          : (quality === "saver" || quality === "low" ? 48 : (quality === "standard" ? 96 : 128));
        const throughputRatio = (downstreamRateKbps / estimatedBitrateKbps).toFixed(2);
        const healthStatus = parseFloat(throughputRatio) >= 1.0 ? "HEALTHY (Buffer growing)" : "RISK (Throughput < Bitrate, buffer depleting)";

        console.log(JSON.stringify({
          streamDiagnostic: {
            trackId: videoId,
            mediaType: isVideo ? "video" : "audio",
            quality,
            streamStartTime: new Date(streamStartTime).toISOString(),
            timeToFirstByteMs: timeToFirstByte,
            bytesReceived,
            bytesSent,
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
      if (!res.writableEnded) {
        isClientDisconnected = true;
      }
      cleanup();
    });
    res.on("finish", cleanup);
    res.on("error", cleanup);

    const cacheKey = `stream:v3:${isVideo ? "video" : "audio"}:${videoId}:${quality.toLowerCase()}`;

    // 1. Resolve Audio or Video Source
    let resolution = await StreamManager.resolveStream(videoId, quality, isVideo ? "video" : "audio");
    let streamEntry = resolution.entry;

    if (!streamEntry) {
      cleanup();
      console.warn(`[Stream 502] Initial resolution failed for ${isVideo ? "video" : "audio"} ${videoId}: ${resolution.error}`);
      res.status(502).json({
        error: `Could not resolve stream for ${isVideo ? "video" : "track"}: ${videoId}`,
        details: resolution.error
      });
      return;
    }

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

      console.warn(`[Upstream retry] Stream request failed for ${videoId} (${firstErr.message}). Purging cache and re-resolving fresh stream...`);
      await cacheService.delete(cacheKey);

      try {
        // Force fresh resolution bypassing cache
        const freshResolution = await StreamManager.resolveStream(videoId, quality, isVideo ? "video" : "audio");
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

      const contentType = audioResponse.headers["content-type"] || (isVideo ? "video/mp4" : "audio/webm");
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
    } catch (pipeErr: any) {
      cleanup();
    }
  }
}
