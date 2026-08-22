export interface MusyncThumbnail {
  url: string;
  width?: number;
  height?: number;
}

export interface MusyncArtist {
  name: string;
  id?: string;
  artistId?: string;
}

export interface MusyncAlbum {
  name: string;
  id?: string;
  albumId?: string;
}

export interface MusyncTrack {
  id: string;
  videoId: string;
  title: string;
  name?: string;
  artists?: MusyncArtist[];
  artist?: MusyncArtist | string;
  album?: MusyncAlbum | string;
  duration?: number;
  duration_seconds?: number;
  durationText?: string;
  thumbnails?: MusyncThumbnail[];
  thumbnailUrl?: string;
  artworkUrl?: string;
  isExplicit?: boolean;
  source: "youtube";
}

export interface MusyncSearchResults {
  tracks: MusyncTrack[];
  artists?: any[];
  albums?: any[];
  playlists?: any[];
}

export interface AudioStreamResolution {
  url: string;
  headers: Record<string, string>;
  format: string;
  ext: "m4a" | "webm" | "mp4";
  itag: number;
  bitrate: number;
  contentLength?: number;
  approxDurationMs?: number;
  expiresAt: number;
}
