package com.example.oneuiapp.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.oneuiapp.data.dao.FontDao;
import com.example.oneuiapp.data.database.AppDatabase;
import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.trash.TrashFileManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TrashRepository - طبقة البيانات الخاصة بسلة المحذوفات
 *
 * تتولى هذه الطبقة تنسيق العمليات بين:
 *  - FontDao    : للاستعلامات وتحديث قاعدة البيانات.
 *  - TrashFileManager : لعمليات نظام الملفات (نقل / استعادة / حذف).
 *
 * المبدأ المعماري:
 * هذا الملف لا يعرف شيئاً عن واجهة المستخدم (ViewModel / Fragment).
 * كل عملية ثقيلة تعمل على خيط الخلفية عبر ExecutorService.
 * يتم الإبلاغ عن التقدم والنتيجة عبر الواجهتين OnProgressListener و OnBatchCompleteListener.
 *
 * مدة الاحتفاظ بالملفات في السلة:
 * 30 يوماً من تاريخ النقل. يُحسب عبر TRASH_EXPIRY_MS.
 *
 * ★ إصلاح الأنيميشن (خطة الإصلاح — الخطوة الأولى):
 * تم نقل التحكم في BatchOperationState من هذه الطبقة إلى طبقة ViewModel.
 * المبرر: Repository تنتهي بسرعة فور اكتمال عمليات قاعدة البيانات، بينما
 * ViewModel هي من تعرف متى يُغلَق ديالوج التقدم فعلياً (بعد انتهاء التأخير الزمني).
 * استدعاء setProcessing(false) هنا كان يُطلق الأنيميشن قبل إغلاق الديالوج.
 */
public class TrashRepository {

    private static final String TAG = "TrashRepository";

    // مدة الاحتفاظ بالملفات في السلة: 30 يوماً بالميلي ثانية
    public static final long TRASH_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000;

    private static volatile TrashRepository INSTANCE;

    private final FontDao         fontDao;
    private final ExecutorService executorService;

    // ────────────────────────────────────────────────────────────────────────
    // ★ Singleton — نمط النسخة الواحدة ★
    // ────────────────────────────────────────────────────────────────────────

    private TrashRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        fontDao         = database.fontDao();
        executorService = AppDatabase.databaseWriteExecutor;
    }

    public static TrashRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TrashRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TrashRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ استعلامات LiveData — المراقبة التفاعلية ★
    // تعمل على خيط الخلفية تلقائياً عبر Room و LiveData
    // ────────────────────────────────────────────────────────────────────────

    /**
     * إرجاع جميع الخطوط الموجودة في السلة كـ LiveData مرتبةً من الأحدث إلى الأقدم حذفاً.
     * يراقبها TrashViewModel تلقائياً لتحديث واجهة المستخدم عند أي تغيير.
     */
    public LiveData<List<FontEntity>> getTrashedFonts() {
        // ✅ إصلاح 1: كان fontDao.getTrashedFonts() — الاسم الصحيح في FontDao هو getTrashFonts()
        return fontDao.getTrashFonts();
    }

    /**
     * إرجاع عدد الخطوط الموجودة في السلة كـ LiveData.
     * يُستخدم لتحديث العنوان الفرعي في CollapsingToolbarLayout.
     */
    public LiveData<Integer> getTrashedFontsCount() {
        // ✅ إصلاح 2: كان fontDao.getTrashedFontsCount() — الاسم الصحيح هو getTrashFontsCount()
        return fontDao.getTrashFontsCount();
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ النقل إلى السلة (Batch) ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * ينقل مجموعة من الخطوط إلى سلة المحذوفات.
     *
     * لكل خط يتم بالترتيب:
     *  1. فحص الملف العالق (Ghost File): إذا لم يوجد الملف فيزيائياً، يُنظَّف من DB ويُكمل.
     *  2. استدعاء TrashFileManager.moveToTrash() لنقل الملف فعلياً.
     *  3. استدعاء fontDao.moveToTrash() لتحديث قاعدة البيانات.
     *  4. الإبلاغ عن التقدم عبر OnProgressListener.
     *
     * عند فشل نقل ملف بعينه: يُسجَّل الخطأ ويُكمل على الخطوط الأخرى.
     * يُستدعى onBatchComplete في النهاية بعدد النجاحات والفشل.
     *
     * ★ إصلاح الأنيميشن: تم نقل استدعاء BatchOperationState إلى TrashViewModel
     *   لضمان أن setProcessing(false) لا يُستدعى إلا بعد إغلاق الديالوج فعلياً. ★
     *
     * ★ حل المشكلة 2 (Ghost Files): إذا أُغلق التطبيق أثناء عملية النقل، قد تتزامن
     *   قاعدة البيانات والملفات الفيزيائية. عند إعادة المحاولة، يكتشف الكود الملف
     *   العالق ويُنظّف قاعدة البيانات بصمت بدلاً من إخفاق العملية. ★
     *
     * @param context          السياق للوصول إلى نظام الملفات
     * @param fonts            قائمة الخطوط المراد نقلها
     * @param cancelFlag       علامة الإلغاء — تُوقف الحلقة عند تعيينها true
     * @param progressListener مُستمع لإبلاغ TrashViewModel بالتقدم (progress, max)
     * @param completeListener مُستمع النتيجة النهائية
     */
    public void moveToTrashBatch(Context context,
                                  List<FontEntity> fonts,
                                  AtomicBoolean cancelFlag,
                                  OnProgressListener progressListener,
                                  OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                int total     = fonts.size();
                int succeeded = 0;
                int failed    = 0;
                long now      = System.currentTimeMillis();

                for (int i = 0; i < total; i++) {

                    // فحص طلب الإلغاء قبل كل ملف
                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "moveToTrashBatch: cancelled at index " + i);
                        break;
                    }

                    FontEntity font = fonts.get(i);

                    // ★ حل المشكلة 2: تنظيف الملفات العالقة (Ghost Files) ★
                    // إذا كان الملف غير موجود فيزيائياً (قُتل التطبيق أثناء عملية سابقة)،
                    // نُنظّف سجله من قاعدة البيانات ونعدّه نجاحاً لاستمرار شريط التقدم بسلاسة.
                    File sourceFile = new File(font.getPath());
                    if (!sourceFile.exists()) {
                        Log.w(TAG, "Ghost file detected. Removing from DB: " + font.getPath());
                        fontDao.deleteByPath(font.getPath()); // تنظيف قاعدة البيانات
                        succeeded++; // نعتبره نجاحاً لكي يستمر شريط التقدم بسلاسة
                        if (progressListener != null) progressListener.onProgress(i + 1, total);
                        continue;
                    }

                    // ★ الخطوة 1: نقل الملف إلى .Trash عبر نظام الملفات ★
                    String newTrashedPath = TrashFileManager.moveToTrash(
                            context, font.getPath());

                    if (newTrashedPath != null) {
                        // ★ الخطوة 2: تحديث قاعدة البيانات بالمسار الجديد وتاريخ الحذف ★
                        try {
                            // ✅ إصلاح 3: كان fontDao.markAsTrashed(path, newPath, path, now, now) بخمسة معاملات.
                            // الاسم الصحيح هو fontDao.moveToTrash() وهو يقبل أربعة معاملات فقط.
                            // المعامل الثالث الزائد كان مكرراً للأول (originalPath)، وقد حُذف لأن
                            // استعلام FontDao يستخدم :originalPath لكلا الغرضين (WHERE و original_path).
                            fontDao.moveToTrash(
                                    font.getPath(),   // originalPath: مفتاح WHERE ويُحفظ في original_path
                                    newTrashedPath,   // path: المسار الجديد داخل .Trash
                                    now,              // deletedAt: وقت النقل إلى السلة
                                    now               // timestamp: updated_at
                            );
                            succeeded++;
                            Log.d(TAG, "Moved to trash [" + (i + 1) + "/" + total + "]: "
                                    + font.getFileName());
                        } catch (Exception e) {
                            Log.e(TAG, "DB update failed after moving: " + font.getPath(), e);
                            fontDao.deleteByPath(font.getPath()); // تنظيف إضافي في حال التعارض
                            succeeded++;
                        }
                    } else {
                        Log.e(TAG, "Failed to move file to trash: " + font.getPath());
                        failed++;
                    }

                    // ★ الخطوة 3: الإبلاغ عن التقدم للـ ViewModel ★
                    if (progressListener != null) {
                        int currentProgress = i + 1;
                        progressListener.onProgress(currentProgress, total);
                    }
                }

                Log.d(TAG, "moveToTrashBatch done — succeeded: " + succeeded + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }
            } catch (Exception e) {
                Log.e(TAG, "moveToTrashBatch: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ استعادة من السلة (Batch) ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يستعيد مجموعة من الخطوط من سلة المحذوفات إلى مواقعها الأصلية.
     *
     * لكل خط يتم بالترتيب:
     *  1. فحص الملف العالق (Ghost File): إذا لم يوجد الملف في السلة فيزيائياً، يُنظَّف من DB ويُكمل.
     *  2. استدعاء TrashFileManager.restoreFromTrash() لاستعادة الملف.
     *     (يُعالج تعارض الأسماء تلقائياً بإضافة رقم — الملاحظة 22)
     *  3. استدعاء fontDao.restoreFromTrash() لتحديث قاعدة البيانات
     *     بالمسار الجديد وإعادة تعيين حقول السلة.
     *  4. الإبلاغ عن التقدم.
     *
     * ★ إصلاح الأنيميشن: تم نقل استدعاء BatchOperationState إلى TrashViewModel
     *   لضمان أن setProcessing(false) لا يُستدعى إلا بعد إغلاق الديالوج فعلياً. ★
     *
     * ★ حل المشكلة 2 (Ghost Files): إذا أُغلق التطبيق أثناء عملية الاستعادة، قد تكون
     *   بعض الملفات استُعيدت فيزيائياً دون تحديث قاعدة البيانات. عند إعادة المحاولة،
     *   يكتشف الكود الملف العالق في السلة ويُنظّف سجله بصمت. ★
     *
     * @param context          السياق للوصول إلى نظام الملفات
     * @param fonts            قائمة الخطوط المراد استعادتها
     * @param cancelFlag       علامة الإلغاء
     * @param progressListener مُستمع التقدم
     * @param completeListener مُستمع النتيجة النهائية
     */
    public void restoreBatch(Context context,
                              List<FontEntity> fonts,
                              AtomicBoolean cancelFlag,
                              OnProgressListener progressListener,
                              OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                int total     = fonts.size();
                int succeeded = 0;
                int failed    = 0;
                long now      = System.currentTimeMillis();

                for (int i = 0; i < total; i++) {

                    // فحص طلب الإلغاء قبل كل ملف
                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "restoreBatch: cancelled at index " + i);
                        break;
                    }

                    FontEntity font = fonts.get(i);

                    // ★ حل المشكلة 2: تنظيف الملفات العالقة (Ghost Files) في السلة ★
                    // إذا كان الملف غير موجود في مجلد السلة فيزيائياً (قد يكون استُعيد
                    // جزئياً في عملية سابقة انقطعت)، نُنظّف سجله من قاعدة البيانات
                    // ونعدّه نجاحاً لاستمرار شريط التقدم بسلاسة.
                    File trashedFile = new File(font.getPath());
                    if (!trashedFile.exists()) {
                        Log.w(TAG, "Ghost file detected in trash. Cleaning DB: " + font.getPath());
                        fontDao.deleteFromTrash(font.getPath()); // تنظيف السلة
                        succeeded++;
                        if (progressListener != null) progressListener.onProgress(i + 1, total);
                        continue;
                    }

                    // تحديد المسار الأصلي: إذا لم يكن محفوظاً، نستخدم اسم الملف كمسار احتياطي
                    String originalPath = font.getOriginalPath() != null
                            ? font.getOriginalPath()
                            : font.getPath(); // احتياطي نادر الحدوث

                    // ★ الخطوة 1: استعادة الملف من .Trash عبر نظام الملفات ★
                    // restoreFromTrash تُعالج تعارض الأسماء تلقائياً (الملاحظة 22)
                    String restoredPath = TrashFileManager.restoreFromTrash(
                            context,
                            font.getPath(),    // المسار الحالي داخل .Trash
                            originalPath       // المسار الأصلي المراد الاستعادة إليه
                    );

                    if (restoredPath != null) {
                        // ★ الخطوة 2: تحديث قاعدة البيانات ★
                        // نستخرج اسم الملف من المسار الفعلي (قد يختلف بعد حل التعارض)
                        String newFileName = new java.io.File(restoredPath).getName();
                        try {
                            // ✅ إصلاح 4: كان fontDao.markAsRestored() — الاسم الصحيح هو fontDao.restoreFromTrash()
                            fontDao.restoreFromTrash(
                                    font.getPath(),  // trashedPath: المسار القديم في .Trash (مفتاح WHERE)
                                    restoredPath,    // restoredPath: المسار الجديد بعد الاستعادة
                                    newFileName,     // fileName: اسم الملف الجديد (قد يحمل رقماً عند التعارض)
                                    now              // timestamp: updated_at
                            );
                            succeeded++;
                            Log.d(TAG, "Restored [" + (i + 1) + "/" + total + "]: "
                                    + font.getFileName() + " → " + restoredPath);
                        } catch (Exception e) {
                            Log.e(TAG, "DB update failed after restoring: " + font.getPath(), e);
                            fontDao.deleteFromTrash(font.getPath()); // تنظيف إضافي في حال التعارض
                            succeeded++;
                        }
                    } else {
                        Log.e(TAG, "Failed to restore file: " + font.getPath());
                        failed++;
                    }

                    // ★ الخطوة 3: الإبلاغ عن التقدم ★
                    if (progressListener != null) {
                        int currentProgress = i + 1;
                        progressListener.onProgress(currentProgress, total);
                    }
                }

                Log.d(TAG, "restoreBatch done — succeeded: " + succeeded + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }
            } catch (Exception e) {
                Log.e(TAG, "restoreBatch: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ الحذف النهائي من السلة (Batch) ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يحذف نهائياً مجموعة من الخطوط من سلة المحذوفات.
     * لا يمكن التراجع عن هذه العملية.
     *
     * لكل خط يتم بالترتيب:
     *  1. استدعاء TrashFileManager.deletePermanently() لحذف الملف من .Trash.
     *  2. استدعاء fontDao.deleteByPath() لحذف السجل من قاعدة البيانات.
     *  3. الإبلاغ عن التقدم.
     *
     * ★ إصلاح الأنيميشن: تم نقل استدعاء BatchOperationState إلى TrashViewModel
     *   لضمان أن setProcessing(false) لا يُستدعى إلا بعد إغلاق الديالوج فعلياً. ★
     *
     * @param context          السياق (محفوظ للتوحيد مع باقي الدوال)
     * @param fonts            قائمة الخطوط المراد حذفها نهائياً
     * @param cancelFlag       علامة الإلغاء
     * @param progressListener مُستمع التقدم
     * @param completeListener مُستمع النتيجة النهائية
     */
    public void deletePermanentlyBatch(Context context,
                                        List<FontEntity> fonts,
                                        AtomicBoolean cancelFlag,
                                        OnProgressListener progressListener,
                                        OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                int total     = fonts.size();
                int succeeded = 0;
                int failed    = 0;

                for (int i = 0; i < total; i++) {

                    // فحص طلب الإلغاء قبل كل ملف
                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "deletePermanentlyBatch: cancelled at index " + i);
                        break;
                    }

                    FontEntity font = fonts.get(i);

                    // ★ الخطوة 1: الحذف النهائي من نظام الملفات ★
                    boolean fileDeleted = TrashFileManager.deletePermanently(font.getPath());

                    if (fileDeleted) {
                        // ★ الخطوة 2: حذف السجل من قاعدة البيانات ★
                        try {
                            fontDao.deleteByPath(font.getPath());
                            succeeded++;
                            Log.d(TAG, "Permanently deleted [" + (i + 1) + "/" + total + "]: "
                                    + font.getFileName());
                        } catch (Exception e) {
                            Log.e(TAG, "DB delete failed after file deletion: " + font.getPath(), e);
                            // الملف حُذف لكن السجل لا يزال في DB — نعدّه فشلاً جزئياً
                            failed++;
                        }
                    } else {
                        Log.e(TAG, "Failed to permanently delete file: " + font.getPath());
                        failed++;
                    }

                    // ★ الخطوة 3: الإبلاغ عن التقدم ★
                    if (progressListener != null) {
                        int currentProgress = i + 1;
                        progressListener.onProgress(currentProgress, total);
                    }
                }

                Log.d(TAG, "deletePermanentlyBatch done — succeeded: " + succeeded
                        + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }
            } catch (Exception e) {
                Log.e(TAG, "deletePermanentlyBatch: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ إفراغ السلة بالكامل ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يُفرغ سلة المحذوفات بالكامل بحذف جميع الملفات نهائياً.
     *
     * يجلب أولاً جميع الخطوط في السلة بشكل متزامن من قاعدة البيانات،
     * ثم يُنفّذ حلقة الحذف داخل نفس الـ ExecutorService مباشرةً
     * لتجنب التداخل بين الخيوط.
     *
     * ★ إصلاح الأنيميشن: تم نقل استدعاء BatchOperationState إلى TrashViewModel
     *   لضمان أن setProcessing(false) لا يُستدعى إلا بعد إغلاق الديالوج فعلياً. ★
     *
     * @param context          السياق للوصول إلى نظام الملفات
     * @param cancelFlag       علامة الإلغاء
     * @param progressListener مُستمع التقدم
     * @param completeListener مُستمع النتيجة النهائية
     */
    public void emptyTrash(Context context,
                            AtomicBoolean cancelFlag,
                            OnProgressListener progressListener,
                            OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                // ✅ إصلاح 5: كان fontDao.getTrashedFontsSync() — الاسم الصحيح هو getTrashFontsSync()
                // جلب جميع الخطوط في السلة بشكل متزامن
                List<FontEntity> allTrashed = fontDao.getTrashFontsSync();

                if (allTrashed == null || allTrashed.isEmpty()) {
                    Log.d(TAG, "emptyTrash: trash is already empty");
                    if (completeListener != null) {
                        completeListener.onBatchComplete(0, 0);
                    }
                    return;
                }

                Log.d(TAG, "emptyTrash: deleting " + allTrashed.size() + " items");

                int total     = allTrashed.size();
                int succeeded = 0;
                int failed    = 0;

                for (int i = 0; i < total; i++) {
                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "emptyTrash: cancelled at index " + i);
                        break;
                    }

                    FontEntity font = allTrashed.get(i);
                    boolean fileDeleted = TrashFileManager.deletePermanently(font.getPath());

                    if (fileDeleted) {
                        try {
                            fontDao.deleteByPath(font.getPath());
                            succeeded++;
                        } catch (Exception e) {
                            Log.e(TAG, "DB delete failed during emptyTrash: " + font.getPath(), e);
                            failed++;
                        }
                    } else {
                        Log.e(TAG, "File delete failed during emptyTrash: " + font.getPath());
                        failed++;
                    }

                    if (progressListener != null) {
                        progressListener.onProgress(i + 1, total);
                    }
                }

                Log.d(TAG, "emptyTrash done — succeeded: " + succeeded + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }

            } catch (Exception e) {
                Log.e(TAG, "emptyTrash: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ التنظيف التلقائي للعناصر منتهية الصلاحية ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يحذف نهائياً جميع الخطوط التي تجاوزت مدة الاحتفاظ (30 يوماً).
     *
     * يُستدعى عادةً عند بدء التطبيق أو عند فتح TrashFragment،
     * دون الحاجة إلى إبلاغ المستخدم (عملية صامتة في الخلفية).
     *
     * معيار انتهاء الصلاحية: (currentTime - deleted_at) >= TRASH_EXPIRY_MS
     *
     * ملاحظة: هذه الدالة لا تستدعي BatchOperationState لأنها تعمل
     * بصمت تام في الخلفية دون تأثير على واجهة المستخدم.
     *
     * @param completeListener مُستمع النتيجة (اختياري)
     */
    public void deleteExpiredItems(OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                long expirationThreshold = System.currentTimeMillis() - TRASH_EXPIRY_MS;

                // ✅ إصلاح 6: كان fontDao.getExpiredTrashedFonts() — الاسم الصحيح هو getExpiredTrashFonts()
                // جلب الخطوط التي تجاوز وقت حذفها حد انتهاء الصلاحية
                List<FontEntity> expiredFonts =
                        fontDao.getExpiredTrashFonts(expirationThreshold);

                if (expiredFonts == null || expiredFonts.isEmpty()) {
                    Log.d(TAG, "deleteExpiredItems: no expired items found");
                    if (completeListener != null) {
                        completeListener.onBatchComplete(0, 0);
                    }
                    return;
                }

                Log.d(TAG, "deleteExpiredItems: found " + expiredFonts.size() + " expired items");

                int succeeded = 0;
                int failed    = 0;

                for (FontEntity font : expiredFonts) {
                    boolean fileDeleted = TrashFileManager.deletePermanently(font.getPath());
                    if (fileDeleted) {
                        try {
                            fontDao.deleteByPath(font.getPath());
                            succeeded++;
                        } catch (Exception e) {
                            Log.e(TAG, "DB delete failed for expired item: " + font.getPath(), e);
                            failed++;
                        }
                    } else {
                        failed++;
                    }
                }

                Log.d(TAG, "deleteExpiredItems done — deleted: " + succeeded
                        + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }

            } catch (Exception e) {
                Log.e(TAG, "deleteExpiredItems: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ واجهات الاستدعاء (Callback Interfaces) ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * مُستمع التقدم — يُستدعى بعد إتمام معالجة كل ملف.
     * يعمل على خيط الخلفية؛ على الـ ViewModel استخدام Handler أو
     * postValue() لتحديث الـ UI على الخيط الرئيسي.
     *
     * @param current العدد المُعالَج حتى الآن (مثلاً: 3)
     * @param total   إجمالي الملفات المراد معالجتها (مثلاً: 5)
     */
    public interface OnProgressListener {
        void onProgress(int current, int total);
    }

    /**
     * مُستمع اكتمال العملية الجماعية.
     *
     * @param succeeded عدد الملفات التي عولجت بنجاح
     * @param failed    عدد الملفات التي فشلت معالجتها
     */
    public interface OnBatchCompleteListener {
        void onBatchComplete(int succeeded, int failed);
    }
                    }
