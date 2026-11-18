package com.oneui.fontviewer.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.oneui.fontviewer.data.network.GalaxyStoreUpdateApi;
import com.oneui.fontviewer.data.network.GitHubUpdateApi;
import com.oneui.fontviewer.data.network.PlayStoreUpdateApi;
import com.oneui.fontviewer.data.prefs.UpdatePreferenceManager;
import com.oneui.fontviewer.data.model.InstallSource;
import com.oneui.fontviewer.data.model.UpdateCheckResult;
import com.oneui.fontviewer.utils.UpdateConfig;
import com.oneui.fontviewer.utils.AppVersionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * مدير التحقق من التحديثات. يعمل بنفس منطق تطبيق Samsung Video:
 *  - {@link #checkIfNeeded()}: تحقق تلقائي عند فتح التطبيق، مرة كل 24 ساعة فقط، ويتخطى أول تشغيل.
 *  - {@link #forceCheck(Callback)}: تحقق فوري عند فتح صفحة "حول" (يظهر مؤشر التحميل ثم النتيجة).
 * نتيجة وجود تحديث تُحفظ في UpdatePreferenceManager فتظهر النقطة والبطاقة في الشاشات المفتوحة.
 */
public class UpdateRepository {

    private static final String TAG = "UpdateRepository";

    public interface Callback {
        void onResult(UpdateCheckResult result);
    }

    private static volatile UpdateRepository sInstance;

    private final Context appContext;
    private final UpdatePreferenceManager prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // المستمعون المنتظرون لنتيجة عملية تحقق جارية (كل العمليات تمر عبر الخيط الرئيسي)
    private final List<Callback> pendingCallbacks = new ArrayList<>();
    private boolean mChecking = false;

    // آخر نتيجة في هذه الجلسة (مثل mResultCode في Samsung)، تُستخدم لرسم صفحة "حول" فوراً
    private UpdateCheckResult mLastResult = null;

    private UpdateRepository(Context context) {
        appContext = context.getApplicationContext();
        prefs = new UpdatePreferenceManager(appContext);
    }

    public static UpdateRepository getInstance(Context context) {
        if (sInstance == null) {
            synchronized (UpdateRepository.class) {
                if (sInstance == null) {
                    sInstance = new UpdateRepository(context);
                }
            }
        }
        return sInstance;
    }

    public UpdatePreferenceManager getPreferences() {
        return prefs;
    }

    public UpdateCheckResult getLastResult() {
        return mLastResult;
    }

    public boolean isUpdateAvailable() {
        return prefs.isUpdateAvailable();
    }

    /**
     * التحقق التلقائي عند فتح التطبيق. يجب استدعاؤه من الخيط الرئيسي.
     */
    public void checkIfNeeded() {
        clearUpdateIfAlreadyInstalled();

        long now = System.currentTimeMillis();
        long last = prefs.getLastCheckTime();

        if (last == 0L) {
            // أول تشغيل: لا نتحقق من الخادم، نسجّل الوقت فقط (نفس سلوك Samsung)
            prefs.setLastCheckTime(now);
            return;
        }

        // الشرط now >= last يعالج رجوع ساعة الجهاز إلى الوراء
        if (now >= last && now - last < UpdateConfig.CHECK_INTERVAL_MS) {
            Log.d(TAG, "checkIfNeeded: operate only once a day. So skip.");
            return;
        }

        if (!isOnline()) {
            // بلا اتصال لا نستهلك الفترة، فنعيد المحاولة في التشغيل التالي
            return;
        }

        startCheck(null);
    }

    /**
     * تحقق فوري بغض النظر عن فترة الـ 24 ساعة (يُستخدم في صفحة "حول"). يجب استدعاؤه من الخيط الرئيسي.
     */
    public void forceCheck(Callback callback) {
        clearUpdateIfAlreadyInstalled();

        if (!isOnline()) {
            UpdateCheckResult error = UpdateCheckResult.error();
            mLastResult = error;
            if (callback != null) {
                callback.onResult(error);
            }
            return;
        }

        startCheck(callback);
    }

    private void startCheck(Callback callback) {
        if (callback != null) {
            pendingCallbacks.add(callback);
        }
        if (mChecking) {
            return;
        }
        mChecking = true;

        // الفترة تُحتسب من بداية التحقق (مثل Samsung)، سواء كان تلقائياً أو فورياً
        prefs.setLastCheckTime(System.currentTimeMillis());

        final InstallSource source = InstallSource.detect(appContext);
        executor.execute(() -> {
            UpdateCheckResult result;
            try {
                switch (source) {
                    case PLAY_STORE:
                        result = PlayStoreUpdateApi.check(appContext);
                        break;
                    case GALAXY_STORE:
                        result = GalaxyStoreUpdateApi.check(appContext);
                        break;
                    case GITHUB:
                    default:
                        result = GitHubUpdateApi.check(appContext);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Update check crashed", e);
                result = UpdateCheckResult.error();
            }

            final UpdateCheckResult finalResult = result;
            mainHandler.post(() -> onCheckFinished(finalResult));
        });
    }

    private void onCheckFinished(UpdateCheckResult result) {
        mChecking = false;
        mLastResult = result;

        if (result.status == UpdateCheckResult.Status.UPDATE_AVAILABLE) {
            prefs.setUpdateAvailable(result.versionName, result.versionCode);
        } else if (result.status == UpdateCheckResult.Status.NO_UPDATE) {
            prefs.clearUpdateAvailable();
        }
        // عند الخطأ نُبقي الحالة المحفوظة كما هي

        List<Callback> callbacks = new ArrayList<>(pendingCallbacks);
        pendingCallbacks.clear();
        for (Callback callback : callbacks) {
            callback.onResult(result);
        }
    }

    /**
     * إذا حدّث المستخدم التطبيق (الإصدار المثبت صار مساوياً للإصدار الجديد المحفوظ أو أحدث منه)
     * نمسح حالة التحديث فتختفي النقطة والبطاقة فوراً دون انتظار التحقق التالي.
     */
    private void clearUpdateIfAlreadyInstalled() {
        if (!prefs.isUpdateAvailable()) {
            return;
        }

        long savedCode = prefs.getLatestVersionCode();
        boolean stillNewer;
        if (savedCode > 0) {
            stillNewer = savedCode > AppVersionUtils.getVersionCode(appContext);
        } else if (prefs.getLatestVersionName().isEmpty()) {
            // لا نعرف رقم الإصدار الجديد (الخادم قال فقط "يوجد تحديث")، فنُبقي الحالة كما هي
            stillNewer = true;
        } else {
            stillNewer = AppVersionUtils.compareVersionNames(
                    prefs.getLatestVersionName(),
                    AppVersionUtils.getVersionName(appContext)) > 0;
        }

        if (!stillNewer) {
            Log.d(TAG, "update installed. clear the badge notification.");
            prefs.clearUpdateAvailable();
        }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }
}
