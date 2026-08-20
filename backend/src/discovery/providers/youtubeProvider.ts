import YTMusic from "ytmusic-api";
import axios from "axios";
import { IMusicProvider } from "./musicProvider";
import { NormalizedTrack, NormalizedArtwork } from "../types";

export class YouTubeProvider implements IMusicProvider {
  readonly providerName = "youtube";
  private ytmusic: YTMusic;
  private apiKey: string | null = process.env.YOUTUBE_API_KEY || null;

  constructor(ytmusic: YTMusic) {
    this.ytmusic = ytmusic;
  }

  private normalizeArtwork(videoId: string, rawThumbnailUrl?: string): NormalizedArtwork {
    let base = rawThumbnailUrl || `https://i.ytimg.com/vi/${videoId}/hq720.jpg`;

    if (base.includes("googleusercontent.com") || base.includes("ggpht.com")) {
      const highRes = base
        .replace(/=w\d+-h\d+[^=]*/, "=w800-h800-l90-rj")
        .replace(/=s\d+[^=]*/, "=s800-c-k-c0x00ffffff-no-rj");
      return {
        small: base.replace(/=w\d+-h\d+[^=]*/, "=w120-h120-l90-rj"),
        medium: base.replace(/=w\d+-h\d+[^=]*/, "=w360-h360-l90-rj"),
        large: highRes,
        original: highRes
      };
    }

    if (base.includes("i.ytimg.com")) {
      const cleanUrl = base
        .replace("/mqdefault.jpg", "/hq720.jpg")
        .replace("/default.jpg", "/hq720.jpg")
        .replace("/sddefault.jpg", "/hq720.jpg");
      return {
        small: `https://i.ytimg.com/vi/${videoId}/mqdefault.jpg`,
        medium: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
        large: cleanUrl,
        original: `https://i.ytimg.com/vi/${videoId}/maxresdefault.jpg`
      };
    }

    return {
      small: base,
      medium: base,
      large: base,
      original: base
    };
  }

  private mapRawToNormalized(raw: any, language: string, isNew = false, isTrending = false): NormalizedTrack | null {
    const videoId = raw.videoId || raw.id;
    if (!videoId || typeof videoId !== "string") return null;

    const title = (raw.name || raw.title || "Unknown Title").trim();
    const artistName =
      raw.artist?.name ||
      raw.author ||
      (Array.isArray(raw.artists) ? raw.artists.map((a: any) => a.name).join(", ") : "YouTube Artist");
    const albumName = raw.album?.name || raw.album || title;

    let thumbUrl = "";
    if (Array.isArray(raw.thumbnails) && raw.thumbnails.length > 0) {
      thumbUrl = raw.thumbnails[raw.thumbnails.length - 1].url;
    } else if (raw.thumbnailUrl) {
      thumbUrl = raw.thumbnailUrl;
    }

    const durationSec = raw.duration_seconds || raw.duration || 180;
    const artwork = this.normalizeArtwork(videoId, thumbUrl);

    return {
      id: `yt_${videoId}`,
      videoId,
      title,
      artist: {
        id: `yt_artist_${artistName.hashCode ? artistName.hashCode() : artistName.length}`,
        name: artistName,
        imageUrl: artwork.medium
      },
      album: {
        id: `yt_album_${albumName.length}_${videoId}`,
        name: albumName,
        artworkUrl: artwork.large
      },
      artwork,
      releaseDate: new Date().toISOString().split("T")[0],
      language,
      genre: "Music",
      duration: typeof durationSec === "number" ? durationSec : 180,
      provider: "youtube",
      providerId: videoId,
      popularity: 0.85,
      trendScore: isTrending ? 0.9 : 0.75,
      trendState: isTrending ? "TRENDING" : isNew ? "NEW" : "STABLE",
      isNew,
      isTrending
    };
  }

  async fetchTrending(region = "global", language = "All", limit = 20): Promise<NormalizedTrack[]> {
    const query = this.getTrendingQuery(region, language);
    try {
      // 1. If YouTube Data API key exists, use official videos.list chart=mostPopular
      if (this.apiKey) {
        const regionCode = region === "india" || language !== "All" ? "IN" : "US";
        const url = `https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails,statistics&chart=mostPopular&videoCategoryId=10&regionCode=${regionCode}&maxResults=${limit}&key=${this.apiKey}`;
        const response = await axios.get(url, { timeout: 8000 });
        if (response.data && Array.isArray(response.data.items)) {
          const tracks: NormalizedTrack[] = [];
          for (const item of response.data.items) {
            const vId = item.id;
            const snippet = item.snippet || {};
            const title = snippet.title || "Unknown Title";
            const channelTitle = snippet.channelTitle || "YouTube Artist";
            const artwork = this.normalizeArtwork(vId, snippet.thumbnails?.high?.url || snippet.thumbnails?.default?.url);

            tracks.push({
              id: `yt_${vId}`,
              videoId: vId,
              title,
              artist: { id: `yt_artist_${channelTitle.length}`, name: channelTitle, imageUrl: artwork.medium },
              album: { id: `yt_album_${vId}`, name: title, artworkUrl: artwork.large },
              artwork,
              releaseDate: snippet.publishedAt ? snippet.publishedAt.split("T")[0] : undefined,
              language: language !== "All" ? language : region === "india" ? "India" : "Global",
              genre: "Music",
              duration: 200,
              provider: "youtube",
              providerId: vId,
              popularity: 0.95,
              trendScore: 0.92,
              trendState: "TRENDING",
              isNew: false,
              isTrending: true
            });
          }
          if (tracks.length > 0) return tracks;
        }
      }

      // 2. YTMusic API fallback
      const results = await this.ytmusic.searchSongs(query);
      const normalized: NormalizedTrack[] = [];
      for (const item of results) {
        const track = this.mapRawToNormalized(item, language !== "All" ? language : "Global", false, true);
        if (track) normalized.push(track);
      }
      return normalized.slice(0, limit);
    } catch (e: any) {
      console.warn(`[YouTubeProvider] fetchTrending failed for ${region}/${language}:`, e.message);
      return [];
    }
  }

  async fetchNewReleases(language = "All", limit = 20): Promise<NormalizedTrack[]> {
    const query = this.getNewReleaseQuery(language);
    try {
      const results = await this.ytmusic.searchSongs(query);
      const normalized: NormalizedTrack[] = [];
      for (const item of results) {
        const track = this.mapRawToNormalized(item, language !== "All" ? language : "Global", true, false);
        if (track) normalized.push(track);
      }
      return normalized.slice(0, limit);
    } catch (e: any) {
      console.warn(`[YouTubeProvider] fetchNewReleases failed for ${language}:`, e.message);
      return [];
    }
  }

  async fetchRising(limit = 20): Promise<NormalizedTrack[]> {
    try {
      const results = await this.ytmusic.searchSongs("Breakout Viral Hits 2026");
      const normalized: NormalizedTrack[] = [];
      for (const item of results) {
        const track = this.mapRawToNormalized(item, "Global", false, true);
        if (track) {
          track.trendState = "RISING";
          track.trendScore = 0.88;
          normalized.push(track);
        }
      }
      return normalized.slice(0, limit);
    } catch (e: any) {
      console.warn(`[YouTubeProvider] fetchRising failed:`, e.message);
      return [];
    }
  }

  private getTrendingQuery(region: string, language: string): string {
    const currentYear = new Date().getFullYear();
    if (language && language !== "All") {
      return `Trending ${language} Songs ${currentYear}`;
    }
    if (region.toLowerCase() === "india") {
      return `Top Trending Songs India ${currentYear}`;
    }
    return `Top Global Hits Trending ${currentYear}`;
  }

  private getNewReleaseQuery(language: string): string {
    const currentYear = new Date().getFullYear();
    if (language && language !== "All") {
      return `Latest New ${language} Songs Releases ${currentYear}`;
    }
    return `Latest New Releases Music Hits ${currentYear}`;
  }
}
