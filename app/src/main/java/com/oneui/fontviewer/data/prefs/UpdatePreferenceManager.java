package com.oneui.fontviewer.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.data.model.UpdateInfo;

/**
 * يحفظ آخر تحديث متوفر عُرف عنه، ليظهر مؤشر التحديث فور فتح التطبيق
 * قبل انتهاء التحقق من الشبكة (ولو كان الجهاز غير متصل).
 *
 * نستخدم SharedPreferences لأن القراءة متزامنة وسريعة، والبطاقة في الإعدادات
 * تحتاج القيمة فوراً عند بناء الشاشة.
 */
public class UpdatePreferenceManager {

    private static final String PREFS_NAME = "update_state";
    private static final String KEY_AVAILABLE_VERSION = "available_version_name";
    private static final String KEY_AVAILABLE_RELEASE_URL = "available_release_url";

    private final SharedPreferences preferences;

    public UpdatePreferenceManager(@NonNull Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    public UpdateInfo loadAvailableUpdate() {
        String versionName = preferences.getString(KEY_AVAILABLE_VERSION, null);
        String releaseUrl = preferences.getString(KEY_AVAILABLE_RELEASE_URL, null);
        if (versionName == null || releaseUrl == null) {
            return null;
        }
        return new UpdateInfo(versionName, releaseUrl);
    }

    public void saveAvailableUpdate(@NonNull UpdateInfo updateInfo) {
        preferences.edit()
                .putString(KEY_AVAILABLE_VERSION, updateInfo.getVersionName())
                .putString(KEY_AVAILABLE_RELEASE_URL, updateInfo.getReleaseUrl())
                .apply();
    }

    public void clearAvailableUpdate() {
        preferences.edit()
                .remove(KEY_AVAILABLE_VERSION)
                .remove(KEY_AVAILABLE_RELEASE_URL)
                .apply();
    }
}
