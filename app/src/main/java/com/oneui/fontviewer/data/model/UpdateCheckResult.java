package com.oneui.fontviewer.data.model;

/**
 * نتيجة عملية تحقق واحدة من وجود تحديث.
 */
public final class UpdateCheckResult {

    public enum Status {
        UPDATE_AVAILABLE,
        NO_UPDATE,
        // تعذّر التحقق (لا اتصال، أو خطأ من الخادم)
        ERROR
    }

    public final Status status;
    // اسم الإصدار الجديد (قد يكون فارغاً، مثل Play الذي يعطي رقم الإصدار فقط)
    public final String versionName;
    // رقم الإصدار الجديد (0 إن لم يكن معروفاً)
    public final long versionCode;

    private UpdateCheckResult(Status status, String versionName, long versionCode) {
        this.status = status;
        this.versionName = versionName == null ? "" : versionName;
        this.versionCode = versionCode;
    }

    public static UpdateCheckResult updateAvailable(String versionName, long versionCode) {
        return new UpdateCheckResult(Status.UPDATE_AVAILABLE, versionName, versionCode);
    }

    public static UpdateCheckResult noUpdate() {
        return new UpdateCheckResult(Status.NO_UPDATE, "", 0L);
    }

    public static UpdateCheckResult error() {
        return new UpdateCheckResult(Status.ERROR, "", 0L);
    }
}
