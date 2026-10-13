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
            log("[Callback] notifyServiceStatus: " + bundleToString(bundle));
        }

        @Override
        public void notifySysStatus(Bundle bundle) throws RemoteException {
            log("[Callback] notifySysStatus: " + bundleToString(bundle));
        }

        @Override
        public void notifyCaliInfo(Bundle bundle) throws RemoteException {
            log("[Callback] notifyCaliInfo (Compass/Sensor): " + bundleToString(bundle));
        }

        @Override
        public void notifySateInfo(Bundle bundle) throws RemoteException {
            log("[Callback] notifySateInfo (Satellite Signals): " + bundleToString(bundle));
            if (listener != null) listener.onSatelliteInfo(bundle);
        }

        @Override
        public void notifySendStatus(Bundle bundle) throws RemoteException {
            log("[Callback] notifySendStatus (Transmit Status): " + bundleToString(bundle));
            if (listener != null) listener.onSendStatus(bundle);
        }

        @Override
        public void receiveSmcMessage(Bundle bundle) throws RemoteException {
            log("[Callback] receiveSmcMessage (Inbound Packet): " + bundleToString(bundle));
            if (listener != null) listener.onReceiveMessage(bundle);
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            log("[RSMC] Connected to satellite service: " + name);
            mService = IHwSmcService.Stub.asInterface(service);
            isBound = true;
            if (listener != null) listener.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            log("[RSMC] Disconnected from satellite service.");
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
        log("[RSMC] Attempting bind to " + targetPackage + "/com.huawei.rsmc.SmcService ...");

        try {
            boolean result = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            log("[RSMC] bindService result: " + result);
            return result;
        } catch (SecurityException se) {
            log("[SECURITY] Permission Denial: " + se.getMessage());
            log("Grant permission via: adb shell pm grant " + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
            return false;
        } catch (Exception e) {
            log("[ERROR] bindService failed: " + e.getMessage());
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
            log("[RSMC] Service unbound.");
        }
    }

    public int startSatelliteService() {
        if (!isConnected()) {
            log("[ERROR] Service not connected. Tap 'Connect to SmcService' first.");
            return -1;
        }
        try {
            log("[RSMC] Invoking startSmcService...");
            int result = mService.startSmcService(mCallback);
            log("[RSMC] startSmcService returned: " + result);
            return result;
        } catch (Exception e) {
            log("[ERROR] startSmcService failed: " + e.getMessage());
            return -1;
        }
    }

    public int stopSatelliteService() {
        if (!isConnected()) return -1;
        try {
            log("[RSMC] Invoking stopSmcService...");
            int result = mService.stopSmcService(mCallback);
            log("[RSMC] stopSmcService returned: " + result);
            return result;
        } catch (Exception e) {
            log("[ERROR] stopSmcService failed: " + e.getMessage());
            return -1;
        }
    }

    public Bundle querySysCapability() {
        if (!isConnected()) return null;
        try {
            log("[RSMC] Querying querySysCapability...");
            Bundle res = mService.querySysCapability();
            log("[RSMC] System Capability: " + bundleToString(res));
            return res;
        } catch (Exception e) {
            log("[ERROR] querySysCapability: " + e.getMessage());
            return null;
        }
    }

    public Bundle queryProductCapability() {
        if (!isConnected()) return null;
        try {
            log("[RSMC] Querying queryProductCapability...");
            Bundle res = mService.queryProductCapability();
            log("[RSMC] Product Capability: " + bundleToString(res));
            return res;
        } catch (Exception e) {
            log("[ERROR] queryProductCapability: " + e.getMessage());
            return null;
        }
    }

    public int sendSatelliteMessage(SmcMessagePacket packet) {
        if (!isConnected()) {
            log("[ERROR] Service not connected.");
            return -1;
        }
        try {
            Bundle bundle = packet.toBundle();
            log("[RSMC] Sending packet via sendSmcMessage (mode: " + packet.sendMode.getCode() + ")...");
            int result = mService.sendSmcMessage(packet.sendMode.getCode(), bundle);
            log("[RSMC] sendSmcMessage returned: " + result);
            return result;
        } catch (Exception e) {
            log("[ERROR] sendSmcMessage failed: " + e.getMessage());
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
