import { LRUCache } from "lru-cache";
import Redis, { RedisOptions } from "ioredis";

export interface StreamCacheEntry {
  url: string;
  headers: Record<string, string>;
  expiresAt: number;
  format?: string;
  ext?: string;
  bitrate?: number;
  source?: string;
}

export interface CacheStats {
  l1Size: number;
  l1Hits: number;
  l1Misses: number;
  l2Hits: number;
  l2Misses: number;
  singleFlightJoins: number;
  redisConnected: boolean;
}

class CacheService {
  // L1: In-memory LRU cache for ultra-low latency (<1ms)
  private l1Cache: LRUCache<string, any>;
  // L2: Redis client
  private redisClient: Redis | null = null;
  private isRedisReady: boolean = false;

  // Single-Flight Request Coalescing Map (stampede prevention)
  private pendingPromises = new Map<string, Promise<any>>();

  // Cache statistics
  private stats = {
    l1Hits: 0,
    l1Misses: 0,
    l2Hits: 0,
    l2Misses: 0,
    singleFlightJoins: 0
  };

  constructor() {
    // Configure L1 cache: up to 15,000 items, default TTL 1 hour
    this.l1Cache = new LRUCache<string, any>({
      max: 15000,
      ttl: 1000 * 60 * 60, // 1 hour default
      updateAgeOnGet: true
    });

    this.initRedis();
  }

  private initRedis() {
    const redisUrl = process.env.REDIS_URL || process.env.REDIS_PRIVATE_URL;
    const redisHost = process.env.REDISHOST || process.env.REDIS_HOST;
    const redisPort = parseInt(process.env.REDISPORT || process.env.REDIS_PORT || "6379", 10);
    const redisPassword = process.env.REDISPASSWORD || process.env.REDIS_PASSWORD;

    if (!redisUrl && !redisHost) {
      console.log("ℹ Redis not configured. Operating with high-performance L1 In-Memory Cache.");
      return;
    }

    try {
      const options: RedisOptions = {
        maxRetriesPerRequest: 2,
        connectTimeout: 5000,
        enableReadyCheck: true,
        retryStrategy: (times: number) => {
          if (times > 10) {
            console.warn("⚠ Redis max reconnection attempts reached. Operating in degraded L1-only mode.");
            return null;
          }
          return Math.min(times * 200, 3000);
        },
        lazyConnect: true
      };

      if (redisUrl) {
        this.redisClient = new Redis(redisUrl, options);
      } else {
        this.redisClient = new Redis({
          host: redisHost,
          port: redisPort,
          password: redisPassword,
          ...options
        });
      }

      this.redisClient.on("connect", () => {
        console.log("✓ Redis connection established.");
      });

      this.redisClient.on("ready", () => {
        this.isRedisReady = true;
        console.log("✓ Redis client ready for shared caching across instances.");
      });

      this.redisClient.on("error", (err) => {
        this.isRedisReady = false;
        console.warn(`⚠ Redis connection warning: ${err.message}. Falling back to L1 cache.`);
      });

      this.redisClient.on("close", () => {
        this.isRedisReady = false;
      });

      // Connect asynchronously
      this.redisClient.connect().catch((e) => {
        console.warn("⚠ Initial Redis connection failed. Operating with L1 cache.", e.message);
      });
    } catch (err: any) {
      console.warn("⚠ Failed to initialize Redis client:", err.message);
    }
  }

  /**
   * Single-Flight Request Coalescing
   * If 100 concurrent requests ask for the same unresolved key, only 1 actual fetcher executes!
   * The other 99 wait on the same Promise and share the exact same result.
   */
  async coalesce<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
    const existing = this.pendingPromises.get(key);
    if (existing) {
      this.stats.singleFlightJoins++;
      return existing as Promise<T>;
    }

    const promise = fetcher()
      .finally(() => {
        this.pendingPromises.delete(key);
      });

    this.pendingPromises.set(key, promise);
    return promise;
  }

  /**
   * Get item with L1 -> L2 fallback
   */
  async get<T>(key: string): Promise<T | null> {
    // 1. Check L1 Memory Cache
    const l1Hit = this.l1Cache.get(key) as T | undefined;
    if (l1Hit !== undefined) {
      this.stats.l1Hits++;
      return l1Hit;
    }
    this.stats.l1Misses++;

    // 2. Check L2 Redis Cache
    if (this.isRedisReady && this.redisClient) {
      try {
        const raw = await this.redisClient.get(key);
        if (raw) {
          this.stats.l2Hits++;
          const parsed = JSON.parse(raw) as T;
          // Populate L1 cache for subsequent fast reads
          this.l1Cache.set(key, parsed);
          return parsed;
        }
        this.stats.l2Misses++;
      } catch (err: any) {
        console.warn(`Redis get failed for key ${key}:`, err.message);
      }
    }

    return null;
  }

  /**
   * Set item in both L1 and L2 with TTL in seconds
   */
  async set<T>(key: string, value: T, ttlSeconds: number = 3600): Promise<void> {
    // Set in L1
    this.l1Cache.set(key, value, { ttl: ttlSeconds * 1000 });

    // Set in L2 Redis
    if (this.isRedisReady && this.redisClient) {
      try {
        const str = JSON.stringify(value);
        await this.redisClient.set(key, str, "EX", ttlSeconds);
      } catch (err: any) {
        console.warn(`Redis set failed for key ${key}:`, err.message);
      }
    }
  }

  /**
   * Delete key from L1 & L2
   */
  async delete(key: string): Promise<void> {
    this.l1Cache.delete(key);
    if (this.isRedisReady && this.redisClient) {
      try {
        await this.redisClient.del(key);
      } catch (err: any) {
        console.warn(`Redis del failed for key ${key}:`, err.message);
      }
    }
  }

  /**
   * Track Popular Tracks via Redis Sorted Set & L1 counter
   */
  async recordTrackPlay(trackId: string): Promise<void> {
    if (this.isRedisReady && this.redisClient) {
      try {
        await this.redisClient.zincrby("musync:popular_tracks", 1, trackId);
      } catch (_e) {}
    }
  }

  async getPopularTracks(limit: number = 20): Promise<string[]> {
    if (this.isRedisReady && this.redisClient) {
      try {
        return await this.redisClient.zrevrange("musync:popular_tracks", 0, limit - 1);
      } catch (_e) {}
    }
    return [];
  }

  getStats(): CacheStats {
    return {
      l1Size: this.l1Cache.size,
      l1Hits: this.stats.l1Hits,
      l1Misses: this.stats.l1Misses,
      l2Hits: this.stats.l2Hits,
      l2Misses: this.stats.l2Misses,
      singleFlightJoins: this.stats.singleFlightJoins,
      redisConnected: this.isRedisReady
    };
  }

  async close(): Promise<void> {
    if (this.redisClient) {
      try {
        await this.redisClient.quit();
      } catch (_e) {}
    }
  }
}

export const cacheService = new CacheService();
