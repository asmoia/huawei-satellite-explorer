package com.huawei.satellite.explorer.model;

import java.util.ArrayList;
import java.util.List;

public class SatelliteTemplate {
    private final int id;
    private final String titleFa;
    private final String contentEn;
    private final String contentZh;

    public SatelliteTemplate(int id, String titleFa, String contentEn, String contentZh) {
        this.id = id;
        this.titleFa = titleFa;
        this.contentEn = contentEn;
        this.contentZh = contentZh;
    }

    public int getId() { return id; }
    public String getTitleFa() { return titleFa; }
    public String getTitleEn() { return contentEn; }
    public String getContentEn() { return contentEn; }
    public String getContentZh() { return contentZh; }

    @Override
    public String toString() {
        return contentEn;
    }

    public static List<SatelliteTemplate> getDefaultTemplates() {
        List<SatelliteTemplate> list = new ArrayList<>();
        list.add(new SatelliteTemplate(1, "Trapped - Urgent rescue needed", "Trapped - Urgent rescue needed", "我被困，急需救援"));
        list.add(new SatelliteTemplate(2, "Traffic Accident", "Help, I've been in a car accident", "发生车祸，请求救援"));
        list.add(new SatelliteTemplate(3, "Severe Weather Hazard", "Severe weather encountered, need help", "遭遇恶劣天气，需要帮助"));
        list.add(new SatelliteTemplate(4, "Lost in Wild", "I'm lost and need help", "我迷路了，请求帮助"));
        list.add(new SatelliteTemplate(5, "Medical Emergency", "Medical emergency, need assistance", "身体不适，需要医疗救援"));
        list.add(new SatelliteTemplate(6, "Camping - Safe", "Camping outdoors, all safe", "正在露营，一切安好"));
        list.add(new SatelliteTemplate(7, "Mountaineering - Safe", "Climbing mountains, all safe", "正在登山，一切良好"));
        list.add(new SatelliteTemplate(8, "Driving Off-grid", "Driving off-grid, don't worry", "正在驾驶途中，无需担心"));
        list.add(new SatelliteTemplate(9, "Arrived Safely", "Arrived safely at destination", "已安全到达，请勿挂念"));
        return list;
    }
}
