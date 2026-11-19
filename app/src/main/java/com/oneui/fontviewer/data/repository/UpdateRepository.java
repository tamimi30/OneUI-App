package com.oneui.fontviewer.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.oneui.fontviewer.BuildConfig;
import com.oneui.fontviewer.data.model.UpdateCheckState;
import com.oneui.fontviewer.data.model.UpdateInfo;
import com.oneui.fontviewer.data.network.GitHubReleaseApi;
import com.oneui.fontviewer.data.prefs.UpdatePreferenceManager;
import com.oneui.fontviewer.utils.VersionComparator;

import java.util.Optional;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * المصدر الوحيد لحالة التحديث في التطبيق.
 *
 * ما يعتمد عليه كل جزء من الواجهة:
 *  - مؤشر "N / ج" البرتقالي على أيقونة الدرج وأيقونة الإعدادات، وبطاقة الإعدادات: {@link #getAvailableUpdate()}
 *  - صفحة "حول التطبيق" (مؤشر التحميل والنتيجة): {@link #getCheckState()}
 *
 * سلوك صفحة "حول التطبيق" (مطابق لتطبيقات سامسونج):
 *  - أول فتح لها داخل جلسة الإعدادات يتحقق من التحديث ويعرض مؤشر التحميل.
 *  - الفتح التالي في نفس الجلسة يعرض النتيجة المحفوظة فوراً بدون مؤشر تحميل.
 *  - جلسة الإعدادات تبدأ عند فتح الإعدادات وتنتهي بالخروج منها، فإذا دخلت الإعدادات
 *    مرة ثانية يعود التحقق مع مؤشر التحميل. (تغيير اللغة أو الثيم لا ينهي الجلسة.)
 *
 * لا يوجد أي انتظار زمني (مثل 24 ساعة) بين عمليات التحقق: يتم التحقق عند كل فتح للتطبيق
 * وعند أول فتح لصفحة "حول التطبيق" في كل جلسة إعدادات.
 *
 * جميع الدوال العامة تُستدعى من الخيط الرئيسي فقط.
 */
public final class UpdateRepository {

    private static final String TAG = "UpdateRepository";

    private static volatile UpdateRepository sInstance;

    private final Context appContext;
    private final UpdatePreferenceManager preferences;
    private final GitHubReleaseApi releaseApi;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // null = لا يتوجد تحديث متوفر
    private final MutableLiveData<UpdateInfo> availableUpdate;
    private final MutableLiveData<UpdateCheckState> checkState =
            new MutableLiveData<>(UpdateCheckState.IDLE);

    private boolean checkInFlight = false;

    // طُلب التحقق من صفحة "حول التطبيق" وننتظر نتيجته
    private boolean aboutCheckRequested = false;
    // تم التحقق من صفحة "حول التطبيق" مرة داخل جلسة الإعدادات الحالية
    private boolean settingsSessionChecked = false;
    // ضغط المستخدم "ليس الآن" على بطاقة الإعدادات داخل الجلسة الحالية
    private boolean updateCardDismissed = false;

    private UpdateRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = new UpdatePreferenceManager(appContext);
        releaseApi = new GitHubReleaseApi(BuildConfig.UPDATE_INCLUDE_PRERELEASES);
        availableUpdate = new MutableLiveData<>(loadStoredUpdate());
    }

    @NonNull
    public static UpdateRepository getInstance(@NonNull Context context) {
        UpdateRepository instance = sInstance;
        if (instance == null) {
            synchronized (UpdateRepository.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new UpdateRepository(context);
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    /**
     * آخر تحديث محفوظ. إذا كانت النسخة المثبتة الآن مساوية له أو أحدث (أي تم التحديث)
     * نمسحه، كي لا يظهر المؤشر لتحديث مثبّت أصلاً.
     */
    @Nullable
    private UpdateInfo loadStoredUpdate() {
        UpdateInfo stored = preferences.loadAvailableUpdate();
        if (stored == null) {
            return null;
        }
        if (!VersionComparator.isNewer(stored.getVersionName(), BuildConfig.VERSION_NAME)) {
            preferences.clearAvailableUpdate();
            return null;
        }
        return stored;
    }

    // ───────────────────────── الحالة المراقَبة ─────────────────────────

    /** التحديث المتوفر حالياً، أو null. يعتمد عليه المؤشر البرتقالي وبطاقة الإعدادات. */
    @NonNull
    public LiveData<UpdateInfo> getAvailableUpdate() {
        return availableUpdate;
    }

    /** حالة التحقق التي تعرضها صفحة "حول التطبيق". */
    @NonNull
    public LiveData<UpdateCheckState> getCheckState() {
        return checkState;
    }

    @Nullable
    public UpdateInfo getAvailableUpdateNow() {
        return availableUpdate.getValue();
    }

    public boolean isUpdateAvailable() {
        return availableUpdate.getValue() != null;
    }

    // ───────────────────────── التحقق ─────────────────────────

    /**
     * تحقق صامت عند فتح التطبيق: يحدّث المؤشر والبطاقة فقط، ولا يؤثر على مؤشر التحميل
     * في صفحة "حول التطبيق".
     */
    public void checkForUpdateOnLaunch() {
        startCheck();
    }

    /**
     * تطلبه صفحة "حول التطبيق" عند فتحها. يبدأ التحقق مع مؤشر تحميل فقط إذا لم يتم
     * التحقق بعد في جلسة الإعدادات الحالية، وإلا تبقى النتيجة المحفوظة معروضة كما هي.
     */
    public void requestAboutPageCheck() {
        if (settingsSessionChecked) {
            return;
        }
        beginAboutCheck();
    }

    /** زر "إعادة المحاولة" في صفحة "حول التطبيق": تحقق إجباري جديد. */
    public void retryCheck() {
        beginAboutCheck();
    }

    private void beginAboutCheck() {
        aboutCheckRequested = true;
        checkState.setValue(UpdateCheckState.CHECKING);
        startCheck();
    }

    private void startCheck() {
        if (checkInFlight) {
            // يوجد تحقق جارٍ بالفعل، ستصل نتيجته إلى جميع المهتمين
            return;
        }

        if (!isNetworkAvailable()) {
            finishCheck(UpdateCheckState.NO_CONNECTION);
            return;
        }

        checkInFlight = true;
        disposables.add(
                Single.fromCallable(() -> Optional.ofNullable(releaseApi.fetchLatestRelease()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                this::onCheckSucceeded,
                                this::onCheckFailed
                        )
        );
    }

    private void onCheckSucceeded(@NonNull Optional<UpdateInfo> latestRelease) {
        checkInFlight = false;

        UpdateInfo update = null;
        if (latestRelease.isPresent()
                && VersionComparator.isNewer(
                        latestRelease.get().getVersionName(), BuildConfig.VERSION_NAME)) {
            update = latestRelease.get();
        }

        applyAvailableUpdate(update);
        finishCheck(update != null
                ? UpdateCheckState.UPDATE_AVAILABLE
                : UpdateCheckState.UP_TO_DATE);
    }

    private void onCheckFailed(@NonNull Throwable error) {
        checkInFlight = false;
        Log.w(TAG, "Update check failed: " + error.getMessage());

        // عند فشل التحقق لا نمس المؤشر الحالي: التحديث الذي عرفناه سابقاً يبقى ظاهراً
        finishCheck(UpdateCheckState.NO_CONNECTION);
    }

    private void applyAvailableUpdate(@Nullable UpdateInfo update) {
        UpdateInfo current = availableUpdate.getValue();
        boolean unchanged = current == null
                ? update == null
                : update != null && current.getVersionName().equals(update.getVersionName());

        if (update != null) {
            preferences.saveAvailableUpdate(update);
        } else {
            preferences.clearAvailableUpdate();
        }

        if (!unchanged) {
            availableUpdate.setValue(update);
        }
    }

    private void finishCheck(@NonNull UpdateCheckState state) {
        checkState.setValue(state);
        if (aboutCheckRequested) {
            aboutCheckRequested = false;
            settingsSessionChecked = true;
        }
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            Network network = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error checking network availability: " + e.getMessage());
            return false;
        }
    }

    // ───────────────────────── جلسة الإعدادات ─────────────────────────

    /**
     * تُستدعى عند فتح شاشة الإعدادات من جديد (وليس عند إعادة بنائها بسبب تغيير اللغة أو الثيم).
     * تعيد البطاقة، وتجعل أول فتح لصفحة "حول التطبيق" يتحقق من التحديث مع مؤشر تحميل.
     */
    public void beginSettingsSession() {
        settingsSessionChecked = false;
        aboutCheckRequested = false;
        updateCardDismissed = false;
    }

    public boolean isUpdateCardDismissed() {
        return updateCardDismissed;
    }

    /** زر "ليس الآن": تختفي البطاقة حتى يخرج المستخدم من الإعدادات ويدخلها مرة أخرى. */
    public void dismissUpdateCard() {
        updateCardDismissed = true;
    }
}
