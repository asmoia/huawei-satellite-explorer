package com.huawei.satellite.explorer.model;

import android.os.Bundle;
import java.util.ArrayList;

/**
 * Packs message fields into the exact Bundle structure extracted from
 * com.huawei.himsg.smcmodel.SmcMapLocationData
 */
public class SmcMessagePacket {
    public String fromPhone = "";
    public String toPhoneNumber = "";
    public String msgContent = "";
    public String inputContent = "";
    public SmcContentType sendMode = SmcContentType.USER_INPUT;
    public boolean isCarryLocation = true;
    public double latitude = 0.0;
    public double longitude = 0.0;
    public int peopleNumNeedHelp = 1;
    public int sendTemplateCode = 0;
    public int sendTemplateVersion = 1;
    public long sendTime = System.currentTimeMillis();
    public ArrayList<String> quickHelp = new ArrayList<>();

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("fromPhone", fromPhone);
        bundle.putString("toPhoneNumber", toPhoneNumber);
        bundle.putString("msgContent", msgContent);
        bundle.putString("inputContent", inputContent);
        bundle.putInt("sendMode", sendMode.getCode());
        bundle.putBoolean("isCarryLocation", isCarryLocation);
        bundle.putDouble("doubleLatitude", latitude);
        bundle.putDouble("doubleLongitude", longitude);
        bundle.putString("latitude", String.valueOf(latitude));
        bundle.putString("longitude", String.valueOf(longitude));
        bundle.putInt("latitudeFlag", latitude >= 0 ? 0 : 1);
        bundle.putInt("longitudeFlag", longitude >= 0 ? 0 : 1);
        bundle.putInt("peopleNumNeedHelp", peopleNumNeedHelp);
        bundle.putInt("sendTemplateCode", sendTemplateCode);
        bundle.putInt("sendTemplateVersion", sendTemplateVersion);
        bundle.putLong("sendTime", sendTime);
        bundle.putStringArrayList("quickHelp", quickHelp);
        bundle.putString("marketingName", "Huawei Satellite Explorer (Pura 70 Test)");
        return bundle;
    }
}
