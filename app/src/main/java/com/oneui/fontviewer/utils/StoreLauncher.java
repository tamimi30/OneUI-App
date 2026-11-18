package com.oneui.fontviewer.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.model.InstallSource;

/**
 * يوجّه المستخدم إلى صفحة التطبيق في المتجر الذي ثبّت منه (Play أو Galaxy Store أو GitHub)
 * عند الضغط على زر "تحديث" في البطاقة أو في صفحة "حول".
 */
public final class StoreLauncher {

    private StoreLauncher() {}

    /**
     * @return true إذا فُتحت صفحة التحديث بنجاح
     */
    public static boolean openUpdatePage(Context context) {
        String pkg = context.getPackageName();
        boolean opened;

        switch (InstallSource.detect(context)) {
            case PLAY_STORE:
                opened = open(context, "market://details?id=" + pkg, UpdateConfig.PLAY_STORE_PACKAGE)
                        || open(context, "https://play.google.com/store/apps/details?id=" + pkg, null);
                break;

            case GALAXY_STORE:
                // نفس الرابط الذي يستخدمه Samsung Video لفتح صفحة التطبيق في Galaxy Store
                opened = open(context, "samsungapps://ProductDetail/" + pkg
                                + "/?fsOrigin=stubUpdateCheck&fsUpdateType=self",
                        UpdateConfig.GALAXY_STORE_PACKAGE)
                        || open(context, "https://galaxystore.samsung.com/detail/" + pkg, null);
                break;

            case GITHUB:
            default:
                opened = UpdateConfig.isGitHubConfigured()
                        && open(context, UpdateConfig.getGitHubReleasesPageUrl(), null);
                break;
        }

        if (!opened) {
            Toast.makeText(context, R.string.update_open_store_failed, Toast.LENGTH_SHORT).show();
        }
        return opened;
    }

    private static boolean open(Context context, String url, String targetPackage) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (targetPackage != null) {
            intent.setPackage(targetPackage);
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
