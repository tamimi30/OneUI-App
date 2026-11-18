package com.oneui.fontviewer.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.oneui.fontviewer.utils.UpdateConfig;

/**
 * حالة التحديث المحفوظة: وقت آخر تحقق، وهل يوجد تحديث، وحالة بطاقة "ليس الآن".
 *
 * نستخدم SharedPreferences هنا وليس DataStore لسببين: نحتاج قراءة فورية متزامنة عند فتح الشاشات
 * (لإظهار النقطة دون وميض)، ونحتاج OnSharedPreferenceChangeListener ليتحدّث أي شاشة مفتوحة
 * لحظة تغيّر الحالة، وهذا نفس أسلوب تطبيقات Samsung.
 */
public class UpdatePreferenceManager {

    private static final String PREFS_NAME = "update_state";

    public static final String KEY_SHOW_BADGE = "update_show_badge";
    public static final String KEY_LATEST_VERSION_NAME = "update_latest_version_name";
    public static final String KEY_LATEST_VERSION_CODE = "update_latest_version_code";
    public static final String KEY_CARD_SNOOZED_UNTIL = "update_card_snoozed_until";
    public static final String KEY_CARD_DISMISSED_VERSION = "update_card_dismissed_version";

    private static final String KEY_LAST_CHECK_TIME = "update_last_check_time";

    private final SharedPreferences prefs;

    public UpdatePreferenceManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public long getLastCheckTime() {
        return prefs.getLong(KEY_LAST_CHECK_TIME, 0L);
    }

    public void setLastCheckTime(long time) {
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, time).apply();
    }

    public boolean isUpdateAvailable() {
        return prefs.getBoolean(KEY_SHOW_BADGE, false);
    }

    public String getLatestVersionName() {
        return prefs.getString(KEY_LATEST_VERSION_NAME, "");
    }

    public long getLatestVersionCode() {
        return prefs.getLong(KEY_LATEST_VERSION_CODE, 0L);
    }

    public void setUpdateAvailable(String versionName, long versionCode) {
        prefs.edit()
                .putBoolean(KEY_SHOW_BADGE, true)
                .putString(KEY_LATEST_VERSION_NAME, versionName == null ? "" : versionName)
                .putLong(KEY_LATEST_VERSION_CODE, versionCode)
                .apply();
    }

    public void clearUpdateAvailable() {
        prefs.edit()
                .putBoolean(KEY_SHOW_BADGE, false)
                .putString(KEY_LATEST_VERSION_NAME, "")
                .putLong(KEY_LATEST_VERSION_CODE, 0L)
                .apply();
    }

    /**
     * يُخفي بطاقة التحديث مؤقتاً ("ليس الآن"). تعود بعد UpdateConfig.NOT_NOW_SNOOZE_MS،
     * أو فوراً إذا صدر إصدار أحدث من الذي أُخفيت من أجله. النقطة الحمراء لا تتأثر بهذا.
     */
    public void snoozeUpdateCard() {
        prefs.edit()
                .putLong(KEY_CARD_SNOOZED_UNTIL,
                        System.currentTimeMillis() + UpdateConfig.NOT_NOW_SNOOZE_MS)
                .putString(KEY_CARD_DISMISSED_VERSION, getLatestVersionKey())
                .apply();
    }

    public boolean shouldShowUpdateCard() {
        if (!isUpdateAvailable()) {
            return false;
        }

        // إصدار أحدث من الذي أُخفيت من أجله البطاقة: نعرضها من جديد
        if (!getLatestVersionKey().equals(prefs.getString(KEY_CARD_DISMISSED_VERSION, ""))) {
            return true;
        }

        long now = System.currentTimeMillis();
        long snoozedUntil = prefs.getLong(KEY_CARD_SNOOZED_UNTIL, 0L);

        // الشرط الثاني يحمي من تغيير ساعة الجهاز إلى وقت بعيد في الماضي
        return now >= snoozedUntil || snoozedUntil - now > UpdateConfig.NOT_NOW_SNOOZE_MS;
    }

    // مفتاح يميّز الإصدار الجديد الحالي (رقمه إن عُرف وإلا اسمه)
    private String getLatestVersionKey() {
        long code = getLatestVersionCode();
        return code > 0 ? "c" + code : "n" + getLatestVersionName();
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
