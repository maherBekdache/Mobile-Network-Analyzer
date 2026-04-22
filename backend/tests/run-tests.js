import assert from "node:assert/strict";
import { createApp } from "../src/server.js";
import { openDatabase } from "../src/db.js";
import { getSummaryStats } from "../src/stats.js";

async function memoryApp() {
  const db = await openDatabase(":memory:");
  return createApp({ db });
}

async function send(app, path, body) {
  const server = app.listen(0);
  const port = server.address().port;
  try {
    const response = await fetch(`http://127.0.0.1:${port}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    const json = await response.json();
    return { status: response.status, body: json };
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

async function get(app, path) {
  const server = app.listen(0);
  const port = server.address().port;
  try {
    const response = await fetch(`http://127.0.0.1:${port}${path}`);
    const json = await response.json();
    return { status: response.status, body: json };
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

async function testMalformedRejection() {
  const { app, io, db } = await memoryApp();
  const response = await send(app, "/api/measurements", { operator: "alfa" });
  assert.equal(response.status, 400);
  assert.match(response.body.errors[0], /deviceId/);
  io.close();
  await db.close();
}

async function testRatios() {
  const { app, io, db } = await memoryApp();
  const samples = [
    { deviceId: "phone-a", sessionId: "a1", operator: "Alfa", rawNetworkType: "LTE", signalPowerDbm: -85, snrDb: 12, cellId: "100", timestamp: "2026-04-22T10:00:00Z" },
    { deviceId: "phone-b", sessionId: "b1", operator: "touch", rawNetworkType: "UMTS", signalPowerDbm: -95, snrDb: 5, cellId: "200", timestamp: "2026-04-22T10:00:10Z" },
    { deviceId: "phone-a", sessionId: "a1", operator: "MTC Touch", rawNetworkType: "EDGE", signalPowerDbm: -101, cellId: "201", timestamp: "2026-04-22T10:00:20Z" }
  ];

  await Promise.all(samples.map((sample) => send(app, "/api/measurements", sample)));
  const stats = await getSummaryStats(db);

  assert.equal(stats.totalSamples, 3);
  assert.equal(stats.operatorRatios.alfa.count, 1);
  assert.equal(stats.operatorRatios.touch.count, 2);
  assert.equal(stats.networkRatios["4G"].count, 1);
  assert.equal(stats.networkRatios["3G"].count, 1);
  assert.equal(stats.networkRatios["2G"].count, 1);
  assert.equal(stats.overall.distinctDevices, 2);

  io.close();
  await db.close();
}

async function testDateFiltering() {
  const { app, io, db } = await memoryApp();
  await send(app, "/api/measurements", { deviceId: "a", operator: "alfa", networkGeneration: "4G", signalPowerDbm: -70, timestamp: "2026-04-22T09:00:00Z" });
  await send(app, "/api/measurements", { deviceId: "a", operator: "touch", networkGeneration: "4G", signalPowerDbm: -90, timestamp: "2026-04-22T11:00:00Z" });

  const response = await get(app, "/api/stats/summary?from=2026-04-22T10:00:00Z&to=2026-04-22T12:00:00Z");
  assert.equal(response.status, 200);
  assert.equal(response.body.totalSamples, 1);
  assert.equal(response.body.operatorRatios.touch.count, 1);

  io.close();
  await db.close();
}

const tests = [
  ["rejects malformed measurements", testMalformedRejection],
  ["stores measurements and normalizes ratios", testRatios],
  ["filters statistics by date window", testDateFiltering]
];

for (const [name, fn] of tests) {
  await fn();
  console.log(`PASS ${name}`);
}

console.log(`${tests.length} backend tests passed`);
