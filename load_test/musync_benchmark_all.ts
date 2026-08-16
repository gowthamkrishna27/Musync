import http from "http";
import https from "https";
import { exec, spawn, ChildProcess } from "child_process";
import { promisify } from "util";

const execAsync = promisify(exec);
const PORT = 5002;
const BASE_URL = `http://127.0.0.1:${PORT}`;

const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 10000, maxFreeSockets: 512 });

async function makeRequest(
  path: string,
  headers: Record<string, string> = {},
  timeoutMs = 15000
): Promise<{ status: number; durationMs: number; bytes: number; headers: http.IncomingHttpHeaders }> {
  const start = Date.now();
  return new Promise((resolve) => {
    const req = http.request(
      `${BASE_URL}${path}`,
      {
        method: "GET",
        headers: {
          "User-Agent": "Musync-Benchmark/1.0",
          ...headers
        },
        agent: httpAgent,
        timeout: timeoutMs
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
            bytes,
            headers: res.headers
          });
        });
      }
    );

    req.on("error", () => {
      resolve({
        status: 0,
        durationMs: Date.now() - start,
        bytes: 0,
        headers: {}
      });
    });

    req.on("timeout", () => {
      req.destroy();
      resolve({
        status: 408,
        durationMs: Date.now() - start,
        bytes: 0,
        headers: {}
      });
    });

    req.end();
  });
}

async function runConcurrencyStage(concurrency: number, durationSeconds: number) {
  const sampleTracks = ["dQw4w9WgXcQ", "3_g2un5M350", "kJQP7kiw5Fk"];
  let totalReqs = 0;
  let successReqs = 0;
  let failedReqs = 0;
  let totalBytes = 0;
  const latencies: number[] = [];
  const endTime = Date.now() + durationSeconds * 1000;

  async function worker(id: number) {
    while (Date.now() < endTime) {
      const trackId = sampleTracks[id % sampleTracks.length];
      const range = (id % 2 === 0) ? "bytes=0-65535" : "bytes=65536-262143";
      const res = await makeRequest(`/stream?id=${trackId}`, { Range: range });
      totalReqs++;
      totalBytes += res.bytes;
      latencies.push(res.durationMs);
      if (res.status === 200 || res.status === 206) {
        successReqs++;
      } else {
        failedReqs++;
      }
      await new Promise((r) => setTimeout(r, 10));
    }
  }

  const workers = Array.from({ length: concurrency }, (_, i) => worker(i));
  await Promise.all(workers);

  latencies.sort((a, b) => a - b);
  const n = latencies.length;
  const p50 = n > 0 ? latencies[Math.floor(n * 0.5)] : 0;
  const p95 = n > 0 ? latencies[Math.floor(n * 0.95)] : 0;
  const avg = n > 0 ? Math.round(latencies.reduce((a, b) => a + b, 0) / n) : 0;
  const rps = (totalReqs / durationSeconds).toFixed(1);
  const successRate = totalReqs > 0 ? ((successReqs / totalReqs) * 100).toFixed(1) : "0.0";

  return {
    concurrency,
    rps: parseFloat(rps),
    successRate: `${successRate}%`,
    p50Ms: p50,
    p95Ms: p95,
    avgMs: avg,
    mbTransferred: (totalBytes / (1024 * 1024)).toFixed(2)
  };
}

async function simulateBandwidthThrottle(bitrateKbps: number, testDurationSec = 5) {
  // We simulate a throttled connection requesting audio chunks
  const trackId = "dQw4w9WgXcQ";
  const bytesPerSec = (bitrateKbps * 1000) / 8;
  const audioBitrateKbps = 128; // Standard stream bitrate
  const audioBytesPerSec = (audioBitrateKbps * 1000) / 8;

  // Make Range request for initial audio chunk
  const t0 = Date.now();
  const res = await makeRequest(`/stream?id=${trackId}`, { Range: "bytes=0-131071" });
  const ttfb = res.durationMs;

  // Calculate buffer growth:
  // Transfer rate vs consumption rate
  const effectiveTransferKbps = Math.min(bitrateKbps, (res.bytes * 8) / (Math.max(1, res.durationMs) * 1.0));
  const throughputRatio = bitrateKbps / audioBitrateKbps;
  const willRebuffer = throughputRatio < 1.0;
  const estimatedBufferDepthSec = Math.max(0, (throughputRatio - 1.0) * testDurationSec + (res.bytes / audioBytesPerSec));

  return {
    throttleBandwidth: `${bitrateKbps} kbps`,
    ttfbMs: ttfb,
    status: res.status,
    throughputRatio: `${throughputRatio.toFixed(2)}x`,
    bufferingRisk: willRebuffer ? "YES (Depleting)" : "NO (Healthy / Growing)",
    safetyBufferDepth: `${estimatedBufferDepthSec.toFixed(1)}s`,
    recommendedAction: willRebuffer ? "Switch to Saver (48kbps Opus)" : "Standard/High Quality"
  };
}

async function main() {
  console.log("===============================================================================");
  console.log("   MUSYNC STREAMING & MULTI-USER PERFORMANCE DIAGNOSTIC BENCHMARK SUITE");
  console.log("===============================================================================\n");

  // 1. Start test server
  process.env.PORT = String(PORT);
  console.log(`Starting background server on port ${PORT}...`);
  const serverProcess: ChildProcess = spawn("npx", ["tsx", "server.ts"], {
    env: { ...process.env, PORT: String(PORT) },
    stdio: "inherit",
    shell: true
  });

  // Wait for server health check
  let ready = false;
  for (let i = 0; i < 30; i++) {
    await new Promise((r) => setTimeout(r, 1000));
    try {
      const ping = await makeRequest("/health");
      if (ping.status === 200) {
        ready = true;
        console.log("✓ Server is ready and healthy.\n");
        break;
      }
    } catch (_e) {}
  }

  if (!ready) {
    console.error("Failed to start test server within 30s.");
    serverProcess.kill();
    process.exit(1);
  }

  try {
    // 2. Test Range Requests & 206 Partial Content
    console.log("--- 1. VERIFYING HTTP RANGE REQUESTS & 206 PARTIAL CONTENT ---");
    const r1 = await makeRequest("/stream?id=dQw4w9WgXcQ", { Range: "bytes=0-65535" });
    console.log(`Range: bytes=0-65535 -> HTTP Status: ${r1.status}, Content-Range: ${r1.headers["content-range"]}, Bytes: ${r1.bytes}, TTFB: ${r1.durationMs}ms`);

    const r2 = await makeRequest("/stream?id=dQw4w9WgXcQ", { Range: "bytes=1048576-1572863" });
    console.log(`Range: bytes=1048576-1572863 -> HTTP Status: ${r2.status}, Content-Range: ${r2.headers["content-range"]}, Bytes: ${r2.bytes}, TTFB: ${r2.durationMs}ms`);

    // 3. Multi-User Load Testing (1, 10, 50, 100, 500, 1000, 2000 listeners)
    console.log("\n--- 2. MULTI-USER LISTENER BENCHMARK (1 to 2,000 Listeners) ---");
    const listenerLevels = [1, 10, 50, 100, 500, 1000, 2000];
    const concurrencyResults = [];

    for (const count of listenerLevels) {
      console.log(`Testing ${count} concurrent listener sessions (duration: 3s)...`);
      const stageRes = await runConcurrencyStage(count, 3);
      concurrencyResults.push(stageRes);
      await new Promise((r) => setTimeout(r, 500));
    }
    console.table(concurrencyResults);

    // 4. Low-Bandwidth Network Throttling Tests (32k, 64k, 96k, 128k, 256k, 512k, 1M)
    console.log("\n--- 3. NETWORK-THROTTLED BANDWIDTH SIMULATIONS ---");
    const throttles = [32, 64, 96, 128, 256, 512, 1024];
    const throttleResults = [];

    for (const bw of throttles) {
      const tRes = await simulateBandwidthThrottle(bw);
      throttleResults.push(tRes);
    }
    console.table(throttleResults);

    // 5. Query Metrics from Server
    console.log("\n--- 4. SERVER INTERNAL RESOURCE METRICS ---");
    const metrics = await makeRequest("/metrics");
    console.log("Metrics status:", metrics.status);
    console.log("Memory & Latency payload received.");

  } finally {
    console.log("\nCleaning up server process...");
    serverProcess.kill();
  }
}

main().catch(console.error);
