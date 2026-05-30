package com.example.oneuiapp.utils;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.oneuiapp.activity.AppScreen;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BatchOperationState — إشارة المرور العامة للعمليات الجماعية
 *
 * تُحلّ هذه الفئة مشكلة اللاج الناتج عن انتقال المستخدم بين الشاشات
 * أثناء تنفيذ عملية ضخمة (نقل/استعادة/حذف مئات الملفات).
 *
 * المشكلة القديمة:
 *   كان كل Fragment يدير mIsBatchOperationRunning بشكل مستقل.
 *   عند إخفاء الديالوج والانتقال لشاشة جديدة، يُنشأ Fragment جديد
 *   بقيمة افتراضية false، فيستقبل مئات التحديثات المتتالية ويرسمها
 *   عنصراً تلو الآخر مما يُجمّد المعالج.
 *
 * الحل:
 *   - عند بدء أي عملية ضخمة في Repository → setProcessing(true)
 *   - كل Fragment في التطبيق يراقب getIsProcessing()
 *   - عند true: يحجز البيانات في mPendingXxxUpdate بدلاً من تحديث الواجهة
 *   - عند false (انتهاء العملية): يطبّق التحديث دفعةً واحدة بأنيميشن سلس
 *
 * ★ الإصلاح الجوهري (الأرقام السحرية → AppScreen):
 *   بدلاً من حفظ فهرس رقمي (_sourceFragmentIndex = 5) يتأثر بترتيب الشاشات،
 *   يُحفظ الآن نوع الشاشة (_sourceScreen = AppScreen.TRASH) باسمها الثابت.
 *   هذا يضمن صحة التوجيه حتى لو تغيّر ترتيب الشاشات في مصفوفة mFragments.
 *
 *   - setSourceScreen(): يُستدعى من الـ Fragment قبل إظهار الإشعار مباشرةً.
 *   - getSourceScreen(): يُقرأ في checkAndReopenProgressDialogPublic() داخل الفراغمنتات.
 *   - getSourceFragmentIndex() @Deprecated: للتوافق مع TrashActionDialogs القائم على int.
 *   - setSourceFragmentIndex() @Deprecated: للتوافق مع الكود القديم الذي يُمرّر أرقاماً.
 *
 * ★ إصلاح المشكلة (2) — مزامنة التقدم عند إعادة فتح الديالوج:
 *   - _progress LiveData: يحمل آخر قيمة تقدم من خيط الخلفية.
 *   - updateProgress(): يُستدعى من TrashRepository في كل دورة حلقة.
 *   - getProgress(): يُراقَب من LocalFontListFragment لتحديث الديالوج المُعاد فتحه.
 *
 * ★ دعم الإلغاء من الإشعار (المشكلة 4):
 *   - setCancelFlag(): يُسجّل علم الإلغاء الخاص بالعملية الجارية.
 *   - requestCancel(): يضبط العلم المُسجَّل على true.
 *
 * يعمل هذا الملف كـ Singleton ثابت (static) بدون حاجة لإنشاء نسخة منه.
 */
public class BatchOperationState {

    private static final String TAG = "BatchOperationState";

    // ─────────────────────────────────────────────────────────────────────
    // ★ حالة العملية الجارية
    // ─────────────────────────────────────────────────────────────────────

    /** true أثناء معالجة ملفات كثيرة — يُراقَب من جميع الـ Fragments */
    private static final MutableLiveData<Boolean> _isProcessing =
            new MutableLiveData<>(false);

    // ─────────────────────────────────────────────────────────────────────
    // ★ شاشة المصدر — الإصلاح الجوهري (AppScreen بدلاً من int)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * نوع الشاشة التي أطلقت العملية الجارية.
     *   AppScreen.LOCAL_FONTS = نقل من قائمة الخطوط المحلية
     *   AppScreen.FAVORITES   = نقل من قائمة المفضلة
     *   AppScreen.TRASH       = استعادة/حذف/إفراغ من سلة المحذوفات
     *   null                  = لا توجد عملية جارية
     *
     * يُقرأ في checkAndReopenProgressDialogPublic() داخل الفراغمنتات لضمان
     * أن الديالوج يُعاد فتحه في الشاشة الصحيحة فقط.
     *
     * volatile: يضمن رؤية القيمة الحديثة عبر الخيوط المختلفة.
     */
    private static volatile AppScreen _sourceScreen = null;

    // ─────────────────────────────────────────────────────────────────────
    // ★ بيانات تقدم العملية — إصلاح المشكلة (2)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * نموذج بيانات التقدم الجارية.
     * يُستخدم من LocalFontListFragment وTrashFragment عند إعادة فتح الديالوج
     * بعد الضغط على الإشعار للتزامن مع التقدم الفعلي.
     *
     * ★ التعديل الأول (خطة الإصلاح): إضافة operationCode لتمييز نوع العملية ★
     *   1 = Move (نقل للسلة)
     *   2 = Restore (استعادة)
     *   3 = Delete (حذف نهائي)
     * يُستخدم operationCode في TrashFragment.reconnectToProgressDialog()
     * لاختيار عنوان الديالوج الصحيح بناءً على لغة الجهاز.
     */
    public static class ProgressData {
        public final int    current;
        public final int    total;
        public final String title;
        public final int    operationCode; // 1=Move, 2=Restore, 3=Delete

        // منشئ للتوافق مع العمليات القديمة (يفترض 1=Move)
        public ProgressData(int current, int total, String title) {
            this(current, total, title, 1);
        }

        public ProgressData(int current, int total, String title, int operationCode) {
            this.current = current;
            this.total   = total;
            this.title   = title;
            this.operationCode = operationCode;
        }
    }

    /** LiveData يحمل آخر تقدم من خيط الخلفية — null بين العمليات */
    private static final MutableLiveData<ProgressData> _progress =
            new MutableLiveData<>();

    // ─────────────────────────────────────────────────────────────────────
    // ★ علم الإلغاء — دعم زر إلغاء الإشعار (المشكلة 4)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * مرجع علم الإلغاء الخاص بالعملية الجارية حالياً.
     * يُسجَّل هنا بواسطة setCancelFlag() لتمكين NotificationActionReceiver
     * من الوصول إليه وضبطه عند ضغط زر الإلغاء في الإشعار.
     *
     * volatile: يضمن رؤية قيمة المرجع الحديثة عبر الخيوط.
     */
    private static volatile AtomicBoolean _currentCancelFlag = null;

    // ─────────────────────────────────────────────────────────────────────
    // ★ متغير جديد: يحدد ما إذا كان يجب فتح الديالوج أم لا
    // ─────────────────────────────────────────────────────────────────────

    private static volatile boolean _shouldReopenDialog = false;

    public static void setShouldReopenDialog(boolean shouldReopen) {
        _shouldReopenDialog = shouldReopen;
    }

    // تقرأ القيمة وتستهلكها (تعيدها إلى false) لكي لا يفتح الديالوج مرتين
    public static boolean consumeShouldReopenDialog() {
        boolean value = _shouldReopenDialog;
        _shouldReopenDialog = false;
        return value;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ★ Getters العامة
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * LiveData يراقبه كل Fragment للاستجابة لتغيرات حالة العملية.
     * يُستدعى في setupViewModelObservers() داخل كل Fragment معني.
     */
    public static LiveData<Boolean> getIsProcessing() {
        return _isProcessing;
    }

    /**
     * ★ (إصلاح م2) LiveData يحمل آخر تقدم من خيط الخلفية.
     * يُراقَب من LocalFontListFragment وTrashFragment لتحديث الديالوج المُعاد فتحه
     * بعد الضغط على الإشعار والعودة للشاشة الصحيحة.
     */
    public static LiveData<ProgressData> getProgress() {
        return _progress;
    }

    /**
     * ★ يُعيد نوع الشاشة التي أطلقت العملية الجارية.
     *
     * يُستخدم في checkAndReopenProgressDialogPublic() داخل الفراغمنتات:
     *   if (getSourceScreen() != AppScreen.TRASH) return; // ليس عملية سلة المحذوفات
     *   if (getSourceScreen() != AppScreen.LOCAL_FONTS) return; // ليس عملية خطوط محلية
     *
     * @return AppScreen الشاشة المُطلِقة، أو null إن لم تكن هناك عملية جارية
     */
    public static AppScreen getSourceScreen() {
        return _sourceScreen;
    }

    /**
     * ★ @Deprecated — للتوافق مع TrashActionDialogs و OperationForegroundService ★
     *
     * يُعيد الفهرس الرقمي المكافئ لـ _sourceScreen للاستخدام في PendingIntent.
     * يجب الاستعاضة عنه بـ getSourceScreen() في الكود الجديد.
     *
     * @return فهرس الـ Fragment (2=محلي، 4=مفضلة، 5=سلة، -1=لا توجد عملية)
     */
    @Deprecated
    public static int getSourceFragmentIndex() {
        if (_sourceScreen == null) return -1;
        switch (_sourceScreen) {
            case LOCAL_FONTS:  return 2;
            case SYSTEM_FONTS: return 3;
            case FAVORITES:    return 4;
            case TRASH:        return 5;
            default:           return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ★ Setters/Mutators العامة
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * يُستدعى من خيط الخلفية (Repository) لإعلام جميع الشاشات ببدء/انتهاء العملية.
     * يستخدم postValue() لضمان التحديث الآمن من أي خيط.
     *
     * @param isProcessing true = بدأت عملية ضخمة | false = انتهت العملية
     */
    public static void setProcessing(boolean isProcessing) {
        if (!isProcessing) {
            // إعادة تعيين شاشة المصدر عند انتهاء العملية
            _sourceScreen = null;
        }
        _isProcessing.postValue(isProcessing);
    }

    /**
     * ★ (إصلاح م2) نسخة موسَّعة تحفظ شاشة المصدر أيضاً.
     *
     * يُستدعى من ViewModels (TrashViewModel, LocalFontListViewModel) عند بدء
     * كل عملية، قبل إظهار الإشعار مباشرةً، لضمان أن buildContentIntent()
     * تقرأ الفهرس الصحيح وتضيفه كـ TARGET_FRAGMENT.
     *
     * ★ @Deprecated: استخدم setProcessing(boolean, AppScreen) في الكود الجديد ★
     *
     * @param isProcessing       true = بدأت عملية | false = انتهت
     * @param sourceFragmentIndex فهرس الـ Fragment المُطلِق (2=محلي، 4=مفضلة، 5=سلة)
     */
    @Deprecated
    public static void setProcessing(boolean isProcessing, int sourceFragmentIndex) {
        if (isProcessing) {
            setSourceFragmentIndex(sourceFragmentIndex);
        } else {
            _sourceScreen = null;
        }
        _isProcessing.postValue(isProcessing);
        Log.d(TAG, "setProcessing=" + isProcessing + " | sourceScreen=" + _sourceScreen);
    }

    /**
     * ★ يُعيَّن نوع الشاشة المصدر بشكل صريح — النسخة الجديدة المُوصى بها ★
     *
     * يُستدعى من الـ Fragment مباشرةً قبل إظهار الإشعار:
     *   BatchOperationState.setSourceScreen(AppScreen.TRASH);
     *   BatchOperationState.setSourceScreen(AppScreen.LOCAL_FONTS);
     *   BatchOperationState.setSourceScreen(AppScreen.FAVORITES);
     *
     * @param screen نوع الشاشة المُطلِقة للعملية
     */
    public static void setSourceScreen(AppScreen screen) {
        _sourceScreen = screen;
        Log.d(TAG, "setSourceScreen=" + screen);
    }

    /**
     * ★ @Deprecated — للتوافق مع الكود القديم الذي يُمرّر أرقاماً ★
     *
     * يُحوِّل الفهرس الرقمي إلى AppScreen ويضبطه.
     * يجب الاستعاضة عنه بـ setSourceScreen(AppScreen) في الكود الجديد.
     *
     * @param index فهرس الـ Fragment المُطلِق (2=محلي، 4=مفضلة، 5=سلة)
     */
    @Deprecated
    public static void setSourceFragmentIndex(int index) {
        switch (index) {
            case 2:  _sourceScreen = AppScreen.LOCAL_FONTS;  break;
            case 3:  _sourceScreen = AppScreen.SYSTEM_FONTS; break;
            case 4:  _sourceScreen = AppScreen.FAVORITES;    break;
            case 5:  _sourceScreen = AppScreen.TRASH;        break;
            default: _sourceScreen = null;                   break;
        }
        Log.d(TAG, "setSourceFragmentIndex=" + index + " → _sourceScreen=" + _sourceScreen);
    }

    /**
     * ★ (إصلاح م2) يُحدِّث بيانات التقدم المشتركة — النسخة الأصلية للتوافق.
     *
     * يُستدعى من LocalFontListViewModel في كل دورة حلقة.
     * يفترض operationCode=1 (Move) للتوافق مع الاستدعاءات القديمة.
     *
     * يعمل على خيط الخلفية — postValue() آمن من أي خيط.
     *
     * @param current العدد المُعالَج حتى الآن
     * @param total   إجمالي الملفات
     * @param title   عنوان العملية (من Plurals)
     */
    public static void updateProgress(int current, int total, String title) {
        _progress.postValue(new ProgressData(current, total, title, 1));
    }

    /**
     * ★ (التعديل الأول — خطة الإصلاح) نسخة موسَّعة تحدد نوع العملية.
     *
     * يُستدعى من TrashViewModel في مستمعات التقدم لعمليات السلة:
     *   operationCode=2 لعملية الاستعادة (restoreFonts)
     *   operationCode=3 لعمليتي الحذف النهائي وإفراغ السلة (deletePermanently, emptyTrash)
     * يُمكّن TrashFragment.reconnectToProgressDialog() من اختيار العنوان الصحيح
     * بناءً على لغة الجهاز عند إعادة فتح الديالوج من الإشعار.
     *
     * يعمل على خيط الخلفية — postValue() آمن من أي خيط.
     *
     * @param current       العدد المُعالَج حتى الآن
     * @param total         إجمالي الملفات
     * @param title         عنوان العملية (من Plurals)
     * @param operationCode كود العملية (1=Move, 2=Restore, 3=Delete)
     */
    public static void updateProgress(int current, int total, String title, int operationCode) {
        _progress.postValue(new ProgressData(current, total, title, operationCode));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ★ دعم الإلغاء من الإشعار (المشكلة 4)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ★ يُسجّل علم الإلغاء الخاص بالعملية الجارية.
     *
     * يُستدعى من TrashViewModel/LocalFontListViewModel في بداية كل عملية
     * بعد إنشاء cancelFlag الجديد مباشرةً.
     * يضمن أن requestCancel() يُوصل طلب الإلغاء إلى العملية الصحيحة.
     *
     * @param flag علم الإلغاء AtomicBoolean المُمرَّر إلى TrashRepository
     */
    public static void setCancelFlag(AtomicBoolean flag) {
        _currentCancelFlag = flag;
        Log.d(TAG, "Cancel flag registered for current operation");
    }

    /**
     * ★ يطلب إلغاء العملية الجارية.
     *
     * يُستدعى من NotificationActionReceiver عند ضغط المستخدم على زر "إلغاء"
     * في الإشعار الموسَّع أثناء تنفيذ عملية السلة.
     * يضبط علم الإلغاء المُسجَّل مسبقاً بواسطة setCancelFlag() على true.
     *
     * إذا لم يكن هناك علم مُسجَّل (لا توجد عملية جارية)، لا يفعل شيئاً.
     */
    public static void requestCancel() {
        if (_currentCancelFlag != null) {
            _currentCancelFlag.set(true);
            Log.d(TAG, "Cancel requested via notification — flag set to true");
        } else {
            Log.w(TAG, "requestCancel() called but no cancel flag is registered");
        }
    }
}
