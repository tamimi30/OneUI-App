package com.oneui.fontviewer.ui.features.update;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.config.AppLinks;
import com.oneui.fontviewer.data.model.DistributionChannel;

/**
 * يفتح الصفحة المناسبة للتحديث بحسب المصدر الذي نُزّل منه التطبيق:
 * GitHub → صفحة آخر Release، وPlay → صفحة التطبيق في متجر Play، وGalaxy Store → صفحته في Galaxy Store.
 * كل دالة تعيد false إذا تعذّر فتح الصفحة لتعرض الشاشة رسالة للمستخدم.
 */
public final class StorePageLauncher {

    private static final String PACKAGE_GOOGLE_PLAY = "com.android.vending";

    private StorePageLauncher() {
    }

    public static boolean openUpdatePage(@NonNull Context context, @NonNull DistributionChannel channel) {
        switch (channel) {
            case GOOGLE_PLAY:
                return openGooglePlay(context);
            case GALAXY_STORE:
                return openGalaxyStore(context);
            case GITHUB:
            default:
                return openUrl(context, AppLinks.GITHUB_LATEST_RELEASE_URL);
        }
    }

    public static boolean openUrl(@NonNull Context context, @NonNull String url) {
        return start(context, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private static boolean openGooglePlay(Context context) {
        String packageName = context.getPackageName();

        Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
        marketIntent.setPackage(PACKAGE_GOOGLE_PLAY);
        if (start(context, marketIntent)) {
            return true;
        }
        return openUrl(context, AppLinks.PLAY_STORE_WEB_URL_PREFIX + packageName);
    }

    private static boolean openGalaxyStore(Context context) {
        String packageName = context.getPackageName();

        // نفس الرابط والعلامات التي يستخدمها Samsung Video (w3.h.a) لفتح صفحة التطبيق للتحديث الذاتي
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("samsungapps://ProductDetail/" + packageName
                        + "/?fsOrigin=stubUpdateCheck&fsUpdateType=self"));
        intent.putExtra("type", "cover");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        if (start(context, intent)) {
            return true;
        }
        return openUrl(context, AppLinks.GALAXY_STORE_WEB_URL_PREFIX + packageName);
    }

    private static boolean start(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
