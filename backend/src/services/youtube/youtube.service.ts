import { Innertube, UniversalCache, Platform } from "youtubei.js";
import vm from "vm";
import fs from "fs";
import path from "path";
import os from "os";
import { MusyncTrack, MusyncSearchResults, AudioStreamResolution } from "./youtube.types";

// Configure safe Node.js VM JavaScript evaluator for YouTube deciphering
Platform.shim.eval = (data: any, env: any) => {
  const code = typeof data === "string" ? data : data?.output;
  if (!code) return env;
  const wrapped = `(function() {\n${code}\n})()`;
  return vm.runInNewContext(wrapped, { ...env });
};

function getYouTubeCookieString(): string | undefined {
  // 1. Direct cookie string in env
  const rawCookie = process.env.YOUTUBE_COOKIES || process.env.YOUTUBE_COOKIES_BASE64;
  if (rawCookie && rawCookie.trim().length > 10) {
    if (rawCookie.startsWith("ey") || (!rawCookie.includes("\n") && rawCookie.length > 50 && !rawCookie.includes(";"))) {
      try {
        return Buffer.from(rawCookie, "base64").toString("utf-8");
      } catch {
        return rawCookie;
      }
    }
    return rawCookie;
  }

  // 2. Cookie file paths
  const candidateFiles = [
    process.env.YOUTUBE_COOKIES_FILE,
    path.join(os.tmpdir(), "musync_yt_cookies.txt"),
    path.join(process.cwd(), "musync_yt_cookies.txt"),
    path.join(process.cwd(), "cookies.txt"),
    path.join(process.cwd(), "scripts", "cookies.txt")
  ];

  for (const filePath of candidateFiles) {
    if (filePath && fs.existsSync(filePath)) {
      try {
        const content = fs.readFileSync(filePath, "utf-8").trim();
        if (content.length > 10) {
          return content;
        }
      } catch {
        // Ignore read error
      }
    }
  }

  return undefined;
}

export class YouTubeService {
  private static instance: YouTubeService | null = null;
  private innertube: Innertube | null = null;
  private initPromise: Promise<Innertube> | null = null;

  private constructor() {}

  public static getInstance(): YouTubeService {
    if (!YouTubeService.instance) {
      YouTubeService.instance = new YouTubeService();
    }
    return YouTubeService.instance;
  }

  /**
   * Lazy singleton initializer for Innertube instance with secure cookie & proxy support
   */
  public async getClient(): Promise<Innertube> {
    if (this.innertube) {
      return this.innertube;
    }

    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = (async () => {
      const cookieStr = getYouTubeCookieString();
      const hasCookies = Boolean(cookieStr);
      console.log(`[YouTubeService] Initializing Innertube client (Auth cookies present: ${hasCookies})...`);

      try {
        const client = await Innertube.create({
          lang: "en",
          location: "IN",
          retrieve_player: true,
          cookie: cookieStr,
          cache: new UniversalCache(true)
        });
        this.innertube = client;
        console.log("✓ [YouTubeService] Innertube initialized successfully.");
        return client;
      } catch (err: any) {
        this.initPromise = null;
        console.error("❌ [YouTubeService] Initialization error:", err.message);
        throw err;
      }
    })();

    return this.initPromise;
  }

  /**
   * Search for songs, videos, and music with normalized Musync data structures
   */
  public async search(query: string, limit: number = 30): Promise<MusyncSearchResults> {
    const yt = await this.getClient();
    const cleanQuery = query.trim();
    if (!cleanQuery) return { tracks: [] };

    const tracks: MusyncTrack[] = [];

    try {
      // 1. Try search with video filter (returns rich music videos & official songs)
      const searchRes = await yt.search(cleanQuery, { type: "video" });
      const videos = searchRes.videos || [];

      for (const item of videos.slice(0, limit)) {
        const v = item as any;
        if (!v || (!v.id && !v.video_id)) continue;
        const videoId = v.id || v.video_id;
        const title = v.title?.text || v.title || "Unknown Title";
        const authorName = v.author?.name || v.author || "Unknown Artist";
        const durationSec = v.duration?.seconds || 0;
        const thumbnails = (v.thumbnails || []).map((t: any) => ({
          url: t.url,
          width: t.width,
          height: t.height
        }));
        const highResThumbnail = thumbnails.length > 0
          ? (thumbnails[thumbnails.length - 1]?.url || `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`)
          : `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;

        tracks.push({
          id: videoId,
          videoId: videoId,
          title: title,
          name: title,
          artists: [{ name: authorName, id: v.author?.id }],
          artist: authorName,
          album: "YouTube Music",
          duration: durationSec,
          duration_seconds: durationSec,
          durationText: v.duration?.text,
          thumbnails: thumbnails,
          thumbnailUrl: highResThumbnail,
          artworkUrl: highResThumbnail,
          source: "youtube"
        });
      }
    } catch (searchErr: any) {
      console.warn("[YouTubeService] Search warning:", searchErr.message);
    }

    return { tracks };
  }

  /**
   * Retrieve rich track info normalized for Musync
   */
  public async getSongInfo(videoId: string): Promise<MusyncTrack | null> {
    const yt = await this.getClient();
    try {
      const info = await yt.getInfo(videoId);
      const basic = info.basic_info;
      if (!basic) return null;

      const title = basic.title || "Unknown Title";
      const author = basic.author || "Unknown Artist";
      const duration = basic.duration || 0;
      const thumbnails = (basic.thumbnail || []).map((t: any) => ({
        url: t.url,
        width: t.width,
        height: t.height
      }));
      const artwork = thumbnails.length > 0
        ? (thumbnails[thumbnails.length - 1]?.url || `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`)
        : `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;

      return {
        id: videoId,
        videoId: videoId,
        title: title,
        name: title,
        artists: [{ name: author, id: basic.channel_id }],
        artist: author,
        album: "YouTube Music",
        duration: duration,
        duration_seconds: duration,
        thumbnails: thumbnails,
        thumbnailUrl: artwork,
        artworkUrl: artwork,
        source: "youtube"
      };
    } catch (err: any) {
      console.warn(`[YouTubeService] getSongInfo error for ${videoId}:`, err.message);
      return null;
    }
  }

  /**
   * Resolve an optimal playable audio format (Opus/AAC) with deciphered URL
   */
  public async resolveAudioStream(videoId: string, quality: string = "low"): Promise<AudioStreamResolution | null> {
    const yt = await this.getClient();
    try {
      let formats: any[] = [];

      // 1. Try YouTube Music info first
      try {
        const musicInfo = await yt.music.getInfo(videoId);
        formats = musicInfo.streaming_data?.adaptive_formats || [];
      } catch (_musicErr: any) {
        formats = [];
      }

      // 2. Fallback to general YouTube info
      if (formats.length === 0) {
        try {
          const generalInfo = await yt.getInfo(videoId);
          formats = generalInfo.streaming_data?.adaptive_formats || [];
        } catch (_genErr: any) {
          formats = [];
        }
      }

      const audioFormats = formats.filter(f => f.has_audio && !f.has_video);
      if (audioFormats.length === 0) {
        const anyAudio = formats.filter(f => f.has_audio);
        if (anyAudio.length === 0) return null;
        audioFormats.push(...anyAudio);
      }

      // Quality-based format selection
      // - low / saver: prefer itag 139 (48k AAC), 249/250 (50-70k Opus)
      // - standard / high: prefer itag 140 (128k AAC), 251 (160k Opus)
      let chosen = audioFormats[0];
      const safeQuality = quality.toLowerCase();

      if (safeQuality === "low" || safeQuality === "saver") {
        chosen = audioFormats.find(f => f.itag === 139)
          || audioFormats.find(f => f.itag === 249 || f.itag === 250)
          || audioFormats.find(f => f.itag === 140)
          || audioFormats[0];
      } else if (safeQuality === "high" || safeQuality === "lossless") {
        chosen = audioFormats.find(f => f.itag === 251)
          || audioFormats.find(f => f.itag === 140)
          || audioFormats[0];
      } else {
        // standard (default)
        chosen = audioFormats.find(f => f.itag === 140)
          || audioFormats.find(f => f.itag === 251)
          || audioFormats[0];
      }

      if (!chosen) return null;

      // Safe deciphering: only attempt decipher when cipher exists or direct URL is missing
      let resolvedUrl: string | null = chosen.url || null;
      if (!resolvedUrl && (chosen.signature_cipher || chosen.cipher)) {
        try {
          resolvedUrl = await chosen.decipher(yt.session.player);
        } catch (decipherErr: any) {
          console.warn(`[YouTubeService] Decipher failed for ${videoId} (${chosen.itag}):`, decipherErr.message);
          return null;
        }
      }

      if (!resolvedUrl) {
        return null;
      }

      const isWebm = (chosen.mime_type && chosen.mime_type.includes("webm")) || chosen.itag === 251 || chosen.itag === 250 || chosen.itag === 249;
      const ext = isWebm ? "webm" : "m4a";
      const mime = isWebm ? "audio/webm" : (ext === "m4a" ? "audio/mp4" : "audio/mp4");

      return {
        url: resolvedUrl,
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
          "Accept": "*/*",
          "Accept-Encoding": "identity",
          "Connection": "keep-alive"
        },
        format: mime,
        ext: ext,
        itag: chosen.itag || 140,
        bitrate: chosen.bitrate || 128000,
        contentLength: chosen.content_length,
        approxDurationMs: chosen.approx_duration_ms,
        expiresAt: Date.now() + 2 * 3600 * 1000 // 2 hours validity
      };
    } catch (err: any) {
      console.warn(`[YouTubeService] resolveAudioStream failed for ${videoId}:`, err.message);
      return null;
    }
  }
}

export const youtubeService = YouTubeService.getInstance();
