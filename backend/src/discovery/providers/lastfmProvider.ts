import axios from "axios";
import { IMusicProvider } from "./musicProvider";
import { NormalizedTrack, NormalizedArtwork } from "../types";

export class LastFMProvider implements IMusicProvider {
  readonly providerName = "lastfm";
  private apiKey: string | null = process.env.LASTFM_API_KEY || null;
  private baseUrl = "https://ws.audioscrobbler.com/2.0/";

  private formatArtwork(images?: Array<{ "#text": string; size: string }>): NormalizedArtwork {
    let original = "";
    let large = "";
    let medium = "";
    let small = "";

    if (Array.isArray(images)) {
      for (const img of images) {
        const url = img["#text"];
        if (!url) continue;
        if (img.size === "mega" || img.size === "extralarge") original = url;
        if (img.size === "large") large = url;
        if (img.size === "medium") medium = url;
        if (img.size === "small") small = url;
      }
    }

    const fallback = original || large || medium || small || "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800";
    return {
      small: small || fallback,
      medium: medium || fallback,
      large: large || fallback,
      original: original || fallback
    };
  }

  async fetchTrending(region = "global", language = "All", limit = 20): Promise<NormalizedTrack[]> {
    if (!this.apiKey) return [];

    try {
      const method = region.toLowerCase() === "india" ? "geo.gettoptracks&country=india" : "chart.gettoptracks";
      const url = `${this.baseUrl}?method=${method}&api_key=${this.apiKey}&format=json&limit=${limit}`;

      const response = await axios.get(url, { timeout: 6000 });
      const tracksData = response.data?.tracks?.track;
      if (!Array.isArray(tracksData)) return [];

      return tracksData.map((item: any, idx: number) => {
        const title = item.name || "Unknown Title";
        const artistName = item.artist?.name || "Last.fm Artist";
        const listeners = parseInt(item.listeners || "1000", 10);
        const playcount = parseInt(item.playcount || "5000", 10);
        const popularity = Math.min(1.0, Math.max(0.1, listeners / 500000));
        const artwork = this.formatArtwork(item.image);

        return {
          id: `lastfm_${idx}_${title.length}`,
          videoId: "", // Provider metadata, matched by catalog/engine
          title,
          artist: {
            id: `lastfm_artist_${artistName.length}`,
            name: artistName,
            imageUrl: artwork.medium
          },
          album: {
            id: `lastfm_album_${title.length}`,
            name: title,
            artworkUrl: artwork.large
          },
          artwork,
          language: language !== "All" ? language : "Global",
          genre: "Pop",
          duration: parseInt(item.duration || "210", 10) || 210,
          provider: "lastfm",
          providerId: item.mbid || title,
          popularity: Number(popularity.toFixed(2)),
          trendScore: Number((0.7 + (limit - idx) * 0.01).toFixed(2)),
          trendState: "TRENDING",
          isNew: false,
          isTrending: true
        };
      });
    } catch (e: any) {
      console.warn("[LastFMProvider] fetchTrending error:", e.message);
      return [];
    }
  }

  async fetchNewReleases(language = "All", limit = 20): Promise<NormalizedTrack[]> {
    // Last.fm tag/charts for new releases
    if (!this.apiKey) return [];
    try {
      const tag = language !== "All" ? language.toLowerCase() : "new";
      const url = `${this.baseUrl}?method=tag.gettoptracks&tag=${tag}&api_key=${this.apiKey}&format=json&limit=${limit}`;
      const response = await axios.get(url, { timeout: 6000 });
      const tracksData = response.data?.tracks?.track;
      if (!Array.isArray(tracksData)) return [];

      return tracksData.map((item: any, idx: number) => {
        const title = item.name || "Unknown Title";
        const artistName = item.artist?.name || "Last.fm Artist";
        const artwork = this.formatArtwork(item.image);

        return {
          id: `lastfm_new_${idx}_${title.length}`,
          videoId: "",
          title,
          artist: {
            id: `lastfm_artist_${artistName.length}`,
            name: artistName,
            imageUrl: artwork.medium
          },
          album: {
            id: `lastfm_album_${title.length}`,
            name: title,
            artworkUrl: artwork.large
          },
          artwork,
          language: language !== "All" ? language : "Global",
          genre: tag,
          duration: 200,
          provider: "lastfm",
          providerId: item.mbid || title,
          popularity: 0.75,
          trendScore: 0.8,
          trendState: "NEW",
          isNew: true,
          isTrending: false
        };
      });
    } catch (e: any) {
      console.warn("[LastFMProvider] fetchNewReleases error:", e.message);
      return [];
    }
  }

  async fetchRising(limit = 20): Promise<NormalizedTrack[]> {
    return this.fetchTrending("global", "All", limit);
  }
}
