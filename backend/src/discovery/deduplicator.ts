import { NormalizedTrack } from "./types";

export class TrackDeduplicator {
  /**
   * Cleans a track title by removing noise patterns while keeping remix / acoustic distinctions.
   */
  public static cleanTitle(title: string): string {
    return title
      .toLowerCase()
      .replace(/\(official\s*(music\s*)?(audio|video|lyric\s*video|hd|4k)?\)/gi, "")
      .replace(/\[official\s*(music\s*)?(audio|video|lyric\s*video|hd|4k)?\]/gi, "")
      .replace(/\|\s*official\s*(music\s*)?(audio|video|lyric\s*video)/gi, "")
      .replace(/\(full\s*(song|video)\)/gi, "")
      .replace(/\[full\s*(song|video)\]/gi, "")
      .replace(/\(audio\)/gi, "")
      .replace(/\[audio\]/gi, "")
      .replace(/\(video\)/gi, "")
      .replace(/\[video\]/gi, "")
      .replace(/\(lyrics?\)/gi, "")
      .replace(/\[lyrics?\]/gi, "")
      .replace(/\(visualizer\)/gi, "")
      .replace(/\[visualizer\]/gi, "")
      .replace(/\s+/g, " ")
      .trim();
  }

  /**
   * Generates a canonical fingerprint for matching duplicate songs.
   */
  public static generateFingerprint(track: NormalizedTrack): string {
    const cleanT = this.cleanTitle(track.title);
    const cleanA = (track.artist.name || "")
      .toLowerCase()
      .replace(/\s*-\s*topic/gi, "")
      .replace(/vevo/gi, "")
      .replace(/ft\.?|feat\.?/gi, "")
      .trim();

    return `${cleanA}:::${cleanT}`;
  }

  /**
   * Deduplicates a list of normalized tracks, preferring tracks with valid playable videoIds,
   * higher popularity, and richer metadata.
   */
  public static deduplicate(tracks: NormalizedTrack[]): NormalizedTrack[] {
    const seen = new Map<string, NormalizedTrack>();
    const seenVideoIds = new Set<string>();

    for (const track of tracks) {
      if (track.videoId && seenVideoIds.has(track.videoId)) {
        continue;
      }

      const fingerprint = this.generateFingerprint(track);
      const existing = seen.get(fingerprint);

      if (!existing) {
        seen.set(fingerprint, track);
        if (track.videoId) seenVideoIds.add(track.videoId);
      } else {
        // If candidate has a valid videoId and existing doesn't, or candidate has higher popularity
        if ((!existing.videoId && track.videoId) || (track.popularity > existing.popularity && track.videoId)) {
          if (existing.videoId) seenVideoIds.delete(existing.videoId);
          seen.set(fingerprint, track);
          if (track.videoId) seenVideoIds.add(track.videoId);
        }
      }
    }

    return Array.from(seen.values());
  }
}
