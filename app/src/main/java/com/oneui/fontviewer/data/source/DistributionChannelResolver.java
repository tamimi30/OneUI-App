package com.oneui.fontviewer.data.source;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.data.model.DistributionChannel;

/**
 * يحدد المصدر الذي ثُبّت منه التطبيق من اسم حزمة المثبّت:
 * Play → GOOGLE_PLAY، وGalaxy Store → GALAXY_STORE، وأي شيء آخر (متصفح، مدير ملفات، adb) → GITHUB.
 */
public final class DistributionChannelResolver {

    private static final String INSTALLER_GOOGLE_PLAY = "com.android.vending";
    private static final String INSTALLER_GALAXY_STORE = "com.sec.android.app.samsungapps";

    private DistributionChannelResolver() {
    }

    @NonNull
    public static DistributionChannel resolve(@NonNull Context context) {
        String installer = readInstallerPackageName(context);

        if (INSTALLER_GOOGLE_PLAY.equals(installer)) {
            return DistributionChannel.GOOGLE_PLAY;
        }
        if (INSTALLER_GALAXY_STORE.equals(installer)) {
            return DistributionChannel.GALAXY_STORE;
        }
        return DistributionChannel.GITHUB;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static String readInstallerPackageName(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            String packageName = context.getPackageName();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return packageManager.getInstallSourceInfo(packageName).getInstallingPackageName();
            }
            return packageManager.getInstallerPackageName(packageName);
        } catch (Exception e) {
            return null;
        }
    }
}
