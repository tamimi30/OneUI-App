package com.oneui.fontviewer.data.model;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.oneui.fontviewer.utils.UpdateConfig;

/**
 * مصدر تثبيت التطبيق. يحدد من أين نتحقق من التحديث وإلى أين نوجّه المستخدم عند الضغط على "تحديث".
 * أي مصدر غير Play وGalaxy Store (تثبيت يدوي من ملف APK) نعتبره GitHub.
 */
public enum InstallSource {
    PLAY_STORE,
    GALAXY_STORE,
    GITHUB;

    @SuppressWarnings("deprecation")
    public static InstallSource detect(Context context) {
        String installer = null;
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                installer = pm.getInstallSourceInfo(pkg).getInstallingPackageName();
            } else {
                installer = pm.getInstallerPackageName(pkg);
            }
        } catch (Exception ignored) {
            // نعتبره تثبيتاً يدوياً
        }

        if (UpdateConfig.PLAY_STORE_PACKAGE.equals(installer)) {
            return PLAY_STORE;
        }
        if (UpdateConfig.GALAXY_STORE_PACKAGE.equals(installer)) {
            return GALAXY_STORE;
        }
        return GITHUB;
    }
}
