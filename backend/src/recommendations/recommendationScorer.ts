export interface TrackMetadata {
  videoId: string;
  title: string;
  artistName: string;
  albumName?: string;
  genre?: string;
  duration?: number;
  playCount?: number;
  thumbnails?: Array<{ url: string; width?: number; height?: number }>;
  raw?: any;
}

export type CandidateSource = "same_artist" | "same_genre" | "same_album" | "related_artist" | "search_sim";

export interface CandidateTrack {
  track: TrackMetadata;
  source: CandidateSource;
  popularityScore?: number;
}

export interface ScoredCandidate {
  track: TrackMetadata;
  score: number;
  signals: {
    artistMatch: number;
    genreMatch: number;
    relatedArtistMatch: number;
    albumMatch: number;
    metadataSimilarity: number;
    popularity: number;
  };
  source: CandidateSource;
}

export class RecommendationScorer {
  // Configurable transparent scoring weights (sum ~ 1.0)
  public static readonly WEIGHTS = {
    ARTIST: 0.40,
    GENRE: 0.25,
    RELATED_ARTIST: 0.20,
    ALBUM: 0.15,
    METADATA: 0.10,
    POPULARITY: 0.05
  };

  /**
   * Score an individual candidate relative to the current playing track.
   * Returns -Infinity if candidate is the exact current track or invalid.
   */
  public static scoreCandidate(
    current: TrackMetadata,
    candidate: CandidateTrack
  ): ScoredCandidate | null {
    const cand = candidate.track;
    if (!cand.videoId || cand.videoId === current.videoId) {
      return null; // Strict exclusion of the current playing track
    }

    const curTitle = (current.title || "").toLowerCase().trim();
    const curArtist = (current.artistName || "").toLowerCase().trim();
    const curAlbum = (current.albumName || "").toLowerCase().trim();
    const curGenre = (current.genre || "").toLowerCase().trim();

    const candTitle = (cand.title || "").toLowerCase().trim();
    const candArtist = (cand.artistName || "").toLowerCase().trim();
    const candAlbum = (cand.albumName || "").toLowerCase().trim();
    const candGenre = (cand.genre || "").toLowerCase().trim();

    // 1. Same Artist Signal [0, 1]
    let artistMatch = 0;
    if (candArtist && curArtist) {
      if (candArtist === curArtist) {
        artistMatch = 1.0;
      } else if (candArtist.includes(curArtist) || curArtist.includes(candArtist)) {
        artistMatch = 0.8;
      }
    }

    // 2. Related Artist Signal [0, 1]
    const relatedArtistMatch = candidate.source === "related_artist" ? 1.0 : 0.0;

    // 3. Same Genre Signal [0, 1]
    let genreMatch = 0;
    if (candidate.source === "same_genre") {
      genreMatch = 1.0;
    } else if (curGenre && candGenre && curGenre === candGenre) {
      genreMatch = 1.0;
    }

    // 4. Same Album Signal [0, 1]
    let albumMatch = 0;
    if (candidate.source === "same_album" || (curAlbum && candAlbum && curAlbum === candAlbum && curAlbum !== "single" && curAlbum !== curTitle)) {
      albumMatch = 1.0;
    }

    // 5. Metadata Similarity (Token overlap between titles and artists) [0, 1]
    const curTokens = new Set(
      `${curTitle} ${curArtist}`.split(/[\s,.\-_/]+/).filter((t) => t.length > 2)
    );
    const candTokens = `${candTitle} ${candArtist}`.split(/[\s,.\-_/]+/).filter((t) => t.length > 2);
    let tokenOverlap = 0;
    if (curTokens.size > 0 && candTokens.length > 0) {
      const matchCount = candTokens.filter((t) => curTokens.has(t)).length;
      tokenOverlap = Math.min(1.0, matchCount / Math.max(1, curTokens.size));
    }
    const metadataSimilarity = tokenOverlap;

    // 6. Popularity Signal [0, 1]
    const popularity = candidate.popularityScore ? Math.min(1.0, Math.max(0.0, candidate.popularityScore)) : 0.5;

    // Calculate final weighted normalized score
    const score =
      this.WEIGHTS.ARTIST * artistMatch +
      this.WEIGHTS.GENRE * genreMatch +
      this.WEIGHTS.RELATED_ARTIST * relatedArtistMatch +
      this.WEIGHTS.ALBUM * albumMatch +
      this.WEIGHTS.METADATA * metadataSimilarity +
      this.WEIGHTS.POPULARITY * popularity;

    return {
      track: cand,
      score: Number(score.toFixed(4)),
      signals: {
        artistMatch,
        genreMatch,
        relatedArtistMatch,
        albumMatch,
        metadataSimilarity,
        popularity
      },
      source: candidate.source
    };
  }

  /**
   * Deduplicates, scores, balances diversity, and ranks candidates to return top N recommendations.
   */
  public static rankAndFilter(
    current: TrackMetadata,
    candidates: CandidateTrack[],
    limit: number = 5
  ): ScoredCandidate[] {
    const scoredList: ScoredCandidate[] = [];
    const seenVideoIds = new Set<string>();
    const seenSignatures = new Set<string>();

    // Add current track to seen set to guarantee exclusion
    seenVideoIds.add(current.videoId);
    const currentSig = `${(current.title || "").toLowerCase().trim()}:${(current.artistName || "").toLowerCase().trim()}`;
    seenSignatures.add(currentSig);

    for (const candidate of candidates) {
      const vid = candidate.track.videoId;
      if (!vid || seenVideoIds.has(vid)) continue;

      const normTitle = (candidate.track.title || "").toLowerCase().trim();
      const normArtist = (candidate.track.artistName || "").toLowerCase().trim();
      const sig = `${normTitle}:${normArtist}`;
      if (seenSignatures.has(sig)) continue;

      const scored = this.scoreCandidate(current, candidate);
      if (scored && scored.score > 0) {
        seenVideoIds.add(vid);
        seenSignatures.add(sig);
        scoredList.push(scored);
      }
    }

    // Sort descending by score
    scoredList.sort((a, b) => b.score - a.score);

    // Apply Lightweight Diversity Balancing:
    // If we have enough candidates from different artists, prevent 100% same artist dominating
    const maxSameArtist = Math.max(2, Math.floor(limit * 0.6));
    const finalSelection: ScoredCandidate[] = [];
    const artistCounts: Record<string, number> = {};
    const deferredList: ScoredCandidate[] = [];

    const normCurArtist = (current.artistName || "").toLowerCase().trim();

    for (const item of scoredList) {
      const artKey = (item.track.artistName || "").toLowerCase().trim();
      const count = artistCounts[artKey] || 0;

      // If item is same artist and exceeds max allowed when other candidates exist
      if (artKey === normCurArtist && count >= maxSameArtist) {
        deferredList.push(item);
        continue;
      }

      artistCounts[artKey] = count + 1;
      finalSelection.push(item);
      if (finalSelection.length >= limit) break;
    }

    // If diversity filtering left us with fewer than limit, fill from deferred list
    if (finalSelection.length < limit && deferredList.length > 0) {
      for (const deferred of deferredList) {
        finalSelection.push(deferred);
        if (finalSelection.length >= limit) break;
      }
    }

    return finalSelection.slice(0, limit);
  }
}
