package com.oneui.fontviewer.data.update;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.data.update.model.InstallSource;
import com.oneui.fontviewer.utils.update.AppLinks;

/**
 * يحدد المتجر الذي ثُبّت منه التطبيق (Play Store أو Galaxy Store أو تثبيت يدوي من GitHub).
 * وجهة زر التحديث تعتمد على هذه النتيجة.
 */
public final class InstallSourceResolver {

    private InstallSourceResolver() {
    }

    @NonNull
    public static InstallSource resolve(@NonNull Context context) {
        String installer = getInstallerPackageName(context);

        if (AppLinks.PACKAGE_GOOGLE_PLAY.equals(installer)) {
            return InstallSource.GOOGLE_PLAY;
        }
        if (AppLinks.PACKAGE_GALAXY_STORE.equals(installer)) {
            return InstallSource.GALAXY_STORE;
        }
        // مثبّت الحزم أو adb أو متصفح أو مدير ملفات أو أي مصدر آخر = تثبيت يدوي من ملف APK
        return InstallSource.GITHUB;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static String getInstallerPackageName(@NonNull Context context) {
        String packageName = context.getPackageName();
        PackageManager packageManager = context.getPackageManager();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return packageManager.getInstallSourceInfo(packageName).getInstallingPackageName();
            }
            return packageManager.getInstallerPackageName(packageName);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }
}
