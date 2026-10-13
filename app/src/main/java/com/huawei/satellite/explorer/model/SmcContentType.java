package com.huawei.satellite.explorer.model;

/**
 * Replicated from com.huawei.himsg.smcmodel.SmcContentType
 */
public enum SmcContentType {
    USER_INPUT("متن دلخواه کاربر (USER_INPUT)", 0),
    SYSTEM_CONTENT("قالب اضطراری سیستم (SYSTEM_CONTENT)", 1),
    SYSTEM_AND_INPUT("قالب اضطراری + متن دلخواه (SYSTEM_AND_INPUT)", 2),
    PICTURE_AND_USER_INPUT("تصویر فشرده + متن دلخواه (PICTURE_AND_USER_INPUT)", 3),
    PICTURE_AND_QUICK_HELP("تصویر فشرده + تگ اضطراری (PICTURE_AND_QUICK_HELP)", 4),
    PICTURE_AND_SHORTCUT_PHRASES("تصویر فشرده + عبارت آماده (PICTURE_AND_SHORTCUT_PHRASES)", 5),
    AUDIO_AND_USER_INPUT("صوت فشرده + متن دلخواه (AUDIO_AND_USER_INPUT)", 6),
    AUDIO_AND_QUICK_HELP("صوت فشرده + تگ اضطراری (AUDIO_AND_QUICK_HELP)", 7),
    AUDIO_AND_SHORTCUT_PHRASES("صوت فشرده + عبارت آماده (AUDIO_AND_SHORTCUT_PHRASES)", 8);

    private final String description;
    private final int code;

    SmcContentType(String description, int code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return description;
    }
}
