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
    public String getContentEn() { return contentEn; }
    public String getContentZh() { return contentZh; }

    @Override
    public String toString() {
        return titleFa + " (" + contentEn + ")";
    }

    public static List<SatelliteTemplate> getDefaultTemplates() {
        List<SatelliteTemplate> list = new ArrayList<>();
        list.add(new SatelliteTemplate(1, "گرفتار در بحران و نیاز فوری به نجات", "I'm trapped and urgently need help", "我被困，急需救援"));
        list.add(new SatelliteTemplate(2, "وقوع تصادف رانندگی", "Help, I've been in a car accident", "发生车祸，请求救援"));
        list.add(new SatelliteTemplate(3, "برخورد با شرایط جوی خطرناک / طوفان", "I've run into severe weather and need help", "遭遇恶劣天气，需要帮助"));
        list.add(new SatelliteTemplate(4, "گم شدن و درخواست کمک", "I'm lost and need help", "我迷路了，请求帮助"));
        list.add(new SatelliteTemplate(5, "بدحالی جسمانی و نیاز به امداد پزشکی", "I'm feeling unwell and need help", "身体不适，需要医疗救援"));
        list.add(new SatelliteTemplate(6, "در حال کمپینگ (اعلام وضعیت سلامت)", "I'm camping, don't worry", "正在露营，一切安好"));
        list.add(new SatelliteTemplate(7, "در حال کوهنوردی (اعلام سلامت)", "I'm out climbing, don't worry", "正在登山，一切良好"));
        list.add(new SatelliteTemplate(8, "در حال رانندگی / خارج از دسترس", "I'm driving, don't worry", "正在驾驶途中，无需担心"));
        list.add(new SatelliteTemplate(9, "رسیدن به مقصد در سلامت کامل", "I've arrived safely. Don't worry!", "已安全到达，请勿挂念"));
        return list;
    }
}
