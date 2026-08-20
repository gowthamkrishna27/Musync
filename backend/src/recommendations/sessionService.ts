import { cacheService } from "../cache/cacheService";

// ---------------------------------------------------------------------------
// Session Profile Types
// ---------------------------------------------------------------------------

export interface ListeningEvent {
  userId: string;         // anonymous or authenticated user ID
  trackId: string;
  artistName?: string;
  genre?: string;
  eventType:
    | "SONG_STARTED"
    | "SONG_25_PERCENT"
    | "SONG_50_PERCENT"
    | "SONG_75_PERCENT"
    | "SONG_COMPLETED"
    | "SONG_SKIPPED"
    | "SONG_LIKED"
    | "SONG_UNLIKED"
    | "SONG_REPLAYED"
    | "SONG_ADDED_TO_PLAYLIST";
  durationMs?: number;
  positionMs?: number;
  timestampMs?: number;
}

export interface SessionProfile {
  userId: string;
  sessionStartMs: number;
  lastUpdatedMs: number;
  // Genre / Artist affinity scores (0-1, exponentially decayed)
  genreAffinities: Record<string, number>;
  artistAffinities: Record<string, number>;
  // Interaction counts used for skip learning
  skipCounts: Record<string, number>;         // trackId -> skip count
  completionCounts: Record<string, number>;   // trackId -> completion count
  artistSkipCounts: Record<string, number>;   // artistName -> skip count
  genreSkipCounts: Record<string, number>;    // genre -> skip count
  // Rolling recency deque (last 50 track IDs for cooldown checks)
  recentTrackIds: string[];
  // Total events in this session
  totalEvents: number;
}

// ---------------------------------------------------------------------------
// SessionService
// ---------------------------------------------------------------------------

const SESSION_TTL_SECONDS = 60 * 60 * 6; // 6 hours
const AFFINITY_DECAY = 0.9;               // exponential recency decay factor
const MAX_RECENT_TRACKS = 50;

function defaultSession(userId: string): SessionProfile {
  return {
    userId,
    sessionStartMs: Date.now(),
    lastUpdatedMs: Date.now(),
    genreAffinities: {},
    artistAffinities: {},
    skipCounts: {},
    completionCounts: {},
    artistSkipCounts: {},
    genreSkipCounts: {},
    recentTrackIds: [],
    totalEvents: 0
  };
}

export class SessionService {
  // In-process L1 cache (avoids Redis round-trip within same request burst)
  private l1Cache: Map<string, { profile: SessionProfile; expiry: number }> = new Map();
  private readonly L1_TTL_MS = 30_000; // 30 seconds

  private cacheKey(userId: string): string {
    return `musync:session:${userId}`;
  }

  async getSession(userId: string): Promise<SessionProfile> {
    // L1
    const l1 = this.l1Cache.get(userId);
    if (l1 && l1.expiry > Date.now()) return l1.profile;

    // L2 Redis
    const cached = await cacheService.get<SessionProfile>(this.cacheKey(userId));
    if (cached) {
      this.l1Cache.set(userId, { profile: cached, expiry: Date.now() + this.L1_TTL_MS });
      return cached;
    }

    return defaultSession(userId);
  }

  async saveSession(profile: SessionProfile): Promise<void> {
    profile.lastUpdatedMs = Date.now();
    this.l1Cache.set(profile.userId, {
      profile,
      expiry: Date.now() + this.L1_TTL_MS
    });
    await cacheService.set(this.cacheKey(profile.userId), profile, SESSION_TTL_SECONDS);
  }

  /**
   * Process a single listening event and update the session profile.
   * Returns the updated profile.
   */
  async processEvent(event: ListeningEvent): Promise<SessionProfile> {
    const profile = await this.getSession(event.userId);

    const artist = (event.artistName || "").toLowerCase().trim();
    const genre = (event.genre || "").toLowerCase().trim();
    const trackId = event.trackId;

    profile.totalEvents += 1;

    switch (event.eventType) {
      case "SONG_STARTED":
        // Add to recency deque
        profile.recentTrackIds = [
          trackId,
          ...profile.recentTrackIds.filter((id) => id !== trackId)
        ].slice(0, MAX_RECENT_TRACKS);
        break;

      case "SONG_COMPLETED":
      case "SONG_REPLAYED": {
        // Strong positive signal: boost artist + genre affinities
        const boost = event.eventType === "SONG_REPLAYED" ? 0.4 : 0.3;
        if (artist) {
          profile.artistAffinities[artist] =
            Math.min(1.0, (profile.artistAffinities[artist] ?? 0) * AFFINITY_DECAY + boost);
        }
        if (genre) {
          profile.genreAffinities[genre] =
            Math.min(1.0, (profile.genreAffinities[genre] ?? 0) * AFFINITY_DECAY + boost);
        }
        profile.completionCounts[trackId] = (profile.completionCounts[trackId] ?? 0) + 1;
        break;
      }

      case "SONG_75_PERCENT": {
        // Moderate positive signal
        const boost = 0.15;
        if (artist) {
          profile.artistAffinities[artist] =
            Math.min(1.0, (profile.artistAffinities[artist] ?? 0) * AFFINITY_DECAY + boost);
        }
        if (genre) {
          profile.genreAffinities[genre] =
            Math.min(1.0, (profile.genreAffinities[genre] ?? 0) * AFFINITY_DECAY + boost);
        }
        break;
      }

      case "SONG_SKIPPED": {
        // Negative signal: reduce artist + genre affinity, record skip
        profile.skipCounts[trackId] = (profile.skipCounts[trackId] ?? 0) + 1;
        const penalty = 0.2;
        if (artist) {
          profile.artistAffinities[artist] = Math.max(
            0,
            (profile.artistAffinities[artist] ?? 0.5) - penalty
          );
          profile.artistSkipCounts[artist] = (profile.artistSkipCounts[artist] ?? 0) + 1;
        }
        if (genre) {
          profile.genreAffinities[genre] = Math.max(
            0,
            (profile.genreAffinities[genre] ?? 0.5) - penalty
          );
          profile.genreSkipCounts[genre] = (profile.genreSkipCounts[genre] ?? 0) + 1;
        }
        break;
      }

      case "SONG_LIKED": {
        // Strong explicit positive signal
        const boost = 0.5;
        if (artist) {
          profile.artistAffinities[artist] = Math.min(
            1.0,
            (profile.artistAffinities[artist] ?? 0) + boost
          );
        }
        if (genre) {
          profile.genreAffinities[genre] = Math.min(
            1.0,
            (profile.genreAffinities[genre] ?? 0) + boost
          );
        }
        break;
      }

      case "SONG_UNLIKED": {
        // Undo like boost
        const penalty = 0.3;
        if (artist) {
          profile.artistAffinities[artist] = Math.max(
            0,
            (profile.artistAffinities[artist] ?? 0) - penalty
          );
        }
        if (genre) {
          profile.genreAffinities[genre] = Math.max(
            0,
            (profile.genreAffinities[genre] ?? 0) - penalty
          );
        }
        break;
      }
    }

    await this.saveSession(profile);
    return profile;
  }

  /**
   * Returns artist affinity score [0, 1] for a given artist within the session.
   * 0.5 is neutral (unknown artist). >0.5 = boosted. <0.5 = suppressed.
   */
  getArtistAffinity(profile: SessionProfile, artistName: string): number {
    const key = (artistName || "").toLowerCase().trim();
    return profile.artistAffinities[key] ?? 0.5;
  }

  /**
   * Returns genre affinity score [0, 1]. 0.5 is neutral.
   */
  getGenreAffinity(profile: SessionProfile, genre: string): number {
    const key = (genre || "").toLowerCase().trim();
    return profile.genreAffinities[key] ?? 0.5;
  }

  /**
   * Returns a skip penalty [0, 1] for an artist. 0 = no penalty. >0 = suppress.
   */
  getArtistSkipPenalty(profile: SessionProfile, artistName: string): number {
    const key = (artistName || "").toLowerCase().trim();
    const skips = profile.artistSkipCounts[key] ?? 0;
    // Diminishing returns: 1 skip -> 0.15, 2 -> 0.25, 3+ -> 0.35
    return Math.min(0.35, skips * 0.12);
  }

  /**
   * Returns a cooldown penalty for recently played tracks.
   * Tracks within the last N slots get a penalty.
   */
  getCooldownPenalty(profile: SessionProfile, trackId: string): number {
    const pos = profile.recentTrackIds.indexOf(trackId);
    if (pos === -1) return 0;
    if (pos < 5) return 0.8;  // Very recently played — heavy penalty
    if (pos < 15) return 0.4;
    if (pos < 30) return 0.15;
    return 0;
  }
}

export const sessionService = new SessionService();
