import { NormalizedTrack, TrendState } from "./types";

export interface TrendScoreWeights {
  externalPopularity: number;
  recentGrowth: number;
  regionalPopularity: number;
  freshness: number;
  listenerActivity: number;
  discoveryMomentum: number;
}

export class TrendScorer {
  // Configurable weights (sum = 1.0)
  public static weights: TrendScoreWeights = {
    externalPopularity: 0.30,
    recentGrowth: 0.20,
    regionalPopularity: 0.15,
    freshness: 0.15,
    listenerActivity: 0.10,
    discoveryMomentum: 0.10
  };

  /**
   * Calculates freshness score based on release date.
   * 0–2 days: 1.0 (Just Released)
   * 3–7 days: 0.85 (New)
   * 8–30 days: 0.60 (Recent)
   * 30+ days: 0.25 (Catalog)
   */
  public static calculateFreshness(releaseDate?: string): number {
    if (!releaseDate) return 0.5;

    try {
      const release = new Date(releaseDate).getTime();
      const now = Date.now();
      const diffDays = Math.max(0, (now - release) / (1000 * 60 * 60 * 24));

      if (diffDays <= 2) return 1.0;
      if (diffDays <= 7) return 0.85;
      if (diffDays <= 30) return 0.60;
      if (diffDays <= 90) return 0.40;
      return 0.20;
    } catch (_e) {
      return 0.5;
    }
  }

  /**
   * Scores and ranks tracks, assigning trendState, momentum, and rank change
   * compared to previous snapshot.
   */
  public static scoreAndRank(
    tracks: NormalizedTrack[],
    previousRanks: Map<string, number> = new Map()
  ): NormalizedTrack[] {
    const scored = tracks.map((track, initialIndex) => {
      const extPop = Math.min(1.0, Math.max(0.0, track.popularity || 0.5));
      const freshness = this.calculateFreshness(track.releaseDate);
      const isRegional = track.language !== "All" && track.language !== "Global" ? 0.85 : 0.65;
      const activity = track.isTrending ? 0.9 : 0.6;

      // Momentum calculation vs previous rank
      const prevRank = previousRanks.get(track.videoId || track.id);
      let rankChange = 0;
      let momentum = 0.5;

      if (prevRank !== undefined) {
        rankChange = prevRank - initialIndex; // Positive = climbed up
        momentum = Math.min(1.0, Math.max(0.1, 0.5 + rankChange * 0.05));
      } else if (freshness > 0.8) {
        momentum = 0.85; // New breakout song
      }

      const trendScore =
        this.weights.externalPopularity * extPop +
        this.weights.recentGrowth * momentum +
        this.weights.regionalPopularity * isRegional +
        this.weights.freshness * freshness +
        this.weights.listenerActivity * activity +
        this.weights.discoveryMomentum * momentum;

      let trendState: TrendState = "STABLE";
      if (freshness >= 0.85) {
        trendState = "NEW";
      } else if (rankChange >= 5 || momentum >= 0.8) {
        trendState = "RISING";
      } else if (trendScore >= 0.80) {
        trendState = "TRENDING";
      } else if (rankChange <= -5) {
        trendState = "COOLING";
      }

      return {
        ...track,
        trendScore: Number(trendScore.toFixed(4)),
        trendState,
        rankChange,
        isNew: freshness >= 0.8,
        isTrending: trendScore >= 0.75
      };
    });

    // Sort descending by trendScore
    scored.sort((a, b) => b.trendScore - a.trendScore);

    return scored;
  }
}
