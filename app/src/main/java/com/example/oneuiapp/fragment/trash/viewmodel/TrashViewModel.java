package com.example.oneuiapp.fragment.trash.viewmodel;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.fragment.trash.data.TrashRepository;
import com.example.oneuiapp.dialog.TrashActionDialogs; // ★ إدارة الإشعارات من الـ ViewModel ★
import com.example.oneuiapp.utils.notification.BatchOperationState;
import com.example.oneuiapp.utils.notification.OperationForegroundService; // ★ الخدمة الأمامية لمنع تجمد الإشعار ★

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TrashViewModel - طبقة العرض الخاصة بسلة المحذوفات
 *
 * تتوسط هذه الطبقة بين TrashFragment و TrashRepository، وتتولى:
 *  - توفير البيانات كـ LiveData لمراقبتها من TrashFragment.
 *  - إدارة حالة العمليات الجماعية (تقدم، إلغاء، نتيجة).
 *  - ترجمة أحداث المستودع إلى حالات واجهة مستخدم واضحة.
 *  - توفير دوال مساعدة خاصة بالسلة (حساب الأيام المتبقية).
 *
 * مبدأ الخيوط:
 * جميع التحديثات الواردة من TrashRepository تصل على خيط الخلفية.
 * يستخدم هذا الـ ViewModel Handler(Looper.getMainLooper()) أو
 * MutableLiveData.postValue() لإعادة نشرها على الخيط الرئيسي
 * قبل أن يستهلكها TrashFragment.
 *
 * ★ إصلاح الأنيميشن (الخطوة الرابعة من خطة الإصلاح): ★
 * تم نقل التحكم في BatchOperationState من طبقة TrashRepository إلى هذه الطبقة.
 * المبدأ: setProcessing(true, sourceIndex) يُستدعى في بداية كل دالة عملية
 * (restoreFonts, deletePermanently, emptyTrash) قبل استدعاء Repository،
 * و setProcessing(false) يُستدعى داخل mainHandler.postDelayed بعد _operationResult.postValue()
 * مباشرةً، أي بعد إغلاق ديالوج التقدم فعلياً. هذا يضمن الترتيب الصحيح:
 * إغلاق الديالوج أولاً → بداية أنيميشن القائمة بعده. ★
 *
 * ★ إصلاح الإشعارات (المشكلات 1، 2، 5): ★
 * تم نقل إدارة إشعارات التقدم من الـ Fragment إلى الـ ViewModel.
 * المبدأ: الـ ViewModel لا يرتبط بدورة حياة الـ Fragment ويستخدم getApplication()
 * كـ Context دائم، لذا تستمر تحديثات الإشعار حتى لو كان التطبيق في الخلفية.
 * هذا يحل المشكلات الثلاث بضربة واحدة:
 *   - (1): الإشعار يظهر فوراً عند بدء النقل لسلة المحذوفات.
 *   - (2): الإشعار يظهر بغض النظر عن حالة الديالوج (مفتوح أو مخفي).
 *   - (5): الإشعار لا يتجمد عند الخروج من التطبيق. ★
 *
 * ★ إصلاح مشكلة (4) — زر إلغاء الإشعار: ★
 * تم إضافة BatchOperationState.setCancelFlag(cancelFlag) في بداية كل عملية.
 * هذا يُسجّل علم الإلغاء الخاص بالعملية في BatchOperationState، مما يُمكّن
 * NotificationActionReceiver من إيقاف حلقة Repository عند ضغط زر "إلغاء"
 * في الإشعار الموسَّع — تماماً كما لو ضغط المستخدم "إلغاء" في الديالوج. ★
 *
 * ★ إصلاح المشكلتين (1)(3) — OperationForegroundService: ★
 * جميع دوال العمليات (moveToTrash, restoreFonts, deletePermanently, emptyTrash)
 * تُشغّل OperationForegroundService قبل العملية وتوقفه في callback الاكتمال.
 * الخدمة تستخدم startForeground() بنفس notifId الذي تستخدمه TrashActionDialogs،
 * مما يجعل إشعار التقدم إشعاراً مرتبطاً بالخدمة. عند قتل التطبيق من
 * التطبيقات الأخيرة، يُوقف Android الخدمة تلقائياً ويُزيل إشعارها،
 * مما يحل المشكلتين (1) و (3) نهائياً. ★
 *
 * جميع دوال العمليات الجماعية تُسجّل startTime قبل بدء العملية وتضمن أن ديالوج
 * التقدم يظهر لمدة لا تقل عن MIN_DIALOG_DURATION_MS (2500ms) قبل إغلاقه.
 * هذا يمنع وميض الديالوج عند معالجة عنصر واحد بسرعة.
 */
public class TrashViewModel extends AndroidViewModel {

    private static final String TAG = "TrashViewModel";

    /** الحد الأدنى لمدة ظهور ديالوج التقدم (بالميلي ثانية) */
    private static final long MIN_DIALOG_DURATION_MS = 2500;

    private final TrashRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ════════════════════════════════════════════════════════════════════════
    // ★ أنواع العمليات الجماعية ★
    // تُستخدم لتمييز عمليات مختلفة في ديالوج التقدم والإشعارات
    // ════════════════════════════════════════════════════════════════════════

    public enum OperationType {
        MOVE_TO_TRASH,       // نقل ملفات إلى سلة المحذوفات
        RESTORE,             // استعادة ملفات من سلة المحذوفات
        DELETE_PERMANENTLY,  // حذف نهائي من سلة المحذوفات
        EMPTY_TRASH          // إفراغ سلة المحذوفات بالكامل
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ نماذج بيانات الحالة — تُنشر عبر LiveData ★
    // ════════════════════════════════════════════════════════════════════════

    /**
     * بيانات تقدم العملية الجماعية الجارية.
     * تُستخدم لتحديث ProgressDialog في TrashFragment:
     *   progressDialog.setMax(progress.total)
     *   progressDialog.setProgress(progress.current)
     */
    public static class OperationProgress {
        public final int           current;       // عدد الملفات المُعالَجة حتى الآن
        public final int           total;         // إجمالي الملفات المراد معالجتها
        public final OperationType operationType; // نوع العملية الجارية

        public OperationProgress(int current, int total, OperationType operationType) {
            this.current       = current;
            this.total         = total;
            this.operationType = operationType;
        }
    }

    /**
     * نتيجة العملية الجماعية بعد اكتمالها.
     * تُستخدم من TrashFragment لإخفاء ديالوج التقدم والإبلاغ بالنتيجة.
     */
    public static class OperationResult {
        public final int           succeeded;     // عدد الملفات التي عولجت بنجاح
        public final int           failed;        // عدد الملفات التي فشلت معالجتها
        public final OperationType operationType; // نوع العملية المُنجزة
        public final boolean       wasCancelled;  // هل أُلغيت العملية قبل اكتمالها؟

        public OperationResult(int succeeded, int failed,
                               OperationType operationType, boolean wasCancelled) {
            this.succeeded     = succeeded;
            this.operationType = operationType;
            this.failed        = failed;
            this.wasCancelled  = wasCancelled;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ LiveData الداخلية — القابلة للتعديل ★
    // ════════════════════════════════════════════════════════════════════════

    // تقدم العملية الجارية — يُحدَّث بعد كل ملف
    private final MutableLiveData<OperationProgress> _operationProgress = new MutableLiveData<>();

    // نتيجة آخر عملية مكتملة — تُراقَب لإخفاء الديالوج وإظهار النتيجة
    private final MutableLiveData<OperationResult> _operationResult = new MutableLiveData<>();

    // حالة التحميل — true أثناء العمليات الثقيلة
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);

    // ════════════════════════════════════════════════════════════════════════
    // ★ علامة الإلغاء — مشتركة بين الـ ViewModel والـ Repository ★
    // AtomicBoolean لضمان القراءة/الكتابة الآمنة عبر الخيوط المختلفة
    // ════════════════════════════════════════════════════════════════════════

    // يُستخدم cancelFlag لإيقاف الحلقة في TrashRepository عند ضغط المستخدم على "إلغاء"
    private AtomicBoolean cancelFlag = new AtomicBoolean(false);

    // ════════════════════════════════════════════════════════════════════════
    // ★ المُنشئ ★
    // ════════════════════════════════════════════════════════════════════════

    public TrashViewModel(@NonNull Application application) {
        super(application);
        repository = TrashRepository.getInstance(application);

        // تنظيف العناصر منتهية الصلاحية عند تهيئة الـ ViewModel (أي عند فتح TrashFragment)
        // العملية صامتة في الخلفية ولا تُظهر أي ديالوج
        repository.deleteExpiredItems(null);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ LiveData العامة — تُراقَب من TrashFragment ★
    // ════════════════════════════════════════════════════════════════════════

    /**
     * قائمة الخطوط الموجودة في السلة — مرتبة من الأحدث إلى الأقدم حذفاً.
     * تُحدَّث تلقائياً عند أي تغيير في قاعدة البيانات.
     */
    public LiveData<List<FontEntity>> getTrashedFontsLiveData() {
        return repository.getTrashedFonts();
    }

    /**
     * عدد الخطوط في السلة — يُستخدم لتحديث العنوان الفرعي في CollapsingToolbarLayout.
     */
    public LiveData<Integer> getTrashedFontsCountLiveData() {
        return repository.getTrashedFontsCount();
    }

    /**
     * تقدم العملية الجارية — تُستخدم لتحديث ProgressDialog.
     * يُراقبها TrashFragment لاستدعاء setProgress() على الديالوج.
     */
    public LiveData<OperationProgress> getOperationProgressLiveData() {
        return _operationProgress;
    }

    /**
     * نتيجة آخر عملية مكتملة — تُستخدم لإخفاء ProgressDialog وإظهار النتيجة.
     * يُراقبها TrashFragment ليتصرف بعد اكتمال أي عملية جماعية.
     */
    public LiveData<OperationResult> getOperationResultLiveData() {
        return _operationResult;
    }

    /**
     * حالة التحميل — true أثناء العمليات الجماعية.
     */
    public LiveData<Boolean> getIsLoadingLiveData() {
        return _isLoading;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ النقل إلى السلة ★
    // تُستدعى من LocalFontListFragment و FavoriteFontListFragment
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ينقل مجموعة خطوط إلى سلة المحذوفات.
     *
     * يُنشئ cancelFlag جديداً لكل عملية لضمان نظافة الحالة.
     * يُبلَّغ TrashFragment بالتقدم عبر _operationProgress،
     * وبالنتيجة النهائية عبر _operationResult.
     *
     * ★ إصلاح الإشعارات (المشكلات 1، 2، 5):
     *   - showMoveToTrashNotification يُستدعى فوراً قبل بدء العملية (حل 1 و 2).
     *   - updateMoveToTrashNotification يُستدعى من خيط الخلفية في كل callback (حل 5).
     *   - dismissMoveToTrashNotification يُستدعى في mainHandler.postDelayed عند الانتهاء.
     *   جميع الاستدعاءات تستخدم getApplication() كـ Context دائم لا يرتبط بالـ Fragment. ★
     *
     * ★ إصلاح مشكلة (4) — زر إلغاء الإشعار:
     *   BatchOperationState.setCancelFlag(cancelFlag) يُسجّل العلم فوراً بعد إنشائه،
     *   حتى يستطيع NotificationActionReceiver ضبطه على true عند ضغط "إلغاء"
     *   من الإشعار الموسَّع. ★
     *
     * ★ إصلاح الأنيميشن (الخطوة الرابعة من خطة الإصلاح):
     *   setProcessing(true) يُستدعى هنا قبل استدعاء Repository لحجز جميع الشاشات.
     *   setProcessing(false) يُستدعى داخل mainHandler.postDelayed بعد _operationResult.postValue()
     *   مباشرةً لضمان أن الأنيميشن لا يبدأ إلا بعد إغلاق الديالوج. ★
     *
     * ★ إصلاح المشكلتين (1)(3) — OperationForegroundService:
     *   يُشغَّل OperationForegroundService قبل بدء العملية ويُوقَف في callback الاكتمال.
     *   الخدمة تستخدم startForeground(NOTIF_ID_MOVE, notif) مما يجعل الإشعار
     *   مرتبطاً بالخدمة. عند قتل التطبيق، يُوقف Android الخدمة ويُزيل الإشعار
     *   تلقائياً — هذا يحل مشكلة الإشعار العالق نهائياً. ★
     *
     * ملاحظة: sourceFragmentIndex يُقرأ من BatchOperationState لأن الـ Fragment
     * (LocalFontListFragment أو FavoriteFontListFragment) يضبطه قبل استدعاء هذه الدالة.
     *
     * @param fonts قائمة الخطوط المراد نقلها إلى السلة
     */
    public void moveToTrash(@NonNull List<FontEntity> fonts) {
        if (fonts.isEmpty()) return;

        // ★ 1. إعلام الشاشات ببدء العملية لمنع الأنيميشن المبكر ★
        // sourceFragmentIndex مضبوط مسبقاً من الـ Fragment (2=محلي، 4=مفضلة)
        BatchOperationState.setProcessing(true);

        // إنشاء علامة إلغاء جديدة وتصفير الحالة السابقة
        cancelFlag = new AtomicBoolean(false);
        // ★ إصلاح (4): تسجيل العلم في BatchOperationState فوراً ★
        BatchOperationState.setCancelFlag(cancelFlag);
        _isLoading.postValue(true);

        // ★ تسجيل وقت بداية العملية ★
        final long startTime = System.currentTimeMillis();

        // ★ إصلاح (1)(3): تشغيل OperationForegroundService ★
        // يربط الإشعار بالخدمة — إذا قُتل التطبيق يُزيل Android الإشعار تلقائياً
        String movingTitle = getApplication().getResources()
                .getQuantityString(com.example.oneuiapp.R.plurals.progress_moving_to_trash, fonts.size());
        Intent moveServiceIntent = new Intent(getApplication(), OperationForegroundService.class);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, TrashActionDialogs.NOTIF_ID_MOVE);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, movingTitle);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fonts.size());
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_SOURCE_FRAGMENT,
                BatchOperationState.getSourceFragmentIndex());
        ContextCompat.startForegroundService(getApplication(), moveServiceIntent);

        // ★ إصلاح (1)(2): إظهار الإشعار فوراً — بغض النظر عن حالة الديالوج ★
        // getApplication() يضمن استمرار الإشعار حتى بعد إخفاء الديالوج أو الخروج من التطبيق
        TrashActionDialogs.showMoveToTrashNotification(getApplication(), fonts.size());

        repository.moveToTrashBatch(
                getApplication(),
                fonts,
                cancelFlag,

                // ★ مُستمع التقدم: يُستدعى من خيط الخلفية بعد كل ملف ★
                (current, total) -> {
                    // ★ إصلاح (5): تحديث الإشعار مباشرةً من خيط الخلفية عبر ViewModel ★
                    TrashActionDialogs.updateMoveToTrashNotification(getApplication(), current, total);
                    _operationProgress.postValue(
                            new OperationProgress(current, total, OperationType.MOVE_TO_TRASH));
                },

                // ★ مُستمع الاكتمال: يضمن الحد الأدنى لمدة الديالوج ثم ينشر النتيجة ★
                (succeeded, failed) -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    mainHandler.postDelayed(() -> {
                        // ★ إيقاف OperationForegroundService — يُزيل الإشعار المرتبط بالخدمة ★
                        getApplication().stopService(
                                new Intent(getApplication(), OperationForegroundService.class));
                        // ★ إزالة الإشعار الثانوي احتياطاً (في حال كان هناك إشعار مستقل) ★
                        TrashActionDialogs.dismissMoveToTrashNotification(getApplication());
                        _isLoading.postValue(false);
                        _operationResult.postValue(new OperationResult(
                                succeeded, failed,
                                OperationType.MOVE_TO_TRASH,
                                cancelFlag.get()
                        ));
                        // ★ 2. إنهاء الحجز فور إغلاق الديالوج لتبدأ الأنيميشن بعده مباشرة ★
                        BatchOperationState.setProcessing(false);
                        Log.d(TAG, "moveToTrash complete — succeeded: " + succeeded
                                + ", failed: " + failed);
                    }, delay);
                }
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ الاستعادة من السلة ★
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يستعيد مجموعة خطوط من سلة المحذوفات إلى مواقعها الأصلية.
     *
     * ★ إصلاح الإشعارات (المشكلات 1، 2، 5):
     *   - showRestoreNotification يُستدعى فوراً قبل بدء العملية (حل 1 و 2).
     *   - updateRestoreNotification يُستدعى من خيط الخلفية في كل callback (حل 5).
     *   - dismissRestoreNotification يُستدعى عند انتهاء العملية. ★
     *
     * ★ إصلاح مشكلة (4) — زر إلغاء الإشعار:
     *   BatchOperationState.setCancelFlag(cancelFlag) يُسجّل العلم فوراً. ★
     *
     * ★ إصلاح الأنيميشن (الخطوة الرابعة من خطة الإصلاح):
     *   setProcessing(true, 5) يُضبط sourceIndex=5 (TrashFragment) لأن الاستعادة
     *   تبدأ دائماً من شاشة سلة المحذوفات.
     *   setProcessing(false) يُستدعى داخل mainHandler.postDelayed بعد _operationResult.postValue(). ★
     *
     * ★ إصلاح المشكلتين (1)(3) — OperationForegroundService:
     *   يُشغَّل بـ NOTIF_ID_RESTORE قبل العملية ويُوقَف عند اكتمالها.
     *   إذا قُتل التطبيق، يُزيل Android الإشعار تلقائياً. ★
     *
     * @param fonts قائمة الخطوط المراد استعادتها
     */
    public void restoreFonts(@NonNull List<FontEntity> fonts) {
        if (fonts.isEmpty()) return;

        // ★ 1. إعلام الشاشات ببدء عملية الاستعادة (sourceIndex=5 لأنها من TrashFragment) ★
        BatchOperationState.setProcessing(true, 5);

        cancelFlag = new AtomicBoolean(false);
        // ★ إصلاح (4): تسجيل العلم في BatchOperationState فوراً ★
        BatchOperationState.setCancelFlag(cancelFlag);
        _isLoading.postValue(true);

        // ★ تسجيل وقت بداية العملية ★
        final long startTime = System.currentTimeMillis();

        // ★ إصلاح (1)(3): تشغيل OperationForegroundService بـ NOTIF_ID_RESTORE ★
        String restoreTitle = getApplication().getResources()
                .getQuantityString(com.example.oneuiapp.R.plurals.progress_restoring, fonts.size());
        Intent restoreServiceIntent = new Intent(getApplication(), OperationForegroundService.class);
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, TrashActionDialogs.NOTIF_ID_RESTORE);
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, restoreTitle);
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fonts.size());
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_SOURCE_FRAGMENT, 5);
        ContextCompat.startForegroundService(getApplication(), restoreServiceIntent);

        // ★ إصلاح (1)(2): إظهار إشعار الاستعادة فوراً — بغض النظر عن حالة الديالوج ★
        TrashActionDialogs.showRestoreNotification(getApplication(), fonts.size());

        repository.restoreBatch(
                getApplication(),
                fonts,
                cancelFlag,

                // ★ مُستمع التقدم ★
                (current, total) -> {
                    // ★ إصلاح (5): تحديث الإشعار من ViewModel — مستقل عن دورة حياة الـ Fragment ★
                    TrashActionDialogs.updateRestoreNotification(getApplication(), current, total);
                    _operationProgress.postValue(
                            new OperationProgress(current, total, OperationType.RESTORE));

                    // ★ الإضافة الجوهرية: تحديث التقدم في الحالة العالمية مع الكود 2 (استعادة) ★
                    String progressTitle = getApplication().getResources()
                            .getQuantityString(com.example.oneuiapp.R.plurals.progress_restoring, total);
                    BatchOperationState.updateProgress(current, total, progressTitle, 2);
                },

                // ★ مُستمع الاكتمال: يضمن الحد الأدنى لمدة الديالوج ثم ينشر النتيجة ★
                (succeeded, failed) -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    mainHandler.postDelayed(() -> {
                        // ★ إيقاف OperationForegroundService — يُزيل الإشعار المرتبط بالخدمة ★
                        getApplication().stopService(
                                new Intent(getApplication(), OperationForegroundService.class));
                        // ★ إزالة الإشعار الثانوي احتياطاً ★
                        TrashActionDialogs.dismissRestoreNotification(getApplication());
                        _isLoading.postValue(false);
                        _operationResult.postValue(new OperationResult(
                                succeeded, failed,
                                OperationType.RESTORE,
                                cancelFlag.get()
                        ));
                        // ★ 2. إنهاء الحجز فور إغلاق الديالوج لتبدأ الأنيميشن بعده مباشرة ★
                        BatchOperationState.setProcessing(false);
                        Log.d(TAG, "restoreFonts complete — succeeded: " + succeeded
                                + ", failed: " + failed);
                    }, delay);
                }
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ الحذف النهائي من السلة ★
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يحذف نهائياً مجموعة خطوط من سلة المحذوفات.
     * لا يمكن التراجع عن هذه العملية.
     *
     * ★ إصلاح الإشعارات (المشكلات 1، 2، 5):
     *   - showDeleteNotification يُستدعى فوراً قبل بدء العملية (حل 1 و 2).
     *   - updateDeleteNotification يُستدعى من خيط الخلفية في كل callback (حل 5).
     *   - dismissDeleteNotification يُستدعى عند انتهاء العملية. ★
     *
     * ★ إصلاح مشكلة (4) — زر إلغاء الإشعار:
     *   BatchOperationState.setCancelFlag(cancelFlag) يُسجّل العلم فوراً. ★
     *
     * ★ إصلاح الأنيميشن (الخطوة الرابعة من خطة الإصلاح):
     *   setProcessing(true, 5) يُضبط sourceIndex=5 (TrashFragment) لأن الحذف
     *   يبدأ دائماً من شاشة سلة المحذوفات.
     *   setProcessing(false) يُستدعى داخل mainHandler.postDelayed. ★
     *
     * ★ إصلاح المشكلتين (1)(3) — OperationForegroundService:
     *   يُشغَّل بـ NOTIF_ID_DELETE قبل العملية ويُوقَف عند اكتمالها. ★
     *
     * @param fonts قائمة الخطوط المراد حذفها نهائياً
     */
    public void deletePermanently(@NonNull List<FontEntity> fonts) {
        if (fonts.isEmpty()) return;

        // ★ 1. إعلام الشاشات ببدء عملية الحذف النهائي (sourceIndex=5 لأنها من TrashFragment) ★
        BatchOperationState.setProcessing(true, 5);

        cancelFlag = new AtomicBoolean(false);
        // ★ إصلاح (4): تسجيل العلم في BatchOperationState فوراً ★
        BatchOperationState.setCancelFlag(cancelFlag);
        _isLoading.postValue(true);

        // ★ تسجيل وقت بداية العملية ★
        final long startTime = System.currentTimeMillis();

        // ★ إصلاح (1)(3): تشغيل OperationForegroundService بـ NOTIF_ID_DELETE ★
        String deleteTitle = getApplication().getResources()
                .getQuantityString(com.example.oneuiapp.R.plurals.progress_deleting, fonts.size());
        Intent deleteServiceIntent = new Intent(getApplication(), OperationForegroundService.class);
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, TrashActionDialogs.NOTIF_ID_DELETE);
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, deleteTitle);
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fonts.size());
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_SOURCE_FRAGMENT, 5);
        ContextCompat.startForegroundService(getApplication(), deleteServiceIntent);

        // ★ إصلاح (1)(2): إظهار إشعار الحذف النهائي فوراً — بغض النظر عن حالة الديالوج ★
        TrashActionDialogs.showDeleteNotification(getApplication(), fonts.size());

        repository.deletePermanentlyBatch(
                getApplication(),
                fonts,
                cancelFlag,

                // ★ مُستمع التقدم ★
                (current, total) -> {
                    // ★ إصلاح (5): تحديث الإشعار من ViewModel — مستقل عن دورة حياة الـ Fragment ★
                    TrashActionDialogs.updateDeleteNotification(getApplication(), current, total);
                    _operationProgress.postValue(
                            new OperationProgress(current, total, OperationType.DELETE_PERMANENTLY));

                    // ★ الإضافة الجوهرية: تحديث التقدم في الحالة العالمية مع الكود 3 (حذف) ★
                    String progressTitle = getApplication().getResources()
                            .getQuantityString(com.example.oneuiapp.R.plurals.progress_deleting, total);
                    BatchOperationState.updateProgress(current, total, progressTitle, 3);
                },

                // ★ مُستمع الاكتمال: يضمن الحد الأدنى لمدة الديالوج ثم ينشر النتيجة ★
                (succeeded, failed) -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    mainHandler.postDelayed(() -> {
                        // ★ إيقاف OperationForegroundService — يُزيل الإشعار المرتبط بالخدمة ★
                        getApplication().stopService(
                                new Intent(getApplication(), OperationForegroundService.class));
                        // ★ إزالة الإشعار الثانوي احتياطاً ★
                        TrashActionDialogs.dismissDeleteNotification(getApplication());
                        _isLoading.postValue(false);
                        _operationResult.postValue(new OperationResult(
                                succeeded, failed,
                                OperationType.DELETE_PERMANENTLY,
                                cancelFlag.get()
                        ));
                        // ★ 2. إنهاء الحجز فور إغلاق الديالوج لتبدأ الأنيميشن بعده مباشرة ★
                        BatchOperationState.setProcessing(false);
                        Log.d(TAG, "deletePermanently complete — succeeded: " + succeeded
                                + ", failed: " + failed);
                    }, delay);
                }
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ إفراغ السلة بالكامل ★
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُفرغ سلة المحذوفات بحذف جميع عناصرها نهائياً.
     * يُستدعى عند اختيار خيار "إفراغ" من قائمة الثلاث نقاط.
     *
     * ★ إصلاح الإشعارات (المشكلات 1، 2، 5):
     *   لأن emptyTrash لا يعرف العدد الإجمالي مسبقاً (يُحدده Repository عند الجلب)،
     *   يُستخدم notifShownForEmpty (AtomicBoolean) لضمان إنشاء الإشعار مرة واحدة فقط
     *   في أول callback تقدم — حيث يكون total متاحاً بشكل صحيح.
     *   هذا النهج يضمن ظهور الإشعار شبه فوري (عند معالجة أول ملف).
     *   updateDeleteNotification يُستمر في التحديث من خيط الخلفية (حل 5). ★
     *
     * ★ إصلاح مشكلة (4) — زر إلغاء الإشعار:
     *   BatchOperationState.setCancelFlag(cancelFlag) يُسجّل العلم فوراً. ★
     *
     * ★ إصلاح الأنيميشن (الخطوة الرابعة من خطة الإصلاح):
     *   setProcessing(true, 5) يُضبط sourceIndex=5 لأن الإفراغ من TrashFragment.
     *   setProcessing(false) يُستدعى داخل mainHandler.postDelayed. ★
     *
     * ★ إصلاح المشكلتين (1)(3) — OperationForegroundService:
     *   يُشغَّل بعد أول callback تقدم (لمعرفة total) ويُوقَف عند الاكتمال. ★
     */
    public void emptyTrash() {
        // ★ 1. إعلام الشاشات ببدء عملية إفراغ السلة (sourceIndex=5 لأنها من TrashFragment) ★
        BatchOperationState.setProcessing(true, 5);

        cancelFlag = new AtomicBoolean(false);
        // ★ إصلاح (4): تسجيل العلم في BatchOperationState فوراً ★
        BatchOperationState.setCancelFlag(cancelFlag);
        _isLoading.postValue(true);

        // ★ تسجيل وقت بداية العملية ★
        final long startTime = System.currentTimeMillis();

        // ★ علامة تضمن إنشاء الخدمة والإشعار مرة واحدة فقط في أول callback تقدم ★
        // compareAndSet(false, true) → آمن عبر الخيوط (thread-safe)
        final AtomicBoolean notifShownForEmpty = new AtomicBoolean(false);

        repository.emptyTrash(
                getApplication(),
                cancelFlag,

                // ★ مُستمع التقدم ★
                (current, total) -> {
                    // ★ إصلاح (1)(3): تشغيل الخدمة والإشعار في أول callback — عند معرفة total ★
                    if (notifShownForEmpty.compareAndSet(false, true)) {
                        // بناء عنوان الإشعار بعد معرفة total
                        String emptyTitle = getApplication().getResources()
                                .getQuantityString(com.example.oneuiapp.R.plurals.progress_deleting, total);
                        Intent emptyServiceIntent = new Intent(getApplication(), OperationForegroundService.class);
                        emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, TrashActionDialogs.NOTIF_ID_DELETE);
                        emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, emptyTitle);
                        emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, total);
                        emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_SOURCE_FRAGMENT, 5);
                        ContextCompat.startForegroundService(getApplication(), emptyServiceIntent);
                        TrashActionDialogs.showDeleteNotification(getApplication(), total);
                    }
                    // ★ إصلاح (5): تحديث الإشعار من ViewModel — مستقل عن دورة حياة الـ Fragment ★
                    TrashActionDialogs.updateDeleteNotification(getApplication(), current, total);
                    _operationProgress.postValue(
                            new OperationProgress(current, total, OperationType.EMPTY_TRASH));

                    // ★ الإضافة الجوهرية: تحديث التقدم في الحالة العالمية مع الكود 3 (حذف) ★
                    String progressTitle = getApplication().getResources()
                            .getQuantityString(com.example.oneuiapp.R.plurals.progress_deleting, total);
                    BatchOperationState.updateProgress(current, total, progressTitle, 3);
                },

                // ★ مُستمع الاكتمال: يضمن الحد الأدنى لمدة الديالوج ثم ينشر النتيجة ★
                (succeeded, failed) -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    mainHandler.postDelayed(() -> {
                        // ★ إيقاف OperationForegroundService — يُزيل الإشعار المرتبط بالخدمة ★
                        getApplication().stopService(
                                new Intent(getApplication(), OperationForegroundService.class));
                        // ★ إزالة الإشعار الثانوي احتياطاً ★
                        TrashActionDialogs.dismissDeleteNotification(getApplication());
                        _isLoading.postValue(false);
                        _operationResult.postValue(new OperationResult(
                                succeeded, failed,
                                OperationType.EMPTY_TRASH,
                                cancelFlag.get()
                        ));
                        // ★ 2. إنهاء الحجز فور إغلاق الديالوج لتبدأ الأنيميشن بعده مباشرة ★
                        BatchOperationState.setProcessing(false);
                        Log.d(TAG, "emptyTrash complete — succeeded: " + succeeded
                                + ", failed: " + failed);
                    }, delay);
                }
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ إلغاء العملية الجارية ★
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُلغي العملية الجارية عن طريق تعيين cancelFlag إلى true.
     *
     * سيلاحظ TrashRepository هذا التغيير في بداية الدورة التالية
     * ويتوقف عن معالجة الملفات المتبقية.
     * ملاحظة: الملف الذي بدأت معالجته بالفعل يُكتمل قبل التوقف.
     *
     * يُستدعى من TrashFragment عند ضغط المستخدم على زر "إلغاء"
     * في ديالوج التقدم.
     */
    public void cancelCurrentOperation() {
        if (cancelFlag != null) {
            cancelFlag.set(true);
            Log.d(TAG, "Operation cancellation requested");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ دوال مساعدة ★
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يحسب الأيام المتبقية قبل حذف خط نهائياً من السلة.
     *
     * المعادلة:
     *   elapsedDays = (currentTime - deletedAt) / MILLIS_PER_DAY
     *   daysRemaining = 30 - elapsedDays
     *
     * القيمة المُعادة:
     *   양수  → أيام متبقية قبل الحذف النهائي
     *   0    → سيُحذف اليوم (same day)
     *   سالب → تجاوز المدة ويجب حذفه (نادر الحدوث، يُعالَج بـ deleteExpiredItems)
     *
     * تُستخدم هذه القيمة في TrashListAdapter لاختيار صيغة plurals المناسبة:
     *   0         → quantity="zero"  → "اليوم" / "Today"
     *   1         → quantity="one"   → "يوم واحد" / "1 day"
     *   2         → quantity="two"   → "يومان"
     *   3..10     → quantity="few"   → "%d أيام"
     *   11..30    → quantity="many"  → "%d يوماً"
     *
     * @param entity كيان الخط الموجود في السلة
     * @return عدد الأيام المتبقية (قد يكون 0 أو سالباً)
     */
    public static int getDaysRemaining(@NonNull FontEntity entity) {
        if (entity.getDeletedAt() <= 0) {
            // deletedAt غير مُعيَّن — نُعيد 30 كقيمة آمنة افتراضية
            return 30;
        }

        long now         = System.currentTimeMillis();
        long elapsedMs   = now - entity.getDeletedAt();
        long elapsedDays = elapsedMs / (24L * 60 * 60 * 1000);

        return (int) (30 - elapsedDays);
    }

    /**
     * يُعيد true إذا كانت العملية الجماعية الحالية لا تزال جارية.
     * يُستخدم من TrashFragment لمعرفة ما إذا كان يجب إظهار ديالوج التقدم
     * عند إعادة بناء الـ Fragment (مثلاً: عند تدوير الشاشة).
     */
    public boolean isOperationRunning() {
        Boolean loading = _isLoading.getValue();
        return loading != null && loading;
    }

    /**
     * يُصفِّر نتيجة آخر عملية لتجنب إعادة معالجتها عند تدوير الشاشة.
     * يُستدعى من TrashFragment بعد استهلاك نتيجة العملية والتصرف بناءً عليها.
     */
    public void clearOperationResult() {
        _operationResult.postValue(null);
    }
            }
