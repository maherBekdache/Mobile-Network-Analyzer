package com.maher.mobilenetworkanalyzer;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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
    private static final int COLOR_TEAL = Color.rgb(15, 118, 110);
    private static final int COLOR_GREEN = Color.rgb(22, 163, 74);
    private static final int COLOR_RED = Color.rgb(220, 38, 38);
    private static final int COLOR_BLUE = Color.rgb(59, 130, 246);
    private static final int COLOR_ORANGE = Color.rgb(234, 88, 12);
    private static final int COLOR_TEXT_DARK = Color.rgb(19, 32, 43);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TelephonyManager telephonyManager;
    private SharedPreferences preferences;
    private String deviceId;
    private String sessionId;
    private boolean streaming = false;
    private int demoCounter = 0;
    private String currentTab = "capture";

    private EditText serverInput;
    private CheckBox demoMode;
    private TextView statusText;
    private TextView qualityText;
    private TextView liveText;
    private TextView statsText;
    private TextView recentText;
    private TextView operatorTile;
    private TextView networkTile;
    private TextView powerTile;
    private TextView noiseTile;
    private TextView cellTile;
    private TextView uploadTile;
    private TextView topStatusChip;
    private TextView tabCapture;
    private TextView tabStats;
    private TextView tabLatestRows;
    private EditText historyFromInput;
    private EditText historyToInput;
    private EditText historyDeviceInput;
    private EditText historyCellInput;
    private EditText historyMinPowerInput;
    private EditText historyMaxPowerInput;
    private Spinner historyOperatorSpinner;
    private Spinner historyGenerationSpinner;
    private Spinner historyQualitySpinner;
    private Button streamButton;
    private Button onceButton;
    private Button deviceStatsButton;
    private Button serverStatsButton;
    private Button latestRowsButton;
    private Button clearHistoryFiltersButton;
    private Button historyLast10MinButton;
    private Button historyLastHourButton;
    private Button historyTodayButton;
    private Button historyCustomRangeButton;
    private LinearLayout captureSection;
    private LinearLayout statsSection;
    private LinearLayout latestRowsSection;

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
        scrollView.setBackgroundColor(Color.rgb(232, 243, 247));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scrollView.addView(root);

        LinearLayout hero = block(Color.rgb(15, 118, 110));
        TextView title = text("Mobile Network Analyzer", 30, true);
        title.setTextColor(Color.WHITE);
        TextView subtitle = text("Live Signal Monitor", 16, true);
        subtitle.setTextColor(Color.rgb(204, 251, 241));
        hero.addView(title);
        hero.addView(subtitle);
        root.addView(hero);

        topStatusChip = chip("Ready to capture", Color.rgb(219, 234, 254), Color.rgb(30, 64, 175));
        LinearLayout topStatusBlock = block(Color.WHITE);
        topStatusBlock.addView(sectionTitle("Quick Status"));
        topStatusBlock.addView(topStatusChip);
        root.addView(topStatusBlock);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, 0, 0, dp(2));
        tabCapture = tabButton("Capture", true);
        tabStats = tabButton("Stats", false);
        tabLatestRows = tabButton("History", false);
        tabs.addView(tabCapture);
        tabs.addView(tabStats);
        tabs.addView(tabLatestRows);
        LinearLayout tabsBlock = block(Color.WHITE);
        tabsBlock.addView(sectionTitle("Views"));
        tabsBlock.addView(tabs);
        root.addView(tabsBlock);

        LinearLayout config = block(Color.WHITE);
        config.addView(sectionTitle("Server & Capture"));
        serverInput = new EditText(this);
        serverInput.setSingleLine(true);
        serverInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverInput.setHint("http://192.168.1.10:8080");
        serverInput.setText(preferences.getString("serverUrl", "http://10.0.2.2:8080"));
        config.addView(label("Server URL"));
        config.addView(serverInput);

        demoMode = new CheckBox(this);
        demoMode.setText("Demo mode (generate Alfa/Touch samples without SIM access)");
        demoMode.setTextColor(Color.rgb(19, 32, 43));
        config.addView(demoMode);
        root.addView(config);

        GridLayout controls = new GridLayout(this);
        controls.setColumnCount(2);
        controls.setUseDefaultMargins(true);
        streamButton = button("Start Streaming", COLOR_GREEN);
        onceButton = button("Send One Sample", COLOR_BLUE);
        controls.addView(streamButton);
        controls.addView(onceButton);
        LinearLayout controlsBlock = block(Color.WHITE);
        controlsBlock.addView(sectionTitle("Controls"));
        controlsBlock.addView(controls);

        GridLayout tiles = new GridLayout(this);
        tiles.setColumnCount(2);
        tiles.setUseDefaultMargins(true);
        operatorTile = tile("Operator", "Waiting", Color.rgb(236, 253, 245));
        networkTile = tile("Network", "Waiting", Color.rgb(238, 242, 255));
        powerTile = tile("Signal", "Waiting", Color.rgb(254, 243, 199));
        noiseTile = tile("Noise", "Waiting", Color.rgb(255, 237, 213));
        cellTile = tile("Cell", "Waiting", Color.rgb(240, 249, 255));
        uploadTile = tile("Upload", "Ready", Color.rgb(232, 243, 247));
        tiles.addView(operatorTile);
        tiles.addView(networkTile);
        tiles.addView(powerTile);
        tiles.addView(noiseTile);
        tiles.addView(cellTile);
        tiles.addView(uploadTile);
        qualityText = card("Signal Quality", Color.rgb(220, 252, 231));
        statusText = card("Status", Color.WHITE);
        liveText = card("Live Measurement", Color.WHITE);
        statsText = card("Statistics", Color.WHITE);
        recentText = card("Server History", Color.WHITE);

        captureSection = new LinearLayout(this);
        captureSection.setOrientation(LinearLayout.VERTICAL);
        captureSection.addView(controlsBlock);
        LinearLayout tilesBlock = block(Color.WHITE);
        tilesBlock.addView(sectionTitle("Live Snapshot"));
        tilesBlock.addView(tiles);
        captureSection.addView(tilesBlock);
        captureSection.addView(qualityText);
        captureSection.addView(statusText);
        captureSection.addView(liveText);

        statsSection = new LinearLayout(this);
        statsSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout statsControlsBlock = block(Color.WHITE);
        statsControlsBlock.addView(sectionTitle("Statistics Actions"));
        GridLayout statsControls = new GridLayout(this);
        statsControls.setColumnCount(2);
        statsControls.setUseDefaultMargins(true);
        deviceStatsButton = button("Load This Device Stats", COLOR_TEAL);
        serverStatsButton = button("Load All Server Stats", COLOR_BLUE);
        statsControls.addView(deviceStatsButton);
        statsControls.addView(serverStatsButton);
        statsControlsBlock.addView(statsControls);
        statsSection.addView(statsControlsBlock);
        statsSection.addView(statsText);
        latestRowsSection = new LinearLayout(this);
        latestRowsSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout historyFiltersBlock = block(Color.WHITE);
        historyFiltersBlock.addView(sectionTitle("History Filters"));
        GridLayout historyFiltersGrid = new GridLayout(this);
        historyFiltersGrid.setColumnCount(2);
        historyFiltersGrid.setUseDefaultMargins(true);
        historyFromInput = dateTimeInput("Pick start date & time");
        historyToInput = dateTimeInput("Pick end date & time");
        historyDeviceInput = filterInput("Device ID");
        historyCellInput = filterInput("Cell ID");
        historyMinPowerInput = filterInput("-100");
        historyMaxPowerInput = filterInput("-70");
        historyOperatorSpinner = dropdown(new String[]{"Any", "alfa", "touch"});
        historyGenerationSpinner = dropdown(new String[]{"Any", "2G", "3G", "4G", "5G"});
        historyQualitySpinner = dropdown(new String[]{"Any", "Excellent", "Good", "Medium", "Bad", "Very Bad"});
        historyFiltersGrid.addView(fieldStack("From", historyFromInput));
        historyFiltersGrid.addView(fieldStack("To", historyToInput));
        historyFiltersGrid.addView(fieldStack("Device ID", historyDeviceInput));
        historyFiltersGrid.addView(fieldStack("Cell ID", historyCellInput));
        historyFiltersGrid.addView(fieldStack("Min Power", historyMinPowerInput));
        historyFiltersGrid.addView(fieldStack("Max Power", historyMaxPowerInput));
        historyFiltersGrid.addView(fieldStack("Operator", historyOperatorSpinner));
        historyFiltersGrid.addView(fieldStack("Generation", historyGenerationSpinner));
        historyFiltersGrid.addView(fieldStack("Signal Tier", historyQualitySpinner));
        historyFiltersBlock.addView(historyFiltersGrid);
        GridLayout historyPresetsGrid = new GridLayout(this);
        historyPresetsGrid.setColumnCount(2);
        historyPresetsGrid.setUseDefaultMargins(true);
        historyLast10MinButton = button("Last 10 Min", COLOR_TEAL);
        historyLastHourButton = button("Last Hour", COLOR_BLUE);
        historyTodayButton = button("Today", COLOR_GREEN);
        historyCustomRangeButton = button("Custom Range", COLOR_ORANGE);
        historyPresetsGrid.addView(historyLast10MinButton);
        historyPresetsGrid.addView(historyLastHourButton);
        historyPresetsGrid.addView(historyTodayButton);
        historyPresetsGrid.addView(historyCustomRangeButton);
        historyFiltersBlock.addView(label("Quick Time Range"));
        historyFiltersBlock.addView(historyPresetsGrid);
        LinearLayout latestRowsControlsBlock = block(Color.WHITE);
        latestRowsControlsBlock.addView(sectionTitle("History Actions"));
        GridLayout latestRowsActions = new GridLayout(this);
        latestRowsActions.setColumnCount(2);
        latestRowsActions.setUseDefaultMargins(true);
        latestRowsButton = button("Load History", COLOR_ORANGE);
        clearHistoryFiltersButton = button("Clear Filters", COLOR_BLUE);
        latestRowsActions.addView(latestRowsButton);
        latestRowsActions.addView(clearHistoryFiltersButton);
        latestRowsControlsBlock.addView(latestRowsActions);
        latestRowsSection.addView(historyFiltersBlock);
        latestRowsSection.addView(latestRowsControlsBlock);
        latestRowsSection.addView(recentText);

        root.addView(captureSection);
        root.addView(statsSection);
        root.addView(latestRowsSection);

        streamButton.setOnClickListener(v -> toggleStreaming());
        onceButton.setOnClickListener(v -> captureAndSend());
        deviceStatsButton.setOnClickListener(v -> {
            switchTab("stats");
            pulseButton(deviceStatsButton);
            loadDeviceStats();
        });
        serverStatsButton.setOnClickListener(v -> {
            switchTab("stats");
            pulseButton(serverStatsButton);
            loadServerStats();
        });
        latestRowsButton.setOnClickListener(v -> {
            switchTab("latest");
            pulseButton(latestRowsButton);
            loadRecentRows();
        });
        clearHistoryFiltersButton.setOnClickListener(v -> {
            clearHistoryFilters();
            pulseButton(clearHistoryFiltersButton);
            setTopStatus("History filters cleared", Color.rgb(219, 234, 254), Color.rgb(30, 64, 175));
            loadRecentRows();
        });
        historyLast10MinButton.setOnClickListener(v -> {
            applyHistoryPresetMinutes(10);
            pulseButton(historyLast10MinButton);
        });
        historyLastHourButton.setOnClickListener(v -> {
            applyHistoryPresetMinutes(60);
            pulseButton(historyLastHourButton);
        });
        historyTodayButton.setOnClickListener(v -> {
            applyHistoryToday();
            pulseButton(historyTodayButton);
        });
        historyCustomRangeButton.setOnClickListener(v -> {
            pulseButton(historyCustomRangeButton);
            showDateTimePicker(historyFromInput);
        });
        tabCapture.setOnClickListener(v -> switchTab("capture"));
        tabStats.setOnClickListener(v -> switchTab("stats"));
        tabLatestRows.setOnClickListener(v -> switchTab("latest"));
        attachTouchFeedback(streamButton);
        attachTouchFeedback(onceButton);
        attachTouchFeedback(deviceStatsButton);
        attachTouchFeedback(serverStatsButton);
        attachTouchFeedback(latestRowsButton);
        attachTouchFeedback(clearHistoryFiltersButton);
        attachTouchFeedback(historyLast10MinButton);
        attachTouchFeedback(historyLastHourButton);
        attachTouchFeedback(historyTodayButton);
        attachTouchFeedback(historyCustomRangeButton);
        switchTab("capture");
        updateStreamingButton();

        return scrollView;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(19, 32, 43));
        view.setPadding(0, dp(5), 0, dp(5));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView heroText(String value) {
        TextView view = text(value, 30, true);
        view.setTextColor(Color.WHITE);
        view.setPadding(dp(16), dp(18), dp(16), dp(6));
        view.setBackground(rounded(Color.rgb(15, 118, 110), dp(8)));
        return view;
    }

    private LinearLayout block(int background) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(14), dp(14), dp(14));
        layout.setBackground(rounded(background, dp(8)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        layout.setLayoutParams(params);
        return layout;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 17, true);
        view.setTextColor(Color.rgb(15, 81, 76));
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView tile(String label, String value, int background) {
        TextView view = text(label + "\n" + value, 15, true);
        view.setMinHeight(dp(86));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(rounded(background, dp(8)));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        view.setLayoutParams(params);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, true);
        view.setTextColor(Color.rgb(15, 118, 110));
        return view;
    }

    private TextView chip(String value, int background, int textColor) {
        TextView view = text(value, 15, true);
        view.setTextColor(textColor);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackground(rounded(background, dp(24)));
        return view;
    }

    private TextView tabButton(String value, boolean active) {
        TextView view = text(value, 15, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, dp(8), 0);
        view.setLayoutParams(params);
        view.setClickable(true);
        view.setFocusable(true);
        setTabState(view, active);
        return view;
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(color, dp(8)));
        button.setMinHeight(dp(46));
        return button;
    }

    private EditText filterInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextColor(COLOR_TEXT_DARK);
        input.setHintTextColor(Color.rgb(100, 115, 131));
        input.setBackground(rounded(Color.WHITE, dp(8)));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        return input;
    }

    private EditText dateTimeInput(String hint) {
        EditText input = filterInput(hint);
        input.setFocusable(false);
        input.setClickable(true);
        input.setInputType(InputType.TYPE_NULL);
        input.setOnClickListener(v -> showDateTimePicker(input));
        return input;
    }

    private Spinner dropdown(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(rounded(Color.WHITE, dp(8)));
        spinner.setPadding(dp(8), dp(8), dp(8), dp(8));
        return spinner;
    }

    private LinearLayout fieldStack(String title, View content) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(label(title));
        layout.addView(content);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        layout.setLayoutParams(params);
        return layout;
    }

    private TextView card(String title, int background) {
        TextView view = text(title + "\nWaiting for data.", 15, false);
        view.setBackground(rounded(background, dp(8)));
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

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), Color.rgb(198, 215, 224));
        return drawable;
    }

    private void toggleStreaming() {
        streaming = !streaming;
        preferences.edit().putString("serverUrl", normalizedServer()).apply();
        updateStreamingButton();
        pulseButton(streamButton);
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
        pulseButton(onceButton);
        Measurement measurement = demoMode.isChecked() ? demoMeasurement() : collectMeasurement();
        renderLive(measurement);
        if (!isAllowedOperator(measurement.operator)) {
            updateStatus("Skipped sample: only Alfa and Touch recordings are accepted.");
            return;
        }
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

    private void loadDeviceStats() {
        executor.execute(() -> {
            try {
                JSONObject stats = request("GET", "/api/stats/summary?deviceId=" + deviceId, null);
                setText(statsText, formatStatsSummary(stats, "This Device Statistics", true));
                setTopStatus("This device statistics refreshed", Color.rgb(219, 234, 254), Color.rgb(30, 64, 175));
            } catch (Exception error) {
                updateStatus("Device stats failed: " + error.getMessage());
            }
        });
    }

    private void loadServerStats() {
        executor.execute(() -> {
            try {
                JSONObject stats = request("GET", "/api/stats/summary", null);
                setText(statsText, formatStatsSummary(stats, "All Server Statistics", false));
                setTopStatus("Server statistics refreshed", Color.rgb(219, 234, 254), Color.rgb(30, 64, 175));
            } catch (Exception error) {
                updateStatus("Server stats failed: " + error.getMessage());
            }
        });
    }

    private void loadRecentRows() {
        executor.execute(() -> {
            try {
                JSONObject response = request("GET", buildHistoryPath(), null);
                JSONArray rows = applyHistoryQualityFilter(response.getJSONArray("rows"));
                StringBuilder builder = new StringBuilder("Server History\n");
                builder.append("Rows returned: ").append(rows.length()).append("\n");
                if (!hasHistoryFilters() && historyQualityTier().isEmpty()) {
                    builder.append("Showing latest 10 rows.\n\n");
                } else {
                    builder.append("Showing rows matching current filters.\n\n");
                }
                if (rows.length() == 0) {
                    builder.append("No server entries matched the history filters.\n");
                }
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    builder.append(row.optString("client_timestamp")).append(" | ")
                            .append(row.optString("device_id")).append(" | ")
                            .append(row.optString("operator")).append(" | ")
                            .append(row.optString("network_generation")).append(" | ")
                            .append(row.optString("signal_power_dbm")).append(" dBm ")
                            .append(signalTier(row.optDouble("signal_power_dbm", Double.NaN))).append(" | Cell ")
                            .append(row.optString("cell_id", "N/A")).append("\n");
                }
                setText(recentText, builder.toString());
                setTopStatus("History loaded", Color.rgb(254, 243, 199), Color.rgb(146, 64, 14));
            } catch (Exception error) {
                updateStatus("History failed: " + error.getMessage());
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

    private String formatStatsSummary(JSONObject stats, String title, boolean deviceScoped) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n");
        int totalSamples = stats.optInt("totalSamples");
        builder.append("Samples: ").append(totalSamples).append("\n");

        if (deviceScoped && totalSamples == 0) {
            builder.append("No samples from this device have reached the server yet.\n");
            builder.append("Try sending one sample or start streaming, then reload this section.\n");
            return builder.toString();
        }

        JSONObject overall = stats.optJSONObject("overall");
        if (overall != null) {
            double avgPower = overall.optDouble("averagePowerDbm", Double.NaN);
            builder.append("Average power: ").append(overall.optString("averagePowerDbm", "N/A"))
                    .append(" dBm (").append(signalTier(avgPower)).append(")\n");
            builder.append("Average SNR: ").append(overall.optString("averageSnrDb", "N/A")).append(" dB\n");
            builder.append("Average SINR: ").append(overall.optString("averageSinrDb", "N/A")).append(" dB\n");
            if (!deviceScoped) {
                builder.append("Distinct devices: ").append(overall.optInt("distinctDevices")).append("\n");
            }
            builder.append("Distinct cells: ").append(overall.optInt("distinctCells")).append("\n");
        }

        if (deviceScoped) {
            builder.append("Device ID: ").append(deviceId).append("\n");
            builder.append("Current session: ").append(sessionId.substring(0, 8)).append("\n");
        }

        builder.append("\nOperator ratios:\n").append(formatRatios(stats.optJSONObject("operatorRatios")));
        builder.append("\nNetwork ratios:\n").append(formatRatios(stats.optJSONObject("networkRatios")));
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
        String tier = signalTier(m.signalPowerDbm == null ? Double.NaN : m.signalPowerDbm);
        String noise = m.snrDb != null ? m.snrDb + " dB SNR" : (m.sinrDb != null ? m.sinrDb + " dB SINR" : "Not reported");
        setTile(operatorTile, "Operator", m.operator, operatorColor(m.operator));
        setTile(networkTile, "Network", m.networkGeneration + " / " + nullText(m.rawNetworkType), Color.rgb(238, 242, 255));
        setTile(powerTile, "Signal", tier + "\n" + nullText(m.signalPowerDbm) + " dBm", signalTierColor(tier));
        setTile(noiseTile, "Noise", noise, m.snrDb != null || m.sinrDb != null ? Color.rgb(255, 237, 213) : Color.rgb(238, 242, 244));
        setTile(cellTile, "Cell", nullText(m.cellId), Color.rgb(240, 249, 255));
        String quality = "Signal Quality\n"
                + tier + "  " + qualityBar(m.signalPowerDbm) + "\n"
                + "Power: " + nullText(m.signalPowerDbm) + " dBm\n"
                + "Noise: " + noise;
        setText(qualityText, quality);
        qualityText.setBackground(rounded(signalTierColor(tier), dp(8)));

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

    private void setTile(TextView view, String label, String value, int color) {
        handler.post(() -> {
            view.setText(label + "\n" + value);
            view.setBackground(rounded(color, dp(8)));
        });
    }

    private boolean isAllowedOperator(String operator) {
        if (operator == null) return false;
        String text = operator.toLowerCase(Locale.US);
        return text.contains("alfa") || text.contains("alpha") || text.contains("touch") || text.contains("mtc");
    }

    private int operatorColor(String operator) {
        if (operator == null) return Color.rgb(238, 242, 244);
        String text = operator.toLowerCase(Locale.US);
        if (text.contains("alfa") || text.contains("alpha")) return Color.rgb(220, 252, 231);
        if (text.contains("touch") || text.contains("mtc")) return Color.rgb(219, 234, 254);
        return Color.rgb(238, 242, 244);
    }

    private String signalTier(double powerDbm) {
        if (Double.isNaN(powerDbm)) return "Unknown";
        if (powerDbm >= -80) return "Excellent";
        if (powerDbm >= -90) return "Good";
        if (powerDbm >= -100) return "Medium";
        if (powerDbm >= -110) return "Bad";
        return "Very Bad";
    }

    private int signalTierColor(String tier) {
        switch (tier) {
            case "Excellent": return Color.rgb(220, 252, 231);
            case "Good": return Color.rgb(216, 245, 239);
            case "Medium": return Color.rgb(254, 243, 199);
            case "Bad": return Color.rgb(255, 237, 213);
            case "Very Bad": return Color.rgb(254, 226, 226);
            default: return Color.rgb(238, 242, 244);
        }
    }

    private String qualityBar(Integer powerDbm) {
        if (powerDbm == null) return "[----------]";
        int filled;
        if (powerDbm >= -80) filled = 10;
        else if (powerDbm >= -90) filled = 8;
        else if (powerDbm >= -100) filled = 6;
        else if (powerDbm >= -110) filled = 3;
        else filled = 1;
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < 10; i++) builder.append(i < filled ? "#" : "-");
        return builder.append("]").toString();
    }

    private void updateStatus(String message) {
        setText(statusText, "Status\n" + message);
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("failed")) {
            setTopStatus(message, Color.rgb(254, 226, 226), Color.rgb(153, 27, 27));
        } else if (lower.contains("stopped")) {
            setTopStatus(message, Color.rgb(255, 237, 213), Color.rgb(154, 52, 18));
        } else if (lower.contains("started") || lower.contains("uploaded") || lower.contains("loaded") || lower.contains("streaming")) {
            setTopStatus(message, Color.rgb(220, 252, 231), Color.rgb(22, 101, 52));
        } else {
            setTopStatus(message, Color.rgb(219, 234, 254), Color.rgb(30, 64, 175));
        }
        if (uploadTile != null) {
            setTile(
                    uploadTile,
                    "Upload",
                    compactUploadMessage(message),
                    message.toLowerCase(Locale.US).contains("failed") || message.toLowerCase(Locale.US).contains("skipped")
                            ? Color.rgb(254, 226, 226)
                            : Color.rgb(216, 245, 239)
            );
        }
    }

    private String compactUploadMessage(String message) {
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("uploaded sample #")) {
            int start = lower.indexOf("uploaded sample #");
            int to = lower.indexOf(" to ", start);
            if (to > start) {
                return message.substring(start, to);
            }
            return "Uploaded";
        }
        if (lower.contains("upload failed")) return "Upload failed";
        if (lower.contains("skipped sample")) return "Skipped sample";
        if (lower.contains("streaming started")) return "Streaming";
        if (lower.contains("streaming stopped")) return "Stopped";
        if (lower.contains("history")) return "History";
        return "Ready";
    }

    private void clearHistoryFilters() {
        setEditText(historyFromInput, "");
        setEditText(historyToInput, "");
        setEditText(historyDeviceInput, "");
        setEditText(historyCellInput, "");
        setEditText(historyMinPowerInput, "");
        setEditText(historyMaxPowerInput, "");
        setSpinner(historyOperatorSpinner, 0);
        setSpinner(historyGenerationSpinner, 0);
        setSpinner(historyQualitySpinner, 0);
    }

    private void applyHistoryPresetMinutes(int minutes) {
        Calendar end = Calendar.getInstance();
        Calendar start = (Calendar) end.clone();
        start.add(Calendar.MINUTE, -minutes);
        setEditText(historyFromInput, isoTimestamp(start));
        setEditText(historyToInput, isoTimestamp(end));
        setTopStatus("History preset applied", Color.rgb(219, 234, 254), Color.rgb(30, 64, 175));
        loadRecentRows();
    }

    private void applyHistoryToday() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = Calendar.getInstance();
        setEditText(historyFromInput, isoTimestamp(start));
        setEditText(historyToInput, isoTimestamp(end));
        setTopStatus("Today preset applied", Color.rgb(219, 234, 254), Color.rgb(30, 64, 175));
        loadRecentRows();
    }

    private String isoTimestamp(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(calendar.getTime());
    }

    private String buildHistoryPath() throws Exception {
        StringBuilder path = new StringBuilder("/api/measurements?");
        if (!hasHistoryFilters() && historyQualityTier().isEmpty()) {
            path.append("limit=10");
            return path.toString();
        }
        appendQuery(path, "all", "true");
        appendQuery(path, "from", textValue(historyFromInput));
        appendQuery(path, "to", textValue(historyToInput));
        appendQuery(path, "deviceId", textValue(historyDeviceInput));
        appendQuery(path, "cellId", textValue(historyCellInput));
        appendQuery(path, "minPower", textValue(historyMinPowerInput));
        appendQuery(path, "maxPower", textValue(historyMaxPowerInput));
        appendQuery(path, "operator", spinnerValue(historyOperatorSpinner));
        appendQuery(path, "networkGeneration", spinnerValue(historyGenerationSpinner));
        return path.toString();
    }

    private void appendQuery(StringBuilder builder, String key, String value) throws Exception {
        if (value == null || value.trim().isEmpty()) return;
        if (builder.charAt(builder.length() - 1) != '?') builder.append('&');
        builder.append(key).append('=').append(URLEncoder.encode(value.trim(), "UTF-8"));
    }

    private boolean hasHistoryFilters() {
        return !textValue(historyFromInput).isEmpty()
                || !textValue(historyToInput).isEmpty()
                || !textValue(historyDeviceInput).isEmpty()
                || !textValue(historyCellInput).isEmpty()
                || !textValue(historyMinPowerInput).isEmpty()
                || !textValue(historyMaxPowerInput).isEmpty()
                || !spinnerValue(historyOperatorSpinner).isEmpty()
                || !spinnerValue(historyGenerationSpinner).isEmpty();
    }

    private String historyQualityTier() {
        return spinnerValue(historyQualitySpinner).toLowerCase(Locale.US);
    }

    private JSONArray applyHistoryQualityFilter(JSONArray rows) throws Exception {
        String quality = historyQualityTier();
        if (quality.isEmpty()) return rows;
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String tier = signalTier(row.optDouble("signal_power_dbm", Double.NaN)).toLowerCase(Locale.US);
            if (tier.equals(quality)) filtered.put(row);
        }
        return filtered;
    }

    private String textValue(EditText input) {
        return input == null ? "" : input.getText().toString().trim();
    }

    private String spinnerValue(Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null) return "";
        String value = String.valueOf(spinner.getSelectedItem()).trim();
        return "Any".equalsIgnoreCase(value) ? "" : value;
    }

    private void setText(TextView view, String message) {
        handler.post(() -> view.setText(message));
    }

    private void setEditText(EditText input, String value) {
        if (input == null) return;
        handler.post(() -> input.setText(value));
    }

    private void setSpinner(Spinner spinner, int index) {
        if (spinner == null) return;
        handler.post(() -> spinner.setSelection(index));
    }

    private void setTopStatus(String message, int background, int textColor) {
        if (topStatusChip == null) return;
        handler.post(() -> {
            topStatusChip.setText(message);
            topStatusChip.setTextColor(textColor);
            topStatusChip.setBackground(rounded(background, dp(24)));
            topStatusChip.animate().cancel();
            topStatusChip.setScaleX(1f);
            topStatusChip.setScaleY(1f);
            topStatusChip.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).withEndAction(() ->
                    topStatusChip.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            ).start();
        });
    }

    private void showDateTimePicker(EditText target) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dateDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    TimePickerDialog timeDialog = new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                Calendar picked = Calendar.getInstance();
                                picked.set(Calendar.YEAR, year);
                                picked.set(Calendar.MONTH, month);
                                picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                                picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                picked.set(Calendar.MINUTE, minute);
                                picked.set(Calendar.SECOND, 0);
                                picked.set(Calendar.MILLISECOND, 0);
                                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                                target.setText(format.format(picked.getTime()));
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    timeDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dateDialog.show();
    }

    private void switchTab(String tab) {
        currentTab = tab;
        boolean showCapture = "capture".equals(tab);
        boolean showStats = "stats".equals(tab);
        boolean showLatest = "latest".equals(tab);
        if (captureSection != null) captureSection.setVisibility(showCapture ? View.VISIBLE : View.GONE);
        if (statsSection != null) statsSection.setVisibility(showStats ? View.VISIBLE : View.GONE);
        if (latestRowsSection != null) latestRowsSection.setVisibility(showLatest ? View.VISIBLE : View.GONE);
        if (tabCapture != null) setTabState(tabCapture, showCapture);
        if (tabStats != null) setTabState(tabStats, showStats);
        if (tabLatestRows != null) setTabState(tabLatestRows, showLatest);
        if (showLatest) {
            loadRecentRows();
        }
        String readyMessage = showCapture ? "Capture tab ready" : showStats ? "Statistics tab ready" : "History tab ready";
        setTopStatus(readyMessage, Color.rgb(219, 234, 254), Color.rgb(30, 64, 175));
    }

    private void setTabState(TextView tab, boolean active) {
        tab.setTextColor(active ? Color.WHITE : COLOR_TEAL);
        tab.setBackground(rounded(active ? COLOR_TEAL : Color.rgb(240, 249, 255), dp(18)));
        tab.setScaleX(active ? 1f : 0.98f);
        tab.setScaleY(active ? 1f : 0.98f);
    }

    private void updateStreamingButton() {
        if (streamButton == null) return;
        streamButton.setText(streaming ? "Stop Streaming" : "Start Streaming");
        streamButton.setBackground(rounded(streaming ? COLOR_RED : COLOR_GREEN, dp(8)));
    }

    private void pulseButton(View view) {
        if (view == null) return;
        handler.post(() -> {
            view.animate().cancel();
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110).withEndAction(() ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            ).start();
        });
    }

    private void attachTouchFeedback(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
            }
            return false;
        });
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
