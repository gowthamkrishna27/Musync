import YTMusic from "ytmusic-api";
import { cacheService } from "../cache/cacheService";
import { YouTubeProvider } from "./providers/youtubeProvider";
import { LastFMProvider } from "./providers/lastfmProvider";
import { TrackDeduplicator } from "./deduplicator";
import { TrendScorer } from "./trendScorer";
import { NormalizedTrack, DiscoverySection, DiscoveryHomeResponse } from "./types";

export class DiscoveryWorker {
  private youtubeProvider: YouTubeProvider;
  private lastfmProvider: LastFMProvider;

  // Track rank history in memory for momentum calculation
  private rankSnapshots = new Map<string, Map<string, number>>();

  private trendingIntervalMs = parseInt(process.env.TRENDING_REFRESH_MINUTES || "20", 10) * 60 * 1000;
  private newReleaseIntervalMs = parseInt(process.env.NEW_RELEASE_REFRESH_HOURS || "2", 10) * 60 * 60 * 1000;

  private isRunning = false;
  private timer: NodeJS.Timeout | null = null;

  constructor(ytmusic: YTMusic) {
    this.youtubeProvider = new YouTubeProvider(ytmusic);
    this.lastfmProvider = new LastFMProvider();
  }

  public start() {
    if (this.isRunning) return;
    this.isRunning = true;
    console.log("✓ DiscoveryWorker initialized. Scheduling live music ingestion.");

    // Initial pre-warming after 3 seconds
    setTimeout(() => {
      this.refreshAllCategories().catch((e) =>
        console.warn("[DiscoveryWorker] Initial refresh notice:", e.message)
      );
    }, 3000);

    // Periodic interval
    this.timer = setInterval(() => {
      this.refreshAllCategories().catch((e) =>
        console.warn("[DiscoveryWorker] Scheduled refresh warning:", e.message)
      );
    }, this.trendingIntervalMs);
  }

  public stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.isRunning = false;
  }

  /**
   * Acquire a distributed lock to prevent multiple instances from overlapping.
   */
  private async acquireLock(lockKey: string, ttlSeconds = 60): Promise<boolean> {
    const existing = await cacheService.get<string>(`lock:${lockKey}`);
    if (existing) return false;
    await cacheService.set(`lock:${lockKey}`, "locked", ttlSeconds);
    return true;
  }

  /**
   * Refreshes all core feeds across regions and languages.
   */
  public async refreshAllCategories(): Promise<void> {
    const hasLock = await this.acquireLock("discover:refresh_all", 120);
    if (!hasLock) {
      console.log("[DiscoveryWorker] Refresh skipped (lock held by another worker).");
      return;
    }

    console.log("⚡ [DiscoveryWorker] Starting live music discovery sweep...");
    const languages = ["All", "Telugu", "Tamil", "Hindi", "Kannada", "Malayalam", "English"];

    // 1. Trending feeds
    for (const lang of languages) {
      await this.getTrending(lang === "All" ? "global" : "india", lang, true);
    }

    // 2. New release feeds
    for (const lang of languages) {
      await this.getNewReleases(lang, true);
    }

    // 3. Rising breakout hits
    await this.getRising(true);

    console.log("✓ [DiscoveryWorker] Live music discovery sweep completed successfully.");
  }

  /**
   * Get trending tracks with Stale-While-Revalidate caching.
   */
  public async getTrending(
    region = "global",
    language = "All",
    forceRefresh = false
  ): Promise<NormalizedTrack[]> {
    const cacheKey = `musync:trending:${region.toLowerCase()}:${language.toLowerCase()}`;

    if (!forceRefresh) {
      const cached = await cacheService.get<NormalizedTrack[]>(cacheKey);
      if (cached && cached.length > 0) {
        return cached;
      }
    }

    return cacheService.coalesce<NormalizedTrack[]>(cacheKey, async () => {
      try {
        const [ytTracks, lfmTracks] = await Promise.allSettled([
          this.youtubeProvider.fetchTrending(region, language, 25),
          this.lastfmProvider.fetchTrending(region, language, 15)
        ]);

        const pool: NormalizedTrack[] = [];
        if (ytTracks.status === "fulfilled") pool.push(...ytTracks.value);
        if (lfmTracks.status === "fulfilled") pool.push(...lfmTracks.value);

        const deduped = TrackDeduplicator.deduplicate(pool);
        const previousRanks = this.rankSnapshots.get(cacheKey) || new Map<string, number>();

        const ranked = TrendScorer.scoreAndRank(deduped, previousRanks);

        // Update rank snapshot
        const newRanks = new Map<string, number>();
        ranked.forEach((t, idx) => newRanks.set(t.videoId || t.id, idx));
        this.rankSnapshots.set(cacheKey, newRanks);

        // Cache in Redis for 30 minutes
        await cacheService.set(cacheKey, ranked, 1800);
        return ranked;
      } catch (err: any) {
        console.error(`[DiscoveryWorker] getTrending error for ${region}/${language}:`, err.message);
        return (await cacheService.get<NormalizedTrack[]>(cacheKey)) || [];
      }
    });
  }

  /**
   * Get new releases with Stale-While-Revalidate caching.
   */
  public async getNewReleases(language = "All", forceRefresh = false): Promise<NormalizedTrack[]> {
    const cacheKey = `musync:new:${language.toLowerCase()}`;

    if (!forceRefresh) {
      const cached = await cacheService.get<NormalizedTrack[]>(cacheKey);
      if (cached && cached.length > 0) {
        return cached;
      }
    }

    return cacheService.coalesce<NormalizedTrack[]>(cacheKey, async () => {
      try {
        const [ytTracks, lfmTracks] = await Promise.allSettled([
          this.youtubeProvider.fetchNewReleases(language, 25),
          this.lastfmProvider.fetchNewReleases(language, 10)
        ]);

        const pool: NormalizedTrack[] = [];
        if (ytTracks.status === "fulfilled") pool.push(...ytTracks.value);
        if (lfmTracks.status === "fulfilled") pool.push(...lfmTracks.value);

        const deduped = TrackDeduplicator.deduplicate(pool);
        const ranked = TrendScorer.scoreAndRank(deduped);

        // Cache in Redis for 2 hours
        await cacheService.set(cacheKey, ranked, 7200);
        return ranked;
      } catch (err: any) {
        console.error(`[DiscoveryWorker] getNewReleases error for ${language}:`, err.message);
        return (await cacheService.get<NormalizedTrack[]>(cacheKey)) || [];
      }
    });
  }

  /**
   * Get rising songs.
   */
  public async getRising(forceRefresh = false): Promise<NormalizedTrack[]> {
    const cacheKey = `musync:discover:rising`;

    if (!forceRefresh) {
      const cached = await cacheService.get<NormalizedTrack[]>(cacheKey);
      if (cached && cached.length > 0) {
        return cached;
      }
    }

    return cacheService.coalesce<NormalizedTrack[]>(cacheKey, async () => {
      try {
        const ytTracks = await this.youtubeProvider.fetchRising(20);
        const deduped = TrackDeduplicator.deduplicate(ytTracks);
        const ranked = TrendScorer.scoreAndRank(deduped);

        await cacheService.set(cacheKey, ranked, 3600);
        return ranked;
      } catch (err: any) {
        console.error(`[DiscoveryWorker] getRising error:`, err.message);
        return (await cacheService.get<NormalizedTrack[]>(cacheKey)) || [];
      }
    });
  }

  /**
   * Generates consolidated home discovery response with live sections.
   */
  public async getHomeDiscovery(language = "All", reqHost: string): Promise<DiscoveryHomeResponse> {
    const [trendingNow, newReleases, risingFast, indiaTrending, regionalTrending] = await Promise.all([
      this.getTrending("global", language),
      this.getNewReleases(language),
      this.getRising(),
      this.getTrending("india", "All"),
      language !== "All" ? this.getTrending("india", language) : Promise.resolve([])
    ]);

    const formatForClient = (tracks: NormalizedTrack[]) =>
      tracks.map((t) => ({
        ...t,
        streamUrl: t.videoId ? `${reqHost}/stream?id=${t.videoId}` : undefined,
        mediaUrl: t.videoId ? `${reqHost}/stream?id=${t.videoId}` : undefined
      }));

    const sections: DiscoverySection[] = [
      {
        id: "trending_now",
        title: "🔥 Trending Now",
        subtitle: "The hottest tracks playing right now",
        badge: "LIVE",
        tracks: formatForClient(trendingNow.slice(0, 12))
      },
      {
        id: "new_releases",
        title: "✨ New Releases",
        subtitle: "Freshly dropped singles, EPs & albums",
        badge: "NEW",
        tracks: formatForClient(newReleases.slice(0, 12))
      },
      {
        id: "rising_fast",
        title: "📈 Rising Fast",
        subtitle: "Breakout viral hits climbing the charts",
        badge: "BREAKOUT",
        tracks: formatForClient(risingFast.slice(0, 10))
      },
      {
        id: "india_trending",
        title: "🇮🇳 Trending in India",
        subtitle: "Top chart-toppers across India",
        badge: "CHART",
        tracks: formatForClient(indiaTrending.slice(0, 12))
      }
    ];

    if (regionalTrending.length > 0 && language !== "All") {
      sections.splice(1, 0, {
        id: `trending_${language.toLowerCase()}`,
        title: `🎵 Trending in ${language}`,
        subtitle: `Top hits in ${language}`,
        badge: language.toUpperCase(),
        language,
        tracks: formatForClient(regionalTrending.slice(0, 12))
      });
    }

    const totalTracks = sections.reduce((acc, s) => acc + s.tracks.length, 0);

    return {
      timestamp: Date.now(),
      sections,
      cached: true,
      totalTracks
    };
  }
}
