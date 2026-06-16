package com.example.oneuiapp.fragment.localfont.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.oneuiapp.data.dao.FontDao;
import com.example.oneuiapp.data.database.AppDatabase;
import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.data.entity.FontFileInfo;
import com.example.oneuiapp.fragment.localfont.fontdirectory.LocalFontDirectory;
import com.example.oneuiapp.metadata.FontMetadataExtractor;
import com.example.oneuiapp.metadata.FontWeightWidthExtractor; // ★ جديد ★

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * LocalFontRepository - محدّث لتطبيق "Data Pre-fetching"
 *
 * التحسينات الرئيسية:
 * 1. استخراج فوري للأسماء الحقيقية عند المسح الأول
 * 2. استخراج موازي في الخلفية للخطوط الجديدة
 * 3. ضمان وجود البيانات جاهزة قبل العرض
 * 4. دعم إعادة التسمية والحذف للتحديد المتعدد
 * ★ الإضافة: استخراج فوري وخلفي لوصف الوزن والعرض (weight_width_label) ★
 * ★ الإضافة: دعم كامل لقائمة المفضلة (إضافة، إزالة، استعلام، بحث، ترتيب) ★
 *
 * ★ الإصلاح: توحيد أسماء دوال الفرز للمفضلة لتتطابق مع تعريفاتها في FontDao ★
 *   كانت: getFavoritesSortedByName() → الصحيح: getFavoriteFontsSortedByName()
 *   (نفس الإصلاح مُطبَّق على جميع دوال الفرز الست للمفضلة)
 *
 * ★ إصلاح الأنيميشن (خطة الإصلاح — الخطوة الثانية):
 *   تم نقل التحكم في BatchOperationState من updateFavoriteStatusBatch
 *   إلى LocalFontListViewModel.toggleFavoritesBatch() لضمان أن
 *   setProcessing(false) لا يُستدعى إلا بعد اكتمال الديالوج. ★
 *
 * ★ إصلاح اللاج (خطة الإصلاح — مشكلتا الاستخراج):
 *   استبدال الاستخراج المتوازي في الخلفية بنظام متسلسل مع إشعار الـ ViewModel
 *   عند اكتمال الاستخراج التام عبر OnSyncCompleteListener.onFullExtractionComplete().
 *   هذا يمنع Room من إرسال تحديثات متكررة للواجهة أثناء الاستخراج. ★
 *
 * ★ الإصلاح الجذري للاج والانهيار (خطة الإصلاح — الخطوات 1-4):
 *   تجميع تحديثات قاعدة البيانات في معاملة واحدة (Transaction) بدلاً من
 *   تنفيذ 1400 تحديث متتالي (700 للأسماء + 700 للأوزان)، مما كان يُغرق
 *   Invalidation Tracker في Room ويُنهار التطبيق (OOM Crash).
 *   الإصلاح: تجميع التحديثات في قوائم ثم تطبيقها دفعة واحدة عبر
 *   database.runInTransaction() فلا يُرسل Room سوى تحديثَين فقط. ★
 *
 * ★ إصلاح المجلد الفارغ (خطة الإصلاح — الخطوة 2):
 *   عند اختيار مجلد فارغ، كان الكود يتوقف دون حذف خطوط المجلد القديم.
 *   الإصلاح: حذف الخطوط الموجودة في قاعدة البيانات عند تفريغ المجلد. ★
 */
public class LocalFontRepository {

    private static final String TAG = "LocalFontRepository";
    private static volatile LocalFontRepository INSTANCE;

    private final FontDao fontDao;
    private final ExecutorService executorService;
    private final AppDatabase database; // ★ الإضافة: الاحتفاظ بمرجع لقاعدة البيانات

    public enum SortType {
        NAME,
        DATE,
        SIZE
    }

    private LocalFontRepository(Context context) {
        database = AppDatabase.getInstance(context); // ★ حفظ المرجع
        fontDao = database.fontDao();
        executorService = AppDatabase.databaseWriteExecutor;
    }

    public static LocalFontRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (LocalFontRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LocalFontRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<FontEntity>> getAllFonts() {
        return fontDao.getAllFonts();
    }

    public LiveData<List<FontEntity>> getLocalFonts() {
        return fontDao.getLocalFonts();
    }

    public LiveData<FontEntity> getFontByPath(String path) {
        return fontDao.getFontByPath(path);
    }

    public LiveData<Integer> getTotalFontsCount() {
        return fontDao.getTotalFontsCount();
    }

    public LiveData<Integer> getLocalFontsCount() {
        return fontDao.getLocalFontsCount();
    }

    public LiveData<List<FontEntity>> getFontsSortedByName(boolean isSystem, boolean ascending) {
        if (ascending) {
            return fontDao.getFontsSortedByName(isSystem);
        } else {
            return fontDao.getFontsSortedByNameDesc(isSystem);
        }
    }

    public LiveData<List<FontEntity>> getFontsSortedByDate(boolean isSystem, boolean ascending) {
        if (ascending) {
            return fontDao.getFontsSortedByDate(isSystem);
        } else {
            return fontDao.getFontsSortedByDateDesc(isSystem);
        }
    }

    public LiveData<List<FontEntity>> getFontsSortedBySize(boolean isSystem, boolean ascending) {
        if (ascending) {
            return fontDao.getFontsSortedBySize(isSystem);
        } else {
            return fontDao.getFontsSortedBySizeDesc(isSystem);
        }
    }

    public LiveData<List<FontEntity>> searchFonts(boolean isSystem, String query) {
        return fontDao.searchFonts(isSystem, query);
    }

    // ════════════════════════════════════════════════════════════
    // ★ Favorites — قائمة المفضلة ★
    // جميع الدوال الخاصة بالمفضلة مجمّعة في قسم واحد
    // ════════════════════════════════════════════════════════════

    /**
     * إرجاع جميع الخطوط المفضلة كـ LiveData لمراقبة التغييرات تلقائياً
     */
    public LiveData<List<FontEntity>> getFavoriteFonts() {
        return fontDao.getFavoriteFonts();
    }

    /**
     * إرجاع عدد الخطوط المفضلة كـ LiveData
     */
    public LiveData<Integer> getFavoriteFontsCount() {
        return fontDao.getFavoriteFontsCount();
    }

    /**
     * الخطوط المفضلة مرتبة حسب الاسم
     *
     * ★ الإصلاح: استخدام الاسم الصحيح للدالة في FontDao ★
     * getFavoriteFontsSortedByName() بدلاً من getFavoritesSortedByName()
     */
    public LiveData<List<FontEntity>> getFavoritesSortedByName(boolean ascending) {
        if (ascending) {
            return fontDao.getFavoriteFontsSortedByName();
        } else {
            return fontDao.getFavoriteFontsSortedByNameDesc();
        }
    }

    /**
     * الخطوط المفضلة مرتبة حسب التاريخ
     *
     * ★ الإصلاح: استخدام الاسم الصحيح للدالة في FontDao ★
     * getFavoriteFontsSortedByDate() بدلاً من getFavoritesSortedByDate()
     */
    public LiveData<List<FontEntity>> getFavoritesSortedByDate(boolean ascending) {
        if (ascending) {
            return fontDao.getFavoriteFontsSortedByDate();
        } else {
            return fontDao.getFavoriteFontsSortedByDateDesc();
        }
    }

    /**
     * الخطوط المفضلة مرتبة حسب الحجم
     *
     * ★ الإصلاح: استخدام الاسم الصحيح للدالة في FontDao ★
     * getFavoriteFontsSortedBySize() بدلاً من getFavoritesSortedBySize()
     */
    public LiveData<List<FontEntity>> getFavoritesSortedBySize(boolean ascending) {
        if (ascending) {
            return fontDao.getFavoriteFontsSortedBySize();
        } else {
            return fontDao.getFavoriteFontsSortedBySizeDesc();
        }
    }

    /**
     * البحث في الخطوط المفضلة
     */
    public LiveData<List<FontEntity>> searchFavoriteFonts(String query) {
        return fontDao.searchFavoriteFonts(query);
    }

    /**
     * ★ تحديث حالة المفضلة لخط واحد ★
     *
     * تُستخدم عند الضغط على أيقونة المفضلة في القائمة أو
     * عند تحديد عنصر واحد فقط في وضع التحديد المتعدد
     *
     * @param path       مسار الخط المراد تعديل حالته
     * @param isFavorite true للإضافة إلى المفضلة، false للإزالة
     * @param listener   مستمع النتيجة (يعمل على خيط الخلفية)
     */
    public void updateFavoriteStatus(String path, boolean isFavorite, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.updateFavoriteStatus(path, isFavorite, System.currentTimeMillis());
                Log.d(TAG, "★ Favorite status updated: " + path + " → " + isFavorite);
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update favorite status", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    /**
     * ★ تحديث حالة المفضلة لمجموعة خطوط (التحديد المتعدد) ★
     *
     * المنطق المتبع (مطابق لـ Samsung Notes):
     * - إذا كانت العناصر المحددة مزيجاً من مفضلة وغير مفضلة → إضافة الجميع
     * - إذا كانت جميعها مفضلة → إزالة الجميع
     * - إذا كانت جميعها غير مفضلة → إضافة الجميع
     *
     * ★ إصلاح الأنيميشن: تم نقل استدعاء BatchOperationState إلى
     *   LocalFontListViewModel.toggleFavoritesBatch() لضمان أن
     *   setProcessing(false) لا يُستدعى إلا بعد اكتمال الديالوج.
     *   هذه الدالة مسؤولة فقط عن تحديث قاعدة البيانات وإخبار الـ ViewModel
     *   بالنتيجة عبر OnCompleteListener. ★
     *
     * @param paths      قائمة مسارات الخطوط المراد تعديلها
     * @param isFavorite true للإضافة إلى المفضلة، false للإزالة
     * @param listener   مستمع النتيجة
     */
    public void updateFavoriteStatusBatch(List<String> paths, boolean isFavorite,
                                          OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                int totalRows = 0;
                for (String path : paths) {
                    totalRows += fontDao.updateFavoriteStatus(path, isFavorite, now);
                }
                Log.d(TAG, "★ Batch favorite status updated: " + paths.size()
                    + " fonts → " + isFavorite);
                if (listener != null) {
                    listener.onComplete(totalRows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to batch update favorite status", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    // CRUD Operations
    // ════════════════════════════════════════════════════════════

    public void insert(FontEntity font, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                long id = fontDao.insert(font);
                if (listener != null) {
                    listener.onComplete(id > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to insert font", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void insertAll(List<FontEntity> fonts, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                List<Long> ids = fontDao.insertAll(fonts);
                if (listener != null) {
                    listener.onComplete(ids != null && ids.size() == fonts.size());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to insert fonts", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void update(FontEntity font, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.update(font);
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update font", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void delete(FontEntity font, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.delete(font);
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete font", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void deleteByPath(String path, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.deleteByPath(path);
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete font by path", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void updateCacheStatus(String path, boolean isCached) {
        executorService.execute(() -> {
            try {
                fontDao.updateCacheStatus(path, isCached, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to update cache status", e);
            }
        });
    }

    public void recordAccess(String path) {
        executorService.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                fontDao.recordAccess(path, now, now);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record access", e);
            }
        });
    }

    public void updateRealName(String path, String realName) {
        executorService.execute(() -> {
            try {
                fontDao.updateRealName(path, realName, System.currentTimeMillis());
                Log.d(TAG, "Updated real name for: " + path + " = " + realName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update real name", e);
            }
        });
    }

    /**
     * ★ تحديث مسار واسم الخط بعد إعادة التسمية ★
     *
     * هذه الدالة تُستخدم بعد إعادة تسمية الملف الفعلي لتحديث
     * المسار واسم الملف في قاعدة البيانات
     *
     * @param oldPath     المسار القديم للملف
     * @param newPath     المسار الجديد للملف
     * @param newFileName اسم الملف الجديد
     */
    public void updatePath(String oldPath, String newPath, String newFileName) {
        executorService.execute(() -> {
            try {
                fontDao.updatePath(oldPath, newPath, newFileName, System.currentTimeMillis());
                Log.d(TAG, "Updated font path: " + oldPath + " -> " + newPath);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update path", e);
            }
        });
    }

    /**
     * ★ التحسين الرئيسي: مزامنة الخطوط مع استخراج فوري للأسماء ★
     *
     * الخطوات:
     * 1. مسح المجلد وإضافة الخطوط لقاعدة البيانات
     * 2. استخراج الأسماء الحقيقية فوراً للخطوط الصغيرة (< 2MB)
     * 3. استخراج وصف الوزن والعرض فوراً لجميع الخطوط الجديدة
     * 4. استخراج متسلسل في الخلفية للخطوط المتبقية، يُشعر الـ ViewModel
     *    عبر onFullExtractionComplete() عند الانتهاء التام
     *
     * النتيجة: البيانات جاهزة في الذاكرة عند فتح القائمة، والواجهة
     * تُحدَّث دفعة واحدة فقط بعد اكتمال الاستخراج الكامل
     */
    public void loadAndSyncLocalFonts(String folderPath, OnSyncCompleteListener listener) {
        executorService.execute(() -> {
            try {
                // ★ استخدام LocalFontDirectory بدلاً من FontDirectoryManager ★
                List<FontFileInfo> filesInFolder =
                    LocalFontDirectory.getFontsInDirectory(folderPath);

                if (filesInFolder == null || filesInFolder.isEmpty()) {
                    // ★ الإصلاح: حذف الخطوط القديمة من قاعدة البيانات إذا كان المجلد الجديد فارغاً ★
                    List<FontEntity> existingFonts = fontDao.getLocalFontsSync();
                    int deletedCount = 0;
                    if (existingFonts != null && !existingFonts.isEmpty()) {
                        for (FontEntity existing : existingFonts) {
                            fontDao.delete(existing);
                            deletedCount++;
                        }
                    }

                    if (listener != null) {
                        listener.onSyncComplete(0, 0, deletedCount);
                        listener.onFullExtractionComplete();
                    }
                    return;
                }

                List<FontEntity> existingFonts = fontDao.getLocalFontsSync();
                List<FontEntity> fontsToAdd = new ArrayList<>();
                int updated = 0;
                int deleted = 0;

                for (FontFileInfo fileInfo : filesInFolder) {
                    FontEntity existing = findByPath(existingFonts, fileInfo.getPath());

                    if (existing == null) {
                        FontEntity newFont = createFontEntityFromFile(fileInfo, false);

                        // ★ استخراج فوري للأسماء الحقيقية للخطوط الصغيرة ★
                        if (fileInfo.getSize() < 2 * 1024 * 1024) { // أقل من 2MB
                            try {
                                File fontFile = new File(fileInfo.getPath());
                                String realName = FontMetadataExtractor.extractFontName(fontFile, 0);
                                if (realName != null && !realName.isEmpty() &&
                                    !realName.equals("Unknown Font")) {
                                    newFont.setRealName(realName);
                                    Log.d(TAG, "★ Instantly extracted name: " + realName);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Quick extraction failed: " + e.getMessage());
                            }
                        }

                        // ★ استخراج فوري لوصف الوزن والعرض لجميع الخطوط الجديدة ★
                        // العملية سريعة لأنها تقرأ بضعة بايتات فقط من جدول OS/2
                        // ولا تتأثر بحجم الخط، لذا تُطبَّق على جميع الخطوط وليس الصغيرة فحسب
                        try {
                            File fontFile = new File(fileInfo.getPath());
                            String weightWidthLabel = FontWeightWidthExtractor.extract(fontFile, 0);
                            newFont.setWeightWidthLabel(weightWidthLabel);
                            Log.d(TAG, "★ Instantly extracted weight/width: " + weightWidthLabel);
                        } catch (Exception e) {
                            Log.w(TAG, "Quick weight/width extraction failed: " + e.getMessage());
                        }

                        fontsToAdd.add(newFont);
                    } else if (existing.getLastModified() != fileInfo.getLastModified()) {
                        existing.setLastModified(fileInfo.getLastModified());
                        existing.setSize(fileInfo.getSize());
                        existing.setUpdatedAt(System.currentTimeMillis());
                        fontDao.update(existing);
                        updated++;
                    }
                }

                for (FontEntity existing : existingFonts) {
                    if (!fileExistsInList(filesInFolder, existing.getPath())) {
                        fontDao.delete(existing);
                        deleted++;
                    }
                }

                if (!fontsToAdd.isEmpty()) {
                    fontDao.insertAll(fontsToAdd);
                }

                if (listener != null) {
                    listener.onSyncComplete(fontsToAdd.size(), updated, deleted);
                }

                // ★ تشغيل الاستخراج بشكل متسلسل وإخبار الـ ViewModel عند الانتهاء التام ★
                // السبب: الاستخراج المتوازي كان يُرسل 700 تحديث متتالي للـ Room LiveData،
                // كل تحديث يُعيد إرسال القائمة الكاملة للواجهة → Over-emission → لاج شديد.
                // الحل: نُشغّل الاستخراجَين بشكل متسلسل في خيط واحد، ثم نُشعر الـ ViewModel
                // عبر onFullExtractionComplete() الذي يرفع الحجز ويُرسل البيانات دفعة واحدة.
                executorService.execute(() -> {
                    extractRealNamesSync();
                    extractWeightWidthSync();

                    if (listener != null) {
                        listener.onFullExtractionComplete();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to sync local fonts", e);
                if (listener != null) {
                    listener.onSyncComplete(0, 0, 0);
                    listener.onFullExtractionComplete();
                }
            }
        });
    }

    private FontEntity createFontEntityFromFile(FontFileInfo fileInfo, boolean isSystem) {
        FontEntity entity = new FontEntity(fileInfo.getPath(), fileInfo.getName());
        entity.setSize(fileInfo.getSize());
        entity.setLastModified(fileInfo.getLastModified());
        entity.setSystemFont(isSystem);
        entity.setTtcIndex(0);

        String fileName = fileInfo.getName().toLowerCase();
        if (fileName.endsWith(".ttc")) {
            entity.setFontType("TTC");
        } else if (fileName.endsWith(".otf")) {
            entity.setFontType("OTF");
        } else {
            entity.setFontType("TTF");
        }

        return entity;
    }

    private FontEntity findByPath(List<FontEntity> fonts, String path) {
        for (FontEntity font : fonts) {
            if (font.getPath().equals(path)) {
                return font;
            }
        }
        return null;
    }

    private boolean fileExistsInList(List<FontFileInfo> files, String path) {
        for (FontFileInfo file : files) {
            if (file.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★ استخراج موازي ذكي مع أولوية للخطوط الصغيرة (الأسماء الحقيقية) ★
     *
     * ملاحظة: هذه الدالة لا تُستدعى من loadAndSyncLocalFonts بعد الإصلاح.
     * تم استبدالها بـ extractRealNamesSync() لمنع Over-emission.
     * محفوظة للاستخدام المستقل إن احتجنا لاستخراج خلفي منفصل.
     */
    private void extractRealNamesInBackgroundPrioritized(String folderPath) {
        executorService.execute(() -> {
            try {
                List<FontEntity> fontsWithoutNames = fontDao.getFontsWithoutRealName(500);

                // ترتيب حسب الحجم (الأصغر أولاً)
                fontsWithoutNames.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

                int extracted = 0;
                for (FontEntity font : fontsWithoutNames) {
                    if (!font.isSystemFont()) {
                        try {
                            File fontFile = new File(font.getPath());
                            if (fontFile.exists() && fontFile.length() < 10 * 1024 * 1024) {
                                String realName = FontMetadataExtractor.extractFontName(
                                    fontFile, font.getTtcIndex());

                                if (realName != null && !realName.isEmpty() &&
                                    !realName.equals("Unknown Font")) {
                                    fontDao.updateRealName(font.getPath(), realName,
                                        System.currentTimeMillis());
                                    extracted++;

                                    if (extracted % 10 == 0) {
                                        Log.d(TAG, "Background extraction: " + extracted + " names extracted");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to extract name in background: " + e.getMessage());
                        }
                    }
                }

                Log.d(TAG, "★ Background extraction complete: " + extracted + " total names extracted");

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract real names in background", e);
            }
        });
    }

    /**
     * ★ استخراج وصف الوزن والعرض في الخلفية للخطوط المحلية التي لم تُعالَج بعد ★
     *
     * تُستدعى بعد كل مزامنة للتأكد من أن جميع الخطوط — بما فيها الموجودة مسبقاً
     * في قاعدة البيانات قبل إضافة هذه الميزة — تحمل وصفاً لوزنها وعرضها.
     * تستخدم getFontsWithoutWeightWidth() للعثور على الخطوط غير المعالَجة،
     * وتُرتّبها من الأصغر إلى الأكبر للحصول على أسرع نتيجة أولى.
     *
     * ملاحظة: هذه الدالة لا تُستدعى من loadAndSyncLocalFonts بعد الإصلاح.
     * تم استبدالها بـ extractWeightWidthSync() لمنع Over-emission.
     * محفوظة للاستخدام المستقل إن احتجنا لاستخراج خلفي منفصل.
     */
    private void extractWeightWidthInBackground() {
        executorService.execute(() -> {
            try {
                List<FontEntity> fontsWithoutLabel = fontDao.getFontsWithoutWeightWidth(500);

                // ترتيب حسب الحجم (الأصغر أولاً) لضمان أسرع استجابة
                fontsWithoutLabel.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

                int extracted = 0;
                for (FontEntity font : fontsWithoutLabel) {
                    if (!font.isSystemFont()) {
                        try {
                            File fontFile = new File(font.getPath());
                            if (fontFile.exists()) {
                                String label = FontWeightWidthExtractor.extract(
                                    fontFile, font.getTtcIndex());
                                fontDao.updateWeightWidthLabel(
                                    font.getPath(), label, System.currentTimeMillis());
                                extracted++;

                                if (extracted % 10 == 0) {
                                    Log.d(TAG, "Weight/width extraction: " + extracted + " labels extracted");
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to extract weight/width in background: " + e.getMessage());
                        }
                    }
                }

                Log.d(TAG, "★ Weight/width extraction complete: " + extracted + " labels extracted");

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract weight/width labels in background", e);
            }
        });
    }

    /**
     * ★ دالة متزامنة لاستخراج الأسماء الحقيقية بتجميع التحديثات في معاملة واحدة ★
     *
     * تُستدعى من داخل خيط executorService في loadAndSyncLocalFonts،
     * لذا لا تحتاج إلى إنشاء خيط إضافي. التنفيذ المتسلسل هنا هو
     * الحل الجذري لمشكلة Over-emission: Room لا يُرسل تحديثات للواجهة
     * إلا عند انتهاء هذه الدالة وانتهاء extractWeightWidthSync()،
     * حينها يستدعي الـ ViewModel onFullExtractionComplete() ويرسل
     * البيانات الكاملة للواجهة دفعة واحدة بدون لاج.
     *
     * ★ الإصلاح الجذري للاج: تجميع جميع التحديثات في قائمة أولاً ثم تطبيقها
     *   دفعة واحدة عبر database.runInTransaction() بدلاً من تنفيذ 700 تحديث
     *   منفرد يُغرق Invalidation Tracker في Room ويُنهار التطبيق (OOM Crash). ★
     */
    private void extractRealNamesSync() {
        try {
            List<FontEntity> fontsWithoutNames = fontDao.getFontsWithoutRealName(500);

            // ترتيب حسب الحجم (الأصغر أولاً)
            fontsWithoutNames.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

            // ★ تجميع التحديثات بدلاً من تحديث قاعدة البيانات عنصراً تلو الآخر ★
            class NameUpdate {
                String path; String realName;
                NameUpdate(String path, String realName) { this.path = path; this.realName = realName; }
            }
            List<NameUpdate> updates = new ArrayList<>();

            for (FontEntity font : fontsWithoutNames) {
                if (!font.isSystemFont()) {
                    try {
                        File fontFile = new File(font.getPath());
                        if (fontFile.exists() && fontFile.length() < 10 * 1024 * 1024) {
                            String realName = FontMetadataExtractor.extractFontName(
                                fontFile, font.getTtcIndex());
                            if (realName != null && !realName.isEmpty() &&
                                !realName.equals("Unknown Font")) {
                                // ★ تخزين التحديث بدلاً من تنفيذه فوراً ★
                                updates.add(new NameUpdate(font.getPath(), realName));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to extract name: " + e.getMessage());
                    }
                }
            }

            // ★ تطبيق جميع التحديثات في معاملة واحدة (Transaction) ★
            // هذا يمنع Room من إرسال مئات التحديثات ويحل مشكلة اختناق المعالج والانهيار كلياً
            if (!updates.isEmpty()) {
                database.runInTransaction(() -> {
                    long now = System.currentTimeMillis();
                    for (NameUpdate update : updates) {
                        fontDao.updateRealName(update.path, update.realName, now);
                    }
                });
            }

            Log.d(TAG, "★ extractRealNamesSync complete: " + updates.size() + " names extracted");

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract real names", e);
        }
    }

    /**
     * ★ دالة متزامنة لاستخراج وصف الوزن والعرض بتجميع التحديثات في معاملة واحدة ★
     *
     * تُستدعى من داخل خيط executorService في loadAndSyncLocalFonts
     * بعد انتهاء extractRealNamesSync()، لضمان التنفيذ المتسلسل الكامل.
     * عند انتهائها يُستدعى listener.onFullExtractionComplete() فيرفع الـ ViewModel
     * الحجز ويُرسل البيانات الكاملة للواجهة دفعة واحدة بدون لاج.
     *
     * ★ الإصلاح الجذري للاج: تجميع جميع التحديثات في قائمة أولاً ثم تطبيقها
     *   دفعة واحدة عبر database.runInTransaction() بدلاً من تنفيذ 700 تحديث
     *   منفرد يُغرق Invalidation Tracker في Room ويُنهار التطبيق (OOM Crash). ★
     */
    private void extractWeightWidthSync() {
        try {
            List<FontEntity> fontsWithoutLabel = fontDao.getFontsWithoutWeightWidth(500);

            // ترتيب حسب الحجم (الأصغر أولاً) لضمان أسرع استجابة
            fontsWithoutLabel.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

            // ★ تجميع التحديثات ★
            class LabelUpdate {
                String path; String label;
                LabelUpdate(String path, String label) { this.path = path; this.label = label; }
            }
            List<LabelUpdate> updates = new ArrayList<>();

            for (FontEntity font : fontsWithoutLabel) {
                if (!font.isSystemFont()) {
                    try {
                        File fontFile = new File(font.getPath());
                        if (fontFile.exists()) {
                            String label = FontWeightWidthExtractor.extract(
                                fontFile, font.getTtcIndex());
                            updates.add(new LabelUpdate(font.getPath(), label));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to extract weight/width: " + e.getMessage());
                    }
                }
            }

            // ★ تنفيذ جميع التحديثات دفعة واحدة في معاملة واحدة ★
            if (!updates.isEmpty()) {
                database.runInTransaction(() -> {
                    long now = System.currentTimeMillis();
                    for (LabelUpdate update : updates) {
                        fontDao.updateWeightWidthLabel(update.path, update.label, now);
                    }
                });
            }

            Log.d(TAG, "★ extractWeightWidthSync complete: " + updates.size() + " labels extracted");

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract weight/width labels", e);
        }
    }

    public interface OnCompleteListener {
        void onComplete(boolean success);
    }

    public interface OnSyncCompleteListener {
        void onSyncComplete(int added, int updated, int deleted);
        void onFullExtractionComplete(); // ★ إضافة جديدة لمعرفة انتهاء استخراج الأسماء والأوزان
    }
                                }
