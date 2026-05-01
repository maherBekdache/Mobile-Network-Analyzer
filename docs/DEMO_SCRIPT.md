# Demo Script

## 1. Start The Backend

```powershell
cd backend
npm install
npm start
```

Open `http://localhost:8080/api/health` and confirm it returns `ok: true`.

## 2. Start The Dashboard

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## 3. Find The Laptop LAN IP

```powershell
ipconfig
```

Use the IPv4 address on the Wi-Fi adapter, for example `192.168.1.10`.

## 4. Run Android

Build:

```powershell
cd android
.\gradlew.bat assembleDebug
```

Install the debug APK on a phone. Enter:

```text
http://<laptop-ip>:8080
```

If using the Android Studio emulator, use:

```text
http://10.0.2.2:8080
```

Suggested Android demo flow:

1. `Capture` tab
   - enter the backend URL
   - enable demo mode if live SIM data is unavailable
   - tap `Start Streaming`
2. `Stats` tab
   - tap `Load This Device Stats`
   - tap `Load All Server Stats`
3. `History` tab
   - show the latest 10 rows loading by default
   - apply a quick preset like `Last 10 Min`
   - optionally filter by operator, generation, power range, device ID, cell ID, or signal tier

## 5. Show Required Features

- Realtime phone sample on the Android screen.
- Backend health endpoint and SQLite persistence.
- Dashboard connected-device count.
- Alfa/Touch ratio card.
- 2G/3G/4G/5G generation chart.
- Signal power timeline.
- Latest samples table.
- Device table with active status and per-device average power.
- Android device-only stats and all-server stats requesting server-calculated analytics.
- Android `History` tab with default latest rows and filterable server history.
- Dashboard filters for date, operator, generation, power, cell ID, and device ID.
