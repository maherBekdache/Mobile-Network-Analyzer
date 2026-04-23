import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { io } from "socket.io-client";
import {
  Activity,
  Antenna,
  BarChart3,
  Clock3,
  Database,
  Filter,
  Info,
  Radio,
  RefreshCw,
  SignalHigh,
  Smartphone,
  Wifi
} from "lucide-react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts";
import "./styles.css";

const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";
const COLORS = ["#14b8a6", "#f97316", "#6366f1", "#eab308", "#ef4444"];

const POWER_TIERS = [
  { min: -80, label: "Excellent", className: "excellent", hint: "Very strong signal" },
  { min: -90, label: "Good", className: "good", hint: "Reliable signal" },
  { min: -100, label: "Medium", className: "medium", hint: "Usable but weaker" },
  { min: -110, label: "Bad", className: "bad", hint: "Weak signal" },
  { min: -Infinity, label: "Very Bad", className: "very-bad", hint: "Likely unstable" }
];

function qs(filters) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== "" && value !== null && value !== undefined) params.set(key, value);
  });
  return params.toString();
}

async function getJson(path, filters = {}) {
  const query = qs(filters);
  const response = await fetch(`${API_BASE}${path}${query ? `?${query}` : ""}`);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
}

function percent(value) {
  if (value === null || value === undefined || Number.isNaN(value)) return "0%";
  return `${Number(value).toFixed(1)}%`;
}

function metric(value, suffix = "") {
  if (value === null || value === undefined || Number.isNaN(value)) return "N/A";
  return `${value}${suffix}`;
}

function powerTier(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return { label: "Unknown", className: "unknown", hint: "Not reported" };
  }
  return POWER_TIERS.find((tier) => Number(value) >= tier.min);
}

function noiseTier(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return { label: "Not Reported", className: "unknown", hint: "Device/network did not expose SNR/SINR" };
  }
  if (value >= 20) return { label: "Excellent", className: "excellent", hint: "Very clean link" };
  if (value >= 13) return { label: "Good", className: "good", hint: "Clean link" };
  if (value >= 5) return { label: "Medium", className: "medium", hint: "Moderate noise" };
  if (value >= 0) return { label: "Bad", className: "bad", hint: "Noisy link" };
  return { label: "Very Bad", className: "very-bad", hint: "High interference" };
}

function QualityBadge({ value, type = "power" }) {
  const tier = type === "noise" ? noiseTier(value) : powerTier(value);
  return <span className={`quality-badge ${tier.className}`}>{tier.label}</span>;
}

function StatCard({ icon: Icon, label, value, hint }) {
  return (
    <article className="stat-card">
      <div className="stat-icon"><Icon size={20} /></div>
      <div>
        <p>{label}</p>
        <strong>{value}</strong>
        {hint && <span>{hint}</span>}
      </div>
    </article>
  );
}

function ChartPanel({ title, icon: Icon, children, compact = false }) {
  return (
    <section className={compact ? "panel compact-panel" : "panel"}>
      <header className="panel-title">
        <Icon size={18} />
        <h2>{title}</h2>
      </header>
      <div className="chart-box">{children}</div>
    </section>
  );
}

function App() {
  const [filters, setFilters] = useState({
    from: "",
    to: "",
    operator: "",
    networkGeneration: "",
    minPower: "",
    maxPower: "",
    cellId: "",
    deviceId: ""
  });
  const [summary, setSummary] = useState(null);
  const [measurements, setMeasurements] = useState([]);
  const [devices, setDevices] = useState([]);
  const [deviceStats, setDeviceStats] = useState([]);
  const [status, setStatus] = useState("Connecting");
  const [lastRefresh, setLastRefresh] = useState(null);

  async function load() {
    setStatus("Refreshing");
    const [nextSummary, measurementResult, deviceResult, deviceStatsResult] = await Promise.all([
      getJson("/api/stats/summary", filters),
      getJson("/api/measurements", { ...filters, limit: 250 }),
      getJson("/api/devices"),
      getJson("/api/stats/devices", filters)
    ]);
    setSummary(nextSummary);
    setMeasurements(measurementResult.rows);
    setDevices(deviceResult.rows);
    setDeviceStats(deviceStatsResult.rows);
    setLastRefresh(new Date());
    setStatus("Live");
  }

  useEffect(() => {
    load().catch((error) => setStatus(error.message));
  }, []);

  useEffect(() => {
    const socket = io(API_BASE, { transports: ["websocket", "polling"] });
    socket.on("connect", () => setStatus("Live"));
    socket.on("disconnect", () => setStatus("Disconnected"));
    socket.on("measurement:new", (measurement) => {
      setMeasurements((rows) => [measurement, ...rows].slice(0, 250));
    });
    socket.on("stats:update", setSummary);
    socket.on("devices:update", setDevices);
    return () => socket.close();
  }, []);

  const operatorData = useMemo(() => {
    const ratios = summary?.operatorRatios || {};
    return Object.entries(ratios).map(([name, value]) => ({
      name,
      value: value.count,
      percentage: value.percentage
    }));
  }, [summary]);

  const generationData = useMemo(() => {
    const ratios = summary?.networkRatios || {};
    return Object.entries(ratios).map(([name, value]) => ({
      name,
      value: value.count,
      percentage: value.percentage
    }));
  }, [summary]);

  const operatorQualityData = useMemo(() => {
    return (summary?.operatorQuality || []).map((row) => ({
      operator: row.label,
      averagePowerDbm: row.averagePowerDbm,
      averageNoiseDb: row.averageNoiseDb,
      samples: row.samples,
      powerTier: powerTier(row.averagePowerDbm).label,
      noiseTier: noiseTier(row.averageNoiseDb).label
    }));
  }, [summary]);

  const timeline = [...measurements]
    .reverse()
    .slice(-60)
    .map((row) => ({
      time: new Date(row.client_timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
      power: row.signal_power_dbm,
      snr: row.snr_db ?? row.sinr_db
    }));

  const activeDevices = devices.filter((device) => device.is_active).length;
  const alfa = summary?.operatorRatios?.alfa?.percentage ?? 0;
  const touch = summary?.operatorRatios?.touch?.percentage ?? 0;
  const latestPower = summary?.latest?.signal_power_dbm ?? summary?.overall?.averagePowerDbm;
  const latestTier = powerTier(latestPower);

  return (
    <main>
      <section className="hero">
        <div>
          <p className="eyebrow">EECE451 Distributed Cellular Measurements</p>
          <h1>Mobile Network Analyzer</h1>
          <p className="hero-copy">
            Live Alfa/Touch coverage, radio generation ratios, signal quality trends, connected devices,
            and server-side statistics from every phone streaming samples.
          </p>
          <div className="explain-strip">
            <Info size={16} />
            dBm is signal power: values closer to 0 are stronger. For example, -75 dBm is much better than -105 dBm.
            SNR/SINR appears only when Android, the modem, and the current radio technology report it.
          </div>
        </div>
        <div className="hero-status">
          <span className={status === "Live" ? "pulse" : "pulse offline"} />
          <strong>{status}</strong>
          <small>{lastRefresh ? `Updated ${lastRefresh.toLocaleTimeString()}` : "Waiting for server"}</small>
        </div>
      </section>

      <section className="toolbar">
        <label>
          <Clock3 size={16} />
          <input type="datetime-local" value={filters.from} onChange={(event) => setFilters({ ...filters, from: event.target.value })} />
        </label>
        <label>
          <Clock3 size={16} />
          <input type="datetime-local" value={filters.to} onChange={(event) => setFilters({ ...filters, to: event.target.value })} />
        </label>
        <select value={filters.operator} onChange={(event) => setFilters({ ...filters, operator: event.target.value })}>
          <option value="">All operators</option>
          <option value="alfa">Alfa</option>
          <option value="touch">Touch</option>
          <option value="unknown">Unknown</option>
        </select>
        <select value={filters.networkGeneration} onChange={(event) => setFilters({ ...filters, networkGeneration: event.target.value })}>
          <option value="">All generations</option>
          <option value="2G">2G</option>
          <option value="3G">3G</option>
          <option value="4G">4G</option>
          <option value="5G">5G</option>
          <option value="unknown">Unknown</option>
        </select>
        <input placeholder="Min dBm" value={filters.minPower} onChange={(event) => setFilters({ ...filters, minPower: event.target.value })} />
        <input placeholder="Max dBm" value={filters.maxPower} onChange={(event) => setFilters({ ...filters, maxPower: event.target.value })} />
        <input placeholder="Cell ID" value={filters.cellId} onChange={(event) => setFilters({ ...filters, cellId: event.target.value })} />
        <input placeholder="Device ID" value={filters.deviceId} onChange={(event) => setFilters({ ...filters, deviceId: event.target.value })} />
        <button onClick={() => load().catch((error) => setStatus(error.message))}>
          <RefreshCw size={16} />
          Refresh
        </button>
      </section>

      <section className="stats-grid">
        <StatCard icon={Database} label="Samples" value={summary?.totalSamples ?? 0} hint={`${summary?.overall?.distinctCells ?? 0} cells`} />
        <StatCard icon={Smartphone} label="Devices" value={devices.length} hint={`${activeDevices} active now`} />
        <StatCard icon={Radio} label="Alfa / Touch" value={`${percent(alfa)} / ${percent(touch)}`} hint={`${summary?.alfaTouchRatio?.alfa ?? 0}:${summary?.alfaTouchRatio?.touch ?? 0} samples`} />
        <StatCard icon={Antenna} label="Avg Power" value={metric(summary?.overall?.averagePowerDbm, " dBm")} hint="Filtered average" />
        <StatCard icon={Wifi} label="Avg SNR" value={metric(summary?.overall?.averageSnrDb ?? summary?.overall?.averageSinrDb, " dB")} hint="When available" />
        <StatCard icon={SignalHigh} label="Signal Tier" value={latestTier.label} hint={latestTier.hint} />
      </section>

      <section className="dashboard-grid">
        <ChartPanel title="Signal Power Timeline" icon={Activity}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={timeline}>
              <CartesianGrid strokeDasharray="3 3" stroke="#d9e2ea" />
              <XAxis dataKey="time" stroke="#536271" />
              <YAxis stroke="#536271" domain={["dataMin - 5", "dataMax + 5"]} />
              <Tooltip />
              <Line type="monotone" dataKey="power" stroke="#14b8a6" strokeWidth={3} dot={false} />
              <Line type="monotone" dataKey="snr" stroke="#f97316" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartPanel>

        <ChartPanel title="Alfa vs Touch Signal Quality" icon={SignalHigh}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={operatorQualityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#d9e2ea" />
              <XAxis dataKey="operator" />
              <YAxis />
              <Tooltip formatter={(value, name, item) => {
                const unit = name === "averagePowerDbm" ? " dBm" : " dB";
                const tier = name === "averagePowerDbm" ? item.payload.powerTier : item.payload.noiseTier;
                return [`${value ?? "N/A"}${unit} (${tier})`, name === "averagePowerDbm" ? "Avg power" : "Avg SNR/SINR"];
              }} />
              <Bar dataKey="averagePowerDbm" fill="#14b8a6" radius={[6, 6, 0, 0]} />
              <Bar dataKey="averageNoiseDb" fill="#f97316" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>

        <ChartPanel title="Operator Distribution" icon={BarChart3}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie dataKey="value" data={operatorData} nameKey="name" innerRadius={54} outerRadius={88} paddingAngle={4}>
                {operatorData.map((_, index) => <Cell key={index} fill={COLORS[index % COLORS.length]} />)}
              </Pie>
              <Tooltip formatter={(value, name, item) => [`${value} samples (${item.payload.percentage}%)`, name]} />
            </PieChart>
          </ResponsiveContainer>
        </ChartPanel>

        <ChartPanel title="Generation Ratios" icon={Filter}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={generationData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#d9e2ea" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip formatter={(value, name, item) => [`${value} samples (${item.payload.percentage}%)`, name]} />
              <Bar dataKey="value" fill="#6366f1" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>

        <ChartPanel title="Average Power By User" icon={Smartphone} compact>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={deviceStats.slice(0, 12)}>
              <CartesianGrid strokeDasharray="3 3" stroke="#d9e2ea" />
              <XAxis dataKey="deviceId" hide />
              <YAxis />
              <Tooltip />
              <Area dataKey="averagePowerDbm" stroke="#14b8a6" fill="#99f6e4" />
            </AreaChart>
          </ResponsiveContainer>
        </ChartPanel>
      </section>

      <section className="tables">
        <section className="panel table-panel">
          <header className="panel-title"><Activity size={18} /><h2>Latest Samples</h2></header>
          <table>
            <thead>
              <tr><th>Time</th><th>Device</th><th>Operator</th><th>Gen</th><th>Power</th><th>SNR/SINR</th><th>Cell</th></tr>
            </thead>
            <tbody>
              {measurements.slice(0, 20).map((row) => (
                <tr key={row.id}>
                  <td>{new Date(row.client_timestamp).toLocaleString()}</td>
                  <td>{row.device_id}</td>
                  <td>{row.operator}</td>
                  <td><span className="badge">{row.network_generation}</span></td>
                  <td>{metric(row.signal_power_dbm, " dBm")} <QualityBadge value={row.signal_power_dbm} /></td>
                  <td>{metric(row.snr_db ?? row.sinr_db, " dB")} <QualityBadge value={row.snr_db ?? row.sinr_db} type="noise" /></td>
                  <td>{row.cell_id || "N/A"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="panel table-panel">
          <header className="panel-title"><Smartphone size={18} /><h2>Devices</h2></header>
          <table>
            <thead>
              <tr><th>Status</th><th>Device</th><th>Last IP</th><th>Samples</th><th>Avg Power</th><th>Tier</th></tr>
            </thead>
            <tbody>
              {deviceStats.map((row) => (
                <tr key={row.deviceId}>
                  <td><span className={row.isActive ? "status active" : "status"}>{row.isActive ? "Active" : "Seen"}</span></td>
                  <td>{row.deviceId}</td>
                  <td>{row.lastIp || "N/A"}</td>
                  <td>{row.totalSamples}</td>
                  <td>{metric(row.averagePowerDbm, " dBm")}</td>
                  <td><QualityBadge value={row.averagePowerDbm} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);
