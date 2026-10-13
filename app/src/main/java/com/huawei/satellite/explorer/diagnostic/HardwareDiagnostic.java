package com.huawei.satellite.explorer.diagnostic;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class HardwareDiagnostic {

    public static class DiagnosticItem {
        public String title;
        public String value;
        public boolean passed;
        public String note;

        public DiagnosticItem(String title, String value, boolean passed, String note) {
            this.title = title;
            this.value = value;
            this.passed = passed;
            this.note = note;
        }
    }

    public static List<DiagnosticItem> runDiagnostic(Context context) {
        List<DiagnosticItem> list = new ArrayList<>();

        // 1. Device Info
        list.add(new DiagnosticItem(
                "Device Hardware Model",
                Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")",
                true,
                "Board: " + Build.BOARD + " | Hardware: " + Build.HARDWARE
        ));

        // 2. Baseband / Radio
        String radio = Build.getRadioVersion();
        list.add(new DiagnosticItem(
                "Modem Baseband Version",
                radio != null ? radio : "Unknown",
                radio != null && !radio.isEmpty(),
                "Cellular & Satellite RF Baseband Firmware"
        ));

        // 3. System Properties check
        String rsmcSwitch = getSystemProperty("hw_sc.rsmc_switch", "not_set");
        list.add(new DiagnosticItem(
                "RSMC Switch Flag (hw_sc.rsmc_switch)",
                rsmcSwitch,
                "true".equalsIgnoreCase(rsmcSwitch) || "1".equals(rsmcSwitch),
                "Key switch property used by Huawei MeeTime"
        ));

        String satSupport = getSystemProperty("ro.config.hw_satellite_support", "not_set");
        list.add(new DiagnosticItem(
                "Satellite Support (ro.config.hw_satellite_support)",
                satSupport,
                "true".equalsIgnoreCase(satSupport) || "1".equals(satSupport),
                "Framework base satellite capability flag"
        ));

        String emuiVersion = getSystemProperty("ro.build.version.emui", "not_set");
        list.add(new DiagnosticItem(
                "EMUI / HarmonyOS Version",
                emuiVersion,
                true,
                "OS Build Version"
        ));

        // 4. Inspect Packages
        checkPackage(context, "com.huawei.lbs", "Huawei LBS & RSMC Driver Service (com.huawei.lbs)", list);
        checkPackage(context, "com.huawei.hwvoipservice", "MeeTime Audio Daemon (com.huawei.hwvoipservice)", list);
        checkPackage(context, "com.huawei.meetime", "Huawei MeeTime Application (com.huawei.meetime)", list);

        // 5. Check Service in com.huawei.lbs
        boolean hasSmcService = checkServiceInPackage(context, "com.huawei.lbs", "com.huawei.rsmc.SmcService");
        list.add(new DiagnosticItem(
                "SmcService Discovery",
                hasSmcService ? "Present (Registered in System)" : "Not Declared",
                hasSmcService,
                "Official Huawei RSMC Satellite IPC Endpoint"
        ));

        // 6. Inspect Device Nodes
        checkDeviceNodes(list);

        return list;
    }

    private static void checkPackage(Context ctx, String pkgName, String label, List<DiagnosticItem> list) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkgName, 0);
            list.add(new DiagnosticItem(
                    label,
                    "Installed (v" + pi.versionName + " / code " + pi.versionCode + ")",
                    true,
                    "Package present on device"
            ));
        } catch (PackageManager.NameNotFoundException e) {
            list.add(new DiagnosticItem(
                    label,
                    "Not Installed",
                    false,
                    "Package not found on this ROM"
            ));
        }
    }

    private static boolean checkServiceInPackage(Context ctx, String pkgName, String serviceClassName) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkgName, PackageManager.GET_SERVICES);
            if (pi.services != null) {
                for (ServiceInfo si : pi.services) {
                    if (serviceClassName.equals(si.name)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void checkDeviceNodes(List<DiagnosticItem> list) {
        String[] targets = {
                "/dev/beidou",
                "/dev/hw_satellite",
                "/dev/hw_modem",
                "/dev/modem_beidou",
                "/dev/ttyAMA0",
                "/dev/ttyAMA1",
                "/dev/davinci_manager"
        };
        StringBuilder found = new StringBuilder();
        int count = 0;
        for (String t : targets) {
            File f = new File(t);
            if (f.exists()) {
                if (found.length() > 0) found.append(", ");
                found.append(t);
                count++;
            }
        }
        list.add(new DiagnosticItem(
                "Kernel Radio Nodes (/dev)",
                count > 0 ? found.toString() : "Direct radio node not exposed in /dev",
                count > 0,
                "Low-level driver character device nodes"
        ));
    }

    public static String getSystemProperty(String key, String def) {
        try {
            Class<?> spClass = Class.forName("android.os.SystemProperties");
            Method getMethod = spClass.getMethod("get", String.class, String.class);
            return (String) getMethod.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }
}
