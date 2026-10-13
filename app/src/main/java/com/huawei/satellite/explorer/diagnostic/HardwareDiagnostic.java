package com.huawei.satellite.explorer.diagnostic;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
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
                "مدل دستگاه (Model)",
                Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")",
                true,
                "بورد: " + Build.BOARD + " | سخت‌افزار: " + Build.HARDWARE
        ));

        // 2. Baseband / Radio
        String radio = Build.getRadioVersion();
        list.add(new DiagnosticItem(
                "ورژن رادیو / بیس‌باند مودم",
                radio != null ? radio : "نامشخص",
                radio != null && !radio.isEmpty(),
                "فرم‌ور رادیویی مودم برای ارتباط بی‌سیم"
        ));

        // 3. System Properties check
        String rsmcSwitch = getSystemProperty("hw_sc.rsmc_switch", "not_set");
        list.add(new DiagnosticItem(
                "فلگ کلیدی RSMC (hw_sc.rsmc_switch)",
                rsmcSwitch,
                "true".equalsIgnoreCase(rsmcSwitch) || "1".equals(rsmcSwitch),
                "این فلگ توسط MeeTime برای فعال‌سازی کامل منوی ماهواره چک می‌شود."
        ));

        String satSupport = getSystemProperty("ro.config.hw_satellite_support", "not_set");
        list.add(new DiagnosticItem(
                "پشتیبانی ماهواره (ro.config.hw_satellite_support)",
                satSupport,
                "true".equalsIgnoreCase(satSupport) || "1".equals(satSupport),
                "پیکربندی پایه رام برای پشتیبانی ماهواره"
        ));

        String huaweiSat = getSystemProperty("ro.huawei.satellite_support", "not_set");
        list.add(new DiagnosticItem(
                "پشتیبانی ماهواره هواوی (ro.huawei.satellite_support)",
                huaweiSat,
                "true".equalsIgnoreCase(huaweiSat) || "1".equals(huaweiSat),
                "فلگ سخت‌افزاری هواوی"
        ));

        String mcTelephonySat = getSystemProperty("hw_mc.telephony.satellite", "not_set");
        list.add(new DiagnosticItem(
                "تلفنی ماهواره (hw_mc.telephony.satellite)",
                mcTelephonySat,
                "true".equalsIgnoreCase(mcTelephonySat) || "1".equals(mcTelephonySat),
                "زیرسیستم مخابرات ماهواره‌ای"
        ));

        String emuiVersion = getSystemProperty("ro.build.version.emui", "not_set");
        list.add(new DiagnosticItem(
                "نسخه رابط‌کاربری EMUI / HarmonyOS",
                emuiVersion,
                true,
                "نسخه سیستم‌عامل دستگاه شما"
        ));

        // 4. Inspect Packages
        checkPackage(context, "com.huawei.lbs", "سرویس موقعیت مکانی و درایور RSMC (com.huawei.lbs)", list);
        checkPackage(context, "com.huawei.hwvoipservice", "دیمن رادیویی میتایم (MeeTime Service)", list);
        checkPackage(context, "com.huawei.meetime", "اپلیکیشن میتایم (Huawei MeeTime)", list);

        // 5. Check Service in com.huawei.lbs
        boolean hasSmcService = checkServiceInPackage(context, "com.huawei.lbs", "com.huawei.rsmc.SmcService");
        list.add(new DiagnosticItem(
                "کشف سرویس SmcService در سیستم",
                hasSmcService ? "یافت شد (Present)" : "یافت نشد (Not Declared)",
                hasSmcService,
                "سرویس رسمی ارتباط ماهواره‌ای هواوی در لایه موقعیت مکانی"
        ));

        // 6. Inspect Device Nodes (/dev/beidou*, /dev/modem*, /dev/hw_*)
        checkDeviceNodes(list);

        // 7. Check Android Telephony Satellite Service
        checkServiceManager(list);

        return list;
    }

    private static void checkPackage(Context ctx, String pkgName, String label, List<DiagnosticItem> list) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkgName, 0);
            list.add(new DiagnosticItem(
                    label,
                    "نصب است (نسخه " + pi.versionName + " / کد " + pi.versionCode + ")",
                    true,
                    "پکیج در رام گوشی حاضر است"
            ));
        } catch (PackageManager.NameNotFoundException e) {
            list.add(new DiagnosticItem(
                    label,
                    "یافت نشد (Not Installed)",
                    false,
                    "پکیج روی این رام تعبیه نشده است"
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
                "درایورهای سطح هسته (Kernel Device Nodes)",
                count > 0 ? found.toString() : "گره مستقیم ماهواره در /dev یافت نشد",
                count > 0,
                "نودهای دسترسی مستقیم درایور رادیویی"
        ));
    }

    private static void checkServiceManager(List<DiagnosticItem> list) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);

            String[] services = {"satellite", "rsmc", "hwtelephony", "location"};
            StringBuilder sb = new StringBuilder();
            for (String s : services) {
                Object binder = getService.invoke(null, s);
                if (binder != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s).append(": فعال");
                }
            }
            list.add(new DiagnosticItem(
                    "بایندرهای فعال سیستم (ServiceManager)",
                    sb.length() > 0 ? sb.toString() : "سرویس‌های ماهواره در بایندر ثبت نشده‌اند",
                    sb.toString().contains("satellite") || sb.toString().contains("rsmc"),
                    "بررسی ثبت مستقیم در سرویس منیجر سیستمی"
            ));
        } catch (Exception e) {
            list.add(new DiagnosticItem(
                    "بررسی ServiceManager",
                    "عدم دسترسی به رفلکشن: " + e.getMessage(),
                    false,
                    "محدودیت مجوز کاربری"
            ));
        }
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
