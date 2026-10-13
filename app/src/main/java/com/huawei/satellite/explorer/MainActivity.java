package com.huawei.satellite.explorer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import com.huawei.satellite.explorer.view.SkyRadarView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements RsmcServiceBridge.BridgeListener, SensorEventListener {

    private TabLayout tabLayout;
    private View viewRadar, viewDiagnostic, viewService, viewTransmitter, viewLauncher;
    private LinearLayout containerDiagnosticResults;
    private TextView tvLogOutput;
    private ScrollView scrollLog;
    private EditText etTargetPackage, etCustomText, etRecipient;
    private Spinner spSendMode, spTemplate;
    private TextView tvGpsCoords, tvAdbCommands;

    // Sky Radar & Calibration UI
    private SkyRadarView skyRadarView;
    private MaterialCardView cardRadarStatus;
    private TextView tvRadarStatus, tvRadarGuide, tvGeoTargetInfo, tvOrientationInfo, tvBdsCount, tvMaxSnr;

    // Sensors & GNSS
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
    private boolean lastAlignedState = false;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBridge = new RsmcServiceBridge(this);
        mBridge.setListener(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initViews();
        setupTabs();
        setupTransmitterUI();
        setupLauncherUI();
        initSensorsAndGnss();
        requestAppPermissions();

        // Run initial quick diagnostic
        runDiagnostic();
    }

    private void checkSecureSettingsPermission() {
        int perm = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS);
        if (perm != PackageManager.PERMISSION_GRANTED) {
            log("⚠️ هشدار: مجوز WRITE_SECURE_SETTINGS به این برنامه داده نشده است!");
            log("📌 در CMD کامپیوتر این دستور را وارد کنید:");
            log("adb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
        } else {
            log("✅ مجوز WRITE_SECURE_SETTINGS فعال است.");
        }
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        viewRadar = findViewById(R.id.viewRadar);
        viewDiagnostic = findViewById(R.id.viewDiagnostic);
        viewService = findViewById(R.id.viewService);
        viewTransmitter = findViewById(R.id.viewTransmitter);
        viewLauncher = findViewById(R.id.viewLauncher);

        // Sky Radar views
        skyRadarView = findViewById(R.id.skyRadarView);
        cardRadarStatus = findViewById(R.id.cardRadarStatus);
        tvRadarStatus = findViewById(R.id.tvRadarStatus);
        tvRadarGuide = findViewById(R.id.tvRadarGuide);
        tvGeoTargetInfo = findViewById(R.id.tvGeoTargetInfo);
        tvOrientationInfo = findViewById(R.id.tvOrientationInfo);
        tvBdsCount = findViewById(R.id.tvBdsCount);
        tvMaxSnr = findViewById(R.id.tvMaxSnr);

        skyRadarView.setAlignmentListener((isAligned, offsetDegrees) -> runOnUiThread(() -> {
            if (isAligned != lastAlignedState) {
                lastAlignedState = isAligned;
                if (isAligned) {
                    cardRadarStatus.setCardBackgroundColor(Color.parseColor("#1B5E20")); // Dark Green
                    tvRadarStatus.setText("🎯 تراز کامل با ماهواره بیدو (GEO-5) برقرار شد!");
                    tvRadarGuide.setText("زاویه سیگنال در حالت ایده‌آل (۹۵٪+). آماده دریافت بیشترین توان سیگنال ماهواره.");
                    triggerHaptic();
                    log("🎯 وضعیت تراز: با موفقیت به سمت ماهواره بیدو هم‌راستا شد (انحراف: " + String.format(Locale.US, "%.1f°", offsetDegrees) + ")");
                } else {
                    cardRadarStatus.setCardBackgroundColor(Color.parseColor("#1A237E")); // Indigo Blue
                    tvRadarStatus.setText("🛰️ در حال جستجو... گوشی را با هدف طلایی تراز کنید");
                    tvRadarGuide.setText("گوشی را به سمت جنوب (آزیموت ~۱۷۰°) گرفته و ۴۵ الی ۵۰ درجه رو به آسمان ببرید.");
                }
            }
        }));

        containerDiagnosticResults = findViewById(R.id.containerDiagnosticResults);
        tvLogOutput = findViewById(R.id.tvLogOutput);
        scrollLog = findViewById(R.id.scrollLog);
        findViewById(R.id.btnClearLog).setOnClickListener(v -> tvLogOutput.setText(""));

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
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_radar));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_diagnostic));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_service));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_transmitter));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_launcher));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewRadar.setVisibility(View.GONE);
                viewDiagnostic.setVisibility(View.GONE);
                viewService.setVisibility(View.GONE);
                viewTransmitter.setVisibility(View.GONE);
                viewLauncher.setVisibility(View.GONE);

                switch (tab.getPosition()) {
                    case 0: viewRadar.setVisibility(View.VISIBLE); break;
                    case 1: viewDiagnostic.setVisibility(View.VISIBLE); break;
                    case 2: viewService.setVisibility(View.VISIBLE); break;
                    case 3: viewTransmitter.setVisibility(View.VISIBLE); break;
                    case 4: viewLauncher.setVisibility(View.VISIBLE); break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void initSensorsAndGnss() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor == null) {
                rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
            }
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        updateGeoTarget(currentLat, currentLon);
    }

    private void startGnssTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

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

                    final int finalBdsCount = bdsCount;
                    final float finalMaxSnr = maxSnr;
                    runOnUiThread(() -> {
                        skyRadarView.updateSatellites(list);
                        tvBdsCount.setText("ماهواره بیدو فعال: " + finalBdsCount);
                        tvMaxSnr.setText(String.format(Locale.US, "حداکثر سیگنال: %.1f dB", finalMaxSnr));
                    });
                }
            };

            locationManager.registerGnssStatusCallback(gnssCallback, new Handler(Looper.getMainLooper()));

            // Also register location listener to keep GPS updated
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5f, new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location loc) {
                    currentLat = loc.getLatitude();
                    currentLon = loc.getLongitude();
                    updateGeoTarget(currentLat, currentLon);
                    tvGpsCoords.setText(String.format(Locale.US, "GPS: %.5f, %.5f", currentLat, currentLon));
                }
            });
        }
    }

    private void updateGeoTarget(double lat, double lon) {
        double Re = 6378.137;
        double Rs = 42164.0;
        double satLon = 58.75; // BeiDou GEO-5 orbital slot

        double phi = Math.toRadians(lat);
        double deltaLam = Math.toRadians(satLon - lon);

        double az = Math.toDegrees(Math.atan2(Math.sin(deltaLam), Math.tan(phi)));
        targetAzimuth = (float) ((180.0 - az) % 360.0);
        if (targetAzimuth < 0) targetAzimuth += 360.0f;

        double cosGamma = Math.cos(phi) * Math.cos(deltaLam);
        double sinGamma = Math.sin(Math.acos(cosGamma));
        targetElevation = (float) Math.toDegrees(Math.atan((cosGamma - (Re / Rs)) / sinGamma));

        skyRadarView.setTargetPosition(targetAzimuth, targetElevation);
        tvGeoTargetInfo.setText(String.format(Locale.US,
                "هدف ثابت: BeiDou GEO-5 (۵۸.۷۵°E) | آزیموت: %.1f° | شیب: %.1f°", targetAzimuth, targetElevation));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            // Heading: 0 to 360
            float heading = (float) Math.toDegrees(orientationAngles[0]);
            if (heading < 0) heading += 360f;

            // Pitch: -90 (vertical down) to +90 (vertical up)
            // When holding phone angled towards the sky, pitch is positive elevation
            float pitch = (float) -Math.toDegrees(orientationAngles[1]);
            if (pitch < 0) pitch = 0f;
            if (pitch > 90f) pitch = 90f;

            skyRadarView.setOrientation(heading, pitch);

            float offset = skyRadarView.getCurrentOffset();
            tvOrientationInfo.setText(String.format(Locale.US,
                    "جهت گوشی: آزیموت %.0f° | شیب %.0f° | انحراف از ماهواره: %.1f°",
                    heading, pitch, offset));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void triggerHaptic() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(180);
            }
        }
    }

    private void runDiagnostic() {
        containerDiagnosticResults.removeAllViews();
        log("در حال اجرای اسکن کامل سخت‌افزار، بیس‌باند و سرویس‌های ماهواره...");

        List<HardwareDiagnostic.DiagnosticItem> items = HardwareDiagnostic.runDiagnostic(this);
        for (HardwareDiagnostic.DiagnosticItem item : items) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(16);
            card.setCardElevation(2);
            card.setUseCompatPadding(true);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(24, 20, 24, 20);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(item.title);
            tvTitle.setTextSize(14);
            tvTitle.setTextColor(Color.parseColor("#333333"));
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvVal = new TextView(this);
            tvVal.setText(item.value);
            tvVal.setTextSize(13);
            tvVal.setTextColor(item.passed ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
            tvVal.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvNote = new TextView(this);
            tvNote.setText(item.note);
            tvNote.setTextSize(11);
            tvNote.setTextColor(Color.parseColor("#777777"));

            layout.addView(tvTitle);
            layout.addView(tvVal);
            layout.addView(tvNote);
            card.addView(layout);
            containerDiagnosticResults.addView(card);
        }
    }

    private void setupTransmitterUI() {
        spSendMode = findViewById(R.id.spSendMode);
        spTemplate = findViewById(R.id.spTemplate);
        etCustomText = findViewById(R.id.etCustomText);
        etRecipient = findViewById(R.id.etRecipient);
        tvGpsCoords = findViewById(R.id.tvGpsCoords);

        // Fill SendMode spinner
        ArrayAdapter<SmcContentType> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SmcContentType.values());
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSendMode.setAdapter(modeAdapter);

        // Fill Template spinner
        List<SatelliteTemplate> templates = SatelliteTemplate.getDefaultTemplates();
        ArrayAdapter<SatelliteTemplate> templateAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, templates);
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTemplate.setAdapter(templateAdapter);

        spTemplate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SatelliteTemplate t = templates.get(position);
                etCustomText.setText(t.getTitleFa());
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

            log("ارسال پکت ماهواره از طریق RSMC...");
            int code = mBridge.sendSatelliteMessage(packet);
            Toast.makeText(this, "نتیجه ارسال: " + code, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupLauncherUI() {
        findViewById(R.id.btnLaunchChat).setOnClickListener(v -> launchMeeTimeActivity(
                "com.huawei.meetime", "com.huawei.meetime.SmcChatActivity"));

        findViewById(R.id.btnLaunchSearchSate).setOnClickListener(v -> {
            // Jump directly to radar tab
            if (tabLayout != null) {
                TabLayout.Tab tab = tabLayout.getTabAt(0);
                if (tab != null) tab.select();
            }
        });

        findViewById(R.id.btnLaunchQuickHelp).setOnClickListener(v -> launchMeeTimeActivity(
                "com.huawei.meetime", "com.huawei.meetime.SmcChatActivity"));

        tvAdbCommands = findViewById(R.id.tvAdbCommands);
        tvAdbCommands.setText(
                "# فعال‌سازی مجوزهای ماهواره‌ای از طریق ADB:\n" +
                "adb shell pm grant com.huawei.satellite.explorer android.permission.WRITE_SECURE_SETTINGS\n" +
                "adb shell pm grant com.huawei.satellite.explorer android.permission.ACCESS_FINE_LOCATION\n\n" +
                "# استارت مستقیم سرویس ماهواره در هسته سیستم:\n" +
                "adb shell am start-service -n com.huawei.lbs/com.huawei.rsmc.SmcService\n\n" +
                "# مشاهده لاگ‌های زنده رادیویی ماهواره:\n" +
                "adb logcat -d | findstr /i \"rsmc sateradio SmcService\""
        );
    }

    private void launchMeeTimeActivity(String pkg, String cls) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, cls));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            log("اکتیویتی با موفقیت فراخوانی شد: " + cls);
        } catch (Exception e) {
            log("خطا در فراخوانی " + cls + ": " + e.getMessage());
            Toast.makeText(this, "امکان باز کردن صفحه وجود ندارد: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }
        if (locationManager != null) {
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) {
                loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (loc != null) {
                currentLat = loc.getLatitude();
                currentLon = loc.getLongitude();
                updateGeoTarget(currentLat, currentLon);
                tvGpsCoords.setText(String.format(Locale.US, "GPS: %.5f, %.5f", currentLat, currentLon));
                log(String.format(Locale.US, "موقعیت مکانی ثبت شد: %.5f, %.5f", currentLat, currentLon));
            } else {
                Toast.makeText(this, "موقعیت یافت نشد، از مختصات پیش‌فرض استفاده شد.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestAppPermissions() {
        String[] perms = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECORD_AUDIO
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
        if (sensorManager != null && rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
        startGnssTracking();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (locationManager != null && gnssCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssCallback);
            gnssCallback = null;
        }
    }

    private void log(String msg) {
        runOnUiThread(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            tvLogOutput.append("[" + time + "] " + msg + "\n");
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    // BridgeListener callbacks
    @Override public void onServiceConnected() { log("وضعیت بریج: سرویس فعال و آماده پاسخگویی است."); }
    @Override public void onServiceDisconnected() { log("وضعیت بریج: ارتباط با سرویس قطع شد."); }
    @Override public void onLog(String message) { log(message); }
    @Override public void onSatelliteInfo(Bundle bundle) { log("📡 دریافت داده سیگنال ماهواره: " + RsmcServiceBridge.bundleToString(bundle)); }
    @Override public void onSendStatus(Bundle bundle) { log("📤 وضعیت ارسال پکت: " + RsmcServiceBridge.bundleToString(bundle)); }
    @Override public void onReceiveMessage(Bundle bundle) { log("📥 پیام ماهواره‌ای جدید: " + RsmcServiceBridge.bundleToString(bundle)); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBridge.unbind();
    }
}
