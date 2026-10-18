package com.hrdcoreee.tabletizer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class MainActivity extends ComponentActivity implements TabletServerService.ConnectionListener, TabletServerService.StatsListener {
    private TabletAreaView tabletAreaView;
    private TextView tvStatusBadge;
    private TextView btnSettings;
    private View overlayDisconnected;
    private TextView tvServerInfo;
    private TextView tvWifiCardText;
    private View btnHelp;

    // Floating Settings Panel
    private View panelSettingsContainer;
    private View cardSettings;
    private View btnCloseSettings;
    private TextView tvHzBadge;
    private TextView btnRatio169;
    private TextView btnRatio43;
    private TextView btnRatioFull;
    private TextView btnPosCenter;
    private TextView btnPosRightBottom;
    private TextView btnPosLeftBottom;
    private TextView btnPosRightCenter;
    private View btnInteractiveEdit;
    private View btnOpenDiagnostics;
    private View btnOpenCrashLog;

    // Diagnostics Modal
    private View panelDiagnosticsModal;
    private TextView tvDiagnosticsBody;
    private View btnCloseDiagnostics;
    private View btnDoneDiagnostics;

    private String currentClientInfo = null;
    private int currentTps = 0;
    private long currentTotalPackets = 0;
    private int currentRefreshRate = 60;
    private int badgeDisplayMode = 0; // 0: full, 1: compact, 2: dot, 3: hidden

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> startTabletService());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Force maximum display refresh rate (e.g. 120Hz/144Hz) for lowest touch latency
        applyMaxRefreshRate();

        setContentView(R.layout.activity_main);

        initViews();
        setupFloatingPanel();
        setupCrashHandler();
        checkPreviousCrash();

        TabletServerService.setConnectionListener(this);
        TabletServerService.setStatsListener(this);

        try {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
        } catch (Throwable ignored) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startTabletService();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            startTabletService();
        }
    }

    private void applyMaxRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Display display;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display = getDisplay();
                } else {
                    display = getWindowManager().getDefaultDisplay();
                }

                if (display != null) {
                    Display.Mode[] modes = display.getSupportedModes();
                    Display.Mode maxMode = null;
                    for (Display.Mode mode : modes) {
                        if (maxMode == null || mode.getRefreshRate() > maxMode.getRefreshRate()) {
                            maxMode = mode;
                        }
                    }

                    if (maxMode != null) {
                        currentRefreshRate = Math.round(maxMode.getRefreshRate());
                        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                        layoutParams.preferredDisplayModeId = maxMode.getModeId();
                        layoutParams.preferredRefreshRate = maxMode.getRefreshRate();
                        getWindow().setAttributes(layoutParams);
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("tabletizer", "Failed to force max refresh rate", t);
        }
    }

    private void initViews() {
        tabletAreaView = findViewById(R.id.tabletAreaView);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        btnSettings = findViewById(R.id.btnSettings);
        overlayDisconnected = findViewById(R.id.overlayDisconnected);
        tvServerInfo = findViewById(R.id.tvServerInfo);
        tvWifiCardText = findViewById(R.id.tvWifiCardText);
        btnHelp = findViewById(R.id.btnHelp);

        panelSettingsContainer = findViewById(R.id.panelSettingsContainer);
        cardSettings = findViewById(R.id.cardSettings);
        btnCloseSettings = findViewById(R.id.btnCloseSettings);
        tvHzBadge = findViewById(R.id.tvHzBadge);
        btnRatio169 = findViewById(R.id.btnRatio169);
        btnRatio43 = findViewById(R.id.btnRatio43);
        btnRatioFull = findViewById(R.id.btnRatioFull);
        btnPosCenter = findViewById(R.id.btnPosCenter);
        btnPosRightBottom = findViewById(R.id.btnPosRightBottom);
        btnPosLeftBottom = findViewById(R.id.btnPosLeftBottom);
        btnPosRightCenter = findViewById(R.id.btnPosRightCenter);
        btnInteractiveEdit = findViewById(R.id.btnInteractiveEdit);
        btnOpenDiagnostics = findViewById(R.id.btnOpenDiagnostics);
        btnOpenCrashLog = findViewById(R.id.btnOpenCrashLog);

        panelDiagnosticsModal = findViewById(R.id.panelDiagnosticsModal);
        tvDiagnosticsBody = findViewById(R.id.tvDiagnosticsBody);
        btnCloseDiagnostics = findViewById(R.id.btnCloseDiagnostics);
        btnDoneDiagnostics = findViewById(R.id.btnDoneDiagnostics);

        if (btnSettings != null && tabletAreaView != null) {
            updateButtonLabel();
            btnSettings.setOnClickListener(v -> openSettingsPanel());
            tabletAreaView.setOnAreaChangedListener((w, h, x, y) -> updateButtonLabel());
            tabletAreaView.setOnEditModeChangeListener(editing -> {
                btnSettings.setVisibility(editing ? View.GONE : View.VISIBLE);
                if (tvStatusBadge != null) {
                    tvStatusBadge.setVisibility(editing ? View.GONE : (badgeDisplayMode == 3 ? View.GONE : View.VISIBLE));
                }
            });
        }

        if (tvStatusBadge != null) {
            tvStatusBadge.setOnClickListener(v -> {
                badgeDisplayMode = (badgeDisplayMode + 1) % 4;
                updateStatusBadge();
            });
        }

        if (btnHelp != null) {
            btnHelp.setOnClickListener(v -> openDiagnosticsModal());
        }
    }

    private void setupFloatingPanel() {
        if (panelSettingsContainer != null) {
            panelSettingsContainer.setOnClickListener(v -> closeSettingsPanel());
        }
        if (cardSettings != null) {
            cardSettings.setOnClickListener(v -> {
                // Prevent click through to container
            });
        }
        if (btnCloseSettings != null) {
            btnCloseSettings.setOnClickListener(v -> closeSettingsPanel());
        }

        if (tvHzBadge != null) {
            tvHzBadge.setText("\u26A1 " + currentRefreshRate + " Hz Active");
        }

        if (btnRatio169 != null && tabletAreaView != null) {
            btnRatio169.setOnClickListener(v -> {
                tabletAreaView.setAspectRatio(TabletAreaView.AspectRatio.RATIO_16_9);
                updateRatioButtons();
            });
        }
        if (btnRatio43 != null && tabletAreaView != null) {
            btnRatio43.setOnClickListener(v -> {
                tabletAreaView.setAspectRatio(TabletAreaView.AspectRatio.RATIO_4_3);
                updateRatioButtons();
            });
        }
        if (btnRatioFull != null && tabletAreaView != null) {
            btnRatioFull.setOnClickListener(v -> {
                tabletAreaView.setAspectRatio(TabletAreaView.AspectRatio.RATIO_FULL);
                updateRatioButtons();
            });
        }

        if (btnPosCenter != null && tabletAreaView != null) {
            btnPosCenter.setOnClickListener(v -> {
                tabletAreaView.setAreaPosition(TabletAreaView.AreaPosition.CENTER);
                updatePositionButtons();
            });
        }
        if (btnPosRightBottom != null && tabletAreaView != null) {
            btnPosRightBottom.setOnClickListener(v -> {
                tabletAreaView.setAreaPosition(TabletAreaView.AreaPosition.RIGHT_BOTTOM);
                updatePositionButtons();
            });
        }
        if (btnPosLeftBottom != null && tabletAreaView != null) {
            btnPosLeftBottom.setOnClickListener(v -> {
                tabletAreaView.setAreaPosition(TabletAreaView.AreaPosition.LEFT_BOTTOM);
                updatePositionButtons();
            });
        }
        if (btnPosRightCenter != null && tabletAreaView != null) {
            btnPosRightCenter.setOnClickListener(v -> {
                tabletAreaView.setAreaPosition(TabletAreaView.AreaPosition.RIGHT_CENTER);
                updatePositionButtons();
            });
        }

        if (btnInteractiveEdit != null && tabletAreaView != null) {
            btnInteractiveEdit.setOnClickListener(v -> {
                closeSettingsPanel();
                tabletAreaView.setEditMode(true);
            });
        }

        if (btnOpenDiagnostics != null) {
            btnOpenDiagnostics.setOnClickListener(v -> {
                closeSettingsPanel();
                openDiagnosticsModal();
            });
        }

        if (btnOpenCrashLog != null) {
            btnOpenCrashLog.setOnClickListener(v -> {
                closeSettingsPanel();
                showCrashLogDialog();
            });
        }

        if (panelDiagnosticsModal != null) {
            panelDiagnosticsModal.setOnClickListener(v -> closeDiagnosticsModal());
        }
        if (btnCloseDiagnostics != null) {
            btnCloseDiagnostics.setOnClickListener(v -> closeDiagnosticsModal());
        }
        if (btnDoneDiagnostics != null) {
            btnDoneDiagnostics.setOnClickListener(v -> closeDiagnosticsModal());
        }
    }

    private void openSettingsPanel() {
        if (panelSettingsContainer == null) return;
        updateRatioButtons();
        updatePositionButtons();
        panelSettingsContainer.setVisibility(View.VISIBLE);
    }

    private void closeSettingsPanel() {
        if (panelSettingsContainer != null) {
            panelSettingsContainer.setVisibility(View.GONE);
        }
    }

    private void updateRatioButtons() {
        if (tabletAreaView == null) return;
        TabletAreaView.AspectRatio r = tabletAreaView.getAspectRatio();
        if (btnRatio169 != null) {
            btnRatio169.setBackgroundResource(r == TabletAreaView.AspectRatio.RATIO_16_9 ? R.drawable.button_accent_bg : R.drawable.button_pill_bg);
        }
        if (btnRatio43 != null) {
            btnRatio43.setBackgroundResource(r == TabletAreaView.AspectRatio.RATIO_4_3 ? R.drawable.button_accent_bg : R.drawable.button_pill_bg);
        }
        if (btnRatioFull != null) {
            btnRatioFull.setBackgroundResource(r == TabletAreaView.AspectRatio.RATIO_FULL ? R.drawable.button_accent_bg : R.drawable.button_pill_bg);
        }
    }

    private void updatePositionButtons() {
        if (tabletAreaView == null) return;
        TabletAreaView.AreaPosition p = tabletAreaView.getAreaPosition();
        if (btnPosCenter != null) {
            btnPosCenter.setBackgroundResource(p == TabletAreaView.AreaPosition.CENTER ? R.drawable.button_accent_bg : R.drawable.button_pill_bg);
        }
        if (btnPosRightBottom != null) {
            btnPosRightBottom.setBackgroundResource(p == TabletAreaView.AreaPosition.RIGHT_BOTTOM ? R.drawable.button_accent_bg : R.drawable.button_pill_bg);
        }
        if (btnPosLeftBottom != null) {
            btnPosLeftBottom.setBackgroundResource(p == TabletAreaView.AreaPosition.LEFT_BOTTOM ? R.drawable.button_accent_bg : R.drawable.button_pill_bg);
        }
        if (btnPosRightCenter != null) {
            btnPosRightCenter.setBackgroundResource(p == TabletAreaView.AreaPosition.RIGHT_CENTER ? R.drawable.button_accent_bg : R.drawable.button_pill_bg);
        }
    }

    private void openDiagnosticsModal() {
        if (panelDiagnosticsModal == null || tvDiagnosticsBody == null) return;
        String ip = TabletServerService.getLocalIpAddress();
        String body =
                "\uD83D\uDD0C USB CONNECTION (Lowest Latency):\n" +
                "\u2022 Connect via USB data cable to PC.\n" +
                "\u2022 Turn ON 'USB Debugging' in Developer Options.\n" +
                "\u2022 Tap 'Allow' when the RSA key authorization prompt appears.\n" +
                "\u2022 OpenTabletDriver will forward port 3240 via ADB and connect with ~0ms lag.\n\n" +
                "\uD83D\uDCE1 WI-FI CONNECTION (Wireless):\n" +
                "\u2022 Ensure Phone & PC are on the same Wi-Fi network.\n" +
                "\u2022 Your Phone Wi-Fi IP: " + (ip != null ? ip : "Not connected to Wi-Fi") + "\n" +
                "\u2022 UDP Beacon Port: 3242 (Auto-discovery)\n" +
                "\u2022 Tablet Data Port: 3240 (TCP stream)\n\n" +
                "\u26A1 HARDWARE PERFORMANCE:\n" +
                "\u2022 Display Refresh Rate: " + currentRefreshRate + " Hz\n" +
                "\u2022 Current Polling: " + currentTps + " TPS (Total: " + currentTotalPackets + " reports)";

        tvDiagnosticsBody.setText(body);
        panelDiagnosticsModal.setVisibility(View.VISIBLE);
    }

    private void closeDiagnosticsModal() {
        if (panelDiagnosticsModal != null) {
            panelDiagnosticsModal.setVisibility(View.GONE);
        }
    }

    private void setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                android.util.Log.e("tabletizer", "FATAL CRASH in " + thread.getName(), throwable);
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                throwable.printStackTrace(pw);
                getSharedPreferences("crash_log", MODE_PRIVATE)
                        .edit()
                        .putString("last_crash", sw.toString())
                        .commit();
            } catch (Throwable ignored) {
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }

    private void checkPreviousCrash() {
        SharedPreferences prefs = getSharedPreferences("crash_log", MODE_PRIVATE);
        String lastCrash = prefs.getString("last_crash", null);
        if (lastCrash != null && !lastCrash.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("\u26A0 Last Crash Details")
                    .setMessage(lastCrash)
                    .setPositiveButton("Copy & Dismiss", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", lastCrash));
                        }
                        prefs.edit().remove("last_crash").apply();
                    })
                    .setNegativeButton("Clear", (dialog, which) -> prefs.edit().remove("last_crash").apply())
                    .show();
        }
    }

    private void showCrashLogDialog() {
        SharedPreferences prefs = getSharedPreferences("crash_log", MODE_PRIVATE);
        String lastCrash = prefs.getString("last_crash", null);
        if (lastCrash == null || lastCrash.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Crash Log")
                    .setMessage("No crashes recorded! Everything is running smoothly at " + currentRefreshRate + "Hz.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            checkPreviousCrash();
        }
    }

    private void startTabletService() {
        Intent serviceIntent = new Intent(this, TabletServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void updateServerInfoDisplay() {
        String ip = TabletServerService.getLocalIpAddress();
        if (tvServerInfo != null) {
            if (ip != null) {
                tvServerInfo.setText("Port: 3240 \u2022 Wi-Fi IP: " + ip);
            } else {
                tvServerInfo.setText("Port: 3240 \u2022 Wi-Fi: Disconnected (Use USB mode)");
            }
        }
        if (tvWifiCardText != null) {
            String targetIp = ip != null ? ip : "<phone-ip>";
            tvWifiCardText.setText("1. Connect phone & PC to same Wi-Fi\n2. Open OpenTabletDriver (Auto-connects!)\n\u2022 IP: " + targetIp + " (or set in OTD Tablets menu)");
        }
    }

    private void updateStatusBadge() {
        if (tvStatusBadge == null) return;

        if (badgeDisplayMode == 3) {
            tvStatusBadge.setVisibility(View.GONE);
            return;
        }

        tvStatusBadge.setVisibility(View.VISIBLE);
        String client = currentClientInfo != null ? currentClientInfo : "Connected";

        switch (badgeDisplayMode) {
            case 0:
                tvStatusBadge.setText("\uD83D\uDFE2 " + client + " \u2022 " + currentTps + " TPS (" + currentRefreshRate + "Hz)");
                break;
            case 1:
                tvStatusBadge.setText("\uD83D\uDFE2 " + currentTps + " TPS \u2022 " + currentRefreshRate + "Hz");
                break;
            case 2:
                tvStatusBadge.setText("\uD83D\uDFE2");
                break;
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String clientInfo) {
        currentClientInfo = clientInfo;
        if (overlayDisconnected != null) {
            overlayDisconnected.setVisibility(connected ? View.GONE : View.VISIBLE);
        }
        if (connected) {
            updateStatusBadge();
            if (tabletAreaView != null) {
                tabletAreaView.syncSettingsToOtd();
            }
        } else {
            if (tvStatusBadge != null) {
                tvStatusBadge.setVisibility(View.GONE);
            }
            updateServerInfoDisplay();
        }
    }

    @Override
    public void onStatsUpdated(int tps, long totalPackets) {
        currentTps = tps;
        currentTotalPackets = totalPackets;
        if (TabletServerService.isConnected.get()) {
            updateStatusBadge();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServerInfoDisplay();
        onConnectionStateChanged(TabletServerService.isConnected.get(), TabletServerService.connectedClientInfo);
    }

    private void updateButtonLabel() {
        if (btnSettings == null || tabletAreaView == null) return;
        btnSettings.setText("\u2699 " + tabletAreaView.getCachedLabel() + " \u2022 " + currentRefreshRate + "Hz");
    }

    @Override
    public void onBackPressed() {
        if (panelDiagnosticsModal != null && panelDiagnosticsModal.getVisibility() == View.VISIBLE) {
            closeDiagnosticsModal();
            return;
        }
        if (panelSettingsContainer != null && panelSettingsContainer.getVisibility() == View.VISIBLE) {
            closeSettingsPanel();
            return;
        }
        if (tabletAreaView != null && tabletAreaView.isEditMode()) {
            tabletAreaView.setEditMode(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        TabletServerService.setConnectionListener(null);
        TabletServerService.setStatsListener(null);
        super.onDestroy();
    }
}
