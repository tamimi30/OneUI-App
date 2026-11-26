package com.oneui.fontviewer.data.update;

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
import com.oneui.fontviewer.data.update.model.UpdateCheckState;
import com.oneui.fontviewer.data.update.model.UpdateInfo;
import com.oneui.fontviewer.data.update.source.GalaxyStoreUpdateSource;
import com.oneui.fontviewer.data.update.source.GitHubUpdateSource;
import com.oneui.fontviewer.data.update.source.PlayStoreUpdateSource;
import com.oneui.fontviewer.data.update.source.UpdateSource;

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
 * سلوك صفحة "حول التطبيق":
 *  - كل فتح لها يتحقق من التحديث ويعرض مؤشر التحميل، سواء كان أول فتح لها في جلسة الإعدادات
 *    أو فتحاً متكرراً في نفس الجلسة.
 *  - جلسة الإعدادات تبدأ عند فتح الإعدادات وتنتهي بالخروج منها، وتُستخدم الآن لبطاقة التحديث فقط
 *    (زر "ليس الآن"). (تغيير اللغة أو الثيم لا ينهي الجلسة.)
 *
 * مصدر معرفة التحديث يتبع مكان تثبيت التطبيق: Play أو Galaxy Store أو GitHub (انظر createUpdateSource).
 *
 * لا يوجد أي انتظار زمني (مثل 24 ساعة) بين عمليات التحقق: يتم التحقق عند كل فتح للتطبيق
 * وعند كل فتح لصفحة "حول التطبيق".
 *
 * جميع الدوال العامة تُستدعى من الخيط الرئيسي فقط.
 */
public final class UpdateRepository {

    private static final String TAG = "UpdateRepository";

    private static volatile UpdateRepository sInstance;

    private final Context appContext;
    private final UpdatePreferenceManager preferences;
    private final UpdateSource updateSource;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // null = لا يتوجد تحديث متوفر
    private final MutableLiveData<UpdateInfo> availableUpdate;
    private final MutableLiveData<UpdateCheckState> checkState =
            new MutableLiveData<>(UpdateCheckState.IDLE);

    private boolean checkInFlight = false;

    // ضغط المستخدم "ليس الآن" على بطاقة الإعدادات داخل الجلسة الحالية
    private boolean updateCardDismissed = false;

    private UpdateRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = new UpdatePreferenceManager(appContext);
        updateSource = createUpdateSource();
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
        if (!stored.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) {
            preferences.clearAvailableUpdate();
            return null;
        }
        return stored;
    }

    /**
     * مصدر معرفة التحديث يتبع مكان تثبيت التطبيق (مثل وجهة زر التحديث):
     * Play ← Play In-App Updates، و Galaxy Store ← خادم Galaxy Store، وغير ذلك ← GitHub.
     */
    @NonNull
    private UpdateSource createUpdateSource() {
        switch (InstallSourceResolver.resolve(appContext)) {
            case GOOGLE_PLAY:
                return new PlayStoreUpdateSource(appContext);
            case GALAXY_STORE:
                return new GalaxyStoreUpdateSource(appContext);
            case GITHUB:
            default:
                return new GitHubUpdateSource(BuildConfig.UPDATE_INCLUDE_PRERELEASES);
        }
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
     * تطلبه صفحة "حول التطبيق" عند كل فتح لها. يبدأ التحقق مع مؤشر تحميل دائماً،
     * بغض النظر عن عدد مرات فتح الصفحة في جلسة الإعدادات نفسها.
     */
    public void requestAboutPageCheck() {
        beginAboutCheck();
    }

    /** زر "إعادة المحاولة" في صفحة "حول التطبيق": تحقق إجباري جديد. */
    public void retryCheck() {
        beginAboutCheck();
    }

    private void beginAboutCheck() {
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
                Single.fromCallable(updateSource::checkForUpdate)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                this::onCheckSucceeded,
                                this::onCheckFailed
                        )
        );
    }

    private void onCheckSucceeded(@NonNull Optional<UpdateInfo> availableUpdate) {
        checkInFlight = false;

        // المصدر يرجع التحديث فقط إذا كان أحدث من النسخة المثبتة
        UpdateInfo update = availableUpdate.orElse(null);

        applyAvailableUpdate(update);
        finishCheck(update != null
                ? UpdateCheckState.UPDATE_AVAILABLE
                : UpdateCheckState.UP_TO_DATE);
    }

    private void onCheckFailed(@NonNull Throwable error) {
        checkInFlight = false;
        Log.w(TAG, "Update check failed: " + error.getMessage());

        // عند فشل التحقق لا نمس المؤشر الحالي: التحديث الذي عرفناه سابقاً يبقى ظاهراً.
        // رفض المصدر للطلب أو ردّه بما لا يُفهم يعني أن الاتصال سليم، فلا نقول للمستخدم إن شبكته غير مستقرة
        finishCheck(error instanceof UpdateServerException
                ? UpdateCheckState.CHECK_FAILED
                : UpdateCheckState.NO_CONNECTION);
    }

    private void applyAvailableUpdate(@Nullable UpdateInfo update) {
        UpdateInfo current = availableUpdate.getValue();
        boolean unchanged = current == null
                ? update == null
                : update != null
                        && current.getVersionName().equals(update.getVersionName())
                        && current.getVersionCode() == update.getVersionCode();

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
     * تعيد البطاقة إذا كان المستخدم أخفاها بـ "ليس الآن".
     */
    public void beginSettingsSession() {
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
