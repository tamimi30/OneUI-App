package com.oneui.fontviewer.config;

/**
 * روابط التطبيق الخارجية في مكان واحد.
 *
 * عدّل GITHUB_OWNER و GITHUB_REPOSITORY فقط، وباقي الروابط تُبنى منهما تلقائياً.
 */
public final class AppLinks {

    private AppLinks() {
    }

    // ★ عدّل هاتين القيمتين فقط (اسم حسابك واسم المستودع على GitHub) ★
    public static final String GITHUB_OWNER = "tamimi30";
    public static final String GITHUB_REPOSITORY = "OneUI-App";

    // الصفحة التي يفتحها زر GitHub في صفحة "حول" (صفحتك الشخصية).
    // لفتح صفحة المستودع بدلاً منها استبدلها بـ GITHUB_REPOSITORY_URL.
    public static final String GITHUB_PROFILE_URL = "https://github.com/" + GITHUB_OWNER;

    public static final String GITHUB_REPOSITORY_URL = GITHUB_PROFILE_URL + "/" + GITHUB_REPOSITORY;

    // الصفحة التي يفتحها زر "تحديث" عندما يكون التطبيق منزّلاً من GitHub
    public static final String GITHUB_LATEST_RELEASE_URL = GITHUB_REPOSITORY_URL + "/releases/latest";

    // واجهة GitHub التي يُسأل منها عن آخر إصدار منشور (لا تحتاج مفتاحاً)
    public static final String GITHUB_LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPOSITORY + "/releases/latest";

    // صفحات التطبيق في المتاجر عند عدم توفر تطبيق المتجر على الجهاز (تُلحق بها حزمة التطبيق)
    public static final String PLAY_STORE_WEB_URL_PREFIX = "https://play.google.com/store/apps/details?id=";
    public static final String GALAXY_STORE_WEB_URL_PREFIX = "https://galaxystore.samsung.com/detail/";

    /** هل غيّر المطوّر القيم الافتراضية أعلاه؟ */
    public static boolean isGitHubConfigured() {
        return !GITHUB_OWNER.startsWith("YOUR_") && !GITHUB_REPOSITORY.startsWith("YOUR_");
    }
}
