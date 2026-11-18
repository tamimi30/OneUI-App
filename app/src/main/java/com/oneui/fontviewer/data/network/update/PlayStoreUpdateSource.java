package com.oneui.fontviewer.data.network.update;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.UpdateAvailability;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.oneui.fontviewer.data.model.AppVersion;
import com.oneui.fontviewer.data.model.UpdateCheckResult;

/**
 * يفحص التحديث عبر واجهة Google Play In-App Updates الرسمية.
 * تعمل فقط إذا كان التطبيق منزّلاً من Play، وهذا ما يضمنه DistributionChannelResolver قبل اختيار هذا المصدر.
 * Play يعطي رقم الإصدار (versionCode) فقط، لذلك الإصدار المتوفر يُحفظ بدون اسم.
 */
public class PlayStoreUpdateSource implements UpdateSource {

    private static final String TAG = "PlayStoreUpdateSource";
    private static final long TIMEOUT_SECONDS = 15L;

    @NonNull
    @Override
    public UpdateCheckResult checkForUpdate(@NonNull Context context, @NonNull AppVersion installedVersion) {
        try {
            AppUpdateManager manager = AppUpdateManagerFactory.create(context.getApplicationContext());
            AppUpdateInfo info = Tasks.await(manager.getAppUpdateInfo(), TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                return UpdateCheckResult.updateAvailable(new AppVersion("", info.availableVersionCode()));
            }
            return UpdateCheckResult.upToDate();

        } catch (ExecutionException | TimeoutException e) {
            // يشمل: التطبيق غير مثبّت من Play، أو Play غير متاح، أو لا يوجد اتصال
            Log.w(TAG, "Play update check failed: " + e.getMessage());
            return UpdateCheckResult.checkFailed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UpdateCheckResult.checkFailed();
        } catch (RuntimeException e) {
            Log.e(TAG, "Unexpected Play response", e);
            return UpdateCheckResult.checkFailed();
        }
    }
}
