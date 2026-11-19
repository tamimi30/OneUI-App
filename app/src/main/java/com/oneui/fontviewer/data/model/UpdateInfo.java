package com.oneui.fontviewer.data.model;

import androidx.annotation.NonNull;

/** معلومات الإصدار الأحدث المتوفر للتحديث. */
public final class UpdateInfo {

    private final String versionName;
    private final String releaseUrl;

    public UpdateInfo(@NonNull String versionName, @NonNull String releaseUrl) {
        this.versionName = versionName;
        this.releaseUrl = releaseUrl;
    }

    /** اسم الإصدار كما هو في GitHub (مثل v1.1). */
    @NonNull
    public String getVersionName() {
        return versionName;
    }

    /** رابط صفحة هذا الإصدار على GitHub. */
    @NonNull
    public String getReleaseUrl() {
        return releaseUrl;
    }
}
