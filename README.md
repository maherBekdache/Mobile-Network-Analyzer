# Mobile Network Analyzer

Full EECE451 mobile networks project implementation: an Android client streams cellular measurements to a separate multithread-capable server, the server stores and analyzes the data, and a React dashboard shows realtime centralized statistics.

## What It Does

- Collects cellular data from Android phones: operator, MCC/MNC, 2G/3G/4G/5G generation, raw radio type, signal power, SNR/SINR when available, cell ID, PCI/PSC, TAC/LAC, channel/frequency identifiers, timestamp, device ID, session ID, phone IP, and optional location.
- Sends every sample directly to a backend over HTTP every 10 seconds.
- Stores all samples in SQLite on the server, not on the phone.
- Calculates Alfa/Touch ratios, 2G/3G/4G/5G ratios, average power, average SNR/SINR, connected devices, filtered sample lists, and per-device statistics.
- Provides a live React dashboard with charts, filters, tables, and Socket.IO updates.
- Includes Android demo mode for classroom demos without SIM/network API availability.

## Repository Layout

- `android/` - native Android Java client.
- `backend/` - Node.js, Express, Socket.IO, SQLite API server.
- `frontend/` - React/Vite dashboard.
- `docs/` - setup notes, API contract, demo script, and project architecture.

## Quick Start

### Backend

```powershell
cd backend
npm install
npm start
```

Backend default URL: `http://localhost:8080`

On a real phone, use the laptop LAN IP instead, for example `http://192.168.1.10:8080`.

### Frontend Dashboard

```powershell
cd frontend
npm install
npm run dev
```

Dashboard default URL: `http://localhost:5173`

If the backend is not on `localhost:8080`, start Vite with:

```powershell
$env:VITE_API_BASE="http://192.168.1.10:8080"; npm run dev
```

### Android

```powershell
cd android
.\gradlew.bat assembleDebug
```

Install `android/app/build/outputs/apk/debug/app-debug.apk` on the phone. Open the app, enter the laptop backend URL, enable demo mode if needed, and start streaming.

## Important Android Notes

Modern Android restricts MAC address access for privacy. This project records stable Android device ID, server-observed IP address, local phone IP when available, and session ID instead. That satisfies the server-side connected-device tracking goal while staying compatible with current Android rules.

Cellular API availability varies by phone, Android version, SIM, operator, and permissions. Demo mode generates realistic Alfa/Touch and generation samples when physical cellular measurements are unavailable.

SNR/SINR can be missing because Android does not expose it consistently for every radio generation, chipset, SIM/operator state, or serving cell. The app and dashboard show missing SNR/SINR as a device/network reporting limitation, not as a failed upload.

Signal power is measured in dBm, where values closer to 0 are stronger. The UI translates raw dBm into tiers: Excellent, Good, Medium, Bad, Very Bad.

## Main API Endpoints

- `POST /api/measurements`
- `GET /api/measurements`
- `GET /api/stats/summary`
- `GET /api/stats/devices`
- `GET /api/devices`
- `GET /api/health`

See [docs/API.md](docs/API.md) for payloads and filters.
