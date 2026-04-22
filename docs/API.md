# API Contract

Base URL defaults to `http://localhost:8080`.

## POST `/api/measurements`

Receives one mobile sample and stores it on the server.

```json
{
  "deviceId": "android-abc123",
  "sessionId": "uuid-session",
  "operator": "alfa",
  "mcc": "415",
  "mnc": "01",
  "networkGeneration": "4G",
  "rawNetworkType": "LTE",
  "signalPowerDbm": -82,
  "snrDb": 12,
  "sinrDb": null,
  "cellId": "81937409",
  "pci": "371",
  "tac": "4102",
  "frequencyBand": "LTE EARFCN 6300",
  "earfcn": 6300,
  "latitude": 33.8938,
  "longitude": 35.5018,
  "accuracyM": 30,
  "timestamp": "2026-04-22T12:05:00.000Z"
}
```

Required field: `deviceId`. The backend normalizes operator names and network generations.

## GET `/api/measurements`

Returns recent samples.

Supported filters:

- `from`, `to`: ISO date/time bounds.
- `operator`: `alfa`, `touch`, or `unknown`.
- `networkGeneration`: `2G`, `3G`, `4G`, `5G`, or `unknown`.
- `deviceId`
- `cellId`
- `minPower`, `maxPower`
- `limit`: maximum rows, capped at 1000.

## GET `/api/stats/summary`

Returns:

- `totalSamples`
- latest sample
- operator ratios and Alfa/Touch counts
- 2G/3G/4G/5G ratios
- average signal power by network generation
- average SNR/SINR by network generation
- average signal power by device
- overall averages, distinct devices, and distinct cells

Accepts the same filters as `/api/measurements`.

## GET `/api/stats/devices`

Returns per-device total samples, average power, average SNR/SINR, distinct cells, first seen, last seen, active status, and last IP.

## GET `/api/devices`

Returns known devices ordered by active status and last seen time.

## WebSocket Events

Socket.IO server emits:

- `measurement:new` after a sample is accepted.
- `stats:update` after aggregate statistics refresh.
- `devices:update` after connected-device state changes.
- `server:hello` when a dashboard/client connects.
