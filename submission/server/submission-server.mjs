import os from "node:os";
import net from "node:net";
import path from "node:path";
import fs from "node:fs";
import { exec } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";
import express from "express";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function resolveBackendRoot() {
  const bundledCandidate = __dirname;
  if (fs.existsSync(path.join(bundledCandidate, "src", "server.js"))) return bundledCandidate;

  const repoCandidate = path.resolve(__dirname, "../../backend");
  if (fs.existsSync(path.join(repoCandidate, "src", "server.js"))) return repoCandidate;

  throw new Error("Could not locate the backend files for the desktop launcher.");
}

function resolveFrontendDist(backendRoot) {
  const bundledCandidate = path.resolve(backendRoot, "../frontend-dist");
  if (fs.existsSync(path.join(bundledCandidate, "index.html"))) return bundledCandidate;

  const repoCandidate = path.resolve(__dirname, "../../frontend/dist");
  if (fs.existsSync(path.join(repoCandidate, "index.html"))) return repoCandidate;

  throw new Error("Could not locate the built dashboard files for the desktop launcher.");
}

const BACKEND_ROOT = resolveBackendRoot();
const FRONTEND_DIST = resolveFrontendDist(BACKEND_ROOT);
const backendModuleUrl = pathToFileURL(path.join(BACKEND_ROOT, "src", "server.js")).href;
const { createApp } = await import(backendModuleUrl);

function networkUrls(port) {
  const interfaces = os.networkInterfaces();
  const urls = [];
  for (const values of Object.values(interfaces)) {
    for (const details of values ?? []) {
      if (details.family !== "IPv4" || details.internal) continue;
      urls.push(`http://${details.address}:${port}`);
    }
  }
  return urls;
}

function canListen(port) {
  return new Promise((resolve) => {
    const tester = net.createServer();
    tester.once("error", () => resolve(false));
    tester.once("listening", () => {
      tester.close(() => resolve(true));
    });
    tester.listen(port, "0.0.0.0");
  });
}

async function choosePort(start = 8080, attempts = 10) {
  for (let port = start; port < start + attempts; port += 1) {
    if (await canListen(port)) return port;
  }
  throw new Error(`Could not find a free port between ${start} and ${start + attempts - 1}`);
}

function openBrowser(url) {
  exec(`start "" "${url}"`);
}

const requestedPort = Number(process.env.PORT || 8080);
const port = await choosePort(requestedPort);
process.env.PORT = String(port);
process.env.DB_FILE = process.env.DB_FILE || path.join(BACKEND_ROOT, "data", "network-analyzer.sqlite");

const { app, server } = await createApp();
app.use(express.static(FRONTEND_DIST));
app.get(/^\/(?!api(?:\/|$)|socket\.io(?:\/|$)).*/, (_req, res) => {
  res.sendFile(path.join(FRONTEND_DIST, "index.html"));
});

server.listen(port, "0.0.0.0", () => {
  const lanUrls = networkUrls(port);
  console.log("");
  console.log("Mobile Network Analyzer");
  console.log("-----------------------");
  console.log(`Dashboard on this computer: http://localhost:${port}`);
  if (lanUrls.length) {
    console.log("Use one of these addresses inside the Android app:");
    for (const url of lanUrls) console.log(`  ${url}`);
  } else {
    console.log("No LAN IPv4 address detected. Connect to Wi-Fi or Ethernet and restart if needed.");
  }
  console.log("");
  console.log("Keep this window open while the app and dashboard are in use.");
  console.log("Closing this window stops the local service and dashboard.");
  console.log("");
  if (process.env.NO_AUTO_OPEN !== "1") {
    openBrowser(`http://localhost:${port}`);
  }
});
