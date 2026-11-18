package com.oneui.fontviewer.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.utils.VersionComparator;

/**
 * رقم إصدار التطبيق (المثبّت على الجهاز أو المتوفر في المتجر).
 * المصادر تختلف فيما تعطيه: GitHub يعطي الاسم فقط، وGoogle Play يعطي الرقم فقط،
 * لذلك المقارنة تستخدم الرقم إن توفر عند الطرفين وإلا تستخدم الاسم.
 */
public final class AppVersion {

    @NonNull
    private final String mVersionName;

    // 0 تعني غير معروف
    private final long mVersionCode;

    public AppVersion(@Nullable String versionName, long versionCode) {
        mVersionName = versionName == null ? "" : versionName;
        mVersionCode = Math.max(versionCode, 0L);
    }

    @NonNull
    public String getVersionName() {
        return mVersionName;
    }

    public long getVersionCode() {
        return mVersionCode;
    }

    /** لا اسم ولا رقم، فلا يمكن مقارنته بشيء. */
    public boolean isUnknown() {
        return mVersionName.isEmpty() && mVersionCode == 0L;
    }

    /** هل هذا الإصدار أحدث من الإصدار المعطى؟ (false إن تعذّرت المقارنة) */
    public boolean isNewerThan(@NonNull AppVersion other) {
        if (mVersionCode > 0L && other.mVersionCode > 0L) {
            return mVersionCode > other.mVersionCode;
        }
        if (!mVersionName.isEmpty() && !other.mVersionName.isEmpty()) {
            return VersionComparator.compare(mVersionName, other.mVersionName) > 0;
        }
        return false;
    }
}
