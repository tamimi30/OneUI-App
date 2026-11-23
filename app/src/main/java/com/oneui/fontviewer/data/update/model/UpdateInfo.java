package com.oneui.fontviewer.data.update.model;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.utils.update.VersionComparator;

/** معلومات الإصدار الأحدث المتوفر للتحديث. */
public final class UpdateInfo {

    private final String versionName;
    private final String releaseUrl;
    private final long versionCode;

    public UpdateInfo(@NonNull String versionName, @NonNull String releaseUrl) {
        this(versionName, releaseUrl, 0);
    }

    public UpdateInfo(@NonNull String versionName, @NonNull String releaseUrl, long versionCode) {
        this.versionName = versionName;
        this.releaseUrl = releaseUrl;
        this.versionCode = versionCode;
    }

    /** اسم الإصدار (مثل v1.1 في GitHub). قد يكون رقم الإصدار نفسه إذا كان المصدر لا يوفر اسماً. */
    @NonNull
    public String getVersionName() {
        return versionName;
    }

    /** رابط صفحة هذا الإصدار أو صفحة التطبيق في المتجر. */
    @NonNull
    public String getReleaseUrl() {
        return releaseUrl;
    }

    /** رقم الإصدار (versionCode) إن وفّره المصدر، وإلا 0. */
    public long getVersionCode() {
        return versionCode;
    }

    /** هل هذا التحديث أحدث من النسخة المثبتة؟ تُستخدم لتجاهل تحديث محفوظ تم تثبيته بالفعل. */
    public boolean isNewerThan(@NonNull String installedVersionName, long installedVersionCode) {
        if (versionCode > 0) {
            return versionCode > installedVersionCode;
        }
        return VersionComparator.isNewer(versionName, installedVersionName);
    }
}
