package com.huawei.satellite.explorer.model;

public class GnssSatInfo {
    public int svid;
    public float azimuth;
    public float elevation;
    public float cn0DbHz;
    public boolean usedInFix;
    public int constellationType;
    public float carrierFreqHz;

    public GnssSatInfo(int svid, float azimuth, float elevation, float cn0DbHz, boolean usedInFix, int constellationType) {
        this.svid = svid;
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.cn0DbHz = cn0DbHz;
        this.usedInFix = usedInFix;
        this.constellationType = constellationType;
    }

    public String getDisplayName() {
        return "B" + String.format("%02d", svid);
    }
}
