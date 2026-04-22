package com.maher.mobilenetworkanalyzer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 451;
    private static final long SAMPLE_INTERVAL_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TelephonyManager telephonyManager;
    private SharedPreferences preferences;
    private String deviceId;
    private String sessionId;
    private boolean streaming = false;
    private int demoCounter = 0;

    private EditText serverInput;
    private CheckBox demoMode;
    private TextView statusText;
    private TextView liveText;
    private TextView statsText;
    private TextView recentText;
    private Button streamButton;

    private final Runnable sampleLoop = new Runnable() {
        @Override
        public void run() {
            if (!streaming) return;
            captureAndSend();
            handler.postDelayed(this, SAMPLE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        preferences = getSharedPreferences("network-analyzer", MODE_PRIVATE);
        deviceId = buildStableDeviceId();
        sessionId = UUID.randomUUID().toString();
        setContentView(buildUi());
        requestPermissionsIfNeeded();
        updateStatus("Ready. Configure the laptop server URL and start streaming.");
    }

    @Override
    protected void onDestroy() {
        streaming = false;
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(16);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(237, 244, 247));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scrollView.addView(root);

        TextView title = text("Mobile Network Analyzer", 28, true);
        TextView subtitle = text("Streams cellular measurements to the separate Node.js server every 10 seconds.", 15, false);
        subtitle.setTextColor(Color.rgb(82, 97, 112));
        root.addView(title);
        root.addView(subtitle);

        serverInput = new EditText(this);
        serverInput.setSingleLine(true);
        serverInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverInput.setHint("http://192.168.1.10:8080");
        serverInput.setText(preferences.getString("serverUrl", "http://10.0.2.2:8080"));
        root.addView(label("Server URL"));
        root.addView(serverInput);

        demoMode = new CheckBox(this);
        demoMode.setText("Demo mode (generate Alfa/Touch samples without SIM access)");
        demoMode.setTextColor(Color.rgb(19, 32, 43));
        root.addView(demoMode);

        GridLayout controls = new GridLayout(this);
        controls.setColumnCount(2);
        controls.setUseDefaultMargins(true);
        streamButton = button("Start Streaming");
        Button onceButton = button("Send One Sample");
        Button statsButton = button("Load Statistics");
        Button recentButton = button("Load Latest Rows");
        controls.addView(streamButton);
        controls.addView(onceButton);
        controls.addView(statsButton);
        controls.addView(recentButton);
        root.addView(controls);

        statusText = card("Status");
        liveText = card("Live Measurement");
        statsText = card("Server Statistics");
        recentText = card("Recent Server Rows");
        root.addView(statusText);
        root.addView(liveText);
        root.addView(statsText);
        root.addView(recentText);

        streamButton.setOnClickListener(v -> toggleStreaming());
        onceButton.setOnClickListener(v -> captureAndSend());
        statsButton.setOnClickListener(v -> loadStats());
        recentButton.setOnClickListener(v -> loadRecentRows());

        return scrollView;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(19, 32, 43));
        view.setPadding(0, dp(5), 0, dp(5));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, true);
        view.setTextColor(Color.rgb(15, 118, 110));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(15, 118, 110));
        button.setMinHeight(dp(46));
        return button;
    }

    private TextView card(String title) {
        TextView view = text(title + "\nWaiting for data.", 15, false);
        view.setBackgroundColor(Color.WHITE);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(12), 0, 0);
        view.setLayoutParams(params);
        view.setGravity(Gravity.START);
        return view;
    }

    private void toggleStreaming() {
        streaming = !streaming;
        streamButton.setText(streaming ? "Stop Streaming" : "Start Streaming");
        preferences.edit().putString("serverUrl", normalizedServer()).apply();
        if (streaming) {
            updateStatus("Streaming started. Next samples will be sent every 10 seconds.");
            captureAndSend();
            handler.postDelayed(sampleLoop, SAMPLE_INTERVAL_MS);
        } else {
            handler.removeCallbacks(sampleLoop);
            updateStatus("Streaming stopped.");
        }
    }

    private void captureAndSend() {
        Measurement measurement = demoMode.isChecked() ? demoMeasurement() : collectMeasurement();
        renderLive(measurement);
        postMeasurement(measurement);
    }

    private void postMeasurement(Measurement measurement) {
        executor.execute(() -> {
            try {
                JSONObject body = measurement.toJson();
                JSONObject response = request("POST", "/api/measurements", body);
                updateStatus("Uploaded sample #" + response.getJSONObject("measurement").getLong("id") + " to " + normalizedServer());
            } catch (Exception error) {
                updateStatus("Upload failed: " + error.getMessage());
            }
        });
    }

    private void loadStats() {
        executor.execute(() -> {
            try {
                JSONObject stats = request("GET", "/api/stats/summary", null);
                StringBuilder builder = new StringBuilder();
                builder.append("Server Statistics\n");
                builder.append("Samples: ").append(stats.optInt("totalSamples")).append("\n");
                JSONObject overall = stats.optJSONObject("overall");
                if (overall != null) {
                    builder.append("Average power: ").append(overall.optString("averagePowerDbm", "N/A")).append(" dBm\n");
                    builder.append("Average SNR: ").append(overall.optString("averageSnrDb", "N/A")).append(" dB\n");
                    builder.append("Distinct devices: ").append(overall.optInt("distinctDevices")).append("\n");
                    builder.append("Distinct cells: ").append(overall.optInt("distinctCells")).append("\n");
                }
                builder.append("\nOperator ratios:\n").append(formatRatios(stats.optJSONObject("operatorRatios")));
                builder.append("\nNetwork ratios:\n").append(formatRatios(stats.optJSONObject("networkRatios")));
                setText(statsText, builder.toString());
            } catch (Exception error) {
                updateStatus("Stats failed: " + error.getMessage());
            }
        });
    }

    private void loadRecentRows() {
        executor.execute(() -> {
            try {
                JSONObject response = request("GET", "/api/measurements?limit=10", null);
                JSONArray rows = response.getJSONArray("rows");
                StringBuilder builder = new StringBuilder("Recent Server Rows\n");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    builder.append(row.optString("client_timestamp")).append(" | ")
                            .append(row.optString("device_id")).append(" | ")
                            .append(row.optString("operator")).append(" | ")
                            .append(row.optString("network_generation")).append(" | ")
                            .append(row.optString("signal_power_dbm")).append(" dBm | Cell ")
                            .append(row.optString("cell_id", "N/A")).append("\n");
                }
                setText(recentText, builder.toString());
            } catch (Exception error) {
                updateStatus("Latest rows failed: " + error.getMessage());
            }
        });
    }

    private String formatRatios(JSONObject ratios) {
        if (ratios == null) return "N/A\n";
        StringBuilder builder = new StringBuilder();
        JSONArray names = ratios.names();
        if (names == null) return "N/A\n";
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i);
            JSONObject item = ratios.optJSONObject(name);
            if (item != null) {
                builder.append(name).append(": ")
                        .append(item.optInt("count")).append(" samples, ")
                        .append(item.optDouble("percentage")).append("%\n");
            }
        }
        return builder.toString();
    }

    private Measurement collectMeasurement() {
        Measurement m = baseMeasurement();
        try {
            if (!hasPhonePermission()) {
                m.error = "Missing phone/location permissions.";
                return m;
            }

            m.operator = valueOrUnknown(telephonyManager.getNetworkOperatorName());
            String operatorCode = telephonyManager.getNetworkOperator();
            if (operatorCode != null && operatorCode.length() >= 5) {
                m.mcc = operatorCode.substring(0, 3);
                m.mnc = operatorCode.substring(3);
            }
            int dataType = telephonyManager.getDataNetworkType();
            m.rawNetworkType = networkTypeName(dataType);
            m.networkGeneration = generationForType(dataType);

            List<CellInfo> cells = telephonyManager.getAllCellInfo();
            if (cells != null) {
                for (CellInfo cell : cells) {
                    if (cell.isRegistered()) {
                        fillFromCell(cell, m);
                        break;
                    }
                }
                if (m.cellId == null && !cells.isEmpty()) fillFromCell(cells.get(0), m);
            }
            fillLocation(m);
        } catch (Exception error) {
            m.error = error.getMessage();
        }
        return m;
    }

    private Measurement demoMeasurement() {
        Measurement m = baseMeasurement();
        String[] operators = {"alfa", "touch"};
        String[] generations = {"2G", "3G", "4G", "4G", "5G"};
        m.operator = operators[demoCounter % operators.length];
        m.networkGeneration = generations[demoCounter % generations.length];
        m.rawNetworkType = "DEMO_" + m.networkGeneration;
        m.signalPowerDbm = -65 - ((demoCounter * 7) % 42);
        m.snrDb = "4G".equals(m.networkGeneration) || "5G".equals(m.networkGeneration) ? 8 + (demoCounter % 14) : null;
        m.sinrDb = "5G".equals(m.networkGeneration) ? 12 + (demoCounter % 10) : null;
        m.cellId = "LB-" + (1000 + (demoCounter % 8));
        m.pci = String.valueOf(20 + (demoCounter % 25));
        m.tac = String.valueOf(4100 + (demoCounter % 6));
        m.earfcn = 6300 + demoCounter;
        m.frequencyBand = demoCounter % 3 == 0 ? "20 (800 MHz)" : "3 (1800 MHz)";
        demoCounter++;
        return m;
    }

    private Measurement baseMeasurement() {
        Measurement m = new Measurement();
        m.deviceId = deviceId;
        m.sessionId = sessionId;
        m.timestamp = new Date();
        m.phoneLocalIp = localIp();
        m.operator = "unknown";
        m.networkGeneration = "unknown";
        return m;
    }

    private void fillFromCell(CellInfo cell, Measurement m) {
        if (cell instanceof CellInfoLte) {
            CellInfoLte lte = (CellInfoLte) cell;
            CellSignalStrengthLte strength = lte.getCellSignalStrength();
            CellIdentityLte identity = lte.getCellIdentity();
            m.networkGeneration = "4G";
            m.signalPowerDbm = strength.getDbm();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) m.snrDb = normalizeUnavailable(strength.getRssnr());
            m.cellId = String.valueOf(identity.getCi());
            m.pci = String.valueOf(identity.getPci());
            m.tac = String.valueOf(identity.getTac());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) m.earfcn = identity.getEarfcn();
            m.frequencyBand = "LTE EARFCN " + nullText(m.earfcn);
            return;
        }
        if (cell instanceof CellInfoWcdma) {
            CellInfoWcdma wcdma = (CellInfoWcdma) cell;
            CellSignalStrengthWcdma strength = wcdma.getCellSignalStrength();
            CellIdentityWcdma identity = wcdma.getCellIdentity();
            m.networkGeneration = "3G";
            m.signalPowerDbm = strength.getDbm();
            m.cellId = String.valueOf(identity.getCid());
            m.psc = String.valueOf(identity.getPsc());
            m.lac = String.valueOf(identity.getLac());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) m.uarfcn = identity.getUarfcn();
            m.frequencyBand = "UMTS UARFCN " + nullText(m.uarfcn);
            return;
        }
        if (cell instanceof CellInfoGsm) {
            CellInfoGsm gsm = (CellInfoGsm) cell;
            CellSignalStrengthGsm strength = gsm.getCellSignalStrength();
            CellIdentityGsm identity = gsm.getCellIdentity();
            m.networkGeneration = "2G";
            m.signalPowerDbm = strength.getDbm();
            m.cellId = String.valueOf(identity.getCid());
            m.lac = String.valueOf(identity.getLac());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) m.arfcn = identity.getArfcn();
            m.frequencyBand = "GSM ARFCN " + nullText(m.arfcn);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell instanceof CellInfoNr) {
            CellInfoNr nr = (CellInfoNr) cell;
            CellSignalStrengthNr strength = (CellSignalStrengthNr) nr.getCellSignalStrength();
            CellIdentityNr identity = (CellIdentityNr) nr.getCellIdentity();
            m.networkGeneration = "5G";
            m.signalPowerDbm = strength.getDbm();
            m.sinrDb = normalizeUnavailable(strength.getSsSinr());
            m.cellId = String.valueOf(identity.getNci());
            m.pci = String.valueOf(identity.getPci());
            m.tac = String.valueOf(identity.getTac());
            m.nrarfcn = identity.getNrarfcn();
            m.frequencyBand = "NR ARFCN " + m.nrarfcn;
        }
    }

    private Integer normalizeUnavailable(int value) {
        return value == Integer.MAX_VALUE || value == CellInfo.UNAVAILABLE ? null : value;
    }

    private void fillLocation(Measurement m) {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location == null) location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                m.latitude = location.getLatitude();
                m.longitude = location.getLongitude();
                m.accuracyM = location.getAccuracy();
            }
        } catch (Exception ignored) {
        }
    }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        URL url = new URL(normalizedServer() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(bytes);
            }
        }
        int status = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) response.append(line);
        if (status >= 400) throw new IllegalStateException(response.toString());
        return new JSONObject(response.toString());
    }

    private void renderLive(Measurement m) {
        StringBuilder builder = new StringBuilder("Live Measurement\n");
        builder.append("Device: ").append(m.deviceId).append("\n");
        builder.append("Session: ").append(m.sessionId.substring(0, 8)).append("\n");
        builder.append("Operator: ").append(m.operator).append("\n");
        builder.append("Network: ").append(m.networkGeneration).append(" (").append(nullText(m.rawNetworkType)).append(")\n");
        builder.append("Power: ").append(nullText(m.signalPowerDbm)).append(" dBm\n");
        builder.append("SNR: ").append(nullText(m.snrDb)).append(" dB | SINR: ").append(nullText(m.sinrDb)).append(" dB\n");
        builder.append("Cell: ").append(nullText(m.cellId)).append(" | PCI/PSC: ").append(nullText(m.pci != null ? m.pci : m.psc)).append("\n");
        builder.append("Frequency: ").append(nullText(m.frequencyBand)).append("\n");
        builder.append("Phone IP: ").append(nullText(m.phoneLocalIp)).append("\n");
        builder.append("Timestamp: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(m.timestamp)).append("\n");
        if (m.error != null) builder.append("Note: ").append(m.error).append("\n");
        setText(liveText, builder.toString());
    }

    private void updateStatus(String message) {
        setText(statusText, "Status\n" + message);
    }

    private void setText(TextView view, String message) {
        handler.post(() -> view.setText(message));
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        requestPermissions(new String[]{
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        }, PERMISSION_REQUEST);
    }

    private boolean hasPhonePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private String buildStableDeviceId() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.trim().isEmpty()) id = UUID.randomUUID().toString();
        return "android-" + id;
    }

    private String normalizedServer() {
        String value = serverInput.getText().toString().trim();
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        return value;
    }

    private String valueOrUnknown(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
    }

    private String nullText(Object value) {
        return value == null ? "N/A" : String.valueOf(value);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String localIp() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ip = wifiManager.getConnectionInfo().getIpAddress();
            return String.format(Locale.US, "%d.%d.%d.%d", ip & 0xff, ip >> 8 & 0xff, ip >> 16 & 0xff, ip >> 24 & 0xff);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String networkTypeName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS: return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "UMTS";
            case TelephonyManager.NETWORK_TYPE_CDMA: return "CDMA";
            case TelephonyManager.NETWORK_TYPE_EVDO_0: return "EVDO_0";
            case TelephonyManager.NETWORK_TYPE_EVDO_A: return "EVDO_A";
            case TelephonyManager.NETWORK_TYPE_1xRTT: return "1XRTT";
            case TelephonyManager.NETWORK_TYPE_HSDPA: return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA: return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA: return "HSPA";
            case TelephonyManager.NETWORK_TYPE_IDEN: return "IDEN";
            case TelephonyManager.NETWORK_TYPE_EVDO_B: return "EVDO_B";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE";
            case TelephonyManager.NETWORK_TYPE_EHRPD: return "EHRPD";
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "HSPAP";
            case TelephonyManager.NETWORK_TYPE_GSM: return "GSM";
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA: return "TD_SCDMA";
            case TelephonyManager.NETWORK_TYPE_IWLAN: return "IWLAN";
            case 19: return "LTE_CA";
            case TelephonyManager.NETWORK_TYPE_NR: return "NR";
            default: return "UNKNOWN";
        }
    }

    private String generationForType(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
            case TelephonyManager.NETWORK_TYPE_GSM:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
            case 19:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            default:
                return "unknown";
        }
    }

    static class Measurement {
        String deviceId;
        String sessionId;
        String operator;
        String mcc;
        String mnc;
        String networkGeneration;
        String rawNetworkType;
        Integer signalPowerDbm;
        Integer snrDb;
        Integer sinrDb;
        String cellId;
        String pci;
        String psc;
        String tac;
        String lac;
        String frequencyBand;
        Integer arfcn;
        Integer uarfcn;
        Integer earfcn;
        Integer nrarfcn;
        Double latitude;
        Double longitude;
        Float accuracyM;
        Date timestamp;
        String phoneLocalIp;
        String error;

        JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            put(json, "deviceId", deviceId);
            put(json, "sessionId", sessionId);
            put(json, "operator", operator);
            put(json, "mcc", mcc);
            put(json, "mnc", mnc);
            put(json, "networkGeneration", networkGeneration);
            put(json, "rawNetworkType", rawNetworkType);
            put(json, "signalPowerDbm", signalPowerDbm);
            put(json, "snrDb", snrDb);
            put(json, "sinrDb", sinrDb);
            put(json, "cellId", cellId);
            put(json, "pci", pci);
            put(json, "psc", psc);
            put(json, "tac", tac);
            put(json, "lac", lac);
            put(json, "frequencyBand", frequencyBand);
            put(json, "arfcn", arfcn);
            put(json, "uarfcn", uarfcn);
            put(json, "earfcn", earfcn);
            put(json, "nrarfcn", nrarfcn);
            put(json, "latitude", latitude);
            put(json, "longitude", longitude);
            put(json, "accuracyM", accuracyM);
            put(json, "phoneLocalIp", phoneLocalIp);
            put(json, "timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(timestamp));
            return json;
        }

        private void put(JSONObject json, String key, Object value) throws Exception {
            if (value != null) json.put(key, value);
        }
    }
}
