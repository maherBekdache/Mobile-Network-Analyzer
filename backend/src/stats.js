import { NETWORK_GENERATIONS } from "./normalizers.js";

const FILTER_COLUMNS = {
  operator: "operator",
  networkGeneration: "network_generation",
  deviceId: "device_id",
  cellId: "cell_id"
};

export function buildMeasurementWhere(query = {}) {
  const clauses = [];
  const params = [];

  if (query.from) {
    clauses.push("client_timestamp >= ?");
    params.push(new Date(query.from).toISOString());
  }
  if (query.to) {
    clauses.push("client_timestamp <= ?");
    params.push(new Date(query.to).toISOString());
  }

  for (const [queryKey, column] of Object.entries(FILTER_COLUMNS)) {
    if (query[queryKey]) {
      clauses.push(`${column} = ?`);
      params.push(String(query[queryKey]));
    }
  }

  if (query.minPower !== undefined && query.minPower !== "") {
    clauses.push("signal_power_dbm >= ?");
    params.push(Number(query.minPower));
  }
  if (query.maxPower !== undefined && query.maxPower !== "") {
    clauses.push("signal_power_dbm <= ?");
    params.push(Number(query.maxPower));
  }

  return {
    where: clauses.length ? `WHERE ${clauses.join(" AND ")}` : "",
    params
  };
}

function ratioRows(rows, total) {
  const ratios = {};
  for (const row of rows) {
    ratios[row.label] = {
      count: row.count,
      percentage: total ? Number(((row.count / total) * 100).toFixed(2)) : 0
    };
  }
  return ratios;
}

async function groupedCounts(db, column, where, params) {
  return db.all(
    `SELECT ${column} AS label, COUNT(*) AS count
     FROM measurements
     ${where}
     GROUP BY ${column}
     ORDER BY count DESC`,
    params
  );
}

async function groupedAverage(db, column, metric, where, params) {
  return db.all(
    `SELECT ${column} AS label, ROUND(AVG(${metric}), 2) AS average, COUNT(${metric}) AS samples
     FROM measurements
     ${where} ${where ? "AND" : "WHERE"} ${metric} IS NOT NULL
     GROUP BY ${column}
     ORDER BY label ASC`,
    params
  );
}

export async function getSummaryStats(db, query = {}) {
  const { where, params } = buildMeasurementWhere(query);
  const totalRow = await db.get(`SELECT COUNT(*) AS total FROM measurements ${where}`, params);
  const total = totalRow?.total ?? 0;
  const latest = await db.get(
    `SELECT * FROM measurements ${where} ORDER BY client_timestamp DESC, id DESC LIMIT 1`,
    params
  );

  const operatorRows = await groupedCounts(db, "operator", where, params);
  const generationRows = await groupedCounts(db, "network_generation", where, params);
  const powerByGeneration = await groupedAverage(db, "network_generation", "signal_power_dbm", where, params);
  const snrByGeneration = await groupedAverage(db, "network_generation", "snr_db", where, params);
  const sinrByGeneration = await groupedAverage(db, "network_generation", "sinr_db", where, params);
  const powerByDevice = await groupedAverage(db, "device_id", "signal_power_dbm", where, params);
  const overall = await db.get(
    `SELECT
       ROUND(AVG(signal_power_dbm), 2) AS averagePowerDbm,
       ROUND(AVG(snr_db), 2) AS averageSnrDb,
       ROUND(AVG(sinr_db), 2) AS averageSinrDb,
       COUNT(DISTINCT device_id) AS distinctDevices,
       COUNT(DISTINCT cell_id) AS distinctCells
     FROM measurements ${where}`,
    params
  );

  const generationRatios = ratioRows(generationRows, total);
  for (const generation of NETWORK_GENERATIONS) {
    generationRatios[generation] ??= { count: 0, percentage: 0 };
  }

  return {
    totalSamples: total,
    latest,
    operatorRatios: ratioRows(operatorRows, total),
    alfaTouchRatio: {
      alfa: operatorRows.find((row) => row.label === "alfa")?.count ?? 0,
      touch: operatorRows.find((row) => row.label === "touch")?.count ?? 0
    },
    networkRatios: generationRatios,
    powerByGeneration,
    snrByGeneration,
    sinrByGeneration,
    powerByDevice,
    overall: overall ?? {
      averagePowerDbm: null,
      averageSnrDb: null,
      averageSinrDb: null,
      distinctDevices: 0,
      distinctCells: 0
    }
  };
}

export async function getDeviceStats(db, query = {}) {
  const { where, params } = buildMeasurementWhere(query);
  return db.all(
    `SELECT
       d.device_id AS deviceId,
       d.display_name AS displayName,
       d.first_seen AS firstSeen,
       d.last_seen AS lastSeen,
       d.last_ip AS lastIp,
       d.is_active AS isActive,
       COUNT(m.id) AS totalSamples,
       ROUND(AVG(m.signal_power_dbm), 2) AS averagePowerDbm,
       ROUND(AVG(m.snr_db), 2) AS averageSnrDb,
       ROUND(AVG(m.sinr_db), 2) AS averageSinrDb,
       COUNT(DISTINCT m.cell_id) AS distinctCells
     FROM devices d
     LEFT JOIN measurements m ON m.device_id = d.device_id
     ${where ? where.replaceAll("client_timestamp", "m.client_timestamp").replaceAll("operator", "m.operator").replaceAll("network_generation", "m.network_generation").replaceAll("device_id", "m.device_id").replaceAll("cell_id", "m.cell_id").replaceAll("signal_power_dbm", "m.signal_power_dbm") : ""}
     GROUP BY d.device_id
     ORDER BY d.is_active DESC, d.last_seen DESC`,
    params
  );
}
