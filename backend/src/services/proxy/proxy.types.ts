/**
 * proxy.types.ts
 *
 * Raw API response shapes for the public Piped and Invidious gateway instances.
 * These types are ONLY used internally inside MusicProxyService — the Android client
 * never sees these structures.
 */

// ---------------------------------------------------------------------------
// Piped
// ---------------------------------------------------------------------------

export interface PipedSearchItem {
  /** Relative watch URL, e.g. "/watch?v=dQw4w9WgXcQ" */
  url: string;
  title: string;
  /** Channel / uploader name */
  uploaderName: string;
  uploaderUrl?: string;
  /** Duration in seconds */
  duration: number;
  /** Thumbnail CDN URL */
  thumbnail: string;
  /** View count (may be absent on some instances) */
  views?: number;
  shortDescription?: string;
  uploaded?: number;
  uploaderVerified?: boolean;
  type?: string;
}

export interface PipedSearchResponse {
  items: PipedSearchItem[];
  nextpage?: string;
  suggestion?: string | null;
  corrected?: boolean;
}

export interface PipedAudioStream {
  url: string;
  format: string;
  quality: string;
  mimeType: string;
  codec: string;
  bitrate: number;
  initStart?: number;
  initEnd?: number;
  indexStart?: number;
  indexEnd?: number;
  contentLength?: number;
}

export interface PipedVideoStream {
  url: string;
  format: string;
  quality: string;
  mimeType: string;
  codec: string;
  bitrate?: number;
  height?: number;
  width?: number;
  fps?: number;
}

export interface PipedStreamDetails {
  title: string;
  description?: string;
  uploader: string;
  uploaderUrl?: string;
  uploaderAvatar?: string;
  uploaderVerified?: boolean;
  thumbnailUrl: string;
  category?: string;
  /** Duration in seconds */
  duration: number;
  views?: number;
  likes?: number;
  audioStreams: PipedAudioStream[];
  videoStreams?: PipedVideoStream[];
  relatedStreams?: PipedSearchItem[];
  chapters?: unknown[];
  hls?: string;
  dash?: string;
  proxyUrl?: string;
}

// ---------------------------------------------------------------------------
// Invidious
// ---------------------------------------------------------------------------

export interface InvidiousThumbnail {
  quality: string;
  url: string;
  width: number;
  height: number;
}

export interface InvidiousSearchItem {
  type: "video" | "playlist" | "channel";
  videoId?: string;
  title?: string;
  author?: string;
  authorId?: string;
  /** Duration in seconds */
  lengthSeconds?: number;
  videoThumbnails?: InvidiousThumbnail[];
  viewCount?: number;
  published?: number;
  description?: string;
  liveNow?: boolean;
}

export interface InvidiousAdaptiveFormat {
  index?: string;
  bitrate: string;
  init?: string;
  url: string;
  itag: string;
  type: string;
  clen?: string;
  lmt?: string;
  projectionType?: string;
  container?: string;
  encoding?: string;
  audioQuality?: string;
  audioSampleRate?: number;
  audioChannels?: number;
}

// ---------------------------------------------------------------------------
// Normalised internal track shape returned by MusicProxyService
// (identical to MusyncTrack from youtube.types.ts, but explicit here)
// ---------------------------------------------------------------------------

export interface ProxyTrack {
  id: string;
  videoId: string;
  title: string;
  artist: string;
  album: string;
  /** Duration in seconds */
  duration: number;
  thumbnailUrl: string;
  artworkUrl: string;
  /** Source gateway used to fetch this track */
  proxySource: "piped" | "invidious";
}
