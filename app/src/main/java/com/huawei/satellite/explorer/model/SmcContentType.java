package com.huawei.satellite.explorer.model;

/**
 * Replicated from com.huawei.himsg.smcmodel.SmcContentType (MeeTime)
 */
public enum SmcContentType {
    USER_INPUT("Custom User Text (USER_INPUT)", 0),
    SYSTEM_CONTENT("System Emergency Template (SYSTEM_CONTENT)", 1),
    SYSTEM_AND_INPUT("Template + Custom Text (SYSTEM_AND_INPUT)", 2),
    PICTURE_AND_USER_INPUT("Compressed Photo + Text (PICTURE_AND_USER_INPUT)", 3),
    PICTURE_AND_QUICK_HELP("Compressed Photo + Quick Help (PICTURE_AND_QUICK_HELP)", 4),
    PICTURE_AND_SHORTCUT_PHRASES("Compressed Photo + Shortcut Phrase (PICTURE_SHORTCUT)", 5),
    AUDIO_AND_USER_INPUT("Compressed Voice + Text (AUDIO_AND_USER_INPUT)", 6),
    AUDIO_AND_QUICK_HELP("Compressed Voice + Quick Help (AUDIO_QUICK_HELP)", 7),
    AUDIO_AND_SHORTCUT_PHRASES("Compressed Voice + Shortcut Phrase (AUDIO_SHORTCUT)", 8);

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
