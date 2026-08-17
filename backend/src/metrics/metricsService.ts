export interface PerformanceMetrics {
  uptimeSeconds: number;
  totalRequests: number;
  activeStreams: number;
  bytesServed: number;
  rangeRequestsCount: number;
  latencyHistogram: {
    p50Ms: number;
    p95Ms: number;
    p99Ms: number;
    avgMs: number;
    maxMs: number;
    sampleSize: number;
  };
  memory: {
    rssMb: number;
    heapUsedMb: number;
    heapTotalMb: number;
    externalMb: number;
  };
  eventLoopLagMs: number;
}

class MetricsService {
  private startTime = Date.now();
  private totalRequests = 0;
  private activeStreams = 0;
  private bytesServed = 0;
  private rangeRequestsCount = 0;

  // Rolling latency samples (last 2000 requests)
  private latencySamples: number[] = [];
  private readonly maxSamples = 2000;

  // Event loop lag measurement
  private eventLoopLagMs = 0;
  private lagInterval: NodeJS.Timeout | null = null;

  constructor() {
    this.startLagMeasurement();
  }

  private startLagMeasurement() {
    let lastTime = Date.now();
    this.lagInterval = setInterval(() => {
      const now = Date.now();
      const delta = now - lastTime - 1000;
      this.eventLoopLagMs = Math.max(0, delta);
      lastTime = now;
    }, 1000);
    this.lagInterval.unref();
  }

  recordRequest(durationMs: number) {
    this.totalRequests++;
    if (this.latencySamples.length >= this.maxSamples) {
      this.latencySamples.shift();
    }
    this.latencySamples.push(durationMs);
  }

  incrementActiveStreams() {
    this.activeStreams++;
  }

  decrementActiveStreams() {
    this.activeStreams = Math.max(0, this.activeStreams - 1);
  }

  recordRangeRequest(bytes: number) {
    this.rangeRequestsCount++;
    this.bytesServed += bytes;
  }

  // Recommendation Metrics
  private recommendationRequests = 0;
  private recommendationCacheHits = 0;
  private recommendationCacheMisses = 0;
  private recommendationSuccesses = 0;
  private recommendationFailures = 0;
  private recLatencySamples: number[] = [];

  recordRecommendationRequest() {
    this.recommendationRequests++;
  }

  recordRecommendationCacheHit() {
    this.recommendationCacheHits++;
  }

  recordRecommendationCacheMiss() {
    this.recommendationCacheMisses++;
  }

  recordRecommendationSuccess() {
    this.recommendationSuccesses++;
  }

  recordRecommendationFailure() {
    this.recommendationFailures++;
  }

  recordRecommendationLatency(latencyMs: number) {
    if (this.recLatencySamples.length >= 500) {
      this.recLatencySamples.shift();
    }
    this.recLatencySamples.push(latencyMs);
  }

  getMetrics(): PerformanceMetrics & { recommendations?: any } {
    const mem = process.memoryUsage();
    const sorted = [...this.latencySamples].sort((a, b) => a - b);
    const n = sorted.length;

    const p50 = n > 0 ? sorted[Math.floor(n * 0.5)] : 0;
    const p95 = n > 0 ? sorted[Math.floor(n * 0.95)] : 0;
    const p99 = n > 0 ? sorted[Math.floor(n * 0.99)] : 0;
    const avg = n > 0 ? Math.round(sorted.reduce((a, b) => a + b, 0) / n) : 0;
    const max = n > 0 ? sorted[n - 1] : 0;

    const recSorted = [...this.recLatencySamples].sort((a, b) => a - b);
    const recN = recSorted.length;
    const recAvg = recN > 0 ? Math.round(recSorted.reduce((a, b) => a + b, 0) / recN) : 0;
    const recP95 = recN > 0 ? recSorted[Math.floor(recN * 0.95)] : 0;

    return {
      uptimeSeconds: Math.floor((Date.now() - this.startTime) / 1000),
      totalRequests: this.totalRequests,
      activeStreams: this.activeStreams,
      bytesServed: this.bytesServed,
      rangeRequestsCount: this.rangeRequestsCount,
      latencyHistogram: {
        p50Ms: Math.round(p50),
        p95Ms: Math.round(p95),
        p99Ms: Math.round(p99),
        avgMs: avg,
        maxMs: Math.round(max),
        sampleSize: n
      },
      memory: {
        rssMb: Math.round(mem.rss / 1024 / 1024),
        heapUsedMb: Math.round(mem.heapUsed / 1024 / 1024),
        heapTotalMb: Math.round(mem.heapTotal / 1024 / 1024),
        externalMb: Math.round(mem.external / 1024 / 1024)
      },
      eventLoopLagMs: this.eventLoopLagMs,
      recommendations: {
        totalRequests: this.recommendationRequests,
        cacheHits: this.recommendationCacheHits,
        cacheMisses: this.recommendationCacheMisses,
        cacheHitRate: this.recommendationRequests > 0
          ? Number((this.recommendationCacheHits / this.recommendationRequests).toFixed(2))
          : 0,
        successes: this.recommendationSuccesses,
        failures: this.recommendationFailures,
        avgLatencyMs: recAvg,
        p95LatencyMs: recP95
      }
    };
  }

  close() {
    if (this.lagInterval) {
      clearInterval(this.lagInterval);
    }
  }
}

export const metricsService = new MetricsService();

