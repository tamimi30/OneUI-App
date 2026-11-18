package com.oneui.fontviewer.utils;

/**
 * إعدادات التحقق من التحديثات في مكان واحد.
 * عدّل القيم هنا فقط، ولا حاجة لتغيير أي ملف آخر.
 */
public final class UpdateConfig {

    private UpdateConfig() {}

    // اسم حساب GitHub واسم المستودع (من رابط المستودع: github.com/OWNER/REPO).
    // ★ يجب تعديلهما قبل التجربة، وإلا يُتخطى التحقق عند التثبيت من GitHub ★
    public static final String GITHUB_OWNER = "YOUR_GITHUB_USERNAME";
    public static final String GITHUB_REPO = "YOUR_REPOSITORY_NAME";

    // هل نعتبر الإصدارات المعلّمة Pre-release تحديثاً؟
    // true  = للتجربة الآن (تضع نسخة تجريبية على GitHub وتعلّمها Pre-release فيظهر التحديث).
    // false = عند النشر الفعلي (المستخدمون يرون الإصدارات المستقرة فقط).
    public static final boolean INCLUDE_PRERELEASES = false;

    // الفاصل بين عمليتي تحقق تلقائيتين (24 ساعة) مثل تطبيق Samsung Video تماماً.
    public static final long CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    // المدة التي تختفي فيها بطاقة التحديث بعد الضغط على "ليس الآن" قبل أن تعود (3 أيام).
    // وتعود فوراً إن صدر إصدار أحدث من الذي أُخفيت من أجله.
    public static final long NOT_NOW_SNOOZE_MS = 3L * 24L * 60L * 60L * 1000L;

    // مهلة الاتصال والقراءة للطلبات (بالمللي ثانية)
    public static final int NETWORK_TIMEOUT_MS = 15000;

    // حزم المتاجر المستخدمة لمعرفة مصدر تثبيت التطبيق
    public static final String PLAY_STORE_PACKAGE = "com.android.vending";
    public static final String GALAXY_STORE_PACKAGE = "com.sec.android.app.samsungapps";

    public static boolean isGitHubConfigured() {
        return !GITHUB_OWNER.startsWith("YOUR_") && !GITHUB_REPO.startsWith("YOUR_");
    }

    // رابط صفحة الإصدارات على GitHub (الوجهة عند الضغط على "تحديث")
    public static String getGitHubReleasesPageUrl() {
        return "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO
                + (INCLUDE_PRERELEASES ? "/releases" : "/releases/latest");
    }
}
