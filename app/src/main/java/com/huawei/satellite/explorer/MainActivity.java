package com.huawei.satellite.explorer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.huawei.satellite.explorer.diagnostic.HardwareDiagnostic;
import com.huawei.satellite.explorer.model.GnssSatInfo;
import com.huawei.satellite.explorer.model.SatelliteTemplate;
import com.huawei.satellite.explorer.model.SmcContentType;
import com.huawei.satellite.explorer.model.SmcMessagePacket;
import com.huawei.satellite.explorer.service.RsmcServiceBridge;
import com.huawei.satellite.explorer.view.MeeTimeSatelliteRadarView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements RsmcServiceBridge.BridgeListener, SensorEventListener {

    private TabLayout tabLayout;
    private View viewAlign, viewTransmitter, viewService, viewDiagnostic;
    private LinearLayout containerDiagnosticResults;
    private TextView tvLogOutput;
    private ScrollView scrollLog;

    // MeeTime Alignment UI
    private MeeTimeSatelliteRadarView meeTimeRadarView;
    private MaterialCardView cardMeeTimeStatus;
    private TextView tvSignalIcon, tvMeeTimeTitle, tvLockBadge, tvMeeTimeInstruction;
    private TextView tvTargetSatDetails, tvPhoneAttitude, tvTrackedBdsCount, tvPeakSignal;

    // Transmitter UI
    private EditText etTargetPackage, etCustomText, etRecipient;
    private Spinner spSendMode, spTemplate;
    private TextView tvGpsCoords;

    // Hardware & Sensors
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private LocationManager locationManager;
    private GnssStatus.Callback gnssCallback;
    private Vibrator vibrator;

    private RsmcServiceBridge mBridge;
    private double currentLat = 35.6892;
    private double currentLon = 51.3890;
    private float targetAzimuth = 169.9f;
    private float targetElevation = 47.8f;
    private boolean lastLockedState = false;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            mBridge = new RsmcServiceBridge(this);
            mBridge.setListener(this);
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignored) {}

        initViews();
        setupTabs();
        setupTransmitterUI();
        initSensorsAndGnss();
        requestAppPermissions();

        // Run hardware audit in background
        runDiagnostic();
    }

    private void checkSecureSettingsPermission() {
        try {
            int perm = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS);
            if (perm != PackageManager.PERMISSION_GRANTED) {
                log("[WARN] WRITE_SECURE_SETTINGS not granted. Run: adb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
            } else {
                log("[OK] WRITE_SECURE_SETTINGS permission granted.");
            }
        } catch (Exception ignored) {}
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        viewAlign = findViewById(R.id.viewAlign);
        viewTransmitter = findViewById(R.id.viewTransmitter);
        viewService = findViewById(R.id.viewService);
        viewDiagnostic = findViewById(R.id.viewDiagnostic);

        // MeeTime Alignment views
        meeTimeRadarView = findViewById(R.id.meeTimeRadarView);
        cardMeeTimeStatus = findViewById(R.id.cardMeeTimeStatus);
        tvSignalIcon = findViewById(R.id.tvSignalIcon);
        tvMeeTimeTitle = findViewById(R.id.tvMeeTimeTitle);
        tvLockBadge = findViewById(R.id.tvLockBadge);
        tvMeeTimeInstruction = findViewById(R.id.tvMeeTimeInstruction);
        tvTargetSatDetails = findViewById(R.id.tvTargetSatDetails);
        tvPhoneAttitude = findViewById(R.id.tvPhoneAttitude);
        tvTrackedBdsCount = findViewById(R.id.tvTrackedBdsCount);
        tvPeakSignal = findViewById(R.id.tvPeakSignal);

        meeTimeRadarView.setStatusListener((isLocked, offsetDegrees, instructionEn) -> runOnUiThread(() -> {
            tvMeeTimeInstruction.setText(instructionEn);

            if (isLocked != lastLockedState) {
                lastLockedState = isLocked;
                if (isLocked) {
                    cardMeeTimeStatus.setCardBackgroundColor(Color.parseColor("#0A2818"));
                    tvSignalIcon.setText("📶");
                    tvMeeTimeTitle.setText("Satellite Connected");
                    tvLockBadge.setText("CONNECTED");
                    tvLockBadge.setTextColor(Color.parseColor("#00E676"));
                    tvLockBadge.setBackgroundColor(Color.parseColor("#2200E676"));
                    triggerHaptic();
                    log("[MeeTime] Satellite link locked (Offset: " + String.format(Locale.US, "%.1f°", offsetDegrees) + ")");
                } else {
                    cardMeeTimeStatus.setCardBackgroundColor(Color.parseColor("#10192D"));
                    tvSignalIcon.setText("🛰️");
                    tvMeeTimeTitle.setText("Search for Satellite");
                    tvLockBadge.setText("SEARCHING");
                    tvLockBadge.setTextColor(Color.parseColor("#FFB300"));
                    tvLockBadge.setBackgroundColor(Color.parseColor("#22FFB300"));
                }
            }
        }));

        findViewById(R.id.btnQuickGoToSend).setOnClickListener(v -> {
            if (tabLayout != null) {
                TabLayout.Tab tab = tabLayout.getTabAt(1);
                if (tab != null) tab.select();
            }
        });

        // Logs
        containerDiagnosticResults = findViewById(R.id.containerDiagnosticResults);
        tvLogOutput = findViewById(R.id.tvLogOutput);
        scrollLog = findViewById(R.id.scrollLog);
        findViewById(R.id.btnClearLog).setOnClickListener(v -> tvLogOutput.setText(""));

        // Service Tab controls
        etTargetPackage = findViewById(R.id.etTargetPackage);
        findViewById(R.id.btnRunDiagnostic).setOnClickListener(v -> runDiagnostic());
        findViewById(R.id.btnBindService).setOnClickListener(v -> {
            checkSecureSettingsPermission();
            String pkg = etTargetPackage.getText().toString().trim();
            mBridge.bindSmcService(pkg);
        });
        findViewById(R.id.btnUnbindService).setOnClickListener(v -> mBridge.unbind());
        findViewById(R.id.btnStartSatellite).setOnClickListener(v -> mBridge.startSatelliteService());
        findViewById(R.id.btnStopSatellite).setOnClickListener(v -> mBridge.stopSatelliteService());
        findViewById(R.id.btnQueryCapabilities).setOnClickListener(v -> {
            mBridge.querySysCapability();
            mBridge.queryProductCapability();
        });
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_align));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_message));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_rsmc));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_hardware));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewAlign.setVisibility(View.GONE);
                viewTransmitter.setVisibility(View.GONE);
                viewService.setVisibility(View.GONE);
                viewDiagnostic.setVisibility(View.GONE);

                switch (tab.getPosition()) {
                    case 0: viewAlign.setVisibility(View.VISIBLE); break;
                    case 1: viewTransmitter.setVisibility(View.VISIBLE); break;
                    case 2: viewService.setVisibility(View.VISIBLE); break;
                    case 3: viewDiagnostic.setVisibility(View.VISIBLE); break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void initSensorsAndGnss() {
        try {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                if (rotationVectorSensor == null) {
                    rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
                }
            }
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            computeGeoOrbitPosition(currentLat, currentLon);
        } catch (Exception e) {
            log("[WARN] Sensor init: " + e.getMessage());
        }
    }

    private void startGnssTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            if (locationManager != null && gnssCallback == null) {
                gnssCallback = new GnssStatus.Callback() {
                    @Override
                    public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                        List<GnssSatInfo> list = new ArrayList<>();
                        int bdsCount = 0;
                        float maxSnr = 0f;
                        int count = status.getSatelliteCount();

                        for (int i = 0; i < count; i++) {
                            if (status.getConstellationType(i) == GnssStatus.CONSTELLATION_BEIDOU) {
                                bdsCount++;
                                float snr = status.getCn0DbHz(i);
                                if (snr > maxSnr) maxSnr = snr;
                                list.add(new GnssSatInfo(
                                        status.getSvid(i),
                                        status.getAzimuthDegrees(i),
                                        status.getElevationDegrees(i),
                                        snr,
                                        status.usedInFix(i),
                                        GnssStatus.CONSTELLATION_BEIDOU
                                ));
                            }
                        }

                        final int finalBds = bdsCount;
                        final float finalSnr = maxSnr;
                        runOnUiThread(() -> {
                            meeTimeRadarView.updateSatellites(list);
                            tvTrackedBdsCount.setText("Active BDS Satellites: " + finalBds);
                            tvPeakSignal.setText(String.format(Locale.US, "Peak C/N0: %.1f dB-Hz", finalSnr));
                        });
                    }
                };

                locationManager.registerGnssStatusCallback(gnssCallback, new Handler(Looper.getMainLooper()));

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5f, new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location loc) {
                        currentLat = loc.getLatitude();
                        currentLon = loc.getLongitude();
                        computeGeoOrbitPosition(currentLat, currentLon);
                        tvGpsCoords.setText(String.format(Locale.US, "GPS: %.5f, %.5f", currentLat, currentLon));
                    }
                });
            }
        } catch (Exception e) {
            log("[WARN] GNSS start: " + e.getMessage());
        }
    }

    /**
     * Exact Huawei MeeTime Clarke Belt Geostationary Orbit Calculation for BeiDou-3 GEO-5 (58.75°E)
     */
    private void computeGeoOrbitPosition(double lat, double lon) {
        double Re = 6378.137;  // Earth equatorial radius (km)
        double Rs = 42164.137; // Geostationary orbital radius (km)
        double satLon = 58.75; // BeiDou-3 GEO-5 orbital position (covers Middle East & Iran)

        double phi = Math.toRadians(lat);
        double deltaLam = Math.toRadians(satLon - lon);

        // Azimuth formula
        double az = Math.toDegrees(Math.atan2(Math.sin(deltaLam), Math.tan(phi)));
        targetAzimuth = (float) ((180.0 - az) % 360.0);
        if (targetAzimuth < 0) targetAzimuth += 360.0f;

        // Elevation formula
        double cosGamma = Math.cos(phi) * Math.cos(deltaLam);
        cosGamma = Math.max(-1.0, Math.min(1.0, cosGamma));
        double sinGamma = Math.sqrt(Math.max(0.0, 1.0 - cosGamma * cosGamma));
        targetElevation = (float) Math.toDegrees(Math.atan((cosGamma - (Re / Rs)) / (sinGamma > 0 ? sinGamma : 0.0001)));

        meeTimeRadarView.setSatelliteTarget(targetAzimuth, targetElevation);
        tvTargetSatDetails.setText(String.format(Locale.US,
                "Target: BDS GEO-5 (58.75°E)\nAz: %.1f° • El: %.1f°", targetAzimuth, targetElevation));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientationAngles);

                // Compass Heading: 0 to 360
                float heading = (float) Math.toDegrees(orientationAngles[0]);
                if (heading < 0) heading += 360f;

                // Pitch angle: phone tilt relative to ground
                float pitch = (float) -Math.toDegrees(orientationAngles[1]);
                if (pitch < 0) pitch = 0f;
                if (pitch > 90f) pitch = 90f;

                meeTimeRadarView.setPhoneOrientation(heading, pitch);

                float offset = meeTimeRadarView.getOffsetDegrees();
                tvPhoneAttitude.setText(String.format(Locale.US,
                        "Heading: %.0f° • Pitch: %.0f°\nOffset: %.1f°", heading, pitch, offset));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void triggerHaptic() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(180);
                }
            }
        } catch (Exception ignored) {}
    }

    private void runDiagnostic() {
        try {
            containerDiagnosticResults.removeAllViews();
            log("[DIAG] Starting full Hardware and Baseband inspection...");

            List<HardwareDiagnostic.DiagnosticItem> items = HardwareDiagnostic.runDiagnostic(this);
            for (HardwareDiagnostic.DiagnosticItem item : items) {
                MaterialCardView card = new MaterialCardView(this);
                card.setRadius(14);
                card.setCardElevation(2);
                card.setUseCompatPadding(true);
                card.setCardBackgroundColor(Color.parseColor("#10192D"));

                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(24, 18, 24, 18);

                TextView tvTitle = new TextView(this);
                tvTitle.setText(item.title);
                tvTitle.setTextSize(13);
                tvTitle.setTextColor(Color.parseColor("#E0E6ED"));
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvVal = new TextView(this);
                tvVal.setText(item.value);
                tvVal.setTextSize(13);
                tvVal.setTextColor(item.passed ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));
                tvVal.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvNote = new TextView(this);
                tvNote.setText(item.note);
                tvNote.setTextSize(11);
                tvNote.setTextColor(Color.parseColor("#8A9BB0"));

                layout.addView(tvTitle);
                layout.addView(tvVal);
                layout.addView(tvNote);
                card.addView(layout);
                containerDiagnosticResults.addView(card);
            }
        } catch (Exception ignored) {}
    }

    private void setupTransmitterUI() {
        spSendMode = findViewById(R.id.spSendMode);
        spTemplate = findViewById(R.id.spTemplate);
        etCustomText = findViewById(R.id.etCustomText);
        etRecipient = findViewById(R.id.etRecipient);
        tvGpsCoords = findViewById(R.id.tvGpsCoords);

        ArrayAdapter<SmcContentType> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SmcContentType.values());
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSendMode.setAdapter(modeAdapter);

        List<SatelliteTemplate> templates = SatelliteTemplate.getDefaultTemplates();
        ArrayAdapter<SatelliteTemplate> templateAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, templates);
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTemplate.setAdapter(templateAdapter);

        spTemplate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SatelliteTemplate t = templates.get(position);
                etCustomText.setText(t.getTitleEn());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        findViewById(R.id.btnFetchGps).setOnClickListener(v -> fetchLocation());

        findViewById(R.id.btnSendSatellite).setOnClickListener(v -> {
            SmcContentType selectedMode = (SmcContentType) spSendMode.getSelectedItem();
            SatelliteTemplate selectedTemplate = (SatelliteTemplate) spTemplate.getSelectedItem();

            SmcMessagePacket packet = new SmcMessagePacket();
            packet.sendMode = selectedMode;
            packet.toPhoneNumber = etRecipient.getText().toString().trim();
            packet.inputContent = etCustomText.getText().toString().trim();
            packet.msgContent = selectedTemplate != null ? selectedTemplate.getContentEn() : packet.inputContent;
            packet.sendTemplateCode = selectedTemplate != null ? selectedTemplate.getId() : 0;
            packet.latitude = currentLat;
            packet.longitude = currentLon;
            packet.isCarryLocation = true;
            packet.peopleNumNeedHelp = 1;

            log("[RSMC] Transmitting packet to SmcService...");
            int code = mBridge.sendSatelliteMessage(packet);
            Toast.makeText(this, "Send Result: " + code, Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }
        try {
            if (locationManager != null) {
                Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) {
                    loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                if (loc != null) {
                    currentLat = loc.getLatitude();
                    currentLon = loc.getLongitude();
                    computeGeoOrbitPosition(currentLat, currentLon);
                    tvGpsCoords.setText(String.format(Locale.US, "GPS: %.5f, %.5f", currentLat, currentLon));
                    log(String.format(Locale.US, "[GPS] Lat: %.5f, Lon: %.5f", currentLat, currentLon));
                } else {
                    Toast.makeText(this, "Using default coordinates", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception ignored) {}
    }

    private void requestAppPermissions() {
        String[] perms = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.VIBRATE
        };
        ActivityCompat.requestPermissions(this, perms, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            startGnssTracking();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (sensorManager != null && rotationVectorSensor != null) {
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
            }
            startGnssTracking();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            if (locationManager != null && gnssCallback != null) {
                locationManager.unregisterGnssStatusCallback(gnssCallback);
                gnssCallback = null;
            }
        } catch (Exception ignored) {}
    }

    private void log(String msg) {
        runOnUiThread(() -> {
            try {
                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                tvLogOutput.append("[" + time + "] " + msg + "\n");
                scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
            } catch (Exception ignored) {}
        });
    }

    // BridgeListener callbacks
    @Override public void onServiceConnected() { log("[RSMC Bridge] Connected to SmcService."); }
    @Override public void onServiceDisconnected() { log("[RSMC Bridge] Disconnected from SmcService."); }
    @Override public void onLog(String message) { log(message); }
    @Override public void onSatelliteInfo(Bundle bundle) { log("[Signal] " + RsmcServiceBridge.bundleToString(bundle)); }
    @Override public void onSendStatus(Bundle bundle) { log("[Send Status] " + RsmcServiceBridge.bundleToString(bundle)); }
    @Override public void onReceiveMessage(Bundle bundle) { log("[Receive] " + RsmcServiceBridge.bundleToString(bundle)); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            mBridge.unbind();
        } catch (Exception ignored) {}
    }
}
