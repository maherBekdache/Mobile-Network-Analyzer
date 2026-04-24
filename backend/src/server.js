import http from "node:http";
import { fileURLToPath } from "node:url";
import cors from "cors";
import express from "express";
import { Server } from "socket.io";
import { openDatabase, markInactiveDevices } from "./db.js";
import {
  normalizeNetworkGeneration,
  normalizeOperator,
  parseNumber,
  parseString,
  toIsoTimestamp
} from "./normalizers.js";
import { buildMeasurementWhere, getDeviceStats, getSummaryStats } from "./stats.js";

const PORT = Number(process.env.PORT || 8080);
const ACTIVE_WINDOW_SECONDS = Number(process.env.ACTIVE_DEVICE_WINDOW_SECONDS || 35);

function clientIp(req) {
  const forwarded = req.headers["x-forwarded-for"];
  if (forwarded) return String(forwarded).split(",")[0].trim();
  return req.socket.remoteAddress;
}

function validateMeasurement(body) {
  const errors = [];
  const deviceId = parseString(body.deviceId);
  if (!deviceId) errors.push("deviceId is required");

  const rawOperator = parseString(body.operator) ?? "unknown";
  const operator = normalizeOperator(rawOperator);
  if (!["alfa", "touch"].includes(operator)) {
    errors.push("Only Alfa and Touch measurements are accepted");
  }
  const networkGeneration = normalizeNetworkGeneration(body.networkGeneration, body.rawNetworkType);
  const clientTimestamp = toIsoTimestamp(body.timestamp ?? body.clientTimestamp ?? new Date());

  return {
    errors,
    value: {
      device_id: deviceId,
      session_id: parseString(body.sessionId),
      operator,
      raw_operator: rawOperator,
      mcc: parseString(body.mcc),
      mnc: parseString(body.mnc),
      network_generation: networkGeneration,
      raw_network_type: parseString(body.rawNetworkType),
      signal_power_dbm: parseNumber(body.signalPowerDbm),
      snr_db: parseNumber(body.snrDb),
      sinr_db: parseNumber(body.sinrDb),
      cell_id: parseString(body.cellId),
      pci: parseString(body.pci),
      psc: parseString(body.psc),
      tac: parseString(body.tac),
      lac: parseString(body.lac),
      channel_number: parseNumber(body.channelNumber),
      frequency_band: parseString(body.frequencyBand),
      arfcn: parseNumber(body.arfcn),
      uarfcn: parseNumber(body.uarfcn),
      earfcn: parseNumber(body.earfcn),
      nrarfcn: parseNumber(body.nrarfcn),
      latitude: parseNumber(body.latitude),
      longitude: parseNumber(body.longitude),
      accuracy_m: parseNumber(body.accuracyM),
      client_timestamp: clientTimestamp
    }
  };
}

async function upsertDevice(db, measurement, ip) {
  const now = new Date().toISOString();
  await db.run(
    `INSERT INTO devices (device_id, display_name, first_seen, last_seen, last_ip, last_session_id, is_active)
     VALUES (?, ?, ?, ?, ?, ?, 1)
     ON CONFLICT(device_id) DO UPDATE SET
       last_seen = excluded.last_seen,
       last_ip = excluded.last_ip,
       last_session_id = excluded.last_session_id,
       is_active = 1`,
    measurement.device_id,
    measurement.device_id,
    now,
    now,
    ip,
    measurement.session_id
  );

  if (measurement.session_id) {
    await db.run(
      `INSERT INTO connections (device_id, session_id, ip_address, connected_at, last_seen)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(device_id, session_id) DO UPDATE SET
         last_seen = excluded.last_seen,
         ip_address = excluded.ip_address`,
      measurement.device_id,
      measurement.session_id,
      ip,
      now,
      now
    );
  }
}

async function insertMeasurement(db, measurement, ip) {
  const now = new Date().toISOString();
  const result = await db.run(
    `INSERT INTO measurements (
       device_id, session_id, operator, raw_operator, mcc, mnc, network_generation, raw_network_type,
       signal_power_dbm, snr_db, sinr_db, cell_id, pci, psc, tac, lac, channel_number, frequency_band,
       arfcn, uarfcn, earfcn, nrarfcn, latitude, longitude, accuracy_m, client_timestamp,
       server_timestamp, source_ip, created_at
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    measurement.device_id,
    measurement.session_id,
    measurement.operator,
    measurement.raw_operator,
    measurement.mcc,
    measurement.mnc,
    measurement.network_generation,
    measurement.raw_network_type,
    measurement.signal_power_dbm,
    measurement.snr_db,
    measurement.sinr_db,
    measurement.cell_id,
    measurement.pci,
    measurement.psc,
    measurement.tac,
    measurement.lac,
    measurement.channel_number,
    measurement.frequency_band,
    measurement.arfcn,
    measurement.uarfcn,
    measurement.earfcn,
    measurement.nrarfcn,
    measurement.latitude,
    measurement.longitude,
    measurement.accuracy_m,
    measurement.client_timestamp,
    now,
    ip,
    now
  );
  return db.get("SELECT * FROM measurements WHERE id = ?", result.lastID);
}

export async function createApp({ db = null } = {}) {
  const database = db ?? (await openDatabase());
  const app = express();
  const server = http.createServer(app);
  const io = new Server(server, {
    cors: { origin: process.env.CORS_ORIGIN || "*" }
  });

  app.use(cors({ origin: process.env.CORS_ORIGIN || "*" }));
  app.use(express.json({ limit: "128kb" }));

  async function broadcastState() {
    await markInactiveDevices(database, ACTIVE_WINDOW_SECONDS);
    const [summary, devices] = await Promise.all([
      getSummaryStats(database),
      database.all("SELECT * FROM devices ORDER BY is_active DESC, last_seen DESC")
    ]);
    io.emit("stats:update", summary);
    io.emit("devices:update", devices);
  }

  io.on("connection", async (socket) => {
    socket.emit("server:hello", { timestamp: new Date().toISOString() });
    socket.on("device:heartbeat", async ({ deviceId, sessionId } = {}) => {
      if (!deviceId) return;
      await upsertDevice(database, { device_id: deviceId, session_id: sessionId }, socket.handshake.address);
      await broadcastState();
    });
  });

  app.get("/api/health", async (_req, res) => {
    const row = await database.get("SELECT COUNT(*) AS samples FROM measurements");
    res.json({
      ok: true,
      samples: row.samples,
      timestamp: new Date().toISOString()
    });
  });

  app.post("/api/measurements", async (req, res) => {
    const { errors, value } = validateMeasurement(req.body);
    if (errors.length) {
      res.status(400).json({ ok: false, errors });
      return;
    }

    const ip = clientIp(req);
    await upsertDevice(database, value, ip);
    const inserted = await insertMeasurement(database, value, ip);
    res.status(201).json({ ok: true, measurement: inserted });

    io.emit("measurement:new", inserted);
    await broadcastState();
  });

  app.get("/api/measurements", async (req, res) => {
    const { where, params } = buildMeasurementWhere(req.query);
    const totalRow = await database.get(`SELECT COUNT(*) AS total FROM measurements ${where}`, params);
    const wantsAll = req.query.all === "true" || req.query.limit === "all";
    const rows = wantsAll
      ? await database.all(
          `SELECT * FROM measurements ${where}
           ORDER BY client_timestamp DESC, id DESC`,
          params
        )
      : await database.all(
          `SELECT * FROM measurements ${where}
           ORDER BY client_timestamp DESC, id DESC
           LIMIT ?`,
          [...params, Math.min(Number(req.query.limit || 250), 1000)]
        );
    res.json({ rows, total: totalRow?.total ?? rows.length });
  });

  app.get("/api/stats/summary", async (req, res) => {
    res.json(await getSummaryStats(database, req.query));
  });

  app.get("/api/stats/devices", async (req, res) => {
    res.json({ rows: await getDeviceStats(database, req.query) });
  });

  app.get("/api/devices", async (_req, res) => {
    await markInactiveDevices(database, ACTIVE_WINDOW_SECONDS);
    const rows = await database.all("SELECT * FROM devices ORDER BY is_active DESC, last_seen DESC");
    res.json({ rows });
  });

  app.use((err, _req, res, _next) => {
    console.error(err);
    res.status(500).json({ ok: false, error: "Internal server error" });
  });

  return { app, server, io, db: database };
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];

if (isMain) {
  const { server } = await createApp();
  server.listen(PORT, "0.0.0.0", () => {
    console.log(`Mobile Network Analyzer backend listening on http://0.0.0.0:${PORT}`);
  });
}
