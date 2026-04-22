import fs from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";

class NativeSqliteDb {
  constructor(filename) {
    this.database = new DatabaseSync(filename);
  }

  async exec(sql) {
    this.database.exec(sql);
  }

  async run(sql, ...params) {
    const statement = this.database.prepare(sql);
    const result = statement.run(...normalizeParams(params));
    return {
      changes: Number(result.changes ?? 0),
      lastID: Number(result.lastInsertRowid ?? 0)
    };
  }

  async get(sql, ...params) {
    const statement = this.database.prepare(sql);
    return statement.get(...normalizeParams(params));
  }

  async all(sql, params = []) {
    const statement = this.database.prepare(sql);
    return statement.all(...normalizeParams([params]));
  }

  async close() {
    this.database.close();
  }
}

function normalizeParams(params) {
  if (params.length === 1 && Array.isArray(params[0])) return params[0];
  return params;
}

export async function openDatabase(filename = process.env.DB_FILE || "./data/network-analyzer.sqlite") {
  const resolved = filename === ":memory:" ? filename : path.resolve(filename);
  if (resolved !== ":memory:") fs.mkdirSync(path.dirname(resolved), { recursive: true });

  const db = new NativeSqliteDb(resolved);
  if (resolved !== ":memory:") await db.exec("PRAGMA journal_mode = WAL;");
  await db.exec("PRAGMA foreign_keys = ON;");
  await migrate(db);
  return db;
}

async function migrate(db) {
  await db.exec(`
    CREATE TABLE IF NOT EXISTS devices (
      device_id TEXT PRIMARY KEY,
      display_name TEXT,
      first_seen TEXT NOT NULL,
      last_seen TEXT NOT NULL,
      last_ip TEXT,
      last_session_id TEXT,
      is_active INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS connections (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT NOT NULL,
      session_id TEXT NOT NULL,
      ip_address TEXT,
      connected_at TEXT NOT NULL,
      last_seen TEXT NOT NULL,
      disconnected_at TEXT,
      UNIQUE(device_id, session_id),
      FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS measurements (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT NOT NULL,
      session_id TEXT,
      operator TEXT NOT NULL,
      raw_operator TEXT,
      mcc TEXT,
      mnc TEXT,
      network_generation TEXT NOT NULL,
      raw_network_type TEXT,
      signal_power_dbm REAL,
      snr_db REAL,
      sinr_db REAL,
      cell_id TEXT,
      pci TEXT,
      psc TEXT,
      tac TEXT,
      lac TEXT,
      channel_number INTEGER,
      frequency_band TEXT,
      arfcn INTEGER,
      uarfcn INTEGER,
      earfcn INTEGER,
      nrarfcn INTEGER,
      latitude REAL,
      longitude REAL,
      accuracy_m REAL,
      client_timestamp TEXT NOT NULL,
      server_timestamp TEXT NOT NULL,
      source_ip TEXT,
      created_at TEXT NOT NULL,
      FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_measurements_time ON measurements(client_timestamp);
    CREATE INDEX IF NOT EXISTS idx_measurements_operator ON measurements(operator);
    CREATE INDEX IF NOT EXISTS idx_measurements_generation ON measurements(network_generation);
    CREATE INDEX IF NOT EXISTS idx_measurements_device ON measurements(device_id);
    CREATE INDEX IF NOT EXISTS idx_measurements_cell ON measurements(cell_id);
  `);
}

export async function markInactiveDevices(db, activeWindowSeconds) {
  await db.run(
    `UPDATE devices
     SET is_active = CASE
       WHEN strftime('%s','now') - strftime('%s', last_seen) <= ? THEN 1
       ELSE 0
     END`,
    activeWindowSeconds
  );
}
