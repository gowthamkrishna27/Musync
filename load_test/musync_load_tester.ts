import http from "http";
import https from "https";

const TARGET_URL = process.env.TARGET_URL || "http://127.0.0.1:5000";

interface TestStage {
  concurrency: number;
  durationSeconds: number;
}

const STAGES: TestStage[] = [
  { concurrency: 100, durationSeconds: 5 },
  { concurrency: 500, durationSeconds: 5 },
  { concurrency: 1000, durationSeconds: 5 },
  { concurrency: 1500, durationSeconds: 5 },
  { concurrency: 2000, durationSeconds: 5 },
  { concurrency: 2500, durationSeconds: 5 }
];

const SAMPLE_TRACKS = [
  "3_g2un5M350",
  "kXYiU_JCYtU",
  "fJ9rUzIMcZQ",
  "kJQP7kiw5Fk",
  "dQw4w9WgXcQ"
];

const SEARCH_QUERIES = [
  "Taylor Swift",
  "The Weeknd",
  "Arijit Singh",
  "Ed Sheeran",
  "Coldplay"
];

const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 10000 });
const httpsAgent = new https.Agent({ keepAlive: true, maxSockets: 10000 });

async function makeRequest(
  url: string,
  headers: Record<string, string> = {}
): Promise<{ status: number; durationMs: number; bytes: number }> {
  const start = Date.now();
  const isHttps = url.startsWith("https");
  const lib = isHttps ? https : http;
  const agent = isHttps ? httpsAgent : httpAgent;

  return new Promise((resolve) => {
    const parsed = new URL(url);
    const req = lib.request(
      {
        protocol: parsed.protocol,
        hostname: parsed.hostname,
        port: parsed.port || (isHttps ? 443 : 80),
        path: parsed.pathname + parsed.search,
        method: "GET",
        headers: {
          "User-Agent": "Musync-LoadTester/1.0",
          ...headers
        },
        agent,
        timeout: 10000
      },
      (res) => {
        let bytes = 0;
        res.on("data", (chunk) => {
          bytes += chunk.length;
        });
        res.on("end", () => {
          resolve({
            status: res.statusCode || 0,
            durationMs: Date.now() - start,
            bytes
          });
        });
      }
    );

    req.on("error", () => {
      resolve({
        status: 0,
        durationMs: Date.now() - start,
        bytes: 0
      });
    });

    req.on("timeout", () => {
      req.destroy();
      resolve({
        status: 408,
        durationMs: Date.now() - start,
        bytes: 0
      });
    });

    req.end();
  });
}

async function runStage(concurrency: number, durationSeconds: number) {
  console.log(`\n======================================================`);
  console.log(`🚀 Benchmarking Target: ${concurrency} Concurrent Playback Sessions`);
  console.log(`⏱  Duration: ${durationSeconds}s | Target: ${TARGET_URL}`);
  console.log(`======================================================`);

  let totalRequests = 0;
  let successfulRequests = 0;
  let failedRequests = 0;
  const latencies: number[] = [];
  let totalBytes = 0;

  const endTime = Date.now() + durationSeconds * 1000;

  async function worker(workerId: number) {
    while (Date.now() < endTime) {
      const trackId = SAMPLE_TRACKS[workerId % SAMPLE_TRACKS.length];
      const query = SEARCH_QUERIES[workerId % SEARCH_QUERIES.length];

      // Simulate realistic weighted client behavior:
      // 50% Stream Range chunks, 30% Search/Suggestions, 20% Metadata/Health
      const rand = Math.random();
      let res: { status: number; durationMs: number; bytes: number };

      if (rand < 0.50) {
        // Stream initial chunk or seek range chunk
        const isInitial = Math.random() < 0.5;
        const range = isInitial ? "bytes=0-65535" : "bytes=65536-524287";
        res = await makeRequest(`${TARGET_URL}/stream?id=${trackId}`, { Range: range });
      } else if (rand < 0.80) {
        res = await makeRequest(`${TARGET_URL}/search?query=${encodeURIComponent(query)}`);
      } else {
        res = await makeRequest(`${TARGET_URL}/song?id=${trackId}`);
      }

      totalRequests++;
      latencies.push(res.durationMs);
      totalBytes += res.bytes;

      if (res.status >= 200 && res.status < 400) {
        successfulRequests++;
      } else {
        failedRequests++;
      }

      // Small jitter between requests in session (10-50ms)
      await new Promise((r) => setTimeout(r, 10 + Math.floor(Math.random() * 40)));
    }
  }

  // Launch workers
  const workers = Array.from({ length: concurrency }, (_, i) => worker(i));
  await Promise.all(workers);

  // Compute Latency Percentiles
  latencies.sort((a, b) => a - b);
  const n = latencies.length;
  const p50 = n > 0 ? latencies[Math.floor(n * 0.5)] : 0;
  const p95 = n > 0 ? latencies[Math.floor(n * 0.95)] : 0;
  const p99 = n > 0 ? latencies[Math.floor(n * 0.99)] : 0;
  const avg = n > 0 ? Math.round(latencies.reduce((a, b) => a + b, 0) / n) : 0;
  const successRate = totalRequests > 0 ? ((successfulRequests / totalRequests) * 100).toFixed(2) : "0.00";
  const rps = (totalRequests / durationSeconds).toFixed(1);
  const mbTransfer = (totalBytes / (1024 * 1024)).toFixed(2);

  console.log(`\n📊 STAGE RESULTS [${concurrency} CONCURRENT SESSIONS]:`);
  console.log(`   - Throughput:           ${rps} requests/sec (${totalRequests} total)`);
  console.log(`   - Success Rate:         ${successRate}% (${successfulRequests} ok / ${failedRequests} fail)`);
  console.log(`   - Latency (P50):        ${p50} ms`);
  console.log(`   - Latency (P95):        ${p95} ms`);
  console.log(`   - Latency (P99):        ${p99} ms`);
  console.log(`   - Latency (Avg):        ${avg} ms`);
  console.log(`   - Data Transferred:     ${mbTransfer} MB`);

  return {
    concurrency,
    rps: parseFloat(rps),
    successRate: parseFloat(successRate),
    p50,
    p95,
    p99,
    avg
  };
}

async function main() {
  console.log("Musync Load & Concurrency Benchmark Suite");
  console.log(`Target: ${TARGET_URL}\n`);

  // Check target reachability
  const ping = await makeRequest(`${TARGET_URL}/health`);
  if (ping.status !== 200) {
    console.error(`Target ${TARGET_URL}/health unreachable (Status: ${ping.status}). Make sure backend server is running.`);
    process.exit(1);
  }

  const results: any[] = [];
  for (const stage of STAGES) {
    const res = await runStage(stage.concurrency, stage.durationSeconds);
    results.push(res);
    // Cool-down between stages
    await new Promise((r) => setTimeout(r, 1000));
  }

  console.log("\n======================================================");
  console.log("🏁 SUMMARY OF ALL CONCURRENCY STAGES (Up to 2,500 Target)");
  console.log("======================================================");
  console.table(results);

  // Fetch backend metrics & cache stats
  const metricsRes = await makeRequest(`${TARGET_URL}/metrics`);
  console.log("\n📈 Server Internal Telemetry:");
  console.log(metricsRes.bytes > 0 ? "Server metrics collected successfully." : "No metrics payload.");
}

main().catch(console.error);
