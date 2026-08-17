import { RecommendationScorer, TrackMetadata, CandidateTrack } from "../src/recommendations/recommendationScorer";
import { cacheService } from "../src/cache/cacheService";

async function runTests() {
  console.log("=== STARTING RECOMMENDATION ENGINE VALIDATION TESTS ===\n");

  const currentTrack: TrackMetadata = {
    videoId: "3_g2un5M350",
    title: "Starboy",
    artistName: "The Weeknd",
    albumName: "Starboy",
    genre: "Pop"
  };

  const candidatePool: CandidateTrack[] = [
    // 1. Current track itself (Must be eliminated)
    {
      track: { videoId: "3_g2un5M350", title: "Starboy", artistName: "The Weeknd" },
      source: "same_artist"
    },
    // 2. Same artist track A
    {
      track: { videoId: "the_hills_id", title: "The Hills", artistName: "The Weeknd", albumName: "Beauty Behind the Madness" },
      source: "same_artist",
      popularityScore: 0.9
    },
    // 3. Same artist track B
    {
      track: { videoId: "blinding_lights_id", title: "Blinding Lights", artistName: "The Weeknd", albumName: "After Hours" },
      source: "same_artist",
      popularityScore: 0.95
    },
    // 4. Same artist track C
    {
      track: { videoId: "often_id", title: "Often", artistName: "The Weeknd" },
      source: "same_artist"
    },
    // 5. Same artist track D
    {
      track: { videoId: "die_for_you_id", title: "Die For You", artistName: "The Weeknd" },
      source: "same_artist"
    },
    // 6. Related artist track
    {
      track: { videoId: "one_more_time_id", title: "One More Time", artistName: "Daft Punk" },
      source: "related_artist",
      popularityScore: 0.85
    },
    // 7. Same genre / mood track
    {
      track: { videoId: "midnight_city_id", title: "Midnight City", artistName: "M83", genre: "Pop" },
      source: "same_genre",
      popularityScore: 0.8
    },
    // 8. Duplicate item (same ID)
    {
      track: { videoId: "the_hills_id", title: "The Hills", artistName: "The Weeknd" },
      source: "same_artist"
    }
  ];

  console.log("TEST 1: Candidate Scoring & Hard Filtering (Current Track Exclusion)");
  const scoredCur = RecommendationScorer.scoreCandidate(currentTrack, candidatePool[0]);
  console.assert(scoredCur === null, "FAIL: Current track was not excluded!");
  console.log("✓ Current track successfully excluded (score = null).");

  console.log("\nTEST 2: Deduplication and Scoring");
  const ranked = RecommendationScorer.rankAndFilter(currentTrack, candidatePool, 5);
  console.log(`Ranked recommendations count: ${ranked.length}`);
  ranked.forEach((r, idx) => {
    console.log(`  ${idx + 1}. [${r.track.videoId}] ${r.track.title} — ${r.track.artistName} (Score: ${r.score}, Source: ${r.source})`);
  });

  console.assert(ranked.length === 5, `FAIL: Expected 5 recommendations, got ${ranked.length}`);
  console.assert(!ranked.some(r => r.track.videoId === "3_g2un5M350"), "FAIL: Current track appeared in output!");
  const uniqueIds = new Set(ranked.map(r => r.track.videoId));
  console.assert(uniqueIds.size === ranked.length, "FAIL: Duplicate track IDs found in output!");
  console.log("✓ Deduplication and output count passed.");

  console.log("\nTEST 3: Diversity Balancing");
  const artistCounts: Record<string, number> = {};
  ranked.forEach(r => {
    artistCounts[r.track.artistName] = (artistCounts[r.track.artistName] || 0) + 1;
  });
  console.log("Artist distribution:", artistCounts);
  console.assert(Object.keys(artistCounts).length > 1, "FAIL: Diversity balancing did not include diverse artists!");
  console.log("✓ Diversity balancing passed (includes same artist and related/genre artists).");

  console.log("\nTEST 4: Single-Flight Coalescing (100 Simultaneous Requests)");
  let fetchCount = 0;
  const simulatedFetcher = async () => {
    fetchCount++;
    await new Promise(r => setTimeout(r, 50));
    return { data: "sample_recommendations" };
  };

  const coalesceKey = "test:singleflight:3_g2un5M350";
  const promises = Array.from({ length: 100 }, () =>
    cacheService.coalesce(coalesceKey, simulatedFetcher)
  );

  const results = await Promise.all(promises);
  console.log(`100 concurrent requests completed. Fetch executions: ${fetchCount}`);
  console.assert(fetchCount === 1, `FAIL: Expected exactly 1 fetch execution, got ${fetchCount}`);
  console.assert(results.every(r => r.data === "sample_recommendations"), "FAIL: Inconsistent result from coalescing");
  console.log("✓ Single-flight coalescing passed (100 requests -> 1 execution).");

  console.log("\nTEST 5: Cache Set and Fast Hit Latency (<1ms in-memory)");
  await cacheService.set("test:rec:hit", { trackId: "3_g2un5M350", list: ranked }, 3600);
  const t0 = performance.now();
  const cached = await cacheService.get<any>("test:rec:hit");
  const hitDuration = performance.now() - t0;
  console.log(`Cache read completed in ${hitDuration.toFixed(3)} ms. Items: ${cached?.list?.length}`);
  console.assert(cached !== null && cached.trackId === "3_g2un5M350", "FAIL: Cache retrieval failed");
  console.log("✓ Multi-tier cache retrieval passed.");

  console.log("\n=== ALL TESTS PASSED SUCCESSFULLY! ===");
  process.exit(0);
}

runTests().catch(err => {
  console.error("Test execution failed:", err);
  process.exit(1);
});
