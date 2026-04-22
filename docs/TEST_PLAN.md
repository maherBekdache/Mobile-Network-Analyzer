# Test Plan

## Backend Automated Tests

Run:

```powershell
cd backend
npm test
```

Covered scenarios:

- malformed measurement rejection
- measurement insertion
- operator normalization
- Alfa/Touch ratios
- 2G/3G/4G generation ratios
- distinct devices
- date-window filtering
- concurrent simulated phone uploads

## Manual Android Tests

- App launches and requests phone/location permissions.
- Server URL is configurable.
- One sample can be sent manually.
- Streaming sends samples every 10 seconds.
- Demo mode works without SIM access.
- Live screen updates after each sample.
- Statistics and latest rows are fetched from the server.
- No SQLite database or file-based measurement persistence exists on Android.

## Manual Dashboard Tests

- Dashboard loads when backend is running.
- Cards show total samples, active devices, ratios, and averages.
- Charts update after new Android samples.
- Latest rows table shows new samples.
- Devices table shows active and historical devices.
- Filters change the data returned by the server.

## End-To-End Acceptance

The project is complete when at least one phone or demo-mode client sends repeated samples to the laptop backend, the data is stored in SQLite, Android can request server statistics, and the React dashboard updates live without refreshing the browser.
