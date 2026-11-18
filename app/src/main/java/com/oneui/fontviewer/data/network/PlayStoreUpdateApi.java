package com.oneui.fontviewer.data.network;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallException;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.oneui.fontviewer.data.model.UpdateCheckResult;
import com.oneui.fontviewer.utils.UpdateConfig;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * التحقق من التحديث عبر Google Play (Play In-App Updates API) دون أي تحميل أو تثبيت.
 * لا تعمل إلا إذا كان التطبيق مثبتاً من Play. دالة {@link #check(Context)} تحجب الخيط،
 * لذلك تُستدعى من خيط خلفي فقط.
 */
public class PlayStoreUpdateApi {

    private static final String TAG = "PlayStoreUpdateApi";

    // رمز خطأ Play عندما لا يكون التطبيق مثبتاً من Play (ERROR_APP_NOT_OWNED)
    private static final int ERROR_APP_NOT_OWNED = -10;

    public static UpdateCheckResult check(Context context) {
        try {
            AppUpdateManager manager = AppUpdateManagerFactory.create(context);
            AppUpdateInfo info = Tasks.await(
                    manager.getAppUpdateInfo(),
                    UpdateConfig.NETWORK_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);

            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                return UpdateCheckResult.updateAvailable("", info.availableVersionCode());
            }
            return UpdateCheckResult.noUpdate();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InstallException
                    && ((InstallException) cause).getErrorCode() == ERROR_APP_NOT_OWNED) {
                return UpdateCheckResult.noUpdate();
            }
            Log.w(TAG, "Play update check failed", e);
            return UpdateCheckResult.error();
        } catch (Exception e) {
            Log.w(TAG, "Play update check failed", e);
            return UpdateCheckResult.error();
        }
    }
}
