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
import com.huawei.satellite.explorer.model.SatelliteMath;
import com.huawei.satellite.explorer.model.SatelliteTarget;
import com.huawei.satellite.explorer.service.RsmcServiceBridge;
import com.huawei.satellite.explorer.view.GnssSkyView;
import com.huawei.satellite.explorer.view.HuaweiCinematicAlignmentView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements RsmcServiceBridge.BridgeListener, SensorEventListener {

    private TabLayout tabLayout;
    private View viewAlign, viewGnss, viewChannels, viewTools;
    private LinearLayout containerDiagnosticResults, containerSatCatalog;
    private TextView tvLogOutput;
    private ScrollView scrollLog;

    // Alignment UI (Huawei Cinematic Screen)
    private Spinner spSatellites;
    private TextView tvAzimuthValue, tvElevationValue, tvSkewValue;
    private HuaweiCinematicAlignmentView cinematicAlignmentView;
    private MaterialCardView cardAlignmentStatus;
    private TextView tvAlignmentInstruction, tvLiveAttitude;

    // GNSS Sky Dome UI
    private GnssSkyView gnssSkyView;
    private TextView tvBdsStats, tvGpsStats, tvGlonassStats, tvGalileoStats, tvGnssSignalQuality;

    // Sensors & Location
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private LocationManager locationManager;
    private GnssStatus.Callback gnssCallback;
    private Vibrator vibrator;

    private RsmcServiceBridge mBridge;
    private double currentLat = 35.6892; // Default Tehran
    private double currentLon = 51.3890;
    private SatelliteTarget selectedSatellite;
    private List<SatelliteTarget> satelliteList;
    private boolean lastLocked = false;

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

        satelliteList = SatelliteTarget.getPresetSatellites();
        selectedSatellite = satelliteList.get(0); // Default Yahsat 1A

        initViews();
        setupTabs();
        setupSatelliteSpinner();
        setupCatalogUI();
        initSensorsAndGnss();
        requestAppPermissions();

        // Run hardware audit
        runDiagnostic();
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        viewAlign = findViewById(R.id.viewAlign);
        viewGnss = findViewById(R.id.viewGnss);
        viewChannels = findViewById(R.id.viewChannels);
        viewTools = findViewById(R.id.viewTools);

        // Alignment Views
        spSatellites = findViewById(R.id.spSatellites);
        tvAzimuthValue = findViewById(R.id.tvAzimuthValue);
        tvElevationValue = findViewById(R.id.tvElevationValue);
        tvSkewValue = findViewById(R.id.tvSkewValue);
        cinematicAlignmentView = findViewById(R.id.cinematicAlignmentView);
        cardAlignmentStatus = findViewById(R.id.cardAlignmentStatus);
        tvAlignmentInstruction = findViewById(R.id.tvAlignmentInstruction);
        tvLiveAttitude = findViewById(R.id.tvLiveAttitude);

        cinematicAlignmentView.setAlignmentListener((isLocked, offsetDegrees) -> runOnUiThread(() -> {
            if (isLocked != lastLocked) {
                lastLocked = isLocked;
                if (isLocked) {
                    cardAlignmentStatus.setCardBackgroundColor(Color.parseColor("#064E3B")); // Emerald Green
                    tvAlignmentInstruction.setText("🎯 SIGNAL LOCKED (100% Alignment)! Keep phone steady.");
                    triggerHaptic();
                    log("[LOCKED] Perfect alignment with " + selectedSatellite.name + " (Offset: " + String.format(Locale.US, "%.1f°", offsetDegrees) + ")");
                } else {
                    cardAlignmentStatus.setCardBackgroundColor(Color.parseColor("#0F172A")); // Normal Space Blue
                    tvAlignmentInstruction.setText("Rotate and tilt phone to guide ball into the center target circle.");
                }
            }
        }));

        // GNSS Sky Dome Views
        gnssSkyView = findViewById(R.id.gnssSkyView);
        tvBdsStats = findViewById(R.id.tvBdsStats);
        tvGpsStats = findViewById(R.id.tvGpsStats);
        tvGlonassStats = findViewById(R.id.tvGlonassStats);
        tvGalileoStats = findViewById(R.id.tvGalileoStats);
        tvGnssSignalQuality = findViewById(R.id.tvGnssSignalQuality);

        // Catalog & Logs
        containerSatCatalog = findViewById(R.id.containerSatCatalog);
        containerDiagnosticResults = findViewById(R.id.containerDiagnosticResults);
        tvLogOutput = findViewById(R.id.tvLogOutput);
        scrollLog = findViewById(R.id.scrollLog);
        findViewById(R.id.btnClearLog).setOnClickListener(v -> tvLogOutput.setText(""));

        // Diagnostic Tab Buttons
        findViewById(R.id.btnRunDiagnostic).setOnClickListener(v -> runDiagnostic());
        findViewById(R.id.btnBindService).setOnClickListener(v -> mBridge.bindSmcService("com.huawei.lbs"));
        findViewById(R.id.btnQueryCapabilities).setOnClickListener(v -> {
            mBridge.querySysCapability();
            mBridge.queryProductCapability();
        });
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_align));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_radar));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_channels));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_tools));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewAlign.setVisibility(View.GONE);
                viewGnss.setVisibility(View.GONE);
                viewChannels.setVisibility(View.GONE);
                viewTools.setVisibility(View.GONE);

                switch (tab.getPosition()) {
                    case 0: viewAlign.setVisibility(View.VISIBLE); break;
                    case 1: viewGnss.setVisibility(View.VISIBLE); break;
                    case 2: viewChannels.setVisibility(View.VISIBLE); break;
                    case 3: viewTools.setVisibility(View.VISIBLE); break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSatelliteSpinner() {
        ArrayAdapter<SatelliteTarget> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, satelliteList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSatellites.setAdapter(adapter);

        spSatellites.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSatellite = satelliteList.get(position);
                updateTargetLookAngles();
                log("[TARGET] Selected satellite: " + selectedSatellite.name + " (" + selectedSatellite.category + ")");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateTargetLookAngles() {
        if (selectedSatellite == null) return;

        SatelliteMath.DishLookAngles angles = SatelliteMath.calculateLookAngles(
                currentLat, currentLon, selectedSatellite.orbitalLongitude);

        tvAzimuthValue.setText(String.format(Locale.US, "%.1f°", angles.azimuth));
        tvElevationValue.setText(String.format(Locale.US, "%.1f°", angles.elevation));
        tvSkewValue.setText(String.format(Locale.US, "%+.1f°", angles.lnbSkew));

        cinematicAlignmentView.setTargetSatellite(
                selectedSatellite.name,
                (float) angles.azimuth,
                (float) angles.elevation
        );
    }

    private void setupCatalogUI() {
        containerSatCatalog.removeAllViews();

        for (SatelliteTarget sat : satelliteList) {
            SatelliteMath.DishLookAngles angles = SatelliteMath.calculateLookAngles(
                    currentLat, currentLon, sat.orbitalLongitude);

            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(14);
            card.setCardElevation(2);
            card.setUseCompatPadding(true);
            card.setCardBackgroundColor(Color.parseColor("#0F172A"));
            card.setStrokeColor(Color.parseColor("#1E293B"));
            card.setStrokeWidth(1);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(20, 16, 20, 16);

            TextView tvName = new TextView(this);
            tvName.setText("🛰️ " + sat.name);
            tvName.setTextSize(15);
            tvName.setTextColor(Color.parseColor("#00E5FF"));
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvDesc = new TextView(this);
            tvDesc.setText(sat.description);
            tvDesc.setTextSize(12);
            tvDesc.setTextColor(Color.parseColor("#94A3B8"));
            tvDesc.setPadding(0, 4, 0, 8);

            TextView tvAngles = new TextView(this);
            tvAngles.setText(String.format(Locale.US, "Dish Azimuth: %.1f°  •  Elevation: %.1f°  •  LNB Skew: %+.1f°",
                    angles.azimuth, angles.elevation, angles.lnbSkew));
            tvAngles.setTextSize(12);
            tvAngles.setTextColor(Color.parseColor("#E2E8F0"));
            tvAngles.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvFreq = new TextView(this);
            tvFreq.setText("Transponders: " + sat.sampleFrequencies);
            tvFreq.setTextSize(11);
            tvFreq.setTextColor(Color.parseColor("#FFD700"));
            tvFreq.setPadding(0, 4, 0, 0);

            layout.addView(tvName);
            layout.addView(tvDesc);
            layout.addView(tvAngles);
            layout.addView(tvFreq);
            card.addView(layout);

            card.setOnClickListener(v -> {
                int idx = satelliteList.indexOf(sat);
                if (idx >= 0 && spSatellites != null) {
                    spSatellites.setSelection(idx);
                    if (tabLayout != null) {
                        TabLayout.Tab tab = tabLayout.getTabAt(0);
                        if (tab != null) tab.select();
                    }
                }
            });

            containerSatCatalog.addView(card);
        }
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
            updateTargetLookAngles();
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
                        int bds = 0, gps = 0, glonass = 0, galileo = 0;
                        float peakCn0 = 0f;
                        int count = status.getSatelliteCount();

                        for (int i = 0; i < count; i++) {
                            int type = status.getConstellationType(i);
                            float snr = status.getCn0DbHz(i);
                            if (snr > peakCn0) peakCn0 = snr;

                            if (type == GnssStatus.CONSTELLATION_BEIDOU) bds++;
                            else if (type == GnssStatus.CONSTELLATION_GPS) gps++;
                            else if (type == GnssStatus.CONSTELLATION_GLONASS) glonass++;
                            else if (type == GnssStatus.CONSTELLATION_GALILEO) galileo++;

                            list.add(new GnssSatInfo(
                                    status.getSvid(i),
                                    status.getAzimuthDegrees(i),
                                    status.getElevationDegrees(i),
                                    snr,
                                    status.usedInFix(i),
                                    type
                            ));
                        }

                        final int fBds = bds, fGps = gps, fGlo = glonass, fGal = galileo;
                        final float fPeak = peakCn0;

                        runOnUiThread(() -> {
                            gnssSkyView.setSatellites(list, (float) Math.toDegrees(orientationAngles[0]));
                            tvBdsStats.setText("● BeiDou: " + fBds);
                            tvGpsStats.setText("● GPS: " + fGps);
                            tvGlonassStats.setText("● GLONASS: " + fGlo);
                            tvGalileoStats.setText("● Galileo: " + fGal);
                            tvGnssSignalQuality.setText(String.format(Locale.US, "Peak Signal: %.1f dB-Hz (%d Sats Tracked)", fPeak, count));
                        });
                    }
                };

                locationManager.registerGnssStatusCallback(gnssCallback, new Handler(Looper.getMainLooper()));

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5f, new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location loc) {
                        currentLat = loc.getLatitude();
                        currentLon = loc.getLongitude();
                        updateTargetLookAngles();
                        setupCatalogUI();
                    }
                });
            }
        } catch (Exception e) {
            log("[WARN] GNSS Tracking: " + e.getMessage());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientationAngles);

                float heading = (float) Math.toDegrees(orientationAngles[0]);
                if (heading < 0) heading += 360f;

                float pitch = (float) -Math.toDegrees(orientationAngles[1]);
                if (pitch < 0) pitch = 0f;
                if (pitch > 90f) pitch = 90f;

                cinematicAlignmentView.setPhoneAttitude(heading, pitch);

                float offset = cinematicAlignmentView.getOffsetDegrees();
                tvLiveAttitude.setText(String.format(Locale.US,
                        "Phone Heading: %.0f° | Pitch: %.0f° | Offset Error: %.1f°",
                        heading, pitch, offset));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void triggerHaptic() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(200);
                }
            }
        } catch (Exception ignored) {}
    }

    private void runDiagnostic() {
        try {
            containerDiagnosticResults.removeAllViews();
            log("[DIAG] Auditing system & modem hardware...");

            List<HardwareDiagnostic.DiagnosticItem> items = HardwareDiagnostic.runDiagnostic(this);
            for (HardwareDiagnostic.DiagnosticItem item : items) {
                MaterialCardView card = new MaterialCardView(this);
                card.setRadius(14);
                card.setCardElevation(2);
                card.setUseCompatPadding(true);
                card.setCardBackgroundColor(Color.parseColor("#0F172A"));

                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(20, 16, 20, 16);

                TextView tvTitle = new TextView(this);
                tvTitle.setText(item.title);
                tvTitle.setTextSize(13);
                tvTitle.setTextColor(Color.parseColor("#E2E8F0"));
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvVal = new TextView(this);
                tvVal.setText(item.value);
                tvVal.setTextSize(13);
                tvVal.setTextColor(item.passed ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));
                tvVal.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvNote = new TextView(this);
                tvNote.setText(item.note);
                tvNote.setTextSize(11);
                tvNote.setTextColor(Color.parseColor("#94A3B8"));

                layout.addView(tvTitle);
                layout.addView(tvVal);
                layout.addView(tvNote);
                card.addView(layout);
                containerDiagnosticResults.addView(card);
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
    @Override public void onServiceConnected() { log("[RSMC] Connected to SmcService."); }
    @Override public void onServiceDisconnected() { log("[RSMC] Disconnected from SmcService."); }
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
