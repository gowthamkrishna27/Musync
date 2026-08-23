/**
 * musicProxy.service.ts
 *
 * Backend-side music proxy service that fans out search and stream-resolution
 * requests to ytmusic-api, public Piped, and Invidious gateway instances with a
 * zero-dependency, high-performance In-Memory caching layer.
 *
 * This service is called INTERNALLY by the Express routes in server.ts.
 * The Android client NEVER calls Piped or Invidious directly — all third-party
 * scraping is encapsulated here on the server side.
 */

import axios, { AxiosInstance } from "axios";
import YTMusic from "ytmusic-api";
import { MusyncTrack } from "../youtube/youtube.types";
import {
  PipedSearchResponse,
  PipedSearchItem,
  PipedStreamDetails,
  InvidiousSearchItem,
  ProxyTrack
} from "./proxy.types";

// ---------------------------------------------------------------------------
// Constants & Configuration
// ---------------------------------------------------------------------------

/** Cache TTL: Exactly 1 hour in milliseconds (3600000 ms) */
const CACHE_TTL_MS = 3600000;

const PIPED_INSTANCES: string[] = [
  "https://pipedapi.kavin.rocks",
  "https://pipedapi.drgns.space",
  "https://piped-api.lunar.icu",
  "https://pipedapi.leptons.xyz",
  "https://api.piped.privacydev.net"
];

const INVIDIOUS_INSTANCES: string[] = [
  "https://invidious.nerdvpn.de",
  "https://inv.nadeko.net",
  "https://invidious.drgns.space",
  "https://inv.tux.pizza",
  "https://invidious.projectsegfau.lt",
  "https://invidious.privacydev.net"
];

// ---------------------------------------------------------------------------
// In-Memory Cache Types
// ---------------------------------------------------------------------------

interface CacheEntry<T> {
  data: T;
  expiresAt: number;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Extract an 11-character YouTube video ID from a Piped relative URL */
function extractVideoId(pipedUrl: string): string {
  if (pipedUrl.includes("v=")) {
    return pipedUrl.split("v=")[1].split("&")[0];
  }
  return pipedUrl.replace("/watch?v=", "").split("&")[0];
}

/** Build a high-res YouTube thumbnail URL from a video ID */
function hqThumb(videoId: string): string {
  return `https://i.ytimg.com/vi/${videoId}/hq720.jpg`;
}

/** Normalise a Piped search item to a ProxyTrack */
function normalisePiped(item: PipedSearchItem): ProxyTrack | null {
  const videoId = extractVideoId(item.url || "");
  if (!videoId || videoId.length < 5) return null;

  const thumbnail =
    item.thumbnail && item.thumbnail.startsWith("http")
      ? item.thumbnail
      : hqThumb(videoId);

  return {
    id: `yt_${videoId}`,
    videoId,
    title: item.title || "Unknown Title",
    artist: item.uploaderName || "YouTube Artist",
    album: item.title || "Single",
    duration: item.duration || 180,
    thumbnailUrl: thumbnail,
    artworkUrl: thumbnail,
    proxySource: "piped"
  };
}

/** Normalise an Invidious search item to a ProxyTrack */
function normaliseInvidious(item: InvidiousSearchItem): ProxyTrack | null {
  if (item.type !== "video" || !item.videoId) return null;
  const videoId = item.videoId;

  const thumbnail =
    item.videoThumbnails && item.videoThumbnails.length > 0
      ? item.videoThumbnails[item.videoThumbnails.length - 1].url
      : hqThumb(videoId);

  return {
    id: `yt_${videoId}`,
    videoId,
    title: item.title || "Unknown Title",
    artist: item.author || "YouTube Artist",
    album: item.title || "Single",
    duration: item.lengthSeconds || 180,
    thumbnailUrl: thumbnail.startsWith("http") ? thumbnail : hqThumb(videoId),
    artworkUrl: thumbnail.startsWith("http") ? thumbnail : hqThumb(videoId),
    proxySource: "invidious"
  };
}

// ---------------------------------------------------------------------------
// MusicProxyService
// ---------------------------------------------------------------------------

export class MusicProxyService {
  private static instance: MusicProxyService | null = null;

  /** Zero-dependency in-memory cache store */
  private readonly searchCache: Map<string, CacheEntry<MusyncTrack[]>> = new Map();

  /** Underlying ytmusic-api client for Tier 1 searches */
  private readonly ytmusic: YTMusic = new YTMusic();
  private ytmusicInitialized = false;

  /** Shared Axios client with sensible defaults for public gateway APIs */
  private readonly http: AxiosInstance = axios.create({
    timeout: 4000, // Reduced slightly for snap failovers
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
      Accept: "application/json"
    }
  });

  private constructor() {}

  public static getInstance(): MusicProxyService {
    if (!MusicProxyService.instance) {
      MusicProxyService.instance = new MusicProxyService();
    }
    return MusicProxyService.instance;
  }

  // -------------------------------------------------------------------------
  // In-Memory Caching Layer
  // -------------------------------------------------------------------------

  /**
   * Reusable private wrapper method for high-performance in-memory caching.
   *
   * 1. Normalizes the query string (trim + lowercase).
   * 2. Checks if valid, unexpired results exist in memory map.
   * 3. On cache miss or expiry, calls the fallback fetchFunction.
   * 4. Saves results only if >= 1 track is returned.
   */
  private async getCachedSearch(
    query: string,
    fetchFunction: () => Promise<MusyncTrack[]>
  ): Promise<MusyncTrack[]> {
    const normalizedKey = query.trim().toLowerCase();
    const now = Date.now();

    const cached = this.searchCache.get(normalizedKey);
    if (cached && cached.expiresAt > now) {
      console.log(`[MusicProxy:Cache HIT] "${normalizedKey}" (${cached.data.length} tracks, expires in ${Math.round((cached.expiresAt - now) / 1000)}s)`);
      return cached.data;
    }

    console.log(`[MusicProxy:Cache MISS] "${normalizedKey}" — executing search pipeline...`);
    const results = await fetchFunction();

    if (Array.isArray(results) && results.length > 0) {
      this.searchCache.set(normalizedKey, {
        data: results,
        expiresAt: now + CACHE_TTL_MS
      });
      console.log(`[MusicProxy:Cache STORED] "${normalizedKey}" -> ${results.length} tracks (TTL: 1h)`);
    }

    return results;
  }

  /**
   * Lazily initialize the internal ytmusic client if needed.
   */
  private async ensureYTMusic(): Promise<void> {
    if (!this.ytmusicInitialized) {
      try {
        await this.ytmusic.initialize();
        this.ytmusicInitialized = true;
      } catch (err: any) {
        console.warn(`[MusicProxy] ytmusic initialization error: ${err.message}`);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Public Endpoint Implementation Handlers
  // -------------------------------------------------------------------------

  /**
   * Primary 3-tier fallback search wrapped automatically with 1-hour in-memory cache:
   * - Tier 1: ytmusic-api
   * - Tier 2: Piped (if Tier 1 yields < 5 results)
   * - Tier 3: Invidious (if Tier 1 + Tier 2 yields < 5 results)
   */
  public async search(query: string): Promise<MusyncTrack[]> {
    return this.getCachedSearch(query, async () => {
      return this.executeThreeTierSearch(query);
    });
  }

  /**
   * Tier 2 Fallback Implementation: Scrape public Piped node mesh network
   */
  public async searchPiped(query: string): Promise<MusyncTrack[]> {
    const encoded = encodeURIComponent(query);
    for (const instance of PIPED_INSTANCES) {
      try {
        const response = await this.http.get<PipedSearchResponse>(
          `${instance}/search?q=${encoded}&filter=music_songs`
        );
        if (response.data && Array.isArray(response.data.items) && response.data.items.length > 0) {
          const tracks = response.data.items
            .map(normalisePiped)
            .filter((t): t is ProxyTrack => t !== null)
            .map(this.proxyTrackToMusyncTrack);

          if (tracks.length > 0) {
            console.log(`[MusicProxy] Piped search (${instance}) -> ${tracks.length} tracks for "${query}"`);
            return tracks;
          }
        }
      } catch (err: any) {
        console.warn(`[MusicProxy:Piped Fallback Fail] Node ${instance} dropped out: ${err.message}`);
      }
    }
    return [];
  }

  /**
   * Tier 3 Fallback Implementation: Scrape public Invidious instances
   */
  public async searchInvidious(query: string): Promise<MusyncTrack[]> {
    const encoded = encodeURIComponent(query);
    for (const instance of INVIDIOUS_INSTANCES) {
      try {
        const response = await this.http.get<InvidiousSearchItem[]>(
          `${instance}/api/v1/search?q=${encoded}&type=video`
        );
        if (Array.isArray(response.data) && response.data.length > 0) {
          const tracks = response.data
            .map(normaliseInvidious)
            .filter((t): t is ProxyTrack => t !== null)
            .map(this.proxyTrackToMusyncTrack);

          if (tracks.length > 0) {
            console.log(`[MusicProxy] Invidious search (${instance}) -> ${tracks.length} tracks for "${query}"`);
            return tracks;
          }
        }
      } catch (err: any) {
        console.warn(`[MusicProxy:Invidious Fallback Fail] Node ${instance} dropped out: ${err.message}`);
      }
    }
    return [];
  }

  // -------------------------------------------------------------------------
  // Internal Execution Engine
  // -------------------------------------------------------------------------

  /**
   * Internal execution of the 3-tier fallback logic when cache misses.
   */
  private async executeThreeTierSearch(query: string): Promise<MusyncTrack[]> {
    const allTracks: MusyncTrack[] = [];
    const seenIds = new Set<string>();

    const appendTracks = (tracks: any[]) => {
      for (const track of tracks) {
        const id = track.videoId || track.id;
        if (id && !seenIds.has(id)) {
          seenIds.add(id);
          allTracks.push(track);
        }
      }
    };

    // Tier 1 Check: Official API Wrapper scraper module
    try {
      await this.ensureYTMusic();
      const ytResults = await this.ytmusic.searchSongs(query);
      if (Array.isArray(ytResults)) {
        const mapped = ytResults.map((item) => this.normaliseYTMusicItem(item)).filter((t): t is MusyncTrack => t !== null);
        appendTracks(mapped);
      }
    } catch (err: any) {
      console.error(`[MusicProxy:Tier 1 Error] Local client layer crashed: ${err.message}`);
    }

    // Try general ytmusic search if results are sparse
    if (allTracks.length < 5) {
      try {
        const general = await this.ytmusic.search(query);
        if (Array.isArray(general)) {
          const songOrVideos = general.filter((item: any) => item.type === "song" || item.type === "video" || !item.type);
          const mapped = songOrVideos.map((item) => this.normaliseYTMusicItem(item)).filter((t): t is MusyncTrack => t !== null);
          appendTracks(mapped);
        }
      } catch (_e) {}
    }

    // Tier 2 Verification: Trigger Piped gateway arrays if metadata metrics are low
    if (allTracks.length < 5) {
      console.log(`[MusicProxy:Tier 2 Triggered] Cache density low (${allTracks.length} tracks). query: "${query}"`);
      const pipedResults = await this.searchPiped(query);
      appendTracks(pipedResults);
    }

    // Tier 3 Verification: Ultimate edge recovery endpoint deployment using Invidious web nodes
    if (allTracks.length < 5) {
      console.log(`[MusicProxy:Tier 3 Triggered] Cache density critical (${allTracks.length} tracks). query: "${query}"`);
      const invidiousResults = await this.searchInvidious(query);
      appendTracks(invidiousResults);
    }

    return allTracks;
  }

  // -------------------------------------------------------------------------
  // Normalization Helpers
  // -------------------------------------------------------------------------

  private normaliseYTMusicItem(item: any): MusyncTrack | null {
    const videoId = item.videoId || item.id;
    if (!videoId || typeof videoId !== "string" || videoId.length < 5) return null;

    const title = item.name || item.title || "Unknown Title";
    let artistName = "YouTube Artist";
    if (Array.isArray(item.artists) && item.artists.length > 0) {
      artistName = item.artists[0]?.name || item.artists[0] || "YouTube Artist";
    } else if (item.artist?.name) {
      artistName = item.artist.name;
    } else if (typeof item.artist === "string") {
      artistName = item.artist;
    }

    const albumName = item.album?.name || (typeof item.album === "string" ? item.album : title);
    const duration = typeof item.duration === "number" ? item.duration : (item.duration_seconds || 180);
    const thumbnail =
      (Array.isArray(item.thumbnails) && item.thumbnails.length > 0
        ? item.thumbnails[item.thumbnails.length - 1]?.url
        : null) || hqThumb(videoId);

    return {
      id: `yt_${videoId}`,
      videoId,
      title,
      name: title,
      artist: { name: artistName },
      artists: [{ name: artistName }],
      album: { name: albumName },
      duration,
      duration_seconds: duration,
      thumbnailUrl: thumbnail,
      artworkUrl: thumbnail,
      thumbnails: [{ url: thumbnail, width: 720, height: 404 }],
      source: "youtube"
    };
  }

  private proxyTrackToMusyncTrack(t: ProxyTrack): MusyncTrack {
    return {
      id: t.id,
      videoId: t.videoId,
      title: t.title,
      name: t.title,
      artist: { name: t.artist },
      artists: [{ name: t.artist }],
      album: { name: t.album },
      duration: t.duration,
      duration_seconds: t.duration,
      thumbnailUrl: t.thumbnailUrl,
      artworkUrl: t.artworkUrl,
      thumbnails: [{ url: t.thumbnailUrl, width: 720, height: 404 }],
      source: "youtube"
    };
  }

  // -------------------------------------------------------------------------
  // Stream Resolution
  // -------------------------------------------------------------------------

  /**
   * Attempt to resolve a direct audio stream URL from Piped stream details.
   * Returns the highest-bitrate audio stream URL, or null if unavailable.
   */
  public async resolvePipedStreamUrl(videoId: string): Promise<string | null> {
    for (const instance of PIPED_INSTANCES) {
      try {
        const url = `${instance}/streams/${videoId}`;
        const { data } = await this.http.get<PipedStreamDetails>(url);

        if (
          !data ||
          !Array.isArray(data.audioStreams) ||
          data.audioStreams.length === 0
        ) {
          continue;
        }

        const best = data.audioStreams.reduce((a, b) =>
          (b.bitrate || 0) > (a.bitrate || 0) ? b : a
        );

        if (best.url) {
          console.log(
            `[MusicProxy] Piped stream resolved (${instance}) for ${videoId} @ ${best.bitrate}bps`
          );
          return best.url;
        }
      } catch (err: any) {
        console.warn(
          `[MusicProxy] Piped stream resolution (${instance}) failed for ${videoId}: ${err.message}`
        );
      }
    }

    return null;
  }

  /**
   * Resolve a direct audio stream URL from an Invidious instance via itag=140 redirect.
   * The returned URL is a redirect/signed CDN URL that is short-lived.
   */
  public async resolveInvidiousStreamUrl(
    videoId: string
  ): Promise<string | null> {
    for (const instance of INVIDIOUS_INSTANCES) {
      try {
        const url = `${instance}/latest_version?id=${videoId}&itag=140`;
        const resp = await this.http.head(url, {
          maxRedirects: 0,
          validateStatus: (s) => s < 400
        });
        if (resp.status >= 200 && resp.status < 400) {
          console.log(
            `[MusicProxy] Invidious stream resolved (${instance}) for ${videoId}`
          );
          return url;
        }
      } catch (err: any) {
        if (err.response?.status && err.response.status >= 300 && err.response.status < 400) {
          const location = err.response.headers?.location;
          if (location) return location;
          return `${instance}/latest_version?id=${videoId}&itag=140`;
        }
        console.warn(
          `[MusicProxy] Invidious stream check (${instance}) failed for ${videoId}: ${err.message}`
        );
      }
    }
    return null;
  }

  // -------------------------------------------------------------------------
  // Artist Top Tracks
  // -------------------------------------------------------------------------

  /**
   * Fetch the latest videos/songs for a YouTube channel (artist) from Piped.
   * Falls back to an Invidious channel search if Piped fails.
   */
  public async getArtistTopTracks(channelId: string): Promise<ProxyTrack[]> {
    for (const instance of PIPED_INSTANCES) {
      try {
        const url = `${instance}/channel/${channelId}`;
        const { data } = await this.http.get<any>(url);
        const relatedStreams: any[] = data?.relatedStreams || [];
        if (relatedStreams.length === 0) continue;

        const tracks: ProxyTrack[] = [];
        for (const item of relatedStreams) {
          if (item.type !== "stream") continue;
          const t = normalisePiped({
            url: item.url,
            title: item.title,
            uploaderName: item.uploaderName || channelId,
            duration: item.duration,
            thumbnail: item.thumbnail
          });
          if (t) tracks.push(t);
        }

        if (tracks.length > 0) {
          console.log(
            `[MusicProxy] Artist ${channelId} -> ${tracks.length} tracks via Piped (${instance})`
          );
          return tracks;
        }
      } catch (err: any) {
        console.warn(
          `[MusicProxy] Piped channel ${channelId} (${instance}) failed: ${err.message}`
        );
      }
    }

    for (const instance of INVIDIOUS_INSTANCES) {
      try {
        const url = `${instance}/api/v1/channels/videos/${channelId}`;
        const { data } = await this.http.get<any>(url);
        const videos: any[] = data?.videos || [];
        if (videos.length === 0) continue;

        const tracks: ProxyTrack[] = [];
        for (const item of videos) {
          if (!item.videoId) continue;
          const t = normaliseInvidious({ ...item, type: "video" });
          if (t) tracks.push(t);
        }

        if (tracks.length > 0) {
          console.log(
            `[MusicProxy] Artist ${channelId} -> ${tracks.length} tracks via Invidious (${instance})`
          );
          return tracks;
        }
      } catch (err: any) {
        console.warn(
          `[MusicProxy] Invidious channel ${channelId} (${instance}) failed: ${err.message}`
        );
      }
    }

    return [];
  }

  // -------------------------------------------------------------------------
  // Playlist Tracks
  // -------------------------------------------------------------------------

  /**
   * Fetch tracks from a YouTube playlist via Piped, falling back to Invidious.
   */
  public async getPlaylistTracks(playlistId: string): Promise<ProxyTrack[]> {
    for (const instance of PIPED_INSTANCES) {
      try {
        const url = `${instance}/playlists/${playlistId}`;
        const { data } = await this.http.get<any>(url);
        const relatedStreams: any[] = data?.relatedStreams || [];
        if (relatedStreams.length === 0) continue;

        const tracks: ProxyTrack[] = [];
        for (const item of relatedStreams) {
          const t = normalisePiped({
            url: item.url,
            title: item.title,
            uploaderName: item.uploaderName || "YouTube Artist",
            duration: item.duration,
            thumbnail: item.thumbnail
          });
          if (t) tracks.push(t);
        }

        if (tracks.length > 0) {
          console.log(
            `[MusicProxy] Playlist ${playlistId} -> ${tracks.length} tracks via Piped (${instance})`
          );
          return tracks;
        }
      } catch (err: any) {
        console.warn(
          `[MusicProxy] Piped playlist ${playlistId} (${instance}) failed: ${err.message}`
        );
      }
    }

    for (const instance of INVIDIOUS_INSTANCES) {
      try {
        const url = `${instance}/api/v1/playlists/${playlistId}`;
        const { data } = await this.http.get<any>(url);
        const videos: any[] = data?.videos || [];
        if (videos.length === 0) continue;

        const tracks: ProxyTrack[] = [];
        for (const item of videos) {
          if (!item.videoId) continue;
          const t = normaliseInvidious({ ...item, type: "video" });
          if (t) tracks.push(t);
        }

        if (tracks.length > 0) {
          console.log(
            `[MusicProxy] Playlist ${playlistId} -> ${tracks.length} tracks via Invidious (${instance})`
          );
          return tracks;
        }
      } catch (err: any) {
        console.warn(
          `[MusicProxy] Invidious playlist ${playlistId} (${instance}) failed: ${err.message}`
        );
      }
    }

    return [];
  }
}

export const musicProxyService = MusicProxyService.getInstance();
