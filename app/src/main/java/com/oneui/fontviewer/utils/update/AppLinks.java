package com.oneui.fontviewer.utils.update;

/**
 * الروابط والثوابت الخاصة بنشر التطبيق (GitHub / Play Store / Galaxy Store).
 * أي تغيير في اسم الحساب أو المستودع يتم هنا فقط.
 */
public final class AppLinks {

    private AppLinks() {
    }

    public static final String GITHUB_OWNER = "tamimi30";
    public static final String GITHUB_REPOSITORY = "OneUI-Appp";

    // صفحة حسابي على GitHub (يفتحها زر GitHub في صفحة "حول التطبيق")
    public static final String GITHUB_PROFILE_URL = "https://github.com/" + GITHUB_OWNER;

    // صفحة الإصدارات في المستودع (وجهة زر التحديث للنسخة المثبتة من GitHub)
    public static final String GITHUB_RELEASES_URL =
            "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPOSITORY + "/releases";

    // قائمة إصدارات المستودع عبر GitHub REST API (مصدر معرفة أحدث إصدار)
    public static final String GITHUB_RELEASES_API_URL =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPOSITORY
                    + "/releases?per_page=10";

    // حزم المتاجر المستخدمة لمعرفة مصدر تثبيت التطبيق
    public static final String PACKAGE_GOOGLE_PLAY = "com.android.vending";
    public static final String PACKAGE_GALAXY_STORE = "com.sec.android.app.samsungapps";

    public static String playStoreMarketUrl(String packageName) {
        return "market://details?id=" + packageName;
    }

    public static String playStoreWebUrl(String packageName) {
        return "https://play.google.com/store/apps/details?id=" + packageName;
    }

    public static String galaxyStoreProductUrl(String packageName) {
        return "samsungapps://ProductDetail/" + packageName;
    }
}
