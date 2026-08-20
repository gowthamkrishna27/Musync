export type TrendState = "NEW" | "RISING" | "TRENDING" | "PEAK" | "COOLING" | "STABLE";

export interface NormalizedArtwork {
  small: string;
  medium: string;
  large: string;
  original: string;
}

export interface NormalizedArtist {
  id: string;
  name: string;
  imageUrl?: string;
}

export interface NormalizedAlbum {
  id: string;
  name: string;
  artworkUrl?: string;
}

export interface NormalizedTrack {
  id: string;
  videoId: string;
  title: string;
  artist: NormalizedArtist;
  album: NormalizedAlbum;
  artwork: NormalizedArtwork;
  releaseDate?: string;
  language: string;
  genre: string;
  duration: number; // in seconds
  provider: "youtube" | "lastfm" | "catalog";
  providerId: string;
  popularity: number; // 0.0 to 1.0
  trendScore: number; // 0.0 to 1.0
  trendState: TrendState;
  isNew: boolean;
  isTrending: boolean;
  rankChange?: number; // e.g. +5, -2, 0
  streamUrl?: string;
  mediaUrl?: string;
}

export interface DiscoverySection {
  id: string;
  title: string;
  subtitle?: string;
  badge?: string;
  language?: string;
  tracks: NormalizedTrack[];
}

export interface DiscoveryHomeResponse {
  timestamp: number;
  sections: DiscoverySection[];
  cached: boolean;
  totalTracks: number;
}
