package com.oneui.fontviewer.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.oneui.fontviewer.data.entity.FontEntity;

import java.util.List;

/**
 * FontDao - محسّن مع دوال فعّالة لإدارة الكاش
 */
@Dao
public interface FontDao {
    
    // ════════════════════════════════════════════════════════════
    // عمليات الإدراج والتحديث والحذف الأساسية
    // ════════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(FontEntity font);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<FontEntity> fonts);
    
    @Update
    int update(FontEntity font);
    
    @Delete
    int delete(FontEntity font);
    
    @Query("DELETE FROM fonts WHERE id = :fontId")
    int deleteById(long fontId);
    
    @Query("DELETE FROM fonts WHERE path = :path")
    int deleteByPath(String path);
    
    @Query("DELETE FROM fonts WHERE is_system_font = 0")
    int deleteAllLocalFonts();
    
    @Query("DELETE FROM fonts WHERE is_system_font = 1")
    int deleteAllSystemFonts();
    
    @Query("DELETE FROM fonts")
    int deleteAll();
    
    // ════════════════════════════════════════════════════════════
    // استعلامات الاسترجاع الأساسية
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM fonts WHERE id = :fontId")
    LiveData<FontEntity> getFontById(long fontId);
    
    @Query("SELECT * FROM fonts WHERE id = :fontId")
    FontEntity getFontByIdSync(long fontId);
    
    @Query("SELECT * FROM fonts WHERE path = :path LIMIT 1")
    LiveData<FontEntity> getFontByPath(String path);
    
    @Query("SELECT * FROM fonts WHERE path = :path LIMIT 1")
    FontEntity getFontByPathSync(String path);
    
    @Query("SELECT * FROM fonts")
    LiveData<List<FontEntity>> getAllFonts();
    
    @Query("SELECT * FROM fonts")
    List<FontEntity> getAllFontsSync();
    
    // ════════════════════════════════════════════════════════════
    // استعلامات حسب النوع (خطوط النظام / المجلدات)
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 1 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getSystemFonts();
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 1 ORDER BY file_name ASC")
    List<FontEntity> getSystemFontsSync();
    
    // ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات ★
    @Query("SELECT * FROM fonts WHERE is_system_font = 0 AND is_trashed = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getLocalFonts();
    
    // ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات ★
    @Query("SELECT * FROM fonts WHERE is_system_font = 0 AND is_trashed = 0 ORDER BY file_name ASC")
    List<FontEntity> getLocalFontsSync();
    
    // ════════════════════════════════════════════════════════════
    // استعلامات الفرز
    // ════════════════════════════════════════════════════════════
    
    // ★ مُحدَّثة: استثناء الخطوط الموجودة في سلة المحذوفات من نتائج الفرز ★
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getFontsSortedByName(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY file_name DESC")
    LiveData<List<FontEntity>> getFontsSortedByNameDesc(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY last_modified ASC")
    LiveData<List<FontEntity>> getFontsSortedByDate(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY last_modified DESC")
    LiveData<List<FontEntity>> getFontsSortedByDateDesc(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY size ASC")
    LiveData<List<FontEntity>> getFontsSortedBySize(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY size DESC")
    LiveData<List<FontEntity>> getFontsSortedBySizeDesc(boolean isSystem);
    
    // ════════════════════════════════════════════════════════════
    // استعلامات البحث
    // ════════════════════════════════════════════════════════════
    
    // ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات من نتائج البحث ★
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    LiveData<List<FontEntity>> searchFonts(boolean isSystem, String query);
    
    // ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات من نتائج البحث ★
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    List<FontEntity> searchFontsSync(boolean isSystem, String query);
    
    // ════════════════════════════════════════════════════════════
    // استعلامات الكاش والاستخدام
    // ════════════════════════════════════════════════════════════
    
    @Query("UPDATE fonts SET is_cached = :isCached, updated_at = :timestamp WHERE path = :path")
    int updateCacheStatus(String path, boolean isCached, long timestamp);
    
    @Query("UPDATE fonts SET last_access_time = :accessTime, " +
           "access_count = access_count + 1, updated_at = :timestamp " +
           "WHERE path = :path")
    int recordAccess(String path, long accessTime, long timestamp);
    
    @Query("SELECT * FROM fonts WHERE is_cached = 1 ORDER BY last_access_time DESC")
    List<FontEntity> getCachedFonts();
    
    @Query("SELECT * FROM fonts ORDER BY access_count DESC LIMIT :limit")
    List<FontEntity> getMostAccessedFonts(int limit);
    
    @Query("SELECT * FROM fonts ORDER BY last_access_time DESC LIMIT :limit")
    List<FontEntity> getRecentlyAccessedFonts(int limit);
    
    // ════════════════════════════════════════════════════════════
    // ★ دوال محسّنة لمسح الكاش (عملية واحدة بدلاً من loop) ★
    // ════════════════════════════════════════════════════════════
    
    /**
     * مسح حالة الكاش لجميع الخطوط دفعة واحدة (عملية SQL واحدة فقط)
     * هذا أسرع بكثير من loop على كل خط
     */
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp")
    int resetAllCacheStatus(long timestamp);
    
    /**
     * مسح حالة الكاش لخطوط النظام فقط دفعة واحدة
     */
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp WHERE is_system_font = 1")
    int resetSystemFontsCacheStatus(long timestamp);
    
    /**
     * مسح حالة الكاش للخطوط المحلية فقط دفعة واحدة
     */
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp WHERE is_system_font = 0")
    int resetLocalFontsCacheStatus(long timestamp);
    
    // ════════════════════════════════════════════════════════════
    // استعلامات الإحصائيات
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT COUNT(*) FROM fonts")
    LiveData<Integer> getTotalFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts")
    int getTotalFontsCountSync();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 1")
    LiveData<Integer> getSystemFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 1")
    int getSystemFontsCountSync();
    
    // ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات من العدد ★
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 0 AND is_trashed = 0")
    LiveData<Integer> getLocalFontsCount();
    
    // ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات من العدد ★
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 0 AND is_trashed = 0")
    int getLocalFontsCountSync();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_variable_font = 1")
    int getVariableFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_cached = 1")
    int getCachedFontsCount();
    
    // ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات من إجمالي الحجم ★
    @Query("SELECT SUM(size) FROM fonts WHERE is_system_font = 0 AND is_trashed = 0")
    long getTotalLocalFontsSize();
    
    // ════════════════════════════════════════════════════════════
    // استعلامات خاصة
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT EXISTS(SELECT 1 FROM fonts WHERE path = :path)")
    boolean fontExists(String path);
    
    @Query("SELECT * FROM fonts WHERE is_variable_font = 1 ORDER BY file_name ASC")
    List<FontEntity> getVariableFonts();
    
    @Query("UPDATE fonts SET real_name = :realName, updated_at = :timestamp WHERE path = :path")
    int updateRealName(String path, String realName, long timestamp);
    
    @Query("SELECT * FROM fonts WHERE real_name IS NULL OR real_name = '' LIMIT :limit")
    List<FontEntity> getFontsWithoutRealName(int limit);
    
    // ════════════════════════════════════════════════════════════
    // ★ دوال إضافية لميزة التحديد المتعدد والحذف وإعادة التسمية ★
    // ════════════════════════════════════════════════════════════
    
    /**
     * تحديث مسار الخط واسم الملف بعد إعادة التسمية
     * تُستخدم في عملية إعادة التسمية لتحديث البيانات في قاعدة البيانات
     */
    @Query("UPDATE fonts SET path = :newPath, file_name = :newFileName, updated_at = :updatedAt WHERE path = :oldPath")
    void updatePath(String oldPath, String newPath, String newFileName, long updatedAt);

    // ════════════════════════════════════════════════════════════
    // ★ استعلامات وصف الوزن والعرض (weight_width_label) ★
    // ════════════════════════════════════════════════════════════

    /**
     * تحديث وصف الوزن والعرض لخط محدد بمساره.
     * يُستدعى بعد الاستخراج عبر FontWeightWidthExtractor.
     *
     * @param path      مسار ملف الخط
     * @param label     الوصف النصي ("Bold, Condensed" أو "VF · Regular" إلخ)
     * @param timestamp وقت التحديث بالميلي ثانية
     */
    @Query("UPDATE fonts SET weight_width_label = :label, updated_at = :timestamp WHERE path = :path")
    int updateWeightWidthLabel(String path, String label, long timestamp);

    /**
     * جلب الخطوط التي لم يُستخرج وصف وزنها وعرضها بعد.
     * تُستخدم في الاستخراج التدريجي في الخلفية.
     *
     * @param limit الحد الأقصى لعدد النتائج المُعادة دفعةً واحدة
     */
    @Query("SELECT * FROM fonts WHERE weight_width_label IS NULL LIMIT :limit")
    List<FontEntity> getFontsWithoutWeightWidth(int limit);

    // ════════════════════════════════════════════════════════════
    // ★ استعلامات المفضلة (Favorites) ★
    // ════════════════════════════════════════════════════════════

    /**
     * تحديث حالة المفضلة لخط محدد بمساره.
     * تُستدعى عند الضغط على زر "مفضلة" أو "غير مفضلة" في وضع التحديد المتعدد.
     *
     * @param path       مسار ملف الخط
     * @param isFavorite true لإضافة المفضلة، false لإزالتها
     * @param timestamp  وقت التحديث بالميلي ثانية
     */
    @Query("UPDATE fonts SET is_favorite = :isFavorite, updated_at = :timestamp WHERE path = :path")
    int updateFavoriteStatus(String path, boolean isFavorite, long timestamp);

    /**
     * جلب جميع الخطوط المفضلة مرتبةً تصاعدياً حسب الاسم.
     * ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات ★
     * يُستخدم كمصدر بيانات رئيسي لـ FavoriteFontListViewModel.
     */
    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getFavoriteFonts();

    /**
     * جلب عدد الخطوط المفضلة.
     * ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات من العدد ★
     * يُستخدم لعرض العداد في عنوان درج التنقل.
     */
    @Query("SELECT COUNT(*) FROM fonts WHERE is_favorite = 1 AND is_trashed = 0")
    LiveData<Integer> getFavoriteFontsCount();

    /**
     * البحث داخل الخطوط المفضلة بالاسم أو الاسم الحقيقي.
     * ★ مُحدَّث: استثناء الخطوط الموجودة في سلة المحذوفات من نتائج البحث ★
     * يدعم ميزة البحث الخاصة بقائمة المفضلة.
     *
     * @param query نص البحث
     */
    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    LiveData<List<FontEntity>> searchFavoriteFonts(String query);

    // ════════════════════════════════════════════════════════════
    // ★ استعلامات الفرز لقائمة المفضلة ★
    // ════════════════════════════════════════════════════════════

    // ★ مُحدَّثة: استثناء الخطوط الموجودة في سلة المحذوفات من قائمة المفضلة ★

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedByName();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name DESC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedByNameDesc();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY last_modified ASC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedByDate();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY last_modified DESC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedByDateDesc();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY size ASC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedBySize();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY size DESC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedBySizeDesc();

    // ════════════════════════════════════════════════════════════
    // ★ استعلامات سلة المحذوفات (Trash) ★
    // ════════════════════════════════════════════════════════════

    /**
     * جلب جميع الخطوط الموجودة في سلة المحذوفات مرتبةً تنازلياً حسب وقت الحذف.
     * يُستخدم كمصدر بيانات رئيسي لـ TrashViewModel.
     */
    @Query("SELECT * FROM fonts WHERE is_trashed = 1 ORDER BY deleted_at DESC")
    LiveData<List<FontEntity>> getTrashFonts();

    /**
     * جلب جميع الخطوط الموجودة في سلة المحذوفات بشكل متزامن.
     * تُستخدم في عمليات الخلفية مثل إفراغ السلة.
     */
    @Query("SELECT * FROM fonts WHERE is_trashed = 1 ORDER BY deleted_at DESC")
    List<FontEntity> getTrashFontsSync();

    /**
     * جلب عدد الخطوط الموجودة في سلة المحذوفات.
     * يُستخدم لعرض العداد في عنوان CollapsingToolbar.
     */
    @Query("SELECT COUNT(*) FROM fonts WHERE is_trashed = 1")
    LiveData<Integer> getTrashFontsCount();

    /**
     * جلب عدد الخطوط الموجودة في سلة المحذوفات بشكل متزامن.
     */
    @Query("SELECT COUNT(*) FROM fonts WHERE is_trashed = 1")
    int getTrashFontsCountSync();

    /**
     * نقل خط إلى سلة المحذوفات.
     * يُحدِّث is_trashed و deleted_at و original_path دفعةً واحدة.
     *
     * @param path         مسار الخط الجديد داخل مجلد Trash
     * @param originalPath المسار الأصلي للخط قبل النقل (للاستعادة لاحقاً)
     * @param deletedAt    وقت النقل إلى السلة بالميلي ثانية
     * @param timestamp    وقت تحديث السجل بالميلي ثانية
     */
    @Query("UPDATE fonts SET is_trashed = 1, deleted_at = :deletedAt, " +
           "original_path = :originalPath, path = :path, updated_at = :timestamp " +
           "WHERE path = :originalPath")
    int moveToTrash(String originalPath, String path, long deletedAt, long timestamp);

    /**
     * استعادة خط من سلة المحذوفات إلى مساره الأصلي.
     * يُعيد تعيين is_trashed و deleted_at و original_path.
     *
     * @param trashedPath  المسار الحالي للخط داخل مجلد Trash
     * @param restoredPath المسار المُستعاد (قد يختلف عن original_path عند التعارض)
     * @param fileName     اسم الملف المُستعاد (قد يحتوي على رقم عند التعارض)
     * @param timestamp    وقت تحديث السجل بالميلي ثانية
     */
    @Query("UPDATE fonts SET is_trashed = 0, deleted_at = 0, original_path = NULL, " +
           "path = :restoredPath, file_name = :fileName, updated_at = :timestamp " +
           "WHERE path = :trashedPath")
    int restoreFromTrash(String trashedPath, String restoredPath, String fileName, long timestamp);

    /**
     * حذف خط من سلة المحذوفات نهائياً (حذف السجل من قاعدة البيانات).
     * يُستخدم بعد حذف الملف الفعلي من مجلد Trash.
     *
     * @param path مسار الخط داخل مجلد Trash
     */
    @Query("DELETE FROM fonts WHERE path = :path AND is_trashed = 1")
    int deleteFromTrash(String path);

    /**
     * إفراغ سلة المحذوفات بالكامل (حذف جميع السجلات من قاعدة البيانات).
     * يُستخدم بعد حذف جميع الملفات الفعلية من مجلد Trash.
     */
    @Query("DELETE FROM fonts WHERE is_trashed = 1")
    int emptyTrash();

    /**
     * جلب الخطوط التي انتهت صلاحيتها في سلة المحذوفات (مضى عليها أكثر من 30 يوماً).
     * تُستخدم في عملية التنظيف التلقائي عند فتح التطبيق.
     *
     * @param expiryTimestamp الحد الزمني: currentTime - 30 * 24 * 60 * 60 * 1000L
     */
    @Query("SELECT * FROM fonts WHERE is_trashed = 1 AND deleted_at <= :expiryTimestamp")
    List<FontEntity> getExpiredTrashFonts(long expiryTimestamp);
    }
