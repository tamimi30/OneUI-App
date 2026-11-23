package com.oneui.fontviewer.data.update.source;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.oneui.fontviewer.data.update.model.UpdateInfo;
import com.oneui.fontviewer.utils.update.AppLinks;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * مصدر التحديث للنسخة المثبتة من Play Store: Play In-App Updates API.
 * نستخدمها فقط لمعرفة توفر تحديث، أما التحديث نفسه فيتم بفتح صفحة التطبيق في Play.
 *
 * تعمل فقط للنسخة المثبتة من Play لتطبيق منشور (ولو في مسار الاختبار الداخلي).
 */
public class PlayStoreUpdateSource implements UpdateSource {

    private static final long TIMEOUT_SECONDS = 15;

    private final Context appContext;

    public PlayStoreUpdateSource(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Optional<UpdateInfo> checkForUpdate() throws Exception {
        AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(appContext);

        // المستمعات تُستدعى على الخيط الرئيسي، ونحن على خيط خلفي ننتظر النتيجة
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AppUpdateInfo> info = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        appUpdateManager.getAppUpdateInfo()
                .addOnSuccessListener(result -> {
                    info.set(result);
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    error.set(e);
                    latch.countDown();
                });

        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IOException("Play update check timed out");
        }
        if (error.get() != null) {
            throw error.get();
        }

        AppUpdateInfo updateInfo = info.get();
        if (updateInfo != null
                && updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
            long versionCode = updateInfo.availableVersionCode();
            return Optional.of(new UpdateInfo(String.valueOf(versionCode),
                    AppLinks.playStoreWebUrl(appContext.getPackageName()), versionCode));
        }
        return Optional.empty();
    }
}
