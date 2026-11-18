package com.oneui.fontviewer.data.repository;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import com.oneui.fontviewer.data.cache.UpdateSessionState;
import com.oneui.fontviewer.data.datastore.SettingsDataStore;
import com.oneui.fontviewer.data.model.AppVersion;
import com.oneui.fontviewer.data.model.DistributionChannel;
import com.oneui.fontviewer.data.model.UpdateCheckResult;
import com.oneui.fontviewer.data.model.UpdateCheckStatus;
import com.oneui.fontviewer.data.network.update.GalaxyStoreUpdateSource;
import com.oneui.fontviewer.data.network.update.GitHubUpdateSource;
import com.oneui.fontviewer.data.network.update.PlayStoreUpdateSource;
import com.oneui.fontviewer.data.network.update.UpdateSource;
import com.oneui.fontviewer.data.source.DistributionChannelResolver;
import com.oneui.fontviewer.utils.NetworkUtils;

/**
 * مصدر الحقيقة الوحيد لحالة التحديث. يطابق سلوك h0 (UpdateCheckUtil) في Samsung Video:
 *
 * 1) فحص تلقائي مرة كل 24 ساعة فقط (checkForUpdateIfDue، يقابل h0.f())، يُستدعى عند فتح التطبيق.
 *    أول تشغيل للتطبيق لا يفحص الخادم، بل يبدأ عدّاد الـ 24 ساعة فقط.
 * 2) فحص فوري عند فتح صفحة "حول" (يقابل h0.g())، لا يتأثر بالفاصل الزمني.
 * 3) نتيجة "يوجد تحديث" تُحفظ في DataStore فتظهر النقطة على أيقونة الدرج وأيقونة الإعدادات والبطاقة
 *    حتى بعد إغلاق التطبيق، وتُمسح عند وصول إصدار مثبّت يساوي أو يفوق الإصدار المتوفر.
 * 4) فشل الفحص (لا إنترنت أو خطأ) لا يغيّر الحالة المحفوظة.
 */
public class UpdateRepository {

    private static final String TAG = "UpdateRepository";

    // الفاصل بين فحصين تلقائيين: 24 ساعة (86400000 ms في Samsung Video). غيّر القيمة لتغيير الفاصل.
    public static final long AUTO_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24);

    private static volatile UpdateRepository INSTANCE;

    private final Context mAppContext;
    private final SettingsDataStore mDataStore;
    private final UpdateSessionState mSession = new UpdateSessionState();

    // هل يوجد تحديث متوفر؟ تُغذّيه قيمة DataStore، فتتحدث النقطة فوراً عند تغيّرها
    private final MutableLiveData<Boolean> mUpdateAvailable = new MutableLiveData<>();

    // يمنع تشغيل فحصين متزامنين (مثلاً الفحص التلقائي أثناء فتح صفحة "حول")
    private final AtomicBoolean mCheckInFlight = new AtomicBoolean(false);

    private volatile DistributionChannel mChannel;

    private UpdateRepository(Context context) {
        mAppContext = context.getApplicationContext();
        mDataStore = SettingsDataStore.getInstance(mAppContext);
        observeUpdateAvailable();
    }

    public static UpdateRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (UpdateRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new UpdateRepository(context);
                }
            }
        }
        return INSTANCE;
    }

    private void observeUpdateAvailable() {
        mDataStore.getUpdateAvailable()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mUpdateAvailable::setValue,
                        error -> Log.e(TAG, "Error observing update availability", error)
                );
    }

    // ════════════════════════════════════════════════════════════
    // Update state
    // ════════════════════════════════════════════════════════════

    /** يُراقَب لإظهار النقطة أو إخفائها على أيقونتي الدرج والإعدادات. */
    @NonNull
    public LiveData<Boolean> getUpdateAvailable() {
        return mUpdateAvailable;
    }

    /** المصدر الذي نُزّل منه التطبيق، يحدد وجهة زر "تحديث". */
    @NonNull
    public DistributionChannel getDistributionChannel() {
        DistributionChannel channel = mChannel;
        if (channel == null) {
            channel = DistributionChannelResolver.resolve(mAppContext);
            mChannel = channel;
        }
        return channel;
    }

    // ════════════════════════════════════════════════════════════
    // Automatic check (once every 24 hours)
    // ════════════════════════════════════════════════════════════

    /** يُستدعى عند فتح التطبيق. غير حاجب، ولا يفحص الخادم إلا إذا مرّ الفاصل الزمني. */
    public void checkForUpdateIfDue() {
        Completable.fromAction(this::checkIfDue)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> { },
                        error -> Log.e(TAG, "Automatic update check failed", error)
                );
    }

    // synchronized: استدعاءان متتاليان (مثل إعادة بناء الشاشة) لا يتجاوزان الفاصل معاً
    private synchronized void checkIfDue() {
        clearUpdateIfAlreadyInstalled();

        long now = System.currentTimeMillis();
        long lastCheck = readLastCheckTime();

        if (lastCheck == 0L) {
            Log.d(TAG, "First launch: not checking the server, starting the interval clock");
            saveLastCheckTime(now);
            return;
        }

        // elapsed سالب يعني أن المستخدم أرجع ساعة الجهاز للخلف، فنعتبر الفحص مستحقاً بدل تعطيله
        long elapsed = now - lastCheck;
        if (elapsed >= 0 && elapsed < AUTO_CHECK_INTERVAL_MS) {
            Log.d(TAG, "Checked less than 24 hours ago, skipping");
            return;
        }

        if (!NetworkUtils.isOnline(mAppContext)) {
            // لا نستهلك الفاصل بلا فحص فعلي: نحاول مجدداً عند الفتح التالي
            Log.d(TAG, "Offline, the check will be retried on the next launch");
            return;
        }

        saveLastCheckTime(now);
        executeCheckAsync();
    }

    /** إذا ثبّت المستخدم الإصدار المتوفر (أو أحدث) تُمسح الحالة المحفوظة فتختفي النقطة. */
    private void clearUpdateIfAlreadyInstalled() {
        if (!readUpdateAvailable()) {
            return;
        }

        AppVersion available = new AppVersion(readAvailableVersionName(), readAvailableVersionCode());
        if (available.isUnknown()) {
            return;
        }

        if (!available.isNewerThan(readInstalledVersion())) {
            Log.d(TAG, "The available version is already installed, clearing the update state");
            clearAvailableUpdate();
        }
    }

    // ════════════════════════════════════════════════════════════
    // Settings visit (card + About page)
    // ════════════════════════════════════════════════════════════

    /** يُستدعى عند فتح شاشة الإعدادات من جديد (وليس عند إعادة بنائها بسبب اللغة أو الثيم). */
    public void beginSettingsSession() {
        mSession.reset();
    }

    /**
     * هل تظهر بطاقة التحديث في هذه الزيارة؟ القرار يُحسم عند أول سؤال ويبقى ثابتاً حتى نهاية الزيارة،
     * فلا تُضاف البطاقة لاحقاً بعد أول رسم للقائمة (وهي مشكلة فقدان التمرير في القوائم القصيرة).
     */
    public boolean shouldShowSettingsUpdateCard() {
        if (mSession.isCardDismissed()) {
            return false;
        }

        Boolean decision = mSession.getCardVisibleDecision();
        if (decision == null) {
            decision = readUpdateAvailable();
            mSession.setCardVisibleDecision(decision);
        }
        return decision;
    }

    /** زر "ليس الآن": تُخفى البطاقة حتى الخروج من الإعدادات، وتعود عند الدخول مجدداً. */
    public void dismissSettingsUpdateCard() {
        mSession.setCardDismissed(true);
    }

    @NonNull
    public LiveData<UpdateCheckStatus> getAboutPageStatus() {
        return mSession.getAboutPageStatus();
    }

    /**
     * يبدأ الفحص عند فتح صفحة "حول" لأول مرة في زيارة الإعدادات فقط. الدخول المتكرر في نفس الزيارة
     * لا يعيد الفحص ولا يُظهر مؤشر التحميل، بل يعرض النتيجة المحفوظة.
     */
    public void startAboutPageCheckIfNeeded() {
        if (mSession.getAboutPageStatus().getValue() != null) {
            return;
        }
        beginAboutPageCheck();
    }

    /** زر "إعادة المحاولة" في صفحة "حول". */
    public void retryAboutPageCheck() {
        if (mSession.getAboutPageStatus().getValue() == UpdateCheckStatus.CHECKING) {
            return;
        }
        beginAboutPageCheck();
    }

    private void beginAboutPageCheck() {
        mSession.setAboutPageStatus(UpdateCheckStatus.CHECKING);
        executeCheckAsync();
    }

    // ════════════════════════════════════════════════════════════
    // Check execution
    // ════════════════════════════════════════════════════════════

    private void executeCheckAsync() {
        // فحص جارٍ بالفعل: نتيجته ستُحسم بها صفحة "حول" إن كانت تنتظر (انظر onCheckFinished)
        if (!mCheckInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "A check is already running");
            return;
        }

        Single.fromCallable(this::performCheck)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> mCheckInFlight.set(false))
                .subscribe(
                        this::onCheckFinished,
                        error -> {
                            Log.e(TAG, "Update check failed", error);
                            onCheckFinished(UpdateCheckResult.checkFailed());
                        }
                );
    }

    @NonNull
    private UpdateCheckResult performCheck() {
        if (!NetworkUtils.isOnline(mAppContext)) {
            return UpdateCheckResult.noConnection();
        }

        UpdateCheckResult result = createUpdateSource(getDistributionChannel())
                .checkForUpdate(mAppContext, readInstalledVersion());
        persistResult(result);
        return result;
    }

    // يُنفَّذ على الخيط الرئيسي
    private void onCheckFinished(@NonNull UpdateCheckResult result) {
        if (mSession.getAboutPageStatus().getValue() == UpdateCheckStatus.CHECKING) {
            mSession.setAboutPageStatus(result.getStatus());
        }
    }

    @NonNull
    private UpdateSource createUpdateSource(@NonNull DistributionChannel channel) {
        switch (channel) {
            case GOOGLE_PLAY:
                return new PlayStoreUpdateSource();
            case GALAXY_STORE:
                return new GalaxyStoreUpdateSource();
            case GITHUB:
            default:
                return new GitHubUpdateSource();
        }
    }

    // مثل Samsung Video: "يوجد تحديث" يحفظ الحالة، و"لا يوجد" يمسحها، وفشل الفحص لا يغيّر شيئاً
    private void persistResult(@NonNull UpdateCheckResult result) {
        try {
            switch (result.getStatus()) {
                case UPDATE_AVAILABLE:
                    AppVersion version = result.getAvailableVersion();
                    if (version != null) {
                        mDataStore.setAvailableUpdate(version.getVersionName(), version.getVersionCode())
                                .blockingGet();
                    }
                    break;
                case UP_TO_DATE:
                    clearAvailableUpdate();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save the update state", e);
        }
    }

    // ════════════════════════════════════════════════════════════
    // Storage helpers (تُستدعى من خيط خلفي، وبعضها من الرئيسي عبر القراءة من النسخة المحفوظة في الذاكرة)
    // ════════════════════════════════════════════════════════════

    private boolean readUpdateAvailable() {
        try {
            return mDataStore.getUpdateAvailable().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading update availability", e);
            return false;
        }
    }

    private String readAvailableVersionName() {
        try {
            return mDataStore.getUpdateAvailableVersionName().blockingFirst();
        } catch (Exception e) {
            return "";
        }
    }

    private long readAvailableVersionCode() {
        try {
            return mDataStore.getUpdateAvailableVersionCode().blockingFirst();
        } catch (Exception e) {
            return 0L;
        }
    }

    private long readLastCheckTime() {
        try {
            return mDataStore.getUpdateLastCheckTime().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading last check time", e);
            return 0L;
        }
    }

    private void saveLastCheckTime(long timeMillis) {
        try {
            mDataStore.setUpdateLastCheckTime(timeMillis).blockingGet();
        } catch (Exception e) {
            Log.e(TAG, "Error saving last check time", e);
        }
    }

    private void clearAvailableUpdate() {
        try {
            mDataStore.clearAvailableUpdate().blockingGet();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing the update state", e);
        }
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private AppVersion readInstalledVersion() {
        try {
            PackageInfo info = mAppContext.getPackageManager().getPackageInfo(mAppContext.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return new AppVersion(info.versionName, code);
        } catch (PackageManager.NameNotFoundException e) {
            return new AppVersion("", 0L);
        }
    }
}
