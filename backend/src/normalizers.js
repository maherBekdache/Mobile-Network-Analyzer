export const NETWORK_GENERATIONS = ["2G", "3G", "4G", "5G", "unknown"];

const NETWORK_ALIASES = new Map([
  ["GPRS", "2G"],
  ["EDGE", "2G"],
  ["GSM", "2G"],
  ["CDMA", "2G"],
  ["1XRTT", "2G"],
  ["IDEN", "2G"],
  ["UMTS", "3G"],
  ["HSDPA", "3G"],
  ["HSUPA", "3G"],
  ["HSPA", "3G"],
  ["HSPAP", "3G"],
  ["EVDO_0", "3G"],
  ["EVDO_A", "3G"],
  ["EVDO_B", "3G"],
  ["LTE", "4G"],
  ["LTE_CA", "4G"],
  ["NR", "5G"]
]);

export function normalizeOperator(value) {
  const text = String(value ?? "").trim().toLowerCase();
  if (text.includes("alfa") || text.includes("alpha")) return "alfa";
  if (text.includes("touch") || text.includes("mtc")) return "touch";
  return text || "unknown";
}

export function normalizeNetworkGeneration(value, rawType) {
  const direct = String(value ?? "").trim().toUpperCase();
  if (NETWORK_GENERATIONS.includes(direct)) return direct;
  if (NETWORK_ALIASES.has(direct)) return NETWORK_ALIASES.get(direct);

  const raw = String(rawType ?? "").trim().toUpperCase().replace(/^NETWORK_TYPE_/, "");
  return NETWORK_ALIASES.get(raw) ?? "unknown";
}

export function parseNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

export function parseString(value) {
  if (value === null || value === undefined) return null;
  const text = String(value).trim();
  return text.length ? text : null;
}

export function toIsoTimestamp(value = new Date()) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return new Date().toISOString();
  return date.toISOString();
}
