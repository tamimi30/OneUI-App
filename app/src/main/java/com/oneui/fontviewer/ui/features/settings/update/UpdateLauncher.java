package com.oneui.fontviewer.ui.features.settings.update;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.data.update.InstallSourceResolver;
import com.oneui.fontviewer.data.update.model.InstallSource;
import com.oneui.fontviewer.data.update.model.UpdateInfo;
import com.oneui.fontviewer.utils.update.AppLinks;

/**
 * يوجّه المستخدم إلى المكان الذي ثُبّت منه التطبيق لتحديثه:
 *  - Play Store  ← صفحة التطبيق في Play
 *  - Galaxy Store ← صفحة التطبيق في Galaxy Store
 *  - تثبيت يدوي  ← صفحة الإصدار على GitHub
 */
public final class UpdateLauncher {

    private static final String TAG = "UpdateLauncher";

    private UpdateLauncher() {
    }

    /** @return true إذا تم فتح وجهة التحديث بنجاح. */
    public static boolean open(@NonNull Context context, @Nullable UpdateInfo updateInfo) {
        InstallSource installSource = InstallSourceResolver.resolve(context);
        String packageName = context.getPackageName();

        switch (installSource) {
            case GOOGLE_PLAY:
                if (openUrl(context, AppLinks.playStoreMarketUrl(packageName),
                        AppLinks.PACKAGE_GOOGLE_PLAY)) {
                    return true;
                }
                // تطبيق Play غير متاح: نفتح صفحة المتجر في المتصفح
                return openUrl(context, AppLinks.playStoreWebUrl(packageName), null);

            case GALAXY_STORE:
                if (openUrl(context, AppLinks.galaxyStoreProductUrl(packageName),
                        AppLinks.PACKAGE_GALAXY_STORE)) {
                    return true;
                }
                return openGitHubRelease(context, updateInfo);

            case GITHUB:
            default:
                return openGitHubRelease(context, updateInfo);
        }
    }

    private static boolean openGitHubRelease(@NonNull Context context, @Nullable UpdateInfo updateInfo) {
        String url = updateInfo != null ? updateInfo.getReleaseUrl() : AppLinks.GITHUB_RELEASES_URL;
        return openUrl(context, url, null);
    }

    private static boolean openUrl(@NonNull Context context, @NonNull String url,
                                   @Nullable String targetPackage) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (targetPackage != null) {
            intent.setPackage(targetPackage);
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Cannot open " + url + ": " + e.getMessage());
            return false;
        }
    }
}
