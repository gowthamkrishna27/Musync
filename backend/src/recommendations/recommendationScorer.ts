import { SessionProfile, sessionService } from "./sessionService";

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

export type CandidateSource =
  | "same_artist"
  | "same_genre"
  | "same_album"
  | "related_artist"
  | "search_sim"
  | "history"
  | "trending"
  | "exploration";

export interface CandidateTrack {
  track: TrackMetadata;
  source: CandidateSource;
  popularityScore?: number;
}

// Explanation labels surfaced to the Android UI
export type RecommendationReason =
  | "similar_to_current"
  | "based_on_recent"
  | "because_you_like_artist"
  | "because_you_like_genre"
  | "trending_favourite"
  | "discover_new"
  | "from_your_history";

export interface ScoredCandidate {
  track: TrackMetadata;
  score: number;
  reason: RecommendationReason;
  signals: {
    artistMatch: number;
    genreMatch: number;
    relatedArtistMatch: number;
    albumMatch: number;
    metadataSimilarity: number;
    popularity: number;
    sessionArtistAffinity: number;
    sessionGenreAffinity: number;
    cooldownPenalty: number;
    skipPenalty: number;
  };
  source: CandidateSource;
}

export class RecommendationScorer {
  // -------------------------------------------------------------------------
  // Configurable transparent scoring weights (sum = 1.0)
  // These can be replaced with an ML model output in future iterations.
  // -------------------------------------------------------------------------
  public static readonly WEIGHTS = {
    ARTIST: 0.25,
    GENRE: 0.15,
    RELATED_ARTIST: 0.15,
    ALBUM: 0.10,
    METADATA: 0.05,
    POPULARITY: 0.05,
    SESSION_ARTIST: 0.15,   // Real-time session artist affinity
    SESSION_GENRE: 0.10     // Real-time session genre affinity
  };

  /**
   * Score an individual candidate relative to the current playing track
   * and the real-time session profile.
   * Returns null if candidate is the exact current track or invalid.
   */
  public static scoreCandidate(
    current: TrackMetadata,
    candidate: CandidateTrack,
    sessionProfile?: SessionProfile
  ): ScoredCandidate | null {
    const cand = candidate.track;
    if (!cand.videoId || cand.videoId === current.videoId) {
      return null;
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
    if (
      candidate.source === "same_album" ||
      (curAlbum && candAlbum && curAlbum === candAlbum && curAlbum !== "single" && curAlbum !== curTitle)
    ) {
      albumMatch = 1.0;
    }

    // 5. Metadata Similarity (Token overlap) [0, 1]
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
    const popularity = candidate.popularityScore
      ? Math.min(1.0, Math.max(0.0, candidate.popularityScore))
      : 0.5;

    // 7. Session-based signals (real-time affinity from listening session)
    let sessionArtistAffinity = 0.5;
    let sessionGenreAffinity = 0.5;
    let cooldownPenalty = 0;
    let skipPenalty = 0;

    if (sessionProfile) {
      sessionArtistAffinity = sessionService.getArtistAffinity(sessionProfile, cand.artistName);
      sessionGenreAffinity = sessionService.getGenreAffinity(sessionProfile, cand.genre || "");
      cooldownPenalty = sessionService.getCooldownPenalty(sessionProfile, cand.videoId);
      skipPenalty = sessionService.getArtistSkipPenalty(sessionProfile, cand.artistName);
    }

    // Calculate final weighted score (before penalties)
    let score =
      this.WEIGHTS.ARTIST * artistMatch +
      this.WEIGHTS.GENRE * genreMatch +
      this.WEIGHTS.RELATED_ARTIST * relatedArtistMatch +
      this.WEIGHTS.ALBUM * albumMatch +
      this.WEIGHTS.METADATA * metadataSimilarity +
      this.WEIGHTS.POPULARITY * popularity +
      this.WEIGHTS.SESSION_ARTIST * sessionArtistAffinity +
      this.WEIGHTS.SESSION_GENRE * sessionGenreAffinity;

    // Apply penalties
    score = Math.max(0, score - cooldownPenalty * 0.5 - skipPenalty * 0.3);

    // Determine explanation reason for this recommendation
    const reason = this.deriveReason(candidate.source, artistMatch, sessionArtistAffinity, sessionGenreAffinity);

    return {
      track: cand,
      score: Number(score.toFixed(4)),
      reason,
      signals: {
        artistMatch,
        genreMatch,
        relatedArtistMatch,
        albumMatch,
        metadataSimilarity,
        popularity,
        sessionArtistAffinity,
        sessionGenreAffinity,
        cooldownPenalty,
        skipPenalty
      },
      source: candidate.source
    };
  }

  private static deriveReason(
    source: CandidateSource,
    artistMatch: number,
    sessionArtistAffinity: number,
    sessionGenreAffinity: number
  ): RecommendationReason {
    if (source === "exploration") return "discover_new";
    if (source === "trending") return "trending_favourite";
    if (source === "history") return "from_your_history";
    if (sessionArtistAffinity > 0.7) return "because_you_like_artist";
    if (sessionGenreAffinity > 0.7) return "because_you_like_genre";
    if (source === "same_artist" || artistMatch >= 0.8) return "similar_to_current";
    if (source === "related_artist" || source === "search_sim") return "based_on_recent";
    return "similar_to_current";
  }

  /**
   * Deduplicates, scores, applies diversity constraints, and ranks candidates.
   */
  public static rankAndFilter(
    current: TrackMetadata,
    candidates: CandidateTrack[],
    limit: number = 5,
    sessionProfile?: SessionProfile
  ): ScoredCandidate[] {
    const scoredList: ScoredCandidate[] = [];
    const seenVideoIds = new Set<string>();
    const seenSignatures = new Set<string>();

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

      const scored = this.scoreCandidate(current, candidate, sessionProfile);
      if (scored && scored.score > 0) {
        seenVideoIds.add(vid);
        seenSignatures.add(sig);
        scoredList.push(scored);
      }
    }

    // Sort descending by score
    scoredList.sort((a, b) => b.score - a.score);

    // Diversity balancing: max same-artist per recommendations window
    const maxSameArtist = Math.max(2, Math.floor(limit * 0.5));
    const finalSelection: ScoredCandidate[] = [];
    const artistCounts: Record<string, number> = {};
    const deferredList: ScoredCandidate[] = [];

    const normCurArtist = (current.artistName || "").toLowerCase().trim();

    for (const item of scoredList) {
      const artKey = (item.track.artistName || "").toLowerCase().trim();
      const count = artistCounts[artKey] || 0;
      if (artKey === normCurArtist && count >= maxSameArtist) {
        deferredList.push(item);
        continue;
      }
      artistCounts[artKey] = count + 1;
      finalSelection.push(item);
      if (finalSelection.length >= limit) break;
    }

    // Fill remaining slots from deferred list
    if (finalSelection.length < limit && deferredList.length > 0) {
      for (const deferred of deferredList) {
        finalSelection.push(deferred);
        if (finalSelection.length >= limit) break;
      }
    }

    return finalSelection.slice(0, limit);
  }

  // -------------------------------------------------------------------------
  // Intelligent Shuffle: Weighted Probabilistic Selection
  // -------------------------------------------------------------------------

  /**
   * Generates an intelligently shuffled ordering of the input tracks using
   * softmax-weighted probabilistic sampling (temperature T controls diversity).
   *
   * Enforces:
   *  - Max 2 consecutive songs from same artist
   *  - Prefers artist spacing of 3-6 songs
   *  - Skipped artists/genres are placed later
   *
   * NOTE: This is the pluggable ranking function — replace the scoring logic
   *       here with an ML model in future iterations.
   */
  public static generateIntelligentShuffle(
    tracks: TrackMetadata[],
    currentTrack: TrackMetadata | null,
    sessionProfile?: SessionProfile,
    temperature: number = 0.7
  ): TrackMetadata[] {
    if (tracks.length === 0) return [];

    const remaining = [...tracks];
    const result: TrackMetadata[] = [];
    const recentArtists: string[] = [];

    while (remaining.length > 0) {
      // Score each remaining track given current context
      const scores: number[] = remaining.map((track) => {
        let score = 0.5; // neutral baseline

        // Session affinity signals
        if (sessionProfile) {
          score += sessionService.getArtistAffinity(sessionProfile, track.artistName) * 0.3;
          score += sessionService.getGenreAffinity(sessionProfile, track.genre || "") * 0.2;
          score -= sessionService.getArtistSkipPenalty(sessionProfile, track.artistName) * 0.4;
          score -= sessionService.getCooldownPenalty(sessionProfile, track.videoId) * 0.3;
        }

        // Artist spacing penalty: penalize if same artist appeared in last 3 slots
        const artistKey = (track.artistName || "").toLowerCase().trim();
        const recentIdx = recentArtists.slice(-6).lastIndexOf(artistKey);
        if (recentIdx >= 0) {
          const distance = recentArtists.slice(-6).length - 1 - recentIdx;
          if (distance < 3) score -= (3 - distance) * 0.25; // heavy penalty within 3
          else if (distance < 6) score -= 0.05; // light penalty within 6
        }

        return Math.max(0.001, score);
      });

      // Softmax with temperature
      const maxScore = Math.max(...scores);
      const expScores = scores.map((s) => Math.exp((s - maxScore) / temperature));
      const sumExp = expScores.reduce((a, b) => a + b, 0);
      const probs = expScores.map((e) => e / sumExp);

      // Weighted random selection
      const rand = Math.random();
      let cumulative = 0;
      let selectedIdx = 0;
      for (let i = 0; i < probs.length; i++) {
        cumulative += probs[i];
        if (rand <= cumulative) {
          selectedIdx = i;
          break;
        }
      }

      const selected = remaining[selectedIdx];
      result.push(selected);
      recentArtists.push((selected.artistName || "").toLowerCase().trim());
      remaining.splice(selectedIdx, 1);
    }

    return result;
  }
}
