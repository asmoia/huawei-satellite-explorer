package com.huawei.satellite.explorer.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.huawei.rsmc.IHwSmcCallBack;
import com.huawei.rsmc.IHwSmcService;
import com.huawei.satellite.explorer.model.SmcMessagePacket;

public class RsmcServiceBridge {
    private static final String TAG = "RsmcServiceBridge";

    public interface BridgeListener {
        void onServiceConnected();
        void onServiceDisconnected();
        void onLog(String message);
        void onSatelliteInfo(Bundle bundle);
        void onSendStatus(Bundle bundle);
        void onReceiveMessage(Bundle bundle);
    }

    private final Context context;
    private IHwSmcService mService;
    private boolean isBound = false;
    private BridgeListener listener;

    private final IHwSmcCallBack.Stub mCallback = new IHwSmcCallBack.Stub() {
        @Override
        public void notifyServiceStatus(Bundle bundle) throws RemoteException {
            log("notifyServiceStatus: " + bundleToString(bundle));
        }

        @Override
        public void notifySysStatus(Bundle bundle) throws RemoteException {
            log("notifySysStatus: " + bundleToString(bundle));
        }

        @Override
        public void notifyCaliInfo(Bundle bundle) throws RemoteException {
            log("notifyCaliInfo (سنسور/کالیبراسیون): " + bundleToString(bundle));
        }

        @Override
        public void notifySateInfo(Bundle bundle) throws RemoteException {
            log("notifySateInfo (اطلاعات سیگنال ماهواره): " + bundleToString(bundle));
            if (listener != null) listener.onSatelliteInfo(bundle);
        }

        @Override
        public void notifySendStatus(Bundle bundle) throws RemoteException {
            log("notifySendStatus (وضعیت ارسال پیام): " + bundleToString(bundle));
            if (listener != null) listener.onSendStatus(bundle);
        }

        @Override
        public void receiveSmcMessage(Bundle bundle) throws RemoteException {
            log("receiveSmcMessage (پیام دریافتی از ماهواره): " + bundleToString(bundle));
            if (listener != null) listener.onReceiveMessage(bundle);
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            log("✅ با موفقیت به سرویس ماهواره متصل شد: " + name);
            mService = IHwSmcService.Stub.asInterface(service);
            isBound = true;
            if (listener != null) listener.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            log("❌ اتصال به سرویس ماهواره قطع شد.");
            mService = null;
            isBound = false;
            if (listener != null) listener.onServiceDisconnected();
        }
    };

    public RsmcServiceBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(BridgeListener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return isBound && mService != null;
    }

    public boolean bindSmcService(String targetPackage) {
        Intent intent = new Intent();
        if (targetPackage == null || targetPackage.isEmpty()) {
            targetPackage = "com.huawei.lbs";
        }
        intent.setClassName(targetPackage, "com.huawei.rsmc.SmcService");
        log("در حال تلاش برای اتصال به " + targetPackage + "/com.huawei.rsmc.SmcService ...");

        try {
            boolean result = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            log("نتیجه درخواست bindService: " + result);
            return result;
        } catch (Exception e) {
            log("خطا هنگام bindService: " + e.getMessage());
            return false;
        }
    }

    public void unbind() {
        if (isBound) {
            try {
                context.unbindService(mConnection);
            } catch (Exception ignored) {}
            isBound = false;
            mService = null;
        }
    }

    public int startSatelliteService() {
        if (!isConnected()) {
            log("خطا: سرویس متصل نیست!");
            return -1;
        }
        try {
            log("فراخوانی startSmcService...");
            int result = mService.startSmcService(mCallback);
            log("کد بازگشتی startSmcService: " + result);
            return result;
        } catch (Exception e) {
            log("خطا در startSmcService: " + e.getMessage());
            return -1;
        }
    }

    public int stopSatelliteService() {
        if (!isConnected()) return -1;
        try {
            log("فراخوانی stopSmcService...");
            int result = mService.stopSmcService(mCallback);
            log("کد بازگشتی stopSmcService: " + result);
            return result;
        } catch (Exception e) {
            log("خطا در stopSmcService: " + e.getMessage());
            return -1;
        }
    }

    public Bundle querySysCapability() {
        if (!isConnected()) return null;
        try {
            log("در حال استعلام قابلیت‌های سیستم (querySysCapability)...");
            Bundle res = mService.querySysCapability();
            log("پاسخ قابلیت سیستم: " + bundleToString(res));
            return res;
        } catch (Exception e) {
            log("خطا در querySysCapability: " + e.getMessage());
            return null;
        }
    }

    public Bundle queryProductCapability() {
        if (!isConnected()) return null;
        try {
            log("در حال استعلام قابلیت‌های محصول و مودم (queryProductCapability)...");
            Bundle res = mService.queryProductCapability();
            log("پاسخ قابلیت محصول: " + bundleToString(res));
            return res;
        } catch (Exception e) {
            log("خطا در queryProductCapability: " + e.getMessage());
            return null;
        }
    }

    public int sendSatelliteMessage(SmcMessagePacket packet) {
        if (!isConnected()) {
            log("خطا: سرویس متصل نیست!");
            return -1;
        }
        try {
            Bundle bundle = packet.toBundle();
            log("در حال ارسال پکت به sendSmcMessage (نوع: " + packet.sendMode.getCode() + ")...");
            log("محتوای پکت: " + bundleToString(bundle));
            int result = mService.sendSmcMessage(packet.sendMode.getCode(), bundle);
            log("نتیجه فراخوانی sendSmcMessage: " + result);
            return result;
        } catch (Exception e) {
            log("خطا در sendSmcMessage: " + e.getMessage());
            return -1;
        }
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        if (listener != null) listener.onLog(msg);
    }

    public static String bundleToString(Bundle bundle) {
        if (bundle == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String key : bundle.keySet()) {
            if (!first) sb.append(", ");
            sb.append(key).append("=").append(bundle.get(key));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
