import YTMusic from "ytmusic-api";
import { cacheService } from "../cache/cacheService";
import { metricsService } from "../metrics/metricsService";
import {
  RecommendationScorer,
  TrackMetadata,
  CandidateTrack,
  ScoredCandidate
} from "./recommendationScorer";

export interface FormattedRecommendation {
  videoId: string;
  id: string;
  songid: string;
  title: string;
  song: string;
  singers: string;
  artist: string;
  album: string;
  image_url: string;
  image: string;
  duration: string;
  duration_seconds: number;
  url: string;
  media_url: string;
  stream_url: string;
  score?: number;
}

export interface RecommendationResponse {
  trackId: string;
  sourceTrack: {
    title: string;
    artist: string;
  };
  recommendations: FormattedRecommendation[];
  cached: boolean;
  latencyMs: number;
}

export class RecommendationEngine {
  private ytmusic: YTMusic;
  private defaultTtlSeconds: number = 7200; // 2 hours

  constructor(ytmusic: YTMusic) {
    this.ytmusic = ytmusic;
  }

  /**
   * Helper to format a raw or metadata item into standard Musync track schema
   */
  private formatTrack(item: any, reqHost: string, score?: number): FormattedRecommendation {
    const videoId = item.videoId || item.id || "";
    const title = item.name || item.title || "Unknown Title";
    const artistName =
      item.artistName ||
      item.artist?.name ||
      (Array.isArray(item.artists) ? item.artists.map((a: any) => a.name).join(", ") : "YouTube Artist");
    const albumName =
      item.albumName ||
      item.album?.name ||
      (typeof item.album === "string" ? item.album : title);

    let thumbnail = `https://i.ytimg.com/vi/${videoId}/hq720.jpg`;
    if (Array.isArray(item.thumbnails) && item.thumbnails.length > 0) {
      thumbnail = item.thumbnails[item.thumbnails.length - 1].url;
    }

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

    const durationSec = item.duration_seconds || item.duration || 180;
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
      stream_url: streamUrl,
      score
    };
  }

  /**
   * Fetch current track metadata from cache or ytmusic
   */
  private async getTrackMetadata(videoId: string): Promise<TrackMetadata> {
    const metaCacheKey = `song_meta:${videoId}`;
    const cached = await cacheService.get<any>(metaCacheKey);
    if (cached && cached.title) {
      return {
        videoId,
        title: cached.title,
        artistName: cached.artist || "YouTube Artist",
        albumName: cached.album || cached.title,
        duration: cached.duration || 180
      };
    }

    try {
      const songData: any = await this.ytmusic.getSong(videoId);
      if (songData) {
        const title = songData.name || songData.title || "Unknown Title";
        const artistName =
          songData.artist?.name ||
          (Array.isArray(songData.artists) ? songData.artists.map((a: any) => a.name).join(", ") : "YouTube Artist");
        const albumName = songData.album?.name || title;
        const duration = songData.duration || 180;

        return {
          videoId,
          title,
          artistName,
          albumName,
          duration,
          thumbnails: songData.thumbnails
        };
      }
    } catch (_e) {
      // Fallback
    }

    return {
      videoId,
      title: "Unknown Track",
      artistName: "YouTube Artist",
      albumName: "Unknown Album"
    };
  }

  /**
   * Main recommendation generation flow with caching, single-flight stampede protection,
   * candidate gathering, scoring, diversity balancing, and metrics.
   */
  public async getRecommendations(
    videoId: string,
    limit: number = 5,
    reqHost: string
  ): Promise<RecommendationResponse> {
    const cleanId = videoId.trim();
    const clampedLimit = Math.min(10, Math.max(1, limit));
    const cacheKey = `rec:v1:${cleanId}:${clampedLimit}`;
    const startMs = Date.now();

    metricsService.recordRecommendationRequest();

    // 1. Check Multi-Tier Cache (L1 Memory & L2 Redis)
    const cached = await cacheService.get<RecommendationResponse>(cacheKey);
    if (cached && Array.isArray(cached.recommendations) && cached.recommendations.length > 0) {
      metricsService.recordRecommendationCacheHit();
      const latencyMs = Date.now() - startMs;
      metricsService.recordRecommendationLatency(latencyMs);
      return {
        ...cached,
        cached: true,
        latencyMs
      };
    }

    metricsService.recordRecommendationCacheMiss();

    // 2. Single-Flight Coalescing: Only 1 calculation executes if concurrent requests arrive for the same track
    return cacheService.coalesce<RecommendationResponse>(cacheKey, async () => {
      try {
        // Double-check cache after winning coalescing lock
        const freshCache = await cacheService.get<RecommendationResponse>(cacheKey);
        if (freshCache && Array.isArray(freshCache.recommendations) && freshCache.recommendations.length > 0) {
          return { ...freshCache, cached: true, latencyMs: Date.now() - startMs };
        }

        // A. Obtain current track metadata
        const currentMeta = await this.getTrackMetadata(cleanId);
        const candidates: CandidateTrack[] = [];

        // B. Multi-Source Candidate Generation in Parallel
        const artistQuery = currentMeta.artistName.replace(/ - Topic|VEVO/gi, "").trim();
        const titleQuery = currentMeta.title.replace(/\(.*\)|\[.*\]/g, "").trim();

        const [artistSongsRes, relatedSearchRes, titleSearchRes] = await Promise.allSettled([
          // Source A: Same Artist
          artistQuery && artistQuery !== "YouTube Artist"
            ? this.ytmusic.searchSongs(artistQuery)
            : Promise.resolve([]),

          // Source B & D: Related Artist / Genre / Similar tracks
          artistQuery && artistQuery !== "YouTube Artist"
            ? this.ytmusic.searchSongs(`${artistQuery} mix`)
            : Promise.resolve([]),

          // Source E: Title & Metadata Similarity
          titleQuery && titleQuery !== "Unknown Track"
            ? this.ytmusic.searchSongs(`${titleQuery} ${artistQuery}`)
            : Promise.resolve([])
        ]);

        // Ingest Source A: Same Artist
        if (artistSongsRes.status === "fulfilled" && Array.isArray(artistSongsRes.value)) {
          for (const raw of artistSongsRes.value) {
            const item = raw as any;
            const vId = item.videoId || item.id;
            if (!vId) continue;
            candidates.push({
              track: {
                videoId: vId,
                title: item.name || item.title || "",
                artistName: item.artist?.name || (Array.isArray(item.artists) ? item.artists[0]?.name : artistQuery),
                albumName: item.album?.name,
                duration: item.duration || undefined,
                thumbnails: item.thumbnails,
                raw: item
              },
              source: "same_artist",
              popularityScore: 0.8
            });
          }
        }

        // Ingest Source B: Related / Genre Mix
        if (relatedSearchRes.status === "fulfilled" && Array.isArray(relatedSearchRes.value)) {
          for (const raw of relatedSearchRes.value) {
            const item = raw as any;
            const vId = item.videoId || item.id;
            if (!vId) continue;
            candidates.push({
              track: {
                videoId: vId,
                title: item.name || item.title || "",
                artistName: item.artist?.name || (Array.isArray(item.artists) ? item.artists[0]?.name : "Artist"),
                albumName: item.album?.name,
                duration: item.duration || undefined,
                thumbnails: item.thumbnails,
                raw: item
              },
              source: "related_artist",
              popularityScore: 0.7
            });
          }
        }

        // Ingest Source E: Title / Similarity
        if (titleSearchRes.status === "fulfilled" && Array.isArray(titleSearchRes.value)) {
          for (const raw of titleSearchRes.value) {
            const item = raw as any;
            const vId = item.videoId || item.id;
            if (!vId) continue;
            candidates.push({
              track: {
                videoId: vId,
                title: item.name || item.title || "",
                artistName: item.artist?.name || (Array.isArray(item.artists) ? item.artists[0]?.name : "Artist"),
                albumName: item.album?.name,
                duration: item.duration || undefined,
                thumbnails: item.thumbnails,
                raw: item
              },
              source: "search_sim",
              popularityScore: 0.6
            });
          }
        }

        // C. Centralized Scoring, Filtering, Deduplication & Diversity Balancing
        const scoredCandidates: ScoredCandidate[] = RecommendationScorer.rankAndFilter(
          currentMeta,
          candidates,
          clampedLimit
        );

        // D. Format results
        const formattedList: FormattedRecommendation[] = scoredCandidates.map((sc) =>
          this.formatTrack(sc.track, reqHost, sc.score)
        );

        const latencyMs = Date.now() - startMs;
        const response: RecommendationResponse = {
          trackId: cleanId,
          sourceTrack: {
            title: currentMeta.title,
            artist: currentMeta.artistName
          },
          recommendations: formattedList,
          cached: false,
          latencyMs
        };

        // E. Cache result in L1 and L2 Redis (TTL: 2 hours)
        if (formattedList.length > 0) {
          await cacheService.set(cacheKey, response, this.defaultTtlSeconds);
        }

        metricsService.recordRecommendationSuccess();
        metricsService.recordRecommendationLatency(latencyMs);

        return response;
      } catch (err: any) {
        metricsService.recordRecommendationFailure();
        console.error(`[RecommendationEngine] Error generating recommendations for ${cleanId}:`, err.message);

        // Graceful error fallback: empty recommendations without crashing
        return {
          trackId: cleanId,
          sourceTrack: {
            title: "Unknown",
            artist: "Unknown"
          },
          recommendations: [],
          cached: false,
          latencyMs: Date.now() - startMs
        };
      }
    });
  }
}
