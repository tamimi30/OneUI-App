package com.oneui.fontviewer.fragment.trash.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * TrashFileManager - مدير ملفات سلة المحذوفات
 *
 * يتولى جميع عمليات نقل الملفات من وإلى مجلد .Trash.
 * هذا الملف مسؤول عن:
 *  - إنشاء مجلد .Trash داخل التخزين الداخلي للتطبيق وضمان وجوده.
 *  - نقل ملف خط من مساره الأصلي إلى مجلد .Trash.
 *  - استعادة ملف خط من .Trash إلى مساره الأصلي مع التعامل مع تعارض الأسماء.
 *  - الحذف النهائي لملف من داخل .Trash.
 *
 * ملاحظة معمارية:
 * هذا الملف يتعامل مع نظام الملفات فقط ولا يعرف شيئاً عن قاعدة البيانات.
 * تحديث قاعدة البيانات هو مسؤولية TrashRepository حصراً.
 */
public class TrashFileManager {

    private static final String TAG = "TrashFileManager";

    // اسم مجلد سلة المحذوفات المخفي داخل التخزين الداخلي للتطبيق
    // يبدأ بنقطة ليكون مجلداً مخفياً بحسب اتفاقية أنظمة Unix/Linux
    private static final String TRASH_DIR_NAME = ".Trash";

    // اسم المجلد الاحتياطي لاستعادة الملفات عندما يتعذّر الوصول إلى المسار الأصلي
    // (مثلاً: بطاقة SD مُفصولة أو مجلد أصلي محذوف)
    private static final String FALLBACK_RESTORE_DIR_NAME = "RestoredFonts";

    // ────────────────────────────────────────────────────────────────────────
    // ★ الحصول على مجلد .Trash وإنشاؤه عند الحاجة ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يُعيد كائن File يمثّل مجلد .Trash.
     * يُنشئ المجلد تلقائياً إذا لم يكن موجوداً بعد.
     *
     * المسار الكامل: context.getFilesDir()/.Trash
     *
     * سبب اختيار getFilesDir() بدلاً من التخزين الخارجي:
     *  - لا تظهر الملفات في تطبيقات الملفات الخارجية.
     *  - محمية من الحذف غير المقصود بواسطة المستخدم.
     *  - لا تتطلب أذونات READ/WRITE_EXTERNAL_STORAGE.
     *
     * @param context السياق للوصول إلى مسار التخزين الداخلي
     * @return مجلد .Trash جاهز للاستخدام، أو null إذا تعذّر إنشاؤه
     */
    @Nullable
    public static File getTrashDirectory(@NonNull Context context) {
        File trashDir = new File(context.getFilesDir(), TRASH_DIR_NAME);

        if (!trashDir.exists()) {
            boolean created = trashDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create .Trash directory at: " + trashDir.getAbsolutePath());
                return null;
            }
            Log.d(TAG, ".Trash directory created at: " + trashDir.getAbsolutePath());
        }

        return trashDir;
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ نقل ملف إلى سلة المحذوفات ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يَنقل ملف خط من مساره الأصلي إلى مجلد .Trash.
     *
     * الخطوات التفصيلية:
     *  1. التحقق من وجود الملف الأصلي وإمكانية قراءته.
     *  2. الحصول على مجلد .Trash أو إنشاؤه.
     *  3. إذا كان هناك ملف بنفس الاسم داخل .Trash، يُحلّ التعارض بإضافة رقم.
     *  4. نسخ الملف إلى .Trash باستخدام FileChannel.
     *  5. حذف الملف الأصلي بعد نجاح النسخ.
     *     (إذا فشل الحذف، تُحذف النسخة من .Trash لتجنب التكرار وتُعاد null)
     *
     * @param context      السياق للوصول إلى مجلد .Trash
     * @param originalPath المسار الكامل للملف المراد نقله إلى السلة
     * @return المسار الجديد للملف داخل .Trash، أو null عند الفشل
     */
    @Nullable
    public static String moveToTrash(@NonNull Context context, @NonNull String originalPath) {
        File sourceFile = new File(originalPath);

        // التحقق من وجود الملف الأصلي وإمكانية قراءته
        if (!sourceFile.exists()) {
            Log.w(TAG, "Source file does not exist: " + originalPath);
            return null;
        }
        if (!sourceFile.canRead()) {
            Log.w(TAG, "Source file is not readable: " + originalPath);
            return null;
        }

        // الحصول على مجلد .Trash
        File trashDir = getTrashDirectory(context);
        if (trashDir == null) {
            Log.e(TAG, "Cannot access or create .Trash directory");
            return null;
        }

        // تحديد ملف الوجهة داخل .Trash مع حل أي تعارض في الأسماء
        File destinationFile = resolveNamingConflict(new File(trashDir, sourceFile.getName()));

        // نسخ الملف إلى .Trash
        boolean copied = copyFile(sourceFile, destinationFile);
        if (!copied) {
            Log.e(TAG, "Failed to copy file to .Trash: " + originalPath);
            return null;
        }

        // حذف الملف الأصلي بعد نجاح النسخ
        boolean deleted = sourceFile.delete();
        if (!deleted) {
            // إذا فشل حذف الأصل، نحذف النسخة في .Trash لتجنب التكرار ونُعيد null
            destinationFile.delete();
            Log.e(TAG, "Failed to delete original file after copy, rolled back: " + originalPath);
            return null;
        }

        Log.d(TAG, "Moved to trash: " + originalPath + " → " + destinationFile.getAbsolutePath());
        return destinationFile.getAbsolutePath();
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ استعادة ملف من سلة المحذوفات ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يُعيد ملف خط من .Trash إلى مساره الأصلي.
     *
     * التعامل مع تعارض الأسماء (الملاحظة 22):
     * إذا وُجد ملف بنفس الاسم في المسار الأصلي — مثلاً: أضاف المستخدم خطاً
     * جديداً بنفس الاسم أثناء وجود القديم في السلة — نحل التعارض تلقائياً
     * بإضافة رقم متصاعد: Cairo(1).ttf، Cairo(2).ttf، وهكذا.
     *
     * التعامل مع المسار الأصلي غير المتاح:
     * إذا كان مجلد الوجهة غير موجود (مثلاً: بطاقة SD مُفصولة)، نحاول إنشاءه.
     * إذا تعذّر الإنشاء، نستعيد الملف إلى موقع احتياطي داخلي (RestoredFonts).
     *
     * @param context      السياق للوصول إلى المجلدات
     * @param trashedPath  المسار الحالي للملف داخل .Trash
     * @param originalPath المسار الأصلي الذي يجب إعادة الملف إليه
     * @return المسار الفعلي للملف بعد الاستعادة (قد يختلف الاسم عند التعارض)، أو null عند الفشل
     */
    @Nullable
    public static String restoreFromTrash(@NonNull Context context,
                                          @NonNull String trashedPath,
                                          @NonNull String originalPath) {
        File trashedFile = new File(trashedPath);

        // التحقق من وجود الملف في .Trash
        if (!trashedFile.exists()) {
            Log.w(TAG, "Trashed file does not exist: " + trashedPath);
            return null;
        }

        // تحديد مجلد الوجهة والتحقق منه
        File destinationFile = new File(originalPath);
        File destinationDir  = destinationFile.getParentFile();

        if (destinationDir != null && !destinationDir.exists()) {
            // محاولة إنشاء مجلد الوجهة إذا اختفى (مثلاً: بعد فصل بطاقة SD)
            boolean dirCreated = destinationDir.mkdirs();
            if (!dirCreated) {
                Log.w(TAG, "Cannot create destination directory: "
                        + destinationDir.getAbsolutePath()
                        + " — restoring to fallback location instead");
                // استعادة الملف إلى موقع احتياطي داخل التخزين الداخلي
                return restoreToFallbackLocation(context, trashedFile);
            }
        }

        // حل تعارض الأسماء: إذا كان هناك ملف بنفس الاسم في المسار الوجهة
        File resolvedDestination = resolveNamingConflict(destinationFile);

        // نسخ الملف من .Trash إلى الوجهة
        boolean copied = copyFile(trashedFile, resolvedDestination);
        if (!copied) {
            Log.e(TAG, "Failed to restore file: "
                    + trashedPath + " → " + resolvedDestination.getAbsolutePath());
            return null;
        }

        // حذف الملف من .Trash بعد نجاح الاستعادة
        boolean deleted = trashedFile.delete();
        if (!deleted) {
            // الاستعادة نجحت، لكن يبقى الملف في .Trash أيضاً — نُسجّل تحذيراً فقط
            Log.w(TAG, "Restored successfully but failed to delete from .Trash: " + trashedPath);
        }

        Log.d(TAG, "Restored from trash: " + trashedPath + " → " + resolvedDestination.getAbsolutePath());
        return resolvedDestination.getAbsolutePath();
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ الحذف النهائي من سلة المحذوفات ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يحذف ملفاً نهائياً من مجلد .Trash.
     * لا يمكن التراجع عن هذه العملية بعد تنفيذها.
     *
     * إذا كان الملف غير موجود أصلاً، تُعتبر العملية ناجحة
     * (قد يكون حُذف مسبقاً بطريقة أخرى).
     *
     * @param trashedPath المسار الكامل للملف داخل .Trash
     * @return true إذا نجح الحذف أو إذا كان الملف غير موجود أصلاً
     */
    public static boolean deletePermanently(@NonNull String trashedPath) {
        File file = new File(trashedPath);

        if (!file.exists()) {
            // الملف غير موجود، نعتبره محذوفاً بنجاح
            Log.w(TAG, "File to permanently delete does not exist (already gone?): " + trashedPath);
            return true;
        }

        boolean deleted = file.delete();

        if (deleted) {
            Log.d(TAG, "Permanently deleted: " + trashedPath);
        } else {
            Log.e(TAG, "Failed to permanently delete: " + trashedPath);
        }

        return deleted;
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★ دوال مساعدة خاصة (Private Helpers) ★
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يحل تعارض أسماء الملفات بإضافة رقم متصاعد بين قوسين.
     *
     * إذا كان الملف المُعطى موجوداً بالفعل في نفس المجلد:
     *  - Cairo.ttf موجود        → يجرّب Cairo(1).ttf
     *  - Cairo(1).ttf موجود أيضاً → يجرّب Cairo(2).ttf
     *  - ... وهكذا حتى يجد اسماً متاحاً
     *
     * يحافظ على صيغة الملف (.ttf / .otf / .ttc) في نهاية الاسم.
     *
     * @param targetFile الملف المُراد التحقق من تعارض اسمه
     * @return كائن File بمسار خالٍ من التعارض وجاهز للكتابة
     */
    @NonNull
    private static File resolveNamingConflict(@NonNull File targetFile) {
        if (!targetFile.exists()) {
            // لا يوجد تعارض، أعد الملف كما هو دون تغيير
            return targetFile;
        }

        File   parentDir          = targetFile.getParentFile();
        String fileName           = targetFile.getName();
        String nameWithoutExt     = FileUtils.removeExtension(fileName);
        String extension          = FileUtils.getExtension(fileName);
        String dotExtension       = extension.isEmpty() ? "" : "." + extension;

        // ابحث عن رقم متاح بدءاً من 1
        int  counter   = 1;
        File candidate;
        do {
            String newName = nameWithoutExt + "(" + counter + ")" + dotExtension;
            candidate = new File(parentDir, newName);
            counter++;
        } while (candidate.exists());

        Log.d(TAG, "Naming conflict resolved: " + fileName + " → " + candidate.getName());
        return candidate;
    }

    /**
     * ينسخ ملفاً من مصدر إلى وجهة باستخدام FileChannel لأداء أفضل.
     *
     * عند الفشل: يحذف الملف الوجهة المعطوب إذا كان قد أُنشئ جزئياً.
     *
     * @param source      الملف المصدر المراد نسخه
     * @param destination الملف الوجهة الذي سيُكتب إليه
     * @return true إذا اكتملت عملية النسخ بنجاح
     */
    private static boolean copyFile(@NonNull File source, @NonNull File destination) {
        try (FileInputStream  fis           = new FileInputStream(source);
             FileOutputStream fos           = new FileOutputStream(destination);
             FileChannel      inputChannel  = fis.getChannel();
             FileChannel      outputChannel = fos.getChannel()) {

            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file: "
                    + source.getAbsolutePath() + " → " + destination.getAbsolutePath(), e);
            // حذف الملف الوجهة المعطوب إن وُجد لتجنب ملف تالف
            if (destination.exists()) {
                destination.delete();
            }
            return false;
        }
    }

    /**
     * يستعيد الملف إلى موقع احتياطي داخل التخزين الداخلي للتطبيق.
     *
     * يُستخدم كخطة بديلة عندما يتعذّر الوصول إلى المسار الأصلي،
     * مثلاً: بطاقة SD مُفصولة، أو مجلد الوجهة الأصلي محذوف.
     *
     * الموقع الاحتياطي: context.getFilesDir()/RestoredFonts/
     *
     * @param context     السياق للوصول إلى التخزين الداخلي
     * @param trashedFile الملف الموجود في .Trash المراد استعادته
     * @return المسار الكامل للملف بعد الاستعادة الاحتياطية، أو null عند الفشل
     */
    @Nullable
    private static String restoreToFallbackLocation(@NonNull Context context,
                                                     @NonNull File trashedFile) {
        File fallbackDir = new File(context.getFilesDir(), FALLBACK_RESTORE_DIR_NAME);
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs();
        }

        // حل أي تعارض في الأسماء داخل المجلد الاحتياطي أيضاً
        File destinationFile = resolveNamingConflict(new File(fallbackDir, trashedFile.getName()));
        boolean copied = copyFile(trashedFile, destinationFile);

        if (copied) {
            trashedFile.delete();
            Log.w(TAG, "Restored to fallback location: " + destinationFile.getAbsolutePath());
            return destinationFile.getAbsolutePath();
        }

        Log.e(TAG, "Failed to restore even to fallback location for: " + trashedFile.getAbsolutePath());
        return null;
    }
}
