package com.huawei.satellite.explorer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
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
import com.huawei.satellite.explorer.model.SatelliteTemplate;
import com.huawei.satellite.explorer.model.SmcContentType;
import com.huawei.satellite.explorer.model.SmcMessagePacket;
import com.huawei.satellite.explorer.service.RsmcServiceBridge;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements RsmcServiceBridge.BridgeListener {

    private TabLayout tabLayout;
    private View viewDiagnostic, viewService, viewTransmitter, viewLauncher;
    private LinearLayout containerDiagnosticResults;
    private TextView tvLogOutput;
    private ScrollView scrollLog;
    private EditText etTargetPackage, etCustomText, etRecipient;
    private Spinner spSendMode, spTemplate;
    private TextView tvGpsCoords, tvAdbCommands;

    private RsmcServiceBridge mBridge;
    private double currentLat = 35.6892;
    private double currentLon = 51.3890;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBridge = new RsmcServiceBridge(this);
        mBridge.setListener(this);

        initViews();
        setupTabs();
        setupTransmitterUI();
        setupLauncherUI();
        requestPermissions();

        // Run initial quick diagnostic
        runDiagnostic();
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        viewDiagnostic = findViewById(R.id.viewDiagnostic);
        viewService = findViewById(R.id.viewService);
        viewTransmitter = findViewById(R.id.viewTransmitter);
        viewLauncher = findViewById(R.id.viewLauncher);

        containerDiagnosticResults = findViewById(R.id.containerDiagnosticResults);
        tvLogOutput = findViewById(R.id.tvLogOutput);
        scrollLog = findViewById(R.id.scrollLog);
        findViewById(R.id.btnClearLog).setOnClickListener(v -> tvLogOutput.setText(""));

        etTargetPackage = findViewById(R.id.etTargetPackage);
        findViewById(R.id.btnRunDiagnostic).setOnClickListener(v -> runDiagnostic());

        findViewById(R.id.btnBindService).setOnClickListener(v -> {
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
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_diagnostic));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_service));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_transmitter));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_launcher));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewDiagnostic.setVisibility(View.GONE);
                viewService.setVisibility(View.GONE);
                viewTransmitter.setVisibility(View.GONE);
                viewLauncher.setVisibility(View.GONE);

                switch (tab.getPosition()) {
                    case 0: viewDiagnostic.setVisibility(View.VISIBLE); break;
                    case 1: viewService.setVisibility(View.VISIBLE); break;
                    case 2: viewTransmitter.setVisibility(View.VISIBLE); break;
                    case 3: viewLauncher.setVisibility(View.VISIBLE); break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
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
                "com.huawei.meetime", "com.huawei.meetime.hismc.message.chat.ui.SmcMessageChatActivity"));

        findViewById(R.id.btnLaunchSearchSate).setOnClickListener(v -> launchMeeTimeActivity(
                "com.huawei.meetime", "com.huawei.meetime.hismc.message.searchsatellite.SearchSatelliteActivity"));

        findViewById(R.id.btnLaunchQuickHelp).setOnClickListener(v -> launchMeeTimeActivity(
                "com.huawei.meetime", "com.huawei.meetime.hismc.message.quickhelp.QuickHelpActivity"));

        tvAdbCommands = findViewById(R.id.tvAdbCommands);
        tvAdbCommands.setText(
                "# فعال‌سازی فلگ ماهواره در تنظیمات سیستمی هواوی:\n" +
                "adb shell setprop hw_sc.rsmc_switch true\n" +
                "adb shell settings put global hw_sc.rsmc_switch 1\n" +
                "adb shell settings put system hw_sc.rsmc_switch 1\n\n" +
                "# راه‌اندازی مستقیم اکتیویتی ماهواره در میتایم:\n" +
                "adb shell am start -n com.huawei.meetime/com.huawei.meetime.hismc.message.chat.ui.SmcMessageChatActivity"
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
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (loc != null) {
                currentLat = loc.getLatitude();
                currentLon = loc.getLongitude();
                tvGpsCoords.setText(String.format(Locale.US, "GPS: %.5f, %.5f", currentLat, currentLon));
                log(String.format(Locale.US, "موقعیت مکانی ثبت شد: %.5f, %.5f", currentLat, currentLon));
            } else {
                Toast.makeText(this, "موقعیت یافت نشد، از مختصات پیش‌فرض استفاده شد.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestPermissions() {
        String[] perms = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECORD_AUDIO
        };
        ActivityCompat.requestPermissions(this, perms, 100);
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
